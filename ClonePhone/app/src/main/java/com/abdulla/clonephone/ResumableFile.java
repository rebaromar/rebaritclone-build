package com.abdulla.clonephone;

import java.io.*;
import java.security.*;

/** Resume only authenticated full records; verify retained prefix against the source. */
public final class ResumableFile {
    public interface Progress { void update(long bytes) throws Exception; }
    public interface Publisher { void publish(File file) throws Exception; }
    public static final class Entry {
        public final File partial;
        public boolean done;
        public long length;
        public byte[] digest;
        public Entry(File file){partial=file;}
    }
    private static MessageDigest hash() throws Exception{return MessageDigest.getInstance("SHA-256");}
    private static void prefix(InputStream in,long length,MessageDigest hash,Progress progress)throws Exception{
        byte[] b=new byte[262144];long n=0;
        while(n<length){int read=in.read(b,0,(int)Math.min(b.length,length-n));if(read<0)throw new IOException("SOURCE_CHANGED");if(read==0)continue;hash.update(b,0,read);n+=read;progress.update(-1);}
    }
    public static long send(SecureChannel channel,InputStream in,long size,int chunk,Progress progress)throws Exception{
        DataInputStream state=new DataInputStream(new ByteArrayInputStream(channel.read()));
        long offset=state.readLong();boolean done=state.readBoolean();byte[] expected=new byte[32];state.readFully(expected);
        if(state.available()!=0||offset<0||(size>=0&&offset>size))throw new IOException("Invalid resume offset");
        MessageDigest hash=hash();
        try{prefix(in,offset,hash,progress);}catch(Exception e){channel.write(new byte[]{0});throw e;}
        byte[] actual=((MessageDigest)hash.clone()).digest();
        if(!MessageDigest.isEqual(actual,expected)){channel.write(new byte[]{0});throw new IOException("SOURCE_CHANGED");}
        channel.write(new byte[]{1});progress.update(offset);
        if(done){if(in.read()!=-1)throw new IOException("SOURCE_CHANGED");return offset;}
        long bytes=offset;byte[] data=new byte[chunk];int n;
        while((n=in.read(data))!=-1){if(n==0)continue;progress.update(-1);hash.update(data,0,n);channel.writeBuffered(data,n);bytes+=n;progress.update(bytes);}
        if(size>=0&&bytes!=size)throw new IOException("SOURCE_CHANGED");
        channel.write(new byte[0]);channel.write(hash.digest());byte[] ack=channel.read();if(ack.length!=1||ack[0]!=2)throw new IOException("Invalid ACK");return bytes;
    }
    public static long receive(SecureChannel channel,Entry entry,long size,Progress progress,Publisher publisher)throws Exception{
        MessageDigest hash=hash();long offset=entry.done?entry.length:entry.partial.length();
        byte[] digest;
        if(entry.done)digest=entry.digest;
        else{if(offset>0)try(InputStream in=new BufferedInputStream(new FileInputStream(entry.partial))){prefix(in,offset,hash,progress);}digest=((MessageDigest)hash.clone()).digest();}
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);out.writeLong(offset);out.writeBoolean(entry.done);out.write(digest);channel.write(b.toByteArray());
        byte[] accept=channel.read();if(accept.length!=1||accept[0]!=1){if(!entry.done)try(RandomAccessFile f=new RandomAccessFile(entry.partial,"rw")){f.setLength(0);}throw new IOException("SOURCE_CHANGED");}
        progress.update(offset);if(entry.done)return offset;
        long bytes=offset;
        try(RandomAccessFile file=new RandomAccessFile(entry.partial,"rw")){
            file.seek(offset);
            for(;;){progress.update(-1);byte[] data=channel.read();if(data.length==0)break;if(size>=0&&data.length>size-bytes)throw new IOException("Size mismatch");
                long before=bytes;try{file.write(data);}catch(IOException e){file.setLength(before);throw e;}hash.update(data);bytes+=data.length;progress.update(bytes);
            }
            digest=hash.digest();if(!MessageDigest.isEqual(digest,channel.read())||(size>=0&&bytes!=size)){file.setLength(0);throw new IOException("Incomplete file");}file.getFD().sync();
        }
        publisher.publish(entry.partial);entry.length=bytes;entry.digest=digest;entry.done=true;entry.partial.delete();
        channel.write(new byte[]{2});return bytes;
    }
}
