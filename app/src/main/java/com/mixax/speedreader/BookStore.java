package com.mixax.speedreader;

import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small metadata sidecars keep library browsing independent of book size. */
public final class BookStore {
    public static final class Entry {
        public final String id,title,author,series,genre,language,source;
        public final int wordCount;
        Entry(JSONObject json) throws Exception {
            id=json.getString("id");title=json.getString("title");author=json.optString("author");
            series=json.optString("series");genre=json.optString("genre");language=json.optString("language");source=json.optString("source");wordCount=json.getInt("wordCount");
            if(!id.matches("[a-f0-9]{64}")||wordCount<1)throw new IOException("METADATA");
        }
        public String displayTitle(){return "FB2".equals(title)&&!source.isEmpty()?source:title;}
        public boolean matches(String query){return (displayTitle()+" "+author+" "+series+" "+genre+" "+language).toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));}
    }
    private final File directory;
    public BookStore(File directory){this.directory=directory;}
    public boolean contains(String id){return new File(directory,id+".json").isFile();}
    public void add(Book book,String source) throws Exception {book.save(directory);writeMetadata(book,source);}
    private void writeMetadata(Book book,String source) throws Exception {
        JSONObject j=new JSONObject();j.put("id",book.id);j.put("title",book.title);j.put("author",book.author);j.put("series",book.series);
        j.put("genre",book.genre);j.put("language",book.language);j.put("wordCount",book.words.size());j.put("source",source);
        AtomicFile file=new AtomicFile(new File(directory,book.id+".meta"));FileOutputStream out=file.startWrite();
        try{out.write(j.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}
    }
    private Entry readMetadata(File file) throws Exception {
        try(InputStream in=new AtomicFile(file).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))!=-1){if((total+=n)>256*1024)throw new IOException("METADATA_LIMIT");out.write(b,0,n);}
            return new Entry(new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)));
        }
    }
    public List<Entry> entries() {
        List<Entry> entries=new ArrayList<>();File[] books=directory.listFiles((d,n)->n.matches("[a-f0-9]{64}\\.json"));
        if(books!=null)for(File f:books){
            if(Thread.currentThread().isInterrupted())break;
            try{
                File meta=new File(directory,f.getName().replace(".json",".meta"));Entry entry;
                try{entry=readMetadata(meta);}catch(Exception absent){Book b=Book.load(f);writeMetadata(b,"");entry=readMetadata(meta);}
                entries.add(entry);
            }catch(Exception damaged){ /* One damaged cache entry must not hide other books. */ }
        }
        entries.sort((a,b)->a.displayTitle().compareToIgnoreCase(b.displayTitle()));return entries;
    }
}
