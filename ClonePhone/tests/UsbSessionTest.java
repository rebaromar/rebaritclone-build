import com.abdulla.clonephone.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
public class UsbSessionTest{
 static InputStream fragmented(InputStream in){return new FilterInputStream(in){public int read(byte[] b,int off,int len)throws IOException{return super.read(b,off,Math.min(len,16384));}};}
 public static void main(String[] args)throws Exception{
  ExecutorService pool=Executors.newSingleThreadExecutor();byte[] data=new byte[1048576];new Random(51).nextBytes(data);
  try(ServerSocket listener=new ServerSocket(0)){
   Future<Long> receiver=pool.submit(()->{try(Socket s=listener.accept()){
    s.setSoTimeout(5000);SecureChannel channel=UsbSession.open(fragmented(s.getInputStream()),s.getOutputStream(),true);MessageDigest hash=MessageDigest.getInstance("SHA-256");long count=0;
    for(;;){byte[] block=channel.read();if(block.length==0)break;hash.update(block);count+=block.length;}
    if(!MessageDigest.isEqual(hash.digest(),channel.read()))throw new AssertionError("digest");channel.write(new byte[]{3});if(channel.read()[0]!=4)throw new AssertionError("receipt");return count;
   }});
   try(Socket s=new Socket("127.0.0.1",listener.getLocalPort())){
    s.setSoTimeout(5000);SecureChannel channel=UsbSession.open(fragmented(s.getInputStream()),s.getOutputStream(),false);MessageDigest hash=MessageDigest.getInstance("SHA-256");
    for(int i=0;i<8;i++){channel.writeBuffered(data,data.length);hash.update(data);}channel.writeBuffered(data,13);hash.update(data,0,13);channel.write(new byte[0]);channel.write(hash.digest());if(channel.read()[0]!=3)throw new AssertionError();channel.write(new byte[]{4});
   }
   if(receiver.get()!=8L*1048576+13)throw new AssertionError("size");
  }finally{pool.shutdownNow();}
  try{UsbSession.open(new ByteArrayInputStream(new byte[]{0,0,0,0}),new ByteArrayOutputStream(),false);throw new AssertionError("header accepted");}catch(IOException expected){}
  System.out.println("PASS: USB session bootstrap, 1 MiB encrypted frames, 16 KiB fragmented reads, tail/digest/final ACK, invalid-header rejection; simulated streams only");
 }
}
