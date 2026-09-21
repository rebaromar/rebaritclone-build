package com.abdulla.clonephone;
import java.util.Locale;

/** Manifest counts; a current file remains pending until its final acknowledgement. */
public final class TransferStats {
    public final long totalBytes;
    public final boolean totalKnown;
    public final int count;
    private final int[][] prefix;
    public TransferStats(String[] names,String[] mimes,long[] sizes){
        if(names.length!=mimes.length||names.length!=sizes.length)throw new IllegalArgumentException();
        count=sizes.length;prefix=new int[count+1][4];long total=0;boolean known=true;
        for(int n=0;n<count;n++){System.arraycopy(prefix[n],0,prefix[n+1],0,4);prefix[n+1][kind(names[n],mimes[n])]++;if(sizes[n]<0)known=false;else total=Math.addExact(total,sizes[n]);}
        totalBytes=total;totalKnown=known;
    }
    public static int kind(String name,String mime){
        String n=name==null?"":name.toLowerCase(Locale.ROOT),m=mime==null?"":mime.toLowerCase(Locale.ROOT);
        if(m.startsWith("image/")||n.matches(".*\\.(jpg|jpeg|png|webp|heic|heif|gif|bmp|avif)$"))return 0;
        if(m.startsWith("video/")||n.matches(".*\\.(mp4|mkv|mov|webm|avi|3gp|m4v)$"))return 1;
        if(n.endsWith(".apk")||n.endsWith(".rbapp"))return 2;return 3;
    }
    public int total(int kind){return prefix[count][kind];}
    public int remaining(int kind,int completed){return total(kind)-prefix[Math.max(0,Math.min(count,completed))][kind];}
    public long remainingBytes(long moved){return totalKnown?Math.max(0,totalBytes-Math.max(0,moved)):-1;}
    public static String gb(long bytes){return bytes>0&&bytes<1000000?"<0.001 GB":String.format(Locale.US,"%.3f GB",Math.max(0,bytes)/1000000000.0);}
}
