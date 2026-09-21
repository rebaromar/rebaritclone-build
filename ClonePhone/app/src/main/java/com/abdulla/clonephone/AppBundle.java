package com.abdulla.clonephone;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Private transfer container: unmodified base APK plus all installed split APKs. */
public final class AppBundle {
    public static final String MIME="application/x-rebaritclone-app";
    public static final long MAX_BYTES=16L*1024*1024*1024;
    public static final int MAX_PARTS=256;
    public interface Sink {void part(String name,InputStream input)throws Exception;}
    public static void write(List<File> apks,File destination)throws Exception{
        if(apks.isEmpty()||apks.size()>MAX_PARTS)throw new IOException("Invalid APK count");
        boolean success=false;long total=0;
        try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destination),1048576))){
            out.setLevel(0);byte[] data=new byte[1048576];int index=0;
            for(File apk:apks){if(!apk.isFile()||!apk.canRead())throw new IOException("APK unavailable");
                out.putNextEntry(new ZipEntry(index++==0?"base.apk":"split-"+(index-1)+".apk"));
                try(InputStream in=new BufferedInputStream(new FileInputStream(apk))){int n;while((n=in.read(data))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>MAX_BYTES)throw new IOException("App too large");out.write(data,0,n);}}
                out.closeEntry();
            }success=true;
        }finally{if(!success)destination.delete();}
    }
    public static void read(InputStream source,Sink sink)throws Exception{
        Set<String> names=new HashSet<>();final long[] total={0};
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source,1048576))){ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){String name=entry.getName();
                if(entry.isDirectory()||!name.matches("base\\.apk|split-[0-9]{1,3}\\.apk")||!names.add(name)||names.size()>MAX_PARTS)throw new IOException("Invalid app bundle");
                InputStream bounded=new FilterInputStream(zip){
                    public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)==-1?-1:b[0]&255;}
                    public int read(byte[] b,int o,int l)throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();int n=in.read(b,o,l);if(n>0){total[0]+=n;if(total[0]>MAX_BYTES)throw new IOException("App too large");}return n;}
                    public void close(){} // Session sink must not close the outer archive.
                };
                sink.part(name,bounded);if(bounded.read()!=-1)throw new IOException("Incomplete APK staging");zip.closeEntry();
            }
        }
        if(!names.contains("base.apk"))throw new IOException("Missing base APK");
    }
}
