package com.mixax.speedreader;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only search of shared storage; Android's private app directories are excluded. */
public final class PhoneScanner {
    public static final class FoundFile {
        public final String path,title,author,bookId;
        FoundFile(String path,String title,String author,String bookId){this.path=path;this.title=title;this.author=author;this.bookId=bookId;}
        public String name(){return new File(path).getName();}
        public boolean matches(String query){return (title+" "+author+" "+path).toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));}
    }
    public static final class Scan {
        public final LibraryScanner.Result stats=new LibraryScanner.Result();
        public final List<FoundFile> files=new ArrayList<>();
    }
    public static List<File> roots(Context context){
        LinkedHashSet<File> roots=new LinkedHashSet<>();roots.add(Environment.getExternalStorageDirectory());
        if(Build.VERSION.SDK_INT>=30){
            StorageManager manager=(StorageManager)context.getSystemService(Context.STORAGE_SERVICE);
            if(manager!=null)for(StorageVolume volume:manager.getStorageVolumes())if(volume.getDirectory()!=null)roots.add(volume.getDirectory());
        }else for(File dir:context.getExternalFilesDirs(null))if(dir!=null){int at=dir.getAbsolutePath().indexOf("/Android/");if(at>0)roots.add(new File(dir.getAbsolutePath().substring(0,at)));}
        return new ArrayList<>(roots);
    }
    private static boolean stopped(AtomicBoolean cancel){return cancel.get()||Thread.currentThread().isInterrupted();}
    public static Scan scan(Context context,List<File> roots,AtomicBoolean cancel,LibraryScanner.Observer observer){
        Scan scan=new Scan();LibraryScanner.Result stats=scan.stats;BookStore store=new BookStore(context.getFilesDir());
        List<String> allowed=new ArrayList<>();for(File root:roots)try{allowed.add(root.getCanonicalPath());}catch(IOException ignored){}
        ArrayDeque<File> pending=new ArrayDeque<>(roots);Set<String> visited=new HashSet<>();long lastReport=0;
        while(!pending.isEmpty()&&!stopped(cancel)&&!stats.limited){
            File file=pending.removeFirst();
            try{
                String path=file.getCanonicalPath();boolean inside=false;for(String root:allowed)if(path.equals(root)||path.startsWith(root+File.separator)){inside=true;break;}if(!inside||!visited.add(path))continue;
                if(++stats.examined>100000||stats.found>=10000){stats.limited=true;break;}
                if(file.isDirectory()){
                    String normalized=path.replace('\\','/');
                    if(normalized.endsWith("/Android/data")||normalized.endsWith("/Android/obb"))continue;
                    File[] children=file.listFiles();if(children==null){stats.unavailable++;continue;}
                    for(File child:children)pending.addLast(child);
                }else{
                    String lower=file.getName().toLowerCase(Locale.ROOT);if(!lower.endsWith(".fb2")&&!lower.endsWith(".fb2.zip"))continue;
                    stats.found++;stats.current=file.getName();FoundFile found;
                    try(InputStream input=new FileInputStream(file)){
                        Book book=Book.parse(input);if(stopped(cancel))break;
                        boolean duplicate=store.contains(book.id);store.add(book,file.getName());
                        if(duplicate)stats.duplicates++;else stats.added++;
                        found=new FoundFile(path,"FB2".equals(book.title)?file.getName():book.title,book.author,book.id);
                    }catch(Exception invalid){if(stopped(cancel))break;stats.failed++;found=new FoundFile(path,file.getName(),"","");}
                    scan.files.add(found);
                    if(scan.files.size()%10==0)save(context,scan.files);
                }
            }catch(Exception inaccessible){if(!stopped(cancel))stats.unavailable++;}
            long now=android.os.SystemClock.elapsedRealtime();if(now-lastReport>=200){observer.update(stats.snapshot());lastReport=now;}
        }
        stats.cancelled=stopped(cancel);stats.current="";
        scan.files.sort((a,b)->a.title.compareToIgnoreCase(b.title));
        if(!scan.files.isEmpty()||!stats.cancelled)try{save(context,scan.files);}catch(Exception error){stats.failed++;}
        observer.update(stats.snapshot());return scan;
    }
    static void save(Context context,List<FoundFile> files) throws Exception {
        JSONArray items=new JSONArray();for(FoundFile f:files){JSONObject j=new JSONObject();j.put("path",f.path);j.put("title",f.title);j.put("author",f.author);j.put("bookId",f.bookId);items.put(j);}
        AtomicFile target=new AtomicFile(new File(context.getFilesDir(),"phone-files.json"));FileOutputStream out=target.startWrite();
        try{out.write(items.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(out);}catch(Exception error){target.failWrite(out);throw error;}
    }
    public static List<FoundFile> load(Context context){
        List<FoundFile> files=new ArrayList<>();
        try(InputStream in=new AtomicFile(new File(context.getFilesDir(),"phone-files.json")).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n,total=0;while((n=in.read(buffer))!=-1){if((total+=n)>16*1024*1024)return files;out.write(buffer,0,n);}
            JSONArray items=new JSONArray(new String(out.toByteArray(),StandardCharsets.UTF_8));
            for(int i=0;i<items.length();i++){JSONObject j=items.getJSONObject(i);String id=j.optString("bookId");if(!id.isEmpty()&&!id.matches("[a-f0-9]{64}"))continue;files.add(new FoundFile(j.getString("path"),j.getString("title"),j.optString("author"),id));}
        }catch(Exception ignored){}
        return files;
    }
}
