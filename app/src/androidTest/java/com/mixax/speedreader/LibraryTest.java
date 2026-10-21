package com.mixax.speedreader;

import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=29)
public class LibraryTest {
    Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    Uri tree(String id){return DocumentsContract.buildTreeDocumentUri(FixtureDocumentsProvider.AUTHORITY,id);}
    Context isolated() throws Exception {
        File directory=new File(context().getCacheDir(),"library-"+UUID.randomUUID());assertTrue(directory.mkdirs());
        return new ContextWrapper(context()){@Override public File getFilesDir(){return directory;}};
    }
    void treeAccess(boolean allow) throws Exception {
        android.content.Intent intent=new android.content.Intent().setClassName("com.mixax.speedreader.test","com.mixax.speedreader.FixtureGrantActivity");
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);intent.putExtra("revoke",!allow);context().startActivity(intent);
        for(int i=0;i<60;i++){
            boolean granted=context().checkUriPermission(tree("root"),android.os.Process.myPid(),android.os.Process.myUid(),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)==android.content.pm.PackageManager.PERMISSION_GRANTED;
            if(granted==allow){InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(150);return;}Thread.sleep(50);
        }
        fail("Test tree grant did not reach expected state: "+allow);
    }
    @Before public void accessTestProvider() throws Exception {
        treeAccess(true);
        context().getContentResolver().takePersistableUriPermission(tree("root"),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try(android.database.Cursor cursor=context().getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree("root"),"root"),null,null,null,null)){
            assertNotNull("Test document provider must be reachable",cursor);assertEquals(6,cursor.getCount());
        }
    }
    @After public void releaseTestProvider() throws Exception {treeAccess(false);}
    @Test public void metadataAuthorsAndOldCacheCompatibility() throws Exception {
        String xml=FixtureDocumentsProvider.book("Метаданные","Анна","Петрова","Цикл","UTF-8").replace("</title-info>","<author><nickname>Second Author</nickname></author></title-info>");
        Book book=Book.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals("Анна Петрова, Second Author",book.author);assertEquals("Цикл · 2",book.series);assertEquals("adventure",book.genre);assertEquals("ru",book.language);
        assertEquals(new Book(book.title,book.words).id,book.id);
        String rawTitle="Line one\n  Line two";Book multiline=Book.parse(new ByteArrayInputStream(FixtureDocumentsProvider.book(rawTitle,"A","B","Series","UTF-8").getBytes(StandardCharsets.UTF_8)));
        assertEquals(new Book(rawTitle,multiline.words).id,multiline.id);
        Context c=isolated();BookStore store=new BookStore(c.getFilesDir());store.add(book,"metadata.fb2");
        Book loaded=Book.load(new File(c.getFilesDir(),book.id+".json"));assertEquals(book.author,loaded.author);
        BookStore.Entry entry=store.entries().get(0);assertTrue(entry.matches("ПЕТРОВА"));assertTrue(entry.matches("Цикл"));assertFalse(entry.matches("No matching author"));
        Book legacy=new Book("Old cache",Arrays.asList("one","two"));legacy.save(c.getFilesDir());assertEquals(2,store.entries().size());assertTrue(new File(c.getFilesDir(),legacy.id+".meta").isFile());
    }
    @Test public void actualDocumentTreeRecursiveScanDedupAndRescan() throws Exception {
        Context c=isolated();AtomicBoolean cancelled=new AtomicBoolean();
        LibraryScanner.Result first=LibraryScanner.scan(c,Collections.singletonList(tree("root")),cancelled,result -> {});
        assertEquals(0,first.unavailable);assertEquals(5,first.found);assertEquals(2,first.added);assertEquals(2,first.duplicates);assertEquals(1,first.failed);
        List<BookStore.Entry> entries=new BookStore(c.getFilesDir()).entries();assertEquals(2,entries.size());
        assertTrue(entries.stream().anyMatch(e->e.title.equals(FixtureDocumentsProvider.SECOND_TITLE)&&e.author.equals("Иван Сидоров")));
        LibraryScanner.Result again=LibraryScanner.scan(c,Collections.singletonList(tree("root")),cancelled,result -> {});
        assertEquals(0,again.added);assertEquals(4,again.duplicates);assertEquals(1,again.failed);assertEquals(2,new BookStore(c.getFilesDir()).entries().size());
        treeAccess(false);
        assertEquals(1,LibraryScanner.scan(c,Collections.singletonList(tree("root")),new AtomicBoolean(),result -> {}).unavailable);
        Book offline=Book.load(new File(c.getFilesDir(),entries.get(0).id+".json"));assertFalse(offline.words.isEmpty());
    }
    @Test public void cancelAndUnavailableFolderDoNotDamageLibrary() throws Exception {
        Context c=isolated();LibraryScanner.Result cancelled=LibraryScanner.scan(c,Collections.singletonList(tree("root")),new AtomicBoolean(true),result -> {});
        assertTrue(cancelled.cancelled);assertEquals(0,cancelled.found);
        LibraryScanner.Result unavailable=LibraryScanner.scan(c,Collections.singletonList(tree("root/missing")),new AtomicBoolean(),result -> {});
        assertEquals(1,unavailable.unavailable);assertEquals(0,unavailable.added);
        AtomicBoolean stop=new AtomicBoolean();LibraryScanner.Result partial=LibraryScanner.scan(c,Collections.singletonList(tree("root")),stop,result -> {if(result.added==1)stop.set(true);});
        assertTrue(partial.cancelled);assertEquals(1,new BookStore(c.getFilesDir()).entries().size());
    }
    @Test public void libraryScreenScanSearchRestoreAndOfflineOpen() throws Exception {
        android.content.SharedPreferences preferences=context().getSharedPreferences("reader",Context.MODE_PRIVATE);
        String previousLast=preferences.getString("last",""),previousLanguage=preferences.getString("language","system");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a -> {a.language="en";a.startScan(Collections.singletonList(tree("root")));});
            for(int i=0;i<100;i++){final boolean[] done={false};scenario.onActivity(a -> done[0]=!a.busy);if(done[0])break;Thread.sleep(100);}
            scenario.onActivity(a -> {assertFalse(a.busy);assertTrue(a.libraryOpen);assertTrue(a.libraryEntries.size()>=2);assertTrue(a.libraryMessage.contains("Skipped: 1"));assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(a.librarySearch.getCurrentHintTextColor(),android.graphics.Color.WHITE)>=4.5);});
            ReaderTest screenshots=new ReaderTest();screenshots.screenshot("library-en.png");
            scenario.onActivity(a -> {a.language="ru";a.libraryMessage="";a.render();});screenshots.screenshot("library-ru.png");
            scenario.onActivity(a -> a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1400);
            scenario.onActivity(a -> assertTrue("Library rows remain visible in landscape",a.libraryList.getHeight()>a.dp(48)));screenshots.screenshot("library-landscape.png");
            scenario.onActivity(a -> a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(1200);
            scenario.onActivity(a -> {a.librarySearch.setText("Петрова");assertEquals(1,a.libraryAdapter.getCount());});
            scenario.recreate();Thread.sleep(1200);
            scenario.onActivity(a -> {assertTrue(a.libraryOpen);assertEquals("Петрова",a.libraryQuery);assertEquals(1,a.libraryAdapter.getCount());});
            treeAccess(false);
            scenario.onActivity(a -> a.libraryList.performItemClick(null,0,0));Thread.sleep(600);
            scenario.onActivity(a -> {assertFalse(a.busy);assertFalse(a.libraryOpen);assertEquals(FixtureDocumentsProvider.FIRST_TITLE,a.book.title);assertEquals("Анна Петрова",a.book.author);a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);});
        }finally{preferences.edit().putString("last",previousLast).putString("language",previousLanguage).apply();}
    }
}
