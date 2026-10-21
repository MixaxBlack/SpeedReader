package com.mixax.speedreader;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Real test-only SAF provider; never packaged in the installable release APK. */
public class FixtureDocumentsProvider extends DocumentsProvider {
    static final String AUTHORITY="com.mixax.speedreader.fixture.documents";
    static final String FIRST_TITLE="Северный ветер",SECOND_TITLE="Ночной полёт";
    static String book(String title,String first,String last,String series,String encoding){
        StringBuilder words=new StringBuilder();for(int i=0;i<30;i++)words.append("книга слово ");
        return "<?xml version=\"1.0\" encoding=\""+encoding+"\"?><FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\"><description><title-info><genre>adventure</genre><author><first-name>"+first+"</first-name><last-name>"+last+"</last-name></author><book-title>"+title+"</book-title><lang>ru</lang><sequence name=\""+series+"\" number=\"2\"/></title-info><document-info><author><nickname>Not the book author</nickname></author></document-info></description><body><section><p>"+words+"</p></section></body></FictionBook>";
    }
    @Override public boolean onCreate(){return true;}
    @Override public Cursor queryRoots(String[] projection){
        String[] columns=projection==null?new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);MatrixCursor.RowBuilder row=cursor.newRow();
        for(String column:columns)row.add(column,column.equals(DocumentsContract.Root.COLUMN_ROOT_ID)?"fixture":column.equals(DocumentsContract.Root.COLUMN_DOCUMENT_ID)?"root":column.equals(DocumentsContract.Root.COLUMN_TITLE)?"SpeedReader test books":column.equals(DocumentsContract.Root.COLUMN_FLAGS)?DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD:null);
        return cursor;
    }
    static final String[] COLUMNS={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_FLAGS};
    void add(MatrixCursor cursor,String id){
        boolean directory=id.equals("root")||id.equals("root/nested");String name=id.substring(id.lastIndexOf('/')+1);
        MatrixCursor.RowBuilder row=cursor.newRow();for(String column:cursor.getColumnNames()){
            Object value=null;
            if(column.equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID))value=id;
            if(column.equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME))value=name;
            if(column.equals(DocumentsContract.Document.COLUMN_MIME_TYPE))value=directory?DocumentsContract.Document.MIME_TYPE_DIR:id.endsWith(".zip")?"application/zip":"application/x-fictionbook+xml";
            if(column.equals(DocumentsContract.Document.COLUMN_SIZE))value=0;
            if(column.equals(DocumentsContract.Document.COLUMN_FLAGS))value=0;
            row.add(column,value);
        }
    }
    @Override public Cursor queryDocument(String id,String[] projection){MatrixCursor c=new MatrixCursor(projection==null?COLUMNS:projection);add(c,id);return c;}
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sortOrder) throws FileNotFoundException {
        MatrixCursor c=new MatrixCursor(projection==null?COLUMNS:projection);
        if(parent.equals("root"))for(String id:new String[]{"root/nested","root/first.fb2","root/copy.fb2","root/broken.fb2","root/ignored.txt","root/archive.fb2.zip"})add(c,id);
        else if(parent.equals("root/nested"))add(c,"root/nested/SECOND.FB2");
        else throw new FileNotFoundException("Unavailable fixture folder");return c;
    }
    @Override public boolean isChildDocument(String parent,String child){return child.startsWith(parent+"/");}
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal) throws FileNotFoundException {
        if(!mode.equals("r"))throw new FileNotFoundException("Read only");
        try{
            byte[] bytes;
            if(id.equals("root/nested/SECOND.FB2"))bytes=book(SECOND_TITLE,"Иван","Сидоров","Небо","windows-1251").getBytes(Charset.forName("windows-1251"));
            else if(id.equals("root/broken.fb2"))bytes="<FictionBook><body><p>broken".getBytes(StandardCharsets.UTF_8);
            else if(id.equals("root/first.fb2")||id.equals("root/copy.fb2")||id.equals("root/archive.fb2.zip")){
                bytes=book(FIRST_TITLE,"Анна","Петрова","Север","UTF-8").getBytes(StandardCharsets.UTF_8);
                if(id.endsWith(".zip")){ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(buffer)){zip.putNextEntry(new ZipEntry("book.fb2"));zip.write(bytes);zip.closeEntry();}bytes=buffer.toByteArray();}
            }else throw new FileNotFoundException(id);
            File file=new File(getContext().getCacheDir(),Integer.toHexString(id.hashCode())+".fixture");try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        }catch(IOException e){throw new FileNotFoundException(e.getMessage());}
    }
}
