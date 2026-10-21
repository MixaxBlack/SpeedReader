package com.mixax.speedreader;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Bounded streaming FB2 reader. Never resolves DTDs, external entities or images. */
public final class Book {
    public final String title, id, author, series, genre, language;
    public final List<String> words;
    static final int MAX_BYTES = 32 * 1024 * 1024;
    public Book(String title, List<String> words) throws Exception {
        this(title,words,"","","","");
    }
    public Book(String title,List<String> words,String author,String series,String genre,String language) throws Exception {
        if (words.isEmpty()) throw new IOException("EMPTY");
        this.title = title.trim().isEmpty() ? "FB2" : title;
        this.author=author;this.series=series;this.genre=genre;this.language=language;
        this.words = Collections.unmodifiableList(new ArrayList<>(words));
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        hash.update(this.title.getBytes(StandardCharsets.UTF_8));
        for (String word : words) { hash.update((byte)0); hash.update(word.getBytes(StandardCharsets.UTF_8)); }
        StringBuilder hex = new StringBuilder();
        for (byte b : hash.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
        id = hex.toString();
    }
    static InputStream bounded(InputStream source) {
        return new FilterInputStream(source) {
            int total;
            private void count(int n) throws IOException { if (n > 0 && (total += n) > MAX_BYTES) throw new IOException("LIMIT"); }
            @Override public int read() throws IOException { int v = super.read(); count(v < 0 ? 0 : 1); return v; }
            @Override public int read(byte[] b, int off, int len) throws IOException { int n = in.read(b, off, len); count(n); return n; }
        };
    }
    public static Book parse(InputStream input) throws Exception {
        PushbackInputStream sniff = new PushbackInputStream(new BufferedInputStream(input), 4);
        byte[] magic = new byte[4]; int n = sniff.read(magic); if (n > 0) sniff.unread(magic, 0, n);
        if (n >= 2 && magic[0] == 'P' && magic[1] == 'K') {
            ZipInputStream zip = new ZipInputStream(sniff);
            ZipEntry entry; int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 100) throw new IOException("LIMIT");
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".fb2")) return parseXml(bounded(zip));
                // Do not decompress unselected archive members.
                throw new IOException("ZIP_FIRST_FB2");
            }
            throw new IOException("EMPTY");
        }
        return parseXml(bounded(sniff));
    }
    private static Book parseXml(InputStream input) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        p.setInput(input, null); // Honors XML declaration, including Windows-1251.
        StringBuilder title = new StringBuilder(), paragraph = new StringBuilder(), fieldValue=new StringBuilder();
        List<String> authors=new ArrayList<>(),nameParts=new ArrayList<>(),genres=new ArrayList<>();
        String series="",language="",nickname="",field="";
        List<String> words = new ArrayList<>();
        int bodyDepth = -1, infoDepth=-1, authorDepth=-1, fieldDepth=-1; boolean root = false, rootClosed = false;
        for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.nextToken()) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("CANCELLED");
            if (event == XmlPullParser.DOCDECL) throw new IOException("DTD");
            if (event == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if (p.getDepth() == 1) { root = "FictionBook".equals(tag); if (!root) throw new IOException("FB2"); }
                if("title-info".equals(tag)&&p.getDepth()==3)infoDepth=p.getDepth();
                if(infoDepth>=0){
                    if("author".equals(tag)){authorDepth=p.getDepth();nameParts.clear();nickname="";}
                    if("book-title".equals(tag)||"genre".equals(tag)||"lang".equals(tag)||
                        (authorDepth>=0&&("first-name".equals(tag)||"middle-name".equals(tag)||"last-name".equals(tag)||"nickname".equals(tag)))){
                        field=tag;fieldDepth=p.getDepth();fieldValue.setLength(0);
                    }
                    if("sequence".equals(tag)&&series.isEmpty()){
                        String name=p.getAttributeValue(null,"name"),number=p.getAttributeValue(null,"number");
                        if(name!=null)series=name.trim()+(number==null||number.trim().isEmpty()?"":" · "+number.trim());
                    }
                }
                if ("body".equals(tag) && p.getAttributeValue(null, "name") == null) bodyDepth = p.getDepth();
            } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT || event == XmlPullParser.ENTITY_REF) {
                String value = p.getText();
                if (value != null) {
                    if(fieldDepth>=0)fieldValue.append(value);
                    if (bodyDepth >= 0) paragraph.append(value);
                }
            } else if (event == XmlPullParser.END_TAG) {
                String tag = p.getName();
                if (p.getDepth() == 1 && "FictionBook".equals(tag)) rootClosed = true;
                if(p.getDepth()==fieldDepth){
                    String value=fieldValue.toString().replaceAll("[\\s\\u00a0]+"," ").trim();
                    if("book-title".equals(field)&&title.length()==0)title.append(value);
                    else if("genre".equals(field)&&!value.isEmpty())genres.add(value);
                    else if("lang".equals(field))language=value;
                    else if("nickname".equals(field))nickname=value;
                    else if(!value.isEmpty())nameParts.add(value);
                    fieldDepth=-1;
                }
                if(p.getDepth()==authorDepth){
                    String name=String.join(" ",nameParts);if(name.isEmpty())name=nickname;
                    if(!name.isEmpty())authors.add(name);authorDepth=-1;
                }
                if(p.getDepth()==infoDepth)infoDepth=-1;
                if (bodyDepth >= 0 && ("p".equals(tag) || "v".equals(tag) || "subtitle".equals(tag) || "title".equals(tag) || "body".equals(tag))) {
                    String text = paragraph.toString().trim(); paragraph.setLength(0);
                    if (!text.isEmpty()) Collections.addAll(words, text.split("[\\s\\u00a0]+"));
                    if (words.size() > 1000000) throw new IOException("LIMIT");
                }
                if (p.getDepth() == bodyDepth) bodyDepth = -1;
            }
        }
        if (!root || !rootClosed) throw new IOException("FB2");
        return new Book(title.toString().trim(),words,String.join(", ",authors),series,String.join(", ",genres),language);
    }
    public void save(File directory) throws Exception {
        JSONObject json = new JSONObject(); json.put("title", title); json.put("words", new JSONArray(words));
        json.put("author",author);json.put("series",series);json.put("genre",genre);json.put("language",language);
        File temporary = new File(directory, id + ".tmp"), destination = new File(directory, id + ".json");
        try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(json.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        if (!temporary.renameTo(destination)) throw new IOException("SAVE");
    }
    public static Book load(File file) throws Exception {
        byte[] bytes;
        try (InputStream in = bounded(new FileInputStream(file)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer,0,n); bytes=out.toByteArray();
        }
        JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        JSONArray a = json.getJSONArray("words"); List<String> words = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) words.add(a.getString(i));
        return new Book(json.getString("title"),words,json.optString("author"),json.optString("series"),json.optString("genre"),json.optString("language"));
    }
}
