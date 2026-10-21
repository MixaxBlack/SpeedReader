package com.mixax.speedreader;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Host revokes access after the granted process ends; cached books must still open. */
@RunWith(AndroidJUnit4.class) @SdkSuppress(minSdkVersion=30)
public class PhoneStorageOfflineTest {
    @Test public void foundTitlesRemainReadableAfterPermissionRevocation() throws Exception {
        PhoneScanTest helper=new PhoneScanTest();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            helper.waitReady(scenario);scenario.onActivity(a -> {assertFalse(a.hasPhoneAccess());a.phoneFilesOpen=true;a.library();});helper.waitReady(scenario);
            scenario.onActivity(a -> {assertTrue(a.phoneFiles.size()>=3);a.librarySearch.setText("Соколова");assertTrue(a.foundAdapter.getCount()>=2);a.libraryList.performItemClick(null,0,0);});helper.waitReady(scenario);
            scenario.onActivity(a -> {assertFalse(a.libraryOpen);assertEquals("Телефонная книга",a.book.title);assertEquals("Мария Соколова",a.book.author);});
        }
    }
}
