package com.mixax.speedreader;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReaderTest {
    String xml(String text) { return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\"><description><title-info><book-title>Тест</book-title></title-info></description><body><section><p>"+text+"</p></section></body><body name=\"notes\"><section><p>HIDDEN NOTE</p></section></body></FictionBook>"; }
    Book parse(String text) throws Exception { return Book.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))); }
    @Test public void unicodeInlineAndMetadata() throws Exception {
        Book b=parse(xml("При<emphasis>вет</emphasis> мир! &amp; hello."));
        assertEquals("Тест",b.title); assertEquals(Arrays.asList("Привет","мир!","&","hello."),b.words);
    }
    @Test public void windows1251() throws Exception {
        String s=xml("Привет книга").replace("UTF-8","windows-1251");
        assertEquals("Привет",Book.parse(new ByteArrayInputStream(s.getBytes(Charset.forName("windows-1251")))).words.get(0));
    }
    @Test public void zippedBook() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(out)){z.putNextEntry(new ZipEntry("book.fb2"));z.write(xml("One two").getBytes(StandardCharsets.UTF_8));z.closeEntry();}
        assertEquals(2,Book.parse(new ByteArrayInputStream(out.toByteArray())).words.size());
    }
    @Test public void rejectInvalidEmptyAndEntities() throws Exception {
        for(String s:new String[]{"<html><p>Wrong</p></html>",xml(""),xml("broken").replace("</FictionBook>",""),"<!DOCTYPE FictionBook [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+xml("&x;").replaceFirst("<\\?xml[^>]+>","")}) {
            try {parse(s); fail("Accepted invalid input: "+s);} catch(Exception expected) {assertNotNull(expected);}
        }
    }
    @Test public void cacheRoundTripAndIdentity() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Book b=parse(xml("One два three"));b.save(c.getCacheDir());
        File f=new File(c.getCacheDir(),b.id+".json");Book loaded=Book.load(f);
        assertEquals(b.id,loaded.id); assertEquals(b.words,loaded.words);assertTrue(f.delete());
        assertNotEquals(b.id,parse(xml("Other book")).id);
    }
    @Test public void playbackSeekPersistenceAndBilingualSettings() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            Thread.sleep(1500);
            scenario.onActivity(a -> {a.language="en";a.speed=500;a.render();assertEquals("Appearance",a.tr("Appearance","Оформление"));a.position=0;a.toggle();assertTrue(a.playing);});
            Thread.sleep(450);
            scenario.onActivity(a -> {a.pause();assertTrue(a.position>=2);a.move(10);assertTrue(a.position>=12);a.move(-10);a.savePosition();a.prefs.edit().putInt("testPosition",a.position).apply();a.language="ru";a.fontSize=48;a.font=2;a.theme=-1;a.background=0xFF101010;a.foreground=0xFFF0F0F0;a.saveSettings();a.settings=true;a.render();});
            scenario.recreate();Thread.sleep(1000);
            scenario.onActivity(a -> {assertEquals("ru",a.language);assertEquals(48,a.fontSize);assertEquals(2,a.font);assertEquals(-1,a.theme);assertEquals(a.prefs.getInt("testPosition",-1),a.position);assertFalse(a.playing);a.language="en";a.theme=0;a.foreground=Themes.fg(0);a.background=Themes.bg(0);a.font=0;a.fontSize=42;a.position=4;a.saveSettings();a.render();});
            screenshot("phone-en.png");
            scenario.onActivity(a -> {a.language="ru";a.theme=1;a.background=Themes.bg(1);a.foreground=Themes.fg(1);a.render();});
            screenshot("phone-ru-night.png");
            scenario.onActivity(a -> {a.settings=true;a.render();});screenshot("settings-ru.png");
        }
    }
    @Test public void speedRangeUpgradeAndSlowPlayback() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(c.getSharedPreferences("reader",Context.MODE_PRIVATE).edit().putInt("speed",1200).commit());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            Thread.sleep(1500);
            scenario.onActivity(a -> {
                assertEquals(500,a.speed);
                com.google.android.material.slider.Slider slider=(com.google.android.material.slider.Slider)a.root.getChildAt(a.root.getChildCount()-1);
                assertEquals(30f,slider.getValueFrom(),0f);assertEquals(500f,slider.getValueTo(),0f);assertEquals(1f,slider.getStepSize(),0f);
                a.speed=30;a.position=0;a.saveSettings();a.toggle();
            });
            Thread.sleep(700);
            scenario.onActivity(a -> {a.pause();assertEquals(0,a.position);});
            scenario.recreate();Thread.sleep(1000);
            scenario.onActivity(a -> {assertEquals(30,a.speed);a.speed=300;a.saveSettings();});
        }
        assertTrue(c.getSharedPreferences("reader",Context.MODE_PRIVATE).edit().putInt("speed",1).commit());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> {assertEquals(30,a.speed);a.speed=300;a.saveSettings();});
        }
    }
    void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(250);
        Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);
        File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"screenshots");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();
        // UTP uninstalls the target after tests. Preserve review captures in shell-owned storage.
        try(android.os.ParcelFileDescriptor descriptor=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("screencap -p /data/local/tmp/speedreader-"+name);
            InputStream commandOutput=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer=new byte[1024];while(commandOutput.read(buffer)!=-1){ /* Wait for screencap completion. */ }
        }
    }
}
