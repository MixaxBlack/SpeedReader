package com.mixax.speedreader;

import android.os.Environment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.UUID;
import static org.junit.Assert.*;

/** Host grants all-files access before this separate instrumentation process starts. */
@RunWith(AndroidJUnit4.class) @SdkSuppress(minSdkVersion=30)
public class PhoneStorageGrantTest {
    @Test public void scanSharedVolumesAndOpenTitles() throws Exception {
        PhoneScanTest helper=new PhoneScanTest();ReaderTest device=new ReaderTest();
        File fixture=new File(Environment.getExternalStorageDirectory(),"SpeedReader-Test-"+UUID.randomUUID());
        try{
            helper.write(new File(fixture,"phone.fb2"),helper.xml());helper.write(new File(fixture,"nested/copy.FB2"),helper.xml());helper.write(new File(fixture,"broken.fb2"),"damaged");
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                helper.waitReady(scenario);scenario.onActivity(a -> {a.language="en";a.library();});helper.waitReady(scenario);
                scenario.onActivity(a -> {assertTrue(a.hasPhoneAccess());assertTrue(PhoneScanner.roots(a).stream().anyMatch(f->f.equals(Environment.getExternalStorageDirectory())));a.scanPhone();});helper.waitReady(scenario);
                scenario.onActivity(a -> {assertTrue(a.phoneFilesOpen);assertTrue(a.phoneFiles.stream().anyMatch(f->f.title.equals("Телефонная книга")));});device.screenshot("phone-found-en.png");
                scenario.onActivity(a -> {a.language="ru";a.render();assertTrue(a.libraryStatus.getText().toString().contains("���������:"));assertFalse(a.libraryStatus.getText().toString().contains("Added:"));a.librarySearch.setText("Соколова");assertTrue(a.foundAdapter.getCount()>=2);});device.screenshot("phone-found-ru.png");
                scenario.recreate();helper.waitReady(scenario);
                scenario.onActivity(a -> {assertTrue(a.phoneFilesOpen);assertEquals("Соколова",a.libraryQuery);assertTrue(a.foundAdapter.getCount()>=2);a.pendingPhoneAccess=true;});
                scenario.recreate();helper.waitReady(scenario);
                scenario.onActivity(a -> {assertFalse("Granted access resumes search after recreation",a.pendingPhoneAccess);a.librarySearch.setText("Соколова");assertTrue(a.foundAdapter.getCount()>=2);a.libraryList.performItemClick(null,0,0);});helper.waitReady(scenario);
                scenario.onActivity(a -> {assertFalse(a.libraryOpen);assertEquals("Телефонная книга",a.book.title);});
            }
        }finally{
            if(fixture.getCanonicalPath().startsWith(Environment.getExternalStorageDirectory().getCanonicalPath()+File.separator+"SpeedReader-Test-"))helper.removeFixture(fixture);
        }
    }
}
