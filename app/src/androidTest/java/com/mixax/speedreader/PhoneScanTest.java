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
    @Test @SdkSuppress(minSdkVersion=30) public void deniedPhoneAccessKeepsFolderFlowAvailable() throws Exception {
        ReaderTest device=new ReaderTest();String language=context().getSharedPreferences("reader",Context.MODE_PRIVATE).getString("language","system");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitReady(scenario);scenario.onActivity(a -> {a.language="en";a.library();});waitReady(scenario);
            scenario.onActivity(a -> {assertFalse(a.hasPhoneAccess());a.scanPhone();});settleSystemSettings();device.screenshot("phone-access.png");
            boolean returned=false;
            for(int back=0;back<3&&!returned;back++){
                device.shell("input keyevent 4");
                for(int i=0;i<20;i++){final boolean[] done={false};scenario.onActivity(a -> done[0]=!a.pendingPhoneAccess);if(done[0]){returned=true;break;}Thread.sleep(100);}
            }
            assertTrue("Android Settings must return the permission result",returned);
            scenario.onActivity(a -> {assertFalse(a.hasPhoneAccess());assertFalse(a.scanning);assertTrue("Denial message: "+a.libraryMessage,a.libraryMessage.contains("not granted"));});
        }finally{context().getSharedPreferences("reader",Context.MODE_PRIVATE).edit().putString("language",language).apply();}
    }
    void settleSystemSettings() throws Exception {
        android.app.UiAutomation ui=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        for(int attempt=0;attempt<4;attempt++){
            try{ui.waitForIdle(400,4000);}catch(java.util.concurrent.TimeoutException busy){}
            android.view.accessibility.AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null){
                // A cold emulator's launcher can time out while opening Settings.
                // Recover only the external Quickstep dialog, never an app ANR.
                if(!root.findAccessibilityNodeInfosByText("Quickstep isn't responding").isEmpty()){
                    for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Wait")){
                        android.view.accessibility.AccessibilityNodeInfo action=node;
                        for(int depth=0;depth<4&&action!=null&&!action.isClickable();depth++)action=action.getParent();
                        if(action!=null)action.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                    }
                    android.util.Log.w("SpeedReaderTest","Waited for external emulator launcher ANR");Thread.sleep(800);
                }else if("com.android.settings".contentEquals(root.getPackageName())){root.recycle();return;}
                root.recycle();
            }
            Thread.sleep(300);
        }
        fail("Android Settings did not become ready");
    }
    void removeFixture(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)removeFixture(child);file.delete();}
}
