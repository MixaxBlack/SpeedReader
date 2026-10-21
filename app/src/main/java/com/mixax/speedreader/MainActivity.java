package com.mixax.speedreader;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.text.Editable;
import android.text.TextWatcher;

public class MainActivity extends AppCompatActivity {
    static final int PAPER=0xFFF7F6F3, INK=0xFF252922, MUTED=0xFF62685D, RULE=0xFFDCDDD5, ACCENT=0xFF426650;
    Typeface uiFace;
    static final int OPEN = 41, SCAN_FOLDER=42, PHONE_ACCESS=43, PHONE_READ=44;
    final Handler handler = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    android.content.SharedPreferences prefs;
    Book book;
    int position, speed, fontSize, font, theme, foreground, background;
    boolean playing, settings, busy;
    String language;
    LinearLayout root, content;
    LinearLayout backdropRoot;
    boolean backdropDark;
    int backdropLeft=-1,backdropRight=-1,backdropBottom=-1;
    TextView word, progress, bookTitle, state, speedValue;
    MaterialButton play, faster, slower;
    VerticalSpeedBar speedControl;
    Slider seek;
    boolean libraryOpen,scanning;
    AtomicBoolean scanCancelled=new AtomicBoolean();
    List<BookStore.Entry> libraryEntries=new ArrayList<>();
    List<Uri> pendingScanRoots=Collections.emptyList();
    String libraryQuery="",libraryMessage="";
    TextView libraryStatus,libraryEmpty;
    EditText librarySearch;
    ListView libraryList;
    LibraryAdapter libraryAdapter;
    boolean phoneFilesOpen,phoneScanning,pendingPhoneScan,pendingPhoneAccess;
    List<PhoneScanner.FoundFile> phoneFiles=new ArrayList<>();
    FoundAdapter foundAdapter;
    final String[] faces = {"serif", "sans-serif", "monospace", "sans-serif-condensed"};
    final Runnable tick = new Runnable() {
        public void run() {
            if (!playing || book == null) return;
            if (position >= book.words.size() - 1) { pause(); update(); return; }
            position++; update();
            if (position % 10 == 0) savePosition();
            handler.postDelayed(this, 60000L / speed);
        }
    };
    String tr(String en, String ru) { return russian() ? ru : en; }
    boolean russian() { return "ru".equals(language) || ("system".equals(language) && Locale.getDefault().getLanguage().equals("ru")); }
    int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        uiFace=getResources().getFont(R.font.ui_regular);
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        libraryOpen=saved!=null&&saved.getBoolean("libraryOpen",false);
        phoneFilesOpen=saved!=null&&saved.getBoolean("phoneFilesOpen",false);pendingPhoneAccess=saved!=null&&saved.getBoolean("pendingPhoneAccess",false);
        libraryQuery=saved==null?"":saved.getString("libraryQuery","");
        language = prefs.getString("language", "system");
        if(saved!=null&&saved.getBoolean("scanning",false))libraryMessage=tr("Scan stopped. Saved books remain in the library.","Сканирование остановлено. Добавленные книги сохранены.");
        speed = Math.max(30, Math.min(500, prefs.getInt("speed", 300))); fontSize = prefs.getInt("size", 42); font = prefs.getInt("font", 0);
        theme = prefs.getInt("theme", 0); foreground = prefs.getInt("fg", Themes.fg(Math.max(0,theme))); background = prefs.getInt("bg", Themes.bg(Math.max(0,theme)));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if(libraryOpen){closeLibrary();}
                else if (settings) { settings = false; render(); }
                else { pause(); setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); setEnabled(true); }
            }
        });
        render();
        String last = prefs.getString("last", ""); busy = true; render();
        boolean restoreLibrary=libraryOpen;
        io.execute(() -> {
            Book loaded = null;
            try { if (!last.isEmpty()) loaded = Book.load(new File(getFilesDir(), last + ".json")); } catch (Exception ignored) { }
            try { if (loaded == null) { try (InputStream in = getAssets().open("welcome.fb2")) { loaded = Book.parse(in); } } }
            catch (Exception error) { runOnUiThread(() -> failure(error)); }
            Book result = loaded;
            List<BookStore.Entry> restoredEntries=restoreLibrary?new BookStore(getFilesDir()).entries():new ArrayList<>();
            List<PhoneScanner.FoundFile> restoredFiles=restoreLibrary?PhoneScanner.load(this):new ArrayList<>();
            runOnUiThread(() -> {
                if (isDestroyed()) return; busy = false;libraryEntries=restoredEntries;phoneFiles=restoredFiles; if (result != null) select(result); else render();
                if(pendingPhoneScan){pendingPhoneScan=false;startPhoneScan(PhoneScanner.roots(this));}
                if(!pendingScanRoots.isEmpty()){List<Uri> pending=pendingScanRoots;pendingScanRoots=Collections.emptyList();startScan(pending);}
            });
        });
    }
    TextView text(String label, int size) {
        TextView v = new TextView(this); v.setText(label); v.setTextSize(size); v.setTextColor(size<=14?MUTED:INK);v.setTypeface(uiFace);v.setFontFeatureSettings("tnum");
        v.setPadding(0, dp(8), 0, dp(8)); return v;
    }
    LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    MaterialButton button(String label, Runnable action) {
        MaterialButton b = new MaterialButton(this); b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setTypeface(uiFace);b.setFontVariationSettings("'wght' 550");b.setTextSize(14);b.setCornerRadius(dp(6));b.setElevation(0);b.setStateListAnimator(null);b.setLetterSpacing(0);
        b.setPadding(dp(10),0,dp(10),0);b.setInsetTop(dp(4));b.setInsetBottom(dp(4));
        b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xFFE9EAE4,PAPER}));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xFF777B73,INK}));
        b.setStrokeWidth(dp(1));b.setStrokeColor(ColorStateList.valueOf(RULE));b.setRippleColor(ColorStateList.valueOf(0x22426650));
        b.setOnClickListener(v -> action.run()); return b;
    }
    void primary(MaterialButton b){b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xFFE0E2DA,INK}));b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,Color.WHITE}));b.setStrokeWidth(0);b.setRippleColor(ColorStateList.valueOf(0x33FFFFFF));}
    void header(String title,MaterialButton action){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(0,dp(8),0,dp(8));
        TextView heading=text(title,28);heading.setTypeface(Typeface.create("serif",Typeface.NORMAL));heading.setLetterSpacing(-0.025f);heading.setMaxLines(2);
        bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));bar.addView(action,new LinearLayout.LayoutParams(-2,-2));root.addView(bar);divider(root);
    }
    void divider(LinearLayout parent){View line=new View(this);line.setBackgroundColor(RULE);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    void section(LinearLayout parent,String label){TextView heading=text(label,18);heading.setFontVariationSettings("'wght' 600");heading.setPadding(0,dp(24),0,dp(8));parent.addView(heading);}
    void styleSlider(Slider slider){slider.setTickVisible(false);slider.setTrackActiveTintList(ColorStateList.valueOf(ACCENT));slider.setTrackInactiveTintList(ColorStateList.valueOf(RULE));slider.setThumbTintList(ColorStateList.valueOf(ACCENT));}
    boolean darkNavigation(){return Build.VERSION.SDK_INT<27||(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;}
    void systemBars(){
        if(root==null)return;boolean dark=darkNavigation();
        androidx.core.view.WindowInsetsControllerCompat bars=new androidx.core.view.WindowInsetsControllerCompat(getWindow(),root);
        bars.setAppearanceLightStatusBars(true);bars.setAppearanceLightNavigationBars(!dark);
        if(Build.VERSION.SDK_INT>=29){getWindow().setNavigationBarContrastEnforced(false);getWindow().getDecorView().setForceDarkAllowed(false);}
        WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(root);
        androidx.core.graphics.Insets nav=dark&&insets!=null?insets.getInsets(WindowInsetsCompat.Type.navigationBars()):androidx.core.graphics.Insets.NONE;
        if(backdropRoot==root&&backdropDark==dark&&backdropLeft==nav.left&&backdropRight==nav.right&&backdropBottom==nav.bottom)return;
        backdropRoot=root;backdropDark=dark;backdropLeft=nav.left;backdropRight=nav.right;backdropBottom=nav.bottom;
        int left=root.getPaddingLeft(),top=root.getPaddingTop(),right=root.getPaddingRight(),bottom=root.getPaddingBottom();
        if(dark){
            // Android 15 draws navigation over app content. Protect its actual inset,
            // including a side-mounted navigation bar, without darkening the reader.
            android.graphics.drawable.LayerDrawable backdrop=new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{new android.graphics.drawable.ColorDrawable(INK),new android.graphics.drawable.ColorDrawable(PAPER)});
            backdrop.setLayerInset(1,nav.left,0,nav.right,nav.bottom);root.setBackground(backdrop);
        }else root.setBackgroundColor(PAPER);
        root.setPadding(left,top,right,bottom);
    }
    void field(EditText field){field.setTypeface(uiFace);field.setTextColor(INK);field.setHintTextColor(MUTED);field.setMinHeight(dp(52));field.setTextSize(16);field.setPadding(dp(14),dp(8),dp(14),dp(8));GradientDrawable fill=new GradientDrawable();fill.setColor(Color.WHITE);fill.setCornerRadius(dp(8));fill.setStroke(dp(1),RULE);field.setBackground(fill);}
    void row(LinearLayout parent, View... views) {
        LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL);
        for (View v : views) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(4),0,dp(4),0); l.addView(v,p); }
        parent.addView(l);
    }
    void render() {
        root = vertical(); root.setBackgroundColor(PAPER); root.setPadding(dp(20),0,dp(20),0);
        boolean compactHeight = !settings && !libraryOpen && (getResources().getConfiguration().screenHeightDp < 600 || getResources().getConfiguration().fontScale > 1.5f);
        if (compactHeight) {
            ScrollView viewport = new ScrollView(this); viewport.setFillViewport(true); viewport.addView(root); setContentView(viewport);
        } else setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(dp(20)+bars.left, bars.top, dp(20)+bars.right, bars.bottom);systemBars();return insets;
        });
        systemBars();root.post(this::systemBars);
        ViewCompat.requestApplyInsets(root);
        if (settings) { settingsScreen(); return; }
        if(libraryOpen){libraryScreen();return;}
        header("SpeedReader",button(tr("Appearance", "Оформление"), () -> { pause(); settings = true; render(); }));
        bookTitle = text(book == null ? tr("Your reading space", "Ваше пространство чтения") : book.title, 18);
        bookTitle.setTypeface(Typeface.create("serif",Typeface.NORMAL));bookTitle.setTextSize(21);bookTitle.setPadding(0,dp(16),0,dp(12));bookTitle.setMaxLines(2); root.addView(bookTitle);
        row(root, button(tr("Open FB2", "Открыть FB2"), this::open), button(tr("Library", "Библиотека"), this::library));
        LinearLayout stage = vertical(); stage.setGravity(Gravity.CENTER); stage.setPadding(dp(12),0,dp(12),0);
        GradientDrawable surface = new GradientDrawable(); surface.setColor(background); surface.setCornerRadius(dp(8)); stage.setBackground(surface);
        LinearLayout readingArea = new LinearLayout(this);
        LinearLayout.LayoutParams areaParams = compactHeight ? new LinearLayout.LayoutParams(-1,dp(420)) : new LinearLayout.LayoutParams(-1,0,1);
        areaParams.setMargins(0,dp(16),0,dp(12)); root.addView(readingArea,areaParams);
        readingArea.addView(stage,new LinearLayout.LayoutParams(0,-1,1));
        speedControl=null;
        faster=button("+1",() -> changeSpeed(speed+1));faster.setSingleLine(true);faster.setPadding(0,0,0,0);faster.setContentDescription(tr("Faster by 1 word per minute", "Быстрее на 1 слово в минуту"));
        slower=button("−1",() -> changeSpeed(speed-1));slower.setSingleLine(true);slower.setPadding(0,0,0,0);slower.setContentDescription(tr("Slower by 1 word per minute", "Медленнее на 1 слово в минуту"));
        word = new FittingWordView(this); word.setTextColor(foreground); word.setTypeface(Typeface.create(faces[font],Typeface.NORMAL)); word.setGravity(Gravity.CENTER);
        word.setPadding(0,dp(8),0,dp(8));
        word.setMinHeight(dp(64)); word.setFocusable(true); word.setOnClickListener(v -> toggle());
        stage.setOnClickListener(v -> toggle()); stage.addView(word,new LinearLayout.LayoutParams(-1,-2));
        state = text("", 14); state.setTextColor(foreground); state.setGravity(Gravity.CENTER); stage.addView(state);
        speedValue=text("",16);speedValue.setGravity(Gravity.CENTER);speedValue.setMinHeight(dp(48));speedValue.setTextColor(ACCENT);speedValue.setPaintFlags(speedValue.getPaintFlags()|Paint.UNDERLINE_TEXT_FLAG);speedValue.setFocusable(true);speedValue.setOnClickListener(v -> showSpeedSettings());
        LinearLayout speedRow=new LinearLayout(this);speedRow.setGravity(Gravity.CENTER_VERTICAL);
        speedRow.addView(slower,new LinearLayout.LayoutParams(dp(64),dp(48)));
        speedRow.addView(speedValue,new LinearLayout.LayoutParams(0,-2,1));
        speedRow.addView(faster,new LinearLayout.LayoutParams(dp(64),dp(48)));root.addView(speedRow);
        progress = text("",14); progress.setGravity(Gravity.CENTER); root.addView(progress);
        seek = new Slider(this); seek.setValueFrom(0); seek.setValueTo(1000); seek.setStepSize(1); seek.setContentDescription(tr("Reading position", "Позиция чтения"));
        styleSlider(seek);
        seek.addOnChangeListener((s,v,user) -> { if (user && book != null) { pause(); position = Math.round(v / 1000f * (book.words.size()-1)); update(); savePosition(); } }); root.addView(seek);
        play = button("", this::toggle);primary(play);
        MaterialButton back=button(tr("Back 10", "Назад 10"), () -> move(-10)),forward=button(tr("Forward 10", "Вперёд 10"), () -> move(10));
        if(getResources().getConfiguration().fontScale>1.15f||getResources().getConfiguration().screenWidthDp<360){row(root,back,forward);root.addView(play,new LinearLayout.LayoutParams(-1,dp(52)));}
        else row(root,back,play,forward);
        update();
    }
    void changeSpeed(int requested) {
        int next=Math.max(30,Math.min(500,requested));
        if(speed!=next){speed=next;saveSettings();if(playing){handler.removeCallbacks(tick);handler.postDelayed(tick,60000L/speed);}}
        if(speedControl!=null&&speedControl.getProgress()!=speed)speedControl.setProgress(speed);
        update();
    }
    interface NumberAction { void accept(int n); }
    interface NumberLabel { String label(int n); }
    void slider(LinearLayout parent,int from,int to,int step,int initial,NumberAction change,NumberLabel label) {
        TextView value = text(label.label(initial),14); parent.addView(value);
        Slider slider = new Slider(this); slider.setValueFrom(from); slider.setValueTo(to); slider.setStepSize(step); slider.setValue(initial);
        styleSlider(slider);
        slider.setContentDescription(label.label(initial));
        slider.addOnChangeListener((s,n,user) -> { if (user) { int i = Math.round(n); value.setText(label.label(i)); slider.setContentDescription(label.label(i)); change.accept(i); } }); parent.addView(slider);
    }
    void update() {
        if (word == null || settings || libraryOpen) return;
        speedValue.setText(speed+tr(" words / min"," слов / мин"));
        speedValue.setContentDescription(speedValue.getText()+tr(". Tap to change reading speed",". Нажмите для изменения скорости"));
        faster.setEnabled(speed<500);slower.setEnabled(speed>30);
        ((FittingWordView)word).showWord(busy ? tr("Loading…", "Загрузка…") : book == null ? "SpeedReader" : book.words.get(position),fontSize);
        word.setContentDescription(tr("Current word. Tap to pause or resume. ", "Текущее слово. Нажмите для паузы или продолжения. ") + word.getText());
        state.setText(busy ? tr("Preparing your book", "Подготовка книги") : playing ? tr("Reading · tap to pause", "Чтение · нажмите для паузы") : tr("Paused · tap to read", "Пауза · нажмите для чтения"));
        play.setText(playing ? tr("Pause", "Пауза") : tr("Read", "Читать")); play.setEnabled(book != null && !busy);
        seek.setEnabled(book != null && !busy);
        if (book != null) {
            int count = book.words.size(); seek.setValue(count <= 1 ? 0 : Math.round(1000f*position/(count-1)));
            progress.setText(String.format(Locale.getDefault(), "%d / %d · %d%% · %d %s", position+1,count,Math.round(100f*(position+1)/count),(int)Math.ceil((count-position-1)/(double)speed),tr("min left", "мин осталось")));
        }
    }
    void toggle() {
        if (book == null || busy) return;
        if (playing) pause();
        else { if (position == book.words.size()-1) position = 0; playing = true; getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); handler.postDelayed(tick,60000L/speed); }
        update();
    }
    void pause() { playing = false; handler.removeCallbacks(tick); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); savePosition(); update(); }
    void move(int offset) { if (book == null) return; pause(); position = Math.max(0,Math.min(book.words.size()-1,position+offset)); update(); savePosition(); }
    void savePosition() { if (book != null) prefs.edit().putInt("pos_"+book.id,position).putString("last",book.id).apply(); }
    void saveSettings() { prefs.edit().putInt("speed",speed).putInt("size",fontSize).putInt("font",font).putInt("theme",theme).putInt("fg",foreground).putInt("bg",background).putString("language",language).apply(); }
    void select(Book next) { pause(); book = next; position = Math.min(prefs.getInt("pos_"+book.id,0),book.words.size()-1); savePosition(); render(); }
    void open() { if(busy)return;pause(); Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i,OPEN); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PHONE_ACCESS){pendingPhoneAccess=false;if(hasPhoneAccess())startPhoneScan(PhoneScanner.roots(this));else{libraryMessage=tr("Phone access was not granted. You can still choose a folder.","Доступ к памяти не предоставлен. Можно выбрать отдельную папку.");if(libraryOpen)render();}return;}
        if(request==SCAN_FOLDER){
            if(result!=RESULT_OK||data==null||data.getData()==null)return;
            Uri tree=data.getData();
            try{
                if(!DocumentsContract.isTreeUri(tree))throw new SecurityException("FOLDER");
                getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Set<String> folders=new LinkedHashSet<>(prefs.getStringSet("folders",Collections.emptySet()));folders.add(tree.toString());
                prefs.edit().putStringSet("folders",folders).apply();startScan(Collections.singletonList(tree));
            }catch(SecurityException denied){folderError();}
            return;
        }
        if (request != OPEN || result != RESULT_OK || data == null || data.getData() == null) return;
        busy = true; update(); android.net.Uri uri = data.getData();
        io.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("OPEN"); Book next = Book.parse(in); new BookStore(getFilesDir()).add(next,sourceName(uri));
                runOnUiThread(() -> { if (!isDestroyed()) { busy = false;libraryOpen=false; select(next); } });
            } catch (Exception e) { runOnUiThread(() -> { if (!isDestroyed()) { busy = false; update(); failure(e); } }); }
        });
    }
    void failure(Exception error) {
        if (isDestroyed()) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(tr("Could not open book", "Не удалось открыть книгу"))
            .setMessage(tr("Choose a valid .fb2 file (up to 32 MB), or a ZIP with an FB2 book as its first file. Empty, damaged and DTD-based documents are not supported. Your previous book is safe.","Выберите исправный файл .fb2 (до 32 МБ) или ZIP с книгой FB2 первым файлом. Пустые, повреждённые файлы и документы с DTD не поддерживаются. Предыдущая книга сохранена."))
            .setPositiveButton(tr("OK", "Понятно"),null).show();
    }
    void library() {
        pause(); if (busy) return; libraryOpen=true;busy=true;render();
        io.execute(() -> {
            List<BookStore.Entry> entries=new BookStore(getFilesDir()).entries();
            List<PhoneScanner.FoundFile> files=PhoneScanner.load(this);
            runOnUiThread(() -> {if(!isDestroyed()){busy=false;libraryEntries=entries;phoneFiles=files;render();}});
        });
    }
    String sourceName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return "";
    }
    void closeLibrary(){pendingScanRoots=Collections.emptyList();pendingPhoneScan=false;if(scanning)scanCancelled.set(true);libraryOpen=false;render();}
    boolean hasPhoneAccess(){return Build.VERSION.SDK_INT>=30?Environment.isExternalStorageManager():androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.READ_EXTERNAL_STORAGE)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    void scanPhone(){
        if(busy)return;pause();
        if(hasPhoneAccess()){startPhoneScan(PhoneScanner.roots(this));return;}
        pendingPhoneAccess=true;
        if(Build.VERSION.SDK_INT>=30){
            try{startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())),PHONE_ACCESS);}
            catch(ActivityNotFoundException absent){try{startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),PHONE_ACCESS);}catch(ActivityNotFoundException missing){pendingPhoneAccess=false;folderError();}}
        }else androidx.core.app.ActivityCompat.requestPermissions(this,new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},PHONE_READ);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==PHONE_READ){pendingPhoneAccess=false;if(hasPhoneAccess())startPhoneScan(PhoneScanner.roots(this));else{libraryMessage=tr("Phone access was not granted. Choose a folder instead.","Доступ к памяти не предоставлен. Выберите отдельную папку.");render();}}}
    void startPhoneScan(List<File> roots){
        if(busy){if(!scanning)pendingPhoneScan=true;return;}
        pause();libraryOpen=true;phoneFilesOpen=true;phoneScanning=true;scanning=true;busy=true;libraryQuery="";scanCancelled=new AtomicBoolean();AtomicBoolean cancellation=scanCancelled;render();
        io.execute(() -> {
            PhoneScanner.Scan result=PhoneScanner.scan(getApplicationContext(),roots,cancellation,snapshot -> runOnUiThread(() -> {if(!isDestroyed()&&libraryOpen&&libraryStatus!=null)libraryStatus.setText(scanSummary(snapshot,false));}));
            List<BookStore.Entry> entries=new BookStore(getFilesDir()).entries();
            runOnUiThread(() -> {if(!isDestroyed()){phoneScanning=false;scanning=false;busy=false;if(!result.files.isEmpty()||!result.stats.cancelled)phoneFiles=result.files;libraryEntries=entries;libraryMessage=scanSummary(result.stats,true);render();}});
        });
    }
    void openFound(PhoneScanner.FoundFile file){
        if(busy)return;busy=true;libraryList.setEnabled(false);libraryStatus.setText(tr("Opening…","Открытие книги…"));
        io.execute(() -> {try{
            Book selected;
            File cached=new File(getFilesDir(),file.bookId+".json");
            if(!file.bookId.isEmpty()&&cached.isFile())selected=Book.load(cached);
            else try(InputStream input=new FileInputStream(file.path)){selected=Book.parse(input);new BookStore(getFilesDir()).add(selected,file.name());}
            Book opened=selected;runOnUiThread(() -> {if(!isDestroyed()){busy=false;libraryOpen=false;select(opened);}});
        }catch(Exception error){runOnUiThread(() -> {if(!isDestroyed()){busy=false;render();failure(error);}});}});
    }
    void chooseScanFolder(){
        if(busy)return;pause();
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent,SCAN_FOLDER);
    }
    void scanAgain(){
        if(busy)return;List<Uri> roots=new ArrayList<>();
        for(String uri:prefs.getStringSet("folders",Collections.emptySet()))roots.add(Uri.parse(uri));
        if(roots.isEmpty())chooseScanFolder();else startScan(roots);
    }
    void folderError(){
        if(isDestroyed())return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(tr("Folder access needed","Нужен доступ к папке"))
            .setMessage(tr("Select a book folder and allow read access. If access was removed, select that folder again.","Выберите папку с книгами и разрешите чтение. Если доступ отозван, выберите эту папку заново."))
            .setPositiveButton(tr("OK","Понятно"),null).show();
    }
    void startScan(List<Uri> roots){
        if(busy){if(!scanning)pendingScanRoots=new ArrayList<>(roots);return;}pause();libraryOpen=true;phoneFilesOpen=false;scanning=true;busy=true;scanCancelled=new AtomicBoolean();AtomicBoolean cancellation=scanCancelled;render();
        io.execute(() -> {
            LibraryScanner.Result result=LibraryScanner.scan(getApplicationContext(),roots,cancellation,snapshot -> runOnUiThread(() -> {
                if(!isDestroyed()&&libraryOpen&&libraryStatus!=null)libraryStatus.setText(scanSummary(snapshot,false));
            }));
            List<BookStore.Entry> entries=new BookStore(getFilesDir()).entries();
            runOnUiThread(() -> {if(!isDestroyed()){scanning=false;busy=false;libraryEntries=entries;libraryMessage=scanSummary(result,true);render();}});
        });
    }
    String scanSummary(LibraryScanner.Result r,boolean complete){
        String heading=complete?(r.cancelled?tr("Scan stopped","Сканирование остановлено"):r.limited?tr("Scan limit reached; choose a smaller folder","Лимит сканирования; выберите папку поменьше"):tr("Scan complete","Сканирование завершено")):tr("Scanning…","Поиск книг…");
        if(r.unavailable>0)heading=phoneFilesOpen?tr("Protected or unavailable folders: ","Закрытых или недоступных папок: ")+r.unavailable:tr("Select folders again. Unavailable: ","Выберите папки заново. Недоступно: ")+r.unavailable;
        return heading+"\n"+tr("Added: ","Добавлено: ")+r.added+tr(" · Duplicates: "," · Дубли: ")+r.duplicates+tr(" · Skipped: "," · Пропущено: ")+r.failed+
            "\n"+tr("Checked: ","Проверено: ")+r.examined+" · FB2: "+r.found+
            (!complete&&!r.current.isEmpty()?"\n"+r.current:"");
    }
    void libraryScreen(){
        boolean compactLibrary=getResources().getConfiguration().screenHeightDp<500;
        header(phoneFilesOpen?tr("Found FB2","Найденные FB2"):tr("Library","Библиотека"),button(tr("Read","Читать"),this::closeLibrary));
        MaterialButton phone=button(scanning?tr("Stop scan","Остановить"):tr("Scan phone","Сканировать телефон"),() -> {if(scanning)scanCancelled.set(true);else scanPhone();});primary(phone);phone.setEnabled(scanning||!busy);
        MaterialButton files=button(compactLibrary?tr("More","Ещё"):phoneFilesOpen?tr("Books","Книги"):tr("Found files","Найденные файлы"),() -> {});files.setEnabled(!busy);
        files.setOnClickListener(v -> {if(compactLibrary)libraryMenu(v);else toggleFoundFiles();});row(root,phone,files);
        if(!compactLibrary){
            MaterialButton scan=button(tr("Find FB2","Найти FB2"),this::chooseScanFolder);scan.setEnabled(!busy);
            MaterialButton again=button(tr("Scan again","Повторить"),this::scanAgain);again.setEnabled(!busy);row(root,scan,again);
            root.addView(text(phoneFilesOpen?tr("Tap a title to read. Shared phone storage and SD cards are scanned; private app folders are excluded.","Нажмите название, чтобы читать. Поиск по общей памяти и SD-картам; приватные папки приложений исключены."):tr("Scan your phone, or choose a book folder. Found books are saved for offline reading.","Сканируйте телефон или выберите папку. Найденные книги сохраняются для чтения без интернета."),14));
        }
        librarySearch=new EditText(this);librarySearch.setSingleLine(true);field(librarySearch);
        librarySearch.setHint(phoneFilesOpen?tr("Search title or filename","Название книги или имя файла"):tr("Search title, author or series","Название, автор или серия"));librarySearch.setContentDescription(tr("Search library","Поиск по библиотеке"));librarySearch.setText(libraryQuery);root.addView(librarySearch);
        libraryStatus=text(scanning?tr("Scanning…","Поиск книг…"):busy?tr("Loading library…","Загрузка библиотеки…"):libraryMessage.isEmpty()?(phoneFilesOpen?tr("Files: ","Файлов: ")+phoneFiles.size():tr("Books: ","Книг: ")+libraryEntries.size()):libraryMessage,14);
        libraryStatus.setMaxLines(compactLibrary?2:5);root.addView(libraryStatus);
        android.widget.FrameLayout listFrame=new android.widget.FrameLayout(this);root.addView(listFrame,new LinearLayout.LayoutParams(-1,0,1));
        libraryList=new ListView(this);libraryList.setDivider(new android.graphics.drawable.ColorDrawable(RULE));libraryList.setDividerHeight(dp(1));libraryList.setEnabled(!busy);listFrame.addView(libraryList,new android.widget.FrameLayout.LayoutParams(-1,-1));
        libraryEmpty=text("",16);libraryEmpty.setGravity(Gravity.CENTER);listFrame.addView(libraryEmpty,new android.widget.FrameLayout.LayoutParams(-1,-1));libraryList.setEmptyView(libraryEmpty);
        libraryAdapter=new LibraryAdapter();foundAdapter=new FoundAdapter();libraryList.setAdapter(phoneFilesOpen?foundAdapter:libraryAdapter);refreshLibraryList();
        librarySearch.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){libraryQuery=s.toString();refreshLibraryList();}public void afterTextChanged(Editable e){}});
        libraryList.setOnItemClickListener((parent,view,index,id) -> {
            if(busy)return;if(phoneFilesOpen){openFound(foundAdapter.filtered.get(index));return;}BookStore.Entry entry=libraryAdapter.filtered.get(index);busy=true;libraryStatus.setText(tr("Opening…","Открытие книги…"));
            io.execute(() -> {try{Book selected=Book.load(new File(getFilesDir(),entry.id+".json"));runOnUiThread(() -> {if(!isDestroyed()){busy=false;libraryOpen=false;select(selected);}});}
                catch(Exception error){runOnUiThread(() -> {if(!isDestroyed()){busy=false;render();failure(error);}});}});
        });
    }
    void toggleFoundFiles(){phoneFilesOpen=!phoneFilesOpen;libraryQuery="";libraryMessage="";render();}
    void libraryMenu(View anchor){PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add(tr("Choose folder","Выбрать папку")).setOnMenuItemClickListener(item -> {chooseScanFolder();return true;});menu.getMenu().add(tr("Scan folders again","Повторить поиск в папках")).setOnMenuItemClickListener(item -> {scanAgain();return true;});menu.getMenu().add(phoneFilesOpen?tr("Books","Книги"):tr("Found files","Найденные файлы")).setOnMenuItemClickListener(item -> {toggleFoundFiles();return true;});menu.show();}
    void refreshLibraryList(){
        if(phoneFilesOpen){foundAdapter.filtered.clear();for(PhoneScanner.FoundFile file:phoneFiles)if(file.matches(libraryQuery))foundAdapter.filtered.add(file);foundAdapter.notifyDataSetChanged();libraryEmpty.setText(busy?tr("Scanning phone…","Сканирование телефона…"):phoneFiles.isEmpty()?tr("No files yet. Tap Scan phone.","Файлов пока нет. Нажмите «Сканировать телефон»."):tr("No matching files","Файлы не найдены"));return;}
        libraryAdapter.filtered.clear();for(BookStore.Entry entry:libraryEntries)if(entry.matches(libraryQuery))libraryAdapter.filtered.add(entry);
        libraryAdapter.notifyDataSetChanged();libraryEmpty.setText(busy?tr("Preparing your library…","Подготовка библиотеки…"):libraryEntries.isEmpty()?tr("No books yet. Tap Find FB2 to scan a folder.","Пока нет книг. Нажмите «Найти FB2» и выберите папку."):tr("No matching books","Книги не найдены"));
    }
    final class FoundAdapter extends BaseAdapter {
        final List<PhoneScanner.FoundFile> filtered=new ArrayList<>();
        public int getCount(){return filtered.size();}public Object getItem(int position){return filtered.get(position);}public long getItemId(int position){return position;}
        public View getView(int index,View recycled,android.view.ViewGroup parent){
            LinearLayout item=vertical();item.setPadding(dp(4),dp(12),dp(4),dp(12));PhoneScanner.FoundFile file=filtered.get(index);
            TextView title=text(file.title,22);title.setTypeface(Typeface.create("serif",Typeface.NORMAL));title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);item.addView(title);
            if(!file.author.isEmpty())item.addView(text(file.author,15));
            TextView path=text(file.path,12);path.setMaxLines(2);path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);item.addView(path);
            if(file.bookId.isEmpty())item.addView(text(tr("Could not import · tap to retry","Не удалось добавить · нажмите для повтора"),12));
            item.setContentDescription(file.title+". "+file.path);return item;
        }
    }
    final class LibraryAdapter extends BaseAdapter {
        final List<BookStore.Entry> filtered=new ArrayList<>();
        public int getCount(){return filtered.size();}public Object getItem(int position){return filtered.get(position);}public long getItemId(int position){return position;}
        public View getView(int index,View recycled,android.view.ViewGroup parent){
            LinearLayout item;
            if(recycled instanceof LinearLayout)item=(LinearLayout)recycled;
            else{item=vertical();item.setPadding(dp(4),dp(16),dp(4),dp(16));for(int size:new int[]{22,15,12}){TextView label=text("",size);if(size==22)label.setTypeface(Typeface.create("serif",Typeface.NORMAL));label.setPadding(0,dp(3),0,dp(3));label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);item.addView(label);}}
            BookStore.Entry entry=filtered.get(index);((TextView)item.getChildAt(0)).setText(entry.displayTitle());
            ((TextView)item.getChildAt(1)).setText(entry.author.isEmpty()?tr("Author not specified","Автор не указан"):entry.author);
            int at=Math.max(0,Math.min(entry.wordCount-1,prefs.getInt("pos_"+entry.id,0)));
            String details=(entry.series.isEmpty()?"":entry.series+" · ")+(entry.language.isEmpty()?"":entry.language+" · ")+entry.wordCount+tr(" words"," слов")+(at>0?" · "+Math.round(100f*(at+1)/entry.wordCount)+"%":"");
            if(!entry.genre.isEmpty())details+="\n"+entry.genre;
            ((TextView)item.getChildAt(2)).setText(details);return item;
        }
    }
    void settingsScreen() {
        header(tr("Appearance", "Оформление"),button(tr("Done", "Готово"), () -> { settings=false; render(); }));
        ScrollView scroll = new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); content = vertical(); scroll.addView(content);
        section(content,tr("Interface language", "Язык интерфейса"));
        choice(content,new String[]{tr("System", "Системный"),"English","Русский"},Arrays.asList("system","en","ru").indexOf(language), i -> { language = new String[]{"system","en","ru"}[i]; saveSettings(); render(); });
        section(content,tr("Word preview", "Предпросмотр слова"));
        TextView preview = text(tr("Attention", "Внимание"),fontSize); preview.setGravity(Gravity.CENTER); preview.setTextColor(foreground); preview.setBackgroundColor(background); preview.setTypeface(Typeface.create(faces[font],Typeface.NORMAL)); preview.setMinHeight(dp(120)); content.addView(preview);
        section(content,tr("Word size", "Размер слова"));
        slider(content,18,72,1,fontSize,n -> { fontSize=n; preview.setTextSize(n); saveSettings(); }, n -> n + " sp");
        section(content,tr("Reading font", "Шрифт чтения"));
        choice(content,new String[]{tr("Serif", "С засечками"),tr("Sans serif", "Без засечек"),tr("Monospace", "Моноширинный"),tr("Condensed", "Узкий")},font,i -> {font=i; preview.setTypeface(Typeface.create(faces[i],Typeface.NORMAL)); saveSettings();});
        section(content,tr("10 reading themes", "10 тем чтения"));
        LinearLayout themeRow=null;
        for (int i=0;i<10;i++) {
            final int selected=i;
            MaterialButton b=button((russian()?Themes.RU:Themes.EN)[i] + (theme==i ? tr(" · selected", " · выбрана") : ""), () -> {theme=selected; background=Themes.bg(selected); foreground=Themes.fg(selected); saveSettings(); render();});
            b.setBackgroundTintList(ColorStateList.valueOf(Themes.bg(i))); b.setTextColor(Themes.fg(i));b.setMinHeight(dp(64));b.setStrokeWidth(dp(theme==i?2:1));b.setStrokeColor(ColorStateList.valueOf(theme==i?ACCENT:RULE));
            if(i%2==0){themeRow=new LinearLayout(this);content.addView(themeRow);}LinearLayout.LayoutParams tile=new LinearLayout.LayoutParams(0,-2,1);tile.setMargins(i%2==0?0:dp(4),0,i%2==0?dp(4):0,0);themeRow.addView(b,tile);
        }
        section(content,tr("Custom reading colors", "Свои цвета чтения"));
        content.addView(text(tr("Use #RRGGBB. Colors apply to the word area.","Формат #RRGGBB. Цвета применяются к области слова."),14));
        LinearLayout colors=vertical(); EditText fg=new EditText(this), bg=new EditText(this);
        field(fg);field(bg);
        fg.setSingleLine(true); bg.setSingleLine(true); fg.setText(String.format("#%06X",foreground&0xFFFFFF)); bg.setText(String.format("#%06X",background&0xFFFFFF));
        fg.setContentDescription(tr("Text color hex", "HEX цвета текста")); bg.setContentDescription(tr("Background color hex", "HEX цвета фона"));
        colors.addView(text(tr("Text", "Текст"),16)); colors.addView(fg); colors.addView(text(tr("Background", "Фон"),16)); colors.addView(bg); content.addView(colors);
        content.addView(button(tr("Apply colors", "Применить цвета"), () -> {
            String f=fg.getText().toString().trim(), b=bg.getText().toString().trim();
            if (!f.matches("#[0-9a-fA-F]{6}")) {fg.setError(tr("Use #RRGGBB", "Введите #RRGGBB")); return;}
            if (!b.matches("#[0-9a-fA-F]{6}")) {bg.setError(tr("Use #RRGGBB", "Введите #RRGGBB")); return;}
            int newFg=Color.parseColor(f), newBg=Color.parseColor(b);
            if (androidx.core.graphics.ColorUtils.calculateContrast(newFg,newBg)<4.5) {fg.setError(tr("Choose more contrasting colors", "Выберите более контрастные цвета")); return;}
            foreground=newFg; background=newBg; theme=-1; saveSettings(); render();
        }));
        section(content,tr("Reading speed", "Скорость чтения"));
        TextView speedLabel=text(speed+tr(" words / min"," слов / мин"),16);speedLabel.setGravity(Gravity.CENTER);content.addView(speedLabel);
        LinearLayout speedRail=vertical();speedRail.setGravity(Gravity.CENTER_HORIZONTAL);content.addView(speedRail);
        speedRail.addView(text("500",14));speedControl=new VerticalSpeedBar(this);speedControl.setProgress(speed);
        speedControl.setContentDescription(tr("Reading speed, words per minute. Up to increase.","Скорость чтения, слов в минуту. Вверх — быстрее."));
        speedRail.addView(speedControl,new LinearLayout.LayoutParams(dp(64),dp(320)));speedRail.addView(text("30",14));
        speedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){changeSpeed(value);speedLabel.setText(speed+tr(" words / min"," слов / мин"));}
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
        });
    }
    void showSpeedSettings(){pause();settings=true;render();content.post(() -> {if(settings&&speedControl!=null)((ScrollView)content.getParent()).scrollTo(0,((View)speedControl.getParent()).getTop()-dp(64));});}
    void choice(LinearLayout parent,String[] labels,int current,NumberAction action) {
        RadioGroup group=new RadioGroup(this);
        for(int i=0;i<labels.length;i++){RadioButton b=new RadioButton(this); b.setId(View.generateViewId()); b.setText(labels[i]);b.setTypeface(uiFace);b.setTextSize(16);b.setTextColor(INK); b.setMinHeight(dp(48)); group.addView(b); if(i==current)group.check(b.getId());}
        group.setOnCheckedChangeListener((g,id) -> action.accept(g.indexOfChild(g.findViewById(id)))); parent.addView(group);
    }
    @Override protected void onResume(){super.onResume();systemBars();if(root!=null)root.post(this::systemBars);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){systemBars();if(root!=null)root.post(this::systemBars);}}
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);systemBars();if(root!=null)root.post(this::systemBars);}
    @Override protected void onPause(){pause(); saveSettings(); super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("libraryOpen",libraryOpen);out.putBoolean("phoneFilesOpen",phoneFilesOpen);out.putBoolean("pendingPhoneAccess",pendingPhoneAccess);out.putBoolean("scanning",scanning);out.putString("libraryQuery",libraryQuery);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){scanCancelled.set(true);handler.removeCallbacksAndMessages(null); io.shutdownNow(); super.onDestroy();}
}
