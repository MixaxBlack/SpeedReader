package com.mixax.speedreader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Recursive read-only SAF traversal: folders must be granted by the user. */
public final class LibraryScanner {
    public static final class Result {
        public int examined,found,added,duplicates,failed,unavailable;
        public boolean cancelled,limited;
        public String current="";
        public Result snapshot(){Result r=new Result();r.examined=examined;r.found=found;r.added=added;r.duplicates=duplicates;r.failed=failed;r.unavailable=unavailable;r.cancelled=cancelled;r.limited=limited;r.current=current;return r;}
    }
    public interface Observer{void update(Result result);}
    private static final class Folder {
        final Uri tree;final String id;
        Folder(Uri tree,String id){this.tree=tree;this.id=id;}
    }
    private static boolean stopped(AtomicBoolean cancel){return cancel.get()||Thread.currentThread().isInterrupted();}
    public static Result scan(Context context,List<Uri> roots,AtomicBoolean cancel,Observer observer) {
        Result result=new Result();BookStore store=new BookStore(context.getFilesDir());
        Set<String> seen=new HashSet<>();ArrayDeque<Folder> pending=new ArrayDeque<>();
        for(Uri tree:roots){try{pending.add(new Folder(tree,DocumentsContract.getTreeDocumentId(tree)));}catch(Exception bad){result.unavailable++;}}
        String[] projection={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
        while(!pending.isEmpty()&&!stopped(cancel)&&!result.limited){
            Folder folder=pending.removeFirst();String key=folder.tree.getAuthority()+"/"+folder.id;
            if(!seen.add(key))continue;
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(folder.tree,folder.id);
            try(Cursor cursor=context.getContentResolver().query(children,projection,null,null,null)){
                if(cursor==null)throw new IOException("FOLDER_UNAVAILABLE");
                while(cursor.moveToNext()){
                    if(stopped(cancel))break;
                    if(++result.examined>100000||result.found>=10000){result.limited=true;break;}
                    String id=cursor.getString(0),name=cursor.getString(1),mime=cursor.getString(2);
                    if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){pending.addLast(new Folder(folder.tree,id));continue;}
                    if(name==null)continue;String lower=name.toLowerCase(Locale.ROOT);
                    if(!lower.endsWith(".fb2")&&!lower.endsWith(".fb2.zip"))continue;
                    result.found++;result.current=name;observer.update(result.snapshot());
                    try(InputStream input=context.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(folder.tree,id))){
                        if(input==null)throw new IOException("FILE_UNAVAILABLE");
                        Book book=Book.parse(input);if(stopped(cancel))break;
                        boolean duplicate=store.contains(book.id);store.add(book,name);
                        if(duplicate)result.duplicates++;else result.added++;
                    }catch(Exception invalid){if(stopped(cancel))break;result.failed++;}
                    observer.update(result.snapshot());
                }
            }catch(Exception denied){if(!stopped(cancel))result.unavailable++;}
        }
        result.cancelled=stopped(cancel);result.current="";observer.update(result.snapshot());return result;
    }
}
