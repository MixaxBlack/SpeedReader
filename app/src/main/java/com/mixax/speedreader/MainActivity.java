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

public class MainActivity extends AppCompatActivity {
    static final int OPEN = 41;
    final Handler handler = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    android.content.SharedPreferences prefs;
    Book book;
    int position, speed, fontSize, font, theme, foreground, background;
    boolean playing, settings, busy;
    String language;
    LinearLayout root, content;
    TextView word, progress, bookTitle, state;
    MaterialButton play;
    Slider seek;
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
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        language = prefs.getString("language", "system");
        speed = prefs.getInt("speed", 300); fontSize = prefs.getInt("size", 42); font = prefs.getInt("font", 0);
        theme = prefs.getInt("theme", 0); foreground = prefs.getInt("fg", Themes.fg(Math.max(0,theme))); background = prefs.getInt("bg", Themes.bg(Math.max(0,theme)));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (settings) { settings = false; render(); }
                else { pause(); setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); setEnabled(true); }
            }
        });
        render();
        String last = prefs.getString("last", ""); busy = true; render();
        io.execute(() -> {
            Book loaded = null;
            try { if (!last.isEmpty()) loaded = Book.load(new File(getFilesDir(), last + ".json")); } catch (Exception ignored) { }
            try { if (loaded == null) { try (InputStream in = getAssets().open("welcome.fb2")) { loaded = Book.parse(in); } } }
            catch (Exception error) { runOnUiThread(() -> failure(error)); }
            Book result = loaded;
            runOnUiThread(() -> { if (isDestroyed()) return; busy = false; if (result != null) select(result); else render(); });
        });
    }
    TextView text(String label, int size) {
        TextView v = new TextView(this); v.setText(label); v.setTextSize(size); v.setTextColor(Color.rgb(36,46,40));
        v.setPadding(0, dp(8), 0, dp(8)); return v;
    }
    LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    MaterialButton button(String label, Runnable action) {
        MaterialButton b = new MaterialButton(this); b.setText(label); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setOnClickListener(v -> action.run()); return b;
    }
    void row(LinearLayout parent, View... views) {
        LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL);
        for (View v : views) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(4),0,dp(4),0); l.addView(v,p); }
        parent.addView(l);
    }
    void render() {
        root = vertical(); root.setBackgroundColor(Color.rgb(245,241,231));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(dp(16)+bars.left, bars.top, dp(16)+bars.right, bars.bottom); return insets;
        });
        ViewCompat.requestApplyInsets(root);
        if (settings) { settingsScreen(); return; }
        row(root, text("SpeedReader", 24), button(tr("Appearance", "Оформление"), () -> { pause(); settings = true; render(); }));
        bookTitle = text(book == null ? tr("Your reading space", "Ваше пространство чтения") : book.title, 18);
        bookTitle.setMaxLines(2); root.addView(bookTitle);
        row(root, button(tr("Open FB2", "Открыть FB2"), this::open), button(tr("Library", "Библиотека"), this::library));
        LinearLayout stage = vertical(); stage.setGravity(Gravity.CENTER); stage.setPadding(dp(12),0,dp(12),0);
        GradientDrawable surface = new GradientDrawable(); surface.setColor(background); surface.setCornerRadius(dp(20)); stage.setBackground(surface);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(-1, 0, 1); stageParams.setMargins(0,dp(16),0,dp(12)); root.addView(stage,stageParams);
        word = text("",fontSize); word.setTextColor(foreground); word.setTypeface(Typeface.create(faces[font],Typeface.NORMAL)); word.setGravity(Gravity.CENTER);
        word.setSingleLine(true); word.setAutoSizeTextTypeUniformWithConfiguration(12,fontSize,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        word.setMinHeight(dp(64)); word.setFocusable(true); word.setOnClickListener(v -> toggle());
        stage.setOnClickListener(v -> toggle()); stage.addView(word,new LinearLayout.LayoutParams(-1,-2));
        state = text("", 14); state.setTextColor(foreground); state.setGravity(Gravity.CENTER); stage.addView(state);
        progress = text("",14); progress.setGravity(Gravity.CENTER); root.addView(progress);
        seek = new Slider(this); seek.setValueFrom(0); seek.setValueTo(1000); seek.setStepSize(1); seek.setContentDescription(tr("Reading position", "Позиция чтения"));
        seek.addOnChangeListener((s,v,user) -> { if (user && book != null) { pause(); position = Math.round(v / 1000f * (book.words.size()-1)); update(); savePosition(); } }); root.addView(seek);
        play = button("", this::toggle);
        row(root, button(tr("Back 10", "Назад 10"), () -> move(-10)), play, button(tr("Forward 10", "Вперёд 10"), () -> move(10)));
        root.addView(text(tr("Reading speed", "Скорость чтения"), 16));
        slider(root, 60, 1200, 10, speed, v -> { speed = v; saveSettings(); if (playing) { handler.removeCallbacks(tick); handler.postDelayed(tick,60000L / speed); } update(); }, v -> v + tr(" words / min", " слов / мин"));
        update();
    }
    interface NumberAction { void accept(int n); }
    interface NumberLabel { String label(int n); }
    void slider(LinearLayout parent,int from,int to,int step,int initial,NumberAction change,NumberLabel label) {
        TextView value = text(label.label(initial),14); parent.addView(value);
        Slider slider = new Slider(this); slider.setValueFrom(from); slider.setValueTo(to); slider.setStepSize(step); slider.setValue(initial);
        slider.setContentDescription(label.label(initial));
        slider.addOnChangeListener((s,n,user) -> { if (user) { int i = Math.round(n); value.setText(label.label(i)); slider.setContentDescription(label.label(i)); change.accept(i); } }); parent.addView(slider);
    }
    void update() {
        if (word == null || settings) return;
        word.setText(busy ? tr("Loading…", "Загрузка…") : book == null ? "SpeedReader" : book.words.get(position));
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
    void open() { pause(); Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i,OPEN); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request != OPEN || result != RESULT_OK || data == null || data.getData() == null) return;
        busy = true; update(); android.net.Uri uri = data.getData();
        io.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("OPEN"); Book next = Book.parse(in); next.save(getFilesDir());
                runOnUiThread(() -> { if (!isDestroyed()) { busy = false; select(next); } });
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
        pause(); if (busy) return; busy = true; update();
        io.execute(() -> {
            ArrayList<File> files = new ArrayList<>(); ArrayList<String> titles = new ArrayList<>();
            File[] all = getFilesDir().listFiles((d,n) -> n.endsWith(".json"));
            if (all != null) { Arrays.sort(all, (a,b) -> Long.compare(b.lastModified(),a.lastModified()));
                for (File f : all) { try { Book b = Book.load(f); files.add(f); titles.add(b.title); } catch (Exception ignored) { } }
            }
            runOnUiThread(() -> {
                if (isDestroyed()) return; busy = false; update();
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(tr("Library", "Библиотека"))
                    .setItems(titles.isEmpty() ? new String[]{tr("No books yet. Open an FB2 file.", "Пока нет книг. Откройте файл FB2.")} : titles.toArray(new String[0]), (d,index) -> {
                        if (files.isEmpty()) { open(); return; } busy = true; update();
                        io.execute(() -> { try { Book b = Book.load(files.get(index)); runOnUiThread(() -> { if (!isDestroyed()) { busy=false; select(b); } }); }
                            catch (Exception e) { runOnUiThread(() -> { busy=false; update(); failure(e); }); } });
                    }).setNegativeButton(tr("Close", "Закрыть"),null).show();
            });
        });
    }
    void settingsScreen() {
        row(root, text(tr("Appearance", "Оформление"),24), button(tr("Done", "Готово"), () -> { settings=false; render(); }));
        ScrollView scroll = new ScrollView(this); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); content = vertical(); scroll.addView(content);
        content.addView(text(tr("Interface language", "Язык интерфейса"),20));
        choice(content,new String[]{tr("System", "Системный"),"English","Русский"},Arrays.asList("system","en","ru").indexOf(language), i -> { language = new String[]{"system","en","ru"}[i]; saveSettings(); render(); });
        content.addView(text(tr("Word preview", "Предпросмотр слова"),20));
        TextView preview = text(tr("Attention", "Внимание"),fontSize); preview.setGravity(Gravity.CENTER); preview.setTextColor(foreground); preview.setBackgroundColor(background); preview.setTypeface(Typeface.create(faces[font],Typeface.NORMAL)); preview.setMinHeight(dp(120)); content.addView(preview);
        content.addView(text(tr("Word size", "Размер слова"),20));
        slider(content,18,72,1,fontSize,n -> { fontSize=n; preview.setTextSize(n); saveSettings(); }, n -> n + " sp");
        content.addView(text(tr("Reading font", "Шрифт чтения"),20));
        choice(content,new String[]{tr("Serif", "С засечками"),tr("Sans serif", "Без засечек"),tr("Monospace", "Моноширинный"),tr("Condensed", "Узкий")},font,i -> {font=i; preview.setTypeface(Typeface.create(faces[i],Typeface.NORMAL)); saveSettings();});
        content.addView(text(tr("10 reading themes", "10 тем чтения"),20));
        for (int i=0;i<10;i++) {
            final int selected=i;
            MaterialButton b=button((russian()?Themes.RU:Themes.EN)[i] + (theme==i ? tr(" · selected", " · выбрана") : ""), () -> {theme=selected; background=Themes.bg(selected); foreground=Themes.fg(selected); saveSettings(); render();});
            b.setBackgroundTintList(ColorStateList.valueOf(Themes.bg(i))); b.setTextColor(Themes.fg(i)); content.addView(b);
        }
        content.addView(text(tr("Custom reading colors", "Свои цвета чтения"),20));
        content.addView(text(tr("Use #RRGGBB. Colors apply to the word area.","Формат #RRGGBB. Цвета применяются к области слова."),14));
        LinearLayout colors=vertical(); EditText fg=new EditText(this), bg=new EditText(this);
        fg.setSingleLine(true); bg.setSingleLine(true); fg.setText(String.format("#%06X",foreground&0xFFFFFF)); bg.setText(String.format("#%06X",background&0xFFFFFF));
        fg.setContentDescription(tr("Text color hex", "HEX цвета текста")); bg.setContentDescription(tr("Background color hex", "HEX цвета фона"));
        colors.addView(text(tr("Text", "Текст"),16)); colors.addView(fg); colors.addView(text(tr("Background", "Фон"),16)); colors.addView(bg); content.addView(colors);
        content.addView(button(tr("Apply colors", "Применить цвета"), () -> {
            String f=fg.getText().toString().trim(), b=bg.getText().toString().trim();
            if (!f.matches("#[0-9a-fA-F]{6}")) {fg.setError(tr("Use #RRGGBB", "Введите #RRGGBB")); return;}
            if (!b.matches("#[0-9a-fA-F]{6}")) {bg.setError(tr("Use #RRGGBB", "Введите #RRGGBB")); return;}
            int newFg=Color.parseColor(f), newBg=Color.parseColor(b);
            if (androidx.core.graphics.ColorUtils.calculateContrast(newFg,newBg)<3) {fg.setError(tr("Choose more contrasting colors", "Выберите более контрастные цвета")); return;}
            foreground=newFg; background=newBg; theme=-1; saveSettings(); render();
        }));
    }
    void choice(LinearLayout parent,String[] labels,int current,NumberAction action) {
        RadioGroup group=new RadioGroup(this);
        for(int i=0;i<labels.length;i++){RadioButton b=new RadioButton(this); b.setId(View.generateViewId()); b.setText(labels[i]); b.setMinHeight(dp(48)); group.addView(b); if(i==current)group.check(b.getId());}
        group.setOnCheckedChangeListener((g,id) -> action.accept(g.indexOfChild(g.findViewById(id)))); parent.addView(group);
    }
    @Override protected void onPause(){pause(); saveSettings(); super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null); io.shutdownNow(); super.onDestroy();}
}
