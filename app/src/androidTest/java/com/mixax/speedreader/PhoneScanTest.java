package com.mixax.speedreader;

import android.content.*;
import android.os.Environment;
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
public class PhoneScanTest {
    Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    void write(File file,String value) throws Exception {assertTrue(file.getParentFile().isDirectory()||file.getParentFile().mkdirs());try(OutputStream out=new FileOutputStream(file)){out.write(value.getBytes(StandardCharsets.UTF_8));}}
    String xml(){return FixtureDocumentsProvider.book("Телефонная книга","Мария","Соколова","Путешествия","UTF-8");}
    @Test public void recursiveFilesPersistMetadataAndKeepSeparatePaths() throws Exception {
        File isolated=new File(context().getCacheDir(),"phone-index-"+UUID.randomUUID());assertTrue(isolated.mkdirs());
        Context target=new ContextWrapper(context()){@Override public File getFilesDir(){return isolated;}};
        File root=new File(isolated,"source");write(new File(root,"book.fb2"),xml());write(new File(root,"nested/copy.FB2"),xml());write(new File(root,"broken.fb2"),"damaged");write(new File(root,"ignore.txt"),xml());
        PhoneScanner.Scan scan=PhoneScanner.scan(target,Arrays.asList(root,new File(root,"nested")),new AtomicBoolean(),r -> {});
        assertEquals(3,scan.stats.found);assertEquals(1,scan.stats.added);assertEquals(1,scan.stats.duplicates);assertEquals(1,scan.stats.failed);assertEquals(3,scan.files.size());
        assertEquals(3,PhoneScanner.load(target).size());assertEquals(1,new BookStore(isolated).entries().size());
        assertTrue(scan.files.stream().anyMatch(f->f.title.equals("Телефонная книга")&&f.author.equals("Мария Соколова")&&f.matches("copy.FB2")));
        assertEquals(0,PhoneScanner.scan(target,Collections.singletonList(root),new AtomicBoolean(),r -> {}).stats.added);
        assertTrue(PhoneScanner.scan(target,Collections.singletonList(root),new AtomicBoolean(true),r -> {}).stats.cancelled);
        assertEquals(3,PhoneScanner.load(target).size());
    }
    void waitReady(ActivityScenario<MainActivity> scenario) throws Exception {for(int i=0;i<200;i++){final boolean[] ready={false};scenario.onActivity(a -> ready[0]=!a.busy);if(ready[0])return;Thread.sleep(100);}fail("Scan did not finish");}
    @Test @SdkSuppress(minSdkVersion=30) public void phonePermissionWholeStorageTitlesAndOfflineOpen() throws Exception {
        ReaderTest device=new ReaderTest();Context c=context();android.content.SharedPreferences preferences=c.getSharedPreferences("reader",Context.MODE_PRIVATE);
        String last=preferences.getString("last",""),language=preferences.getString("language","system");
        File fixture=new File(Environment.getExternalStorageDirectory(),"SpeedReader-Test-"+UUID.randomUUID());
        device.shell("appops set --uid com.mixax.speedreader MANAGE_EXTERNAL_STORAGE ignore");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitReady(scenario);scenario.onActivity(a -> {a.language="en";a.library();});waitReady(scenario);
            scenario.onActivity(a -> {assertFalse(a.hasPhoneAccess());a.scanPhone();});Thread.sleep(800);device.screenshot("phone-access.png");
            device.shell("input keyevent 4");Thread.sleep(800);
            scenario.onActivity(a -> {assertFalse(a.hasPhoneAccess());assertFalse(a.scanning);assertTrue(a.libraryMessage.contains("not granted"));});
            device.shell("appops set --uid com.mixax.speedreader MANAGE_EXTERNAL_STORAGE allow");
            write(new File(fixture,"phone.fb2"),xml());write(new File(fixture,"nested/copy.FB2"),xml());write(new File(fixture,"broken.fb2"),"damaged");
            scenario.onActivity(a -> {assertTrue(a.hasPhoneAccess());assertTrue(PhoneScanner.roots(a).stream().anyMatch(f->f.equals(Environment.getExternalStorageDirectory())));a.scanPhone();});waitReady(scenario);
            scenario.onActivity(a -> {assertTrue(a.phoneFilesOpen);assertTrue(a.phoneFiles.stream().anyMatch(f->f.title.equals("Телефонная книга")));});device.screenshot("phone-found-en.png");
            scenario.onActivity(a -> {a.language="ru";a.render();a.librarySearch.setText("Соколова");assertTrue(a.foundAdapter.getCount()>=2);});device.screenshot("phone-found-ru.png");
            scenario.recreate();waitReady(scenario);
            scenario.onActivity(a -> {assertTrue(a.phoneFilesOpen);assertEquals("Соколова",a.libraryQuery);assertTrue(a.foundAdapter.getCount()>=2);});
            device.shell("appops set --uid com.mixax.speedreader MANAGE_EXTERNAL_STORAGE ignore");
            scenario.onActivity(a -> {assertFalse(a.hasPhoneAccess());a.libraryList.performItemClick(null,0,0);});waitReady(scenario);
            scenario.onActivity(a -> {assertFalse(a.libraryOpen);assertEquals("Телефонная книга",a.book.title);assertEquals("Мария Соколова",a.book.author);});
        }finally{
            device.shell("appops set --uid com.mixax.speedreader MANAGE_EXTERNAL_STORAGE allow");
            if(fixture.getCanonicalPath().startsWith(Environment.getExternalStorageDirectory().getCanonicalPath()+File.separator+"SpeedReader-Test-"))removeFixture(fixture);
            device.shell("appops set --uid com.mixax.speedreader MANAGE_EXTERNAL_STORAGE ignore");
            preferences.edit().putString("last",last).putString("language",language).apply();
        }
    }
    void removeFixture(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)removeFixture(child);file.delete();}
}
