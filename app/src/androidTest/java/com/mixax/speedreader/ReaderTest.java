package com.mixax.speedreader;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.LinearLayout;
import android.widget.TextView;
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
            scenario.onActivity(a -> {for(int i=0;i<a.content.getChildCount();i++){android.view.View child=a.content.getChildAt(i);if(child instanceof android.widget.TextView&&((android.widget.TextView)child).getText().toString().equals("10 тем чтения")){((android.widget.ScrollView)a.content.getParent()).scrollTo(0,child.getTop());break;}}});screenshot("settings-themes-ru.png");
        }
    }
    @Test public void speedRangeUpgradeAndSlowPlayback() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(c.getSharedPreferences("reader",Context.MODE_PRIVATE).edit().putInt("speed",1200).commit());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            Thread.sleep(1500);
            scenario.onActivity(a -> {
                assertEquals(500,a.speed);
                assertNull("Reader leaves the full word width free",a.speedControl);a.showSpeedSettings();
                assertEquals(30,a.speedControl.getMin());assertEquals(500,a.speedControl.getMax());assertEquals(1,a.speedControl.getKeyProgressIncrement());
                a.settings=false;a.render();a.speed=30;a.position=0;a.saveSettings();a.toggle();
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
    @Test public void verticalSpeedTouchKeyboardAndAccessibility() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            Thread.sleep(1500);
            scenario.onActivity(a -> {assertNull(a.speedControl);a.speedValue.performClick();});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(300);
            screenshot("speed-settings.png");
            scenario.onActivity(a -> {
                VerticalSpeedBar bar=a.speedControl;
                assertTrue("Vertical track must be taller than its touch width",bar.getHeight()>bar.getWidth()*2);
                touch(bar,android.view.MotionEvent.ACTION_DOWN,bar.getHeight());assertEquals(30,a.speed);
                touch(bar,android.view.MotionEvent.ACTION_MOVE,0);assertEquals(500,a.speed);
                touch(bar,android.view.MotionEvent.ACTION_UP,0);
                android.os.Bundle arguments=new android.os.Bundle();arguments.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,137);
                assertTrue(bar.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),arguments));assertEquals(137,a.speed);
                bar.onKeyDown(android.view.KeyEvent.KEYCODE_DPAD_UP,new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_UP));assertEquals(138,a.speed);
                a.settings=false;a.render();a.faster.performClick();assertEquals(139,a.speed);a.slower.performClick();assertEquals(138,a.speed);
                a.saveSettings();
            });
            scenario.recreate();Thread.sleep(1000);scenario.onActivity(a -> {assertEquals(138,a.speed);assertNull(a.speedControl);a.showSpeedSettings();assertEquals(138,a.speedControl.getProgress());a.changeSpeed(300);});
        }
    }
    @Test public void longWordFitsAndNextWordRestoresPreferredSize() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            Thread.sleep(1500);
            final Book[] original=new Book[1];final int[] old=new int[3];
            scenario.onActivity(a -> {a.pause();original[0]=a.book;old[0]=a.fontSize;old[1]=a.font;old[2]=a.position;});
            Book fitting=new Book("Word fitting",Arrays.asList(String.join("",Collections.nCopies(12,"длинноеслово")),"book"));
            try{
                for(int face=0;face<4;face++){
                    final int selected=face;
                    scenario.onActivity(a -> {a.fontSize=72;a.font=selected;a.book=fitting;a.position=0;a.render();});
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(150);
                    scenario.onActivity(a -> {
                        float preferred=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,72,a.getResources().getDisplayMetrics());
                        assertTrue("Long word shrinks",a.word.getTextSize()<preferred);
                        assertTrue("Complete word fits",a.word.getPaint().measureText(a.word.getText().toString())<=a.word.getWidth()-a.word.getCompoundPaddingLeft()-a.word.getCompoundPaddingRight());
                        assertEquals(72,a.fontSize);
                    });
                    if(face==0)screenshot("long-word.png");
                    scenario.onActivity(a -> {
                        float preferred=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,72,a.getResources().getDisplayMetrics());a.move(1);
                        assertEquals("Next short word restores selected size",preferred,a.word.getTextSize(),0.1f);assertEquals(72,a.fontSize);
                    });
                    if(face==0)screenshot("restored-word.png");
                }
            }finally{scenario.onActivity(a -> {a.book=original[0];a.fontSize=old[0];a.font=old[1];a.position=old[2];a.saveSettings();a.savePosition();a.render();});}
        }
    }
    @Test public void largeSystemFontKeepsTransportLabelsSeparate() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences preferences=context.getSharedPreferences("reader",Context.MODE_PRIVATE);
        String previousLanguage=preferences.getString("language","system");
        shell("settings put system font_scale 1.3");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            Thread.sleep(1500);
            for(String language:new String[]{"en","ru"}){
                scenario.onActivity(a -> {a.pause();a.language=language;a.render();});
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(250);
                scenario.onActivity(a -> {
                    assertTrue(a.getResources().getConfiguration().fontScale>1.15f);
                    assertTrue("Read has its own wide row",a.play.getWidth()>a.faster.getWidth()*2);
                    LinearLayout steps=(LinearLayout)a.root.getChildAt(a.root.getChildCount()-2);
                    for(int i=0;i<steps.getChildCount();i++){
                        TextView label=(TextView)steps.getChildAt(i);
                        assertTrue("Step labels fit independently",label.getPaint().measureText(label.getText().toString())<=label.getWidth()-label.getPaddingLeft()-label.getPaddingRight());
                        assertTrue(label.getHeight()>=a.dp(48));
                    }
                });
                if(language.equals("ru"))screenshot("phone-ru-large-font.png");
            }
        }finally{shell("settings put system font_scale 1.0");preferences.edit().putString("language",previousLanguage).apply();}
    }
    void shell(String command) throws Exception {
        try(android.os.ParcelFileDescriptor descriptor=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
            InputStream output=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
            byte[] buffer=new byte[1024];while(output.read(buffer)!=-1){}
        }
    }
    void touch(VerticalSpeedBar bar,int action,float y){
        long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now,action,bar.getWidth()/2f,y,0);
        try{assertTrue(bar.dispatchTouchEvent(event));}finally{event.recycle();}
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
