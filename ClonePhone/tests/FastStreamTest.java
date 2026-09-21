import com.abdulla.clonephone.SecureChannel;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
public class FastStreamTest{
 public static void main(String[] args)throws Exception{
  byte[] key=SecureChannel.parseCode(SecureChannel.newCode());byte[] block=new byte[262144];new Random(17).nextBytes(block);ExecutorService pool=Executors.newSingleThreadExecutor();long start=System.nanoTime();
  try(ServerSocket listener=new ServerSocket(0)){
   Future<Long> received=pool.submit(()->{try(Socket socket=listener.accept()){
    socket.setSoTimeout(10000);SecureChannel c=new SecureChannel(socket.getInputStream(),socket.getOutputStream(),key,true);MessageDigest hash=MessageDigest.getInstance("SHA-256");long bytes=0;
    for(;;){byte[] p=c.read();if(p.length==0)break;hash.update(p);bytes+=p.length;}
    if(!MessageDigest.isEqual(hash.digest(),c.read()))throw new AssertionError("digest");c.write(new byte[]{2});return bytes;
   }});
   try(Socket socket=new Socket("127.0.0.1",listener.getLocalPort())){
    socket.setSoTimeout(10000);SecureChannel c=new SecureChannel(socket.getInputStream(),socket.getOutputStream(),key,false);MessageDigest hash=MessageDigest.getInstance("SHA-256");
    for(int i=0;i<256;i++){hash.update(block);c.writeBuffered(block,block.length);}
    // A short tail catches truncated/final buffered data; no full-block copy required.
    hash.update(block,0,37);c.writeBuffered(block,37);c.write(new byte[0]);c.write(hash.digest());if(c.read()[0]!=2)throw new AssertionError();
   }
   if(received.get()!=67108864L+37)throw new AssertionError("length");
  }finally{pool.shutdownNow();}
  System.out.printf("PASS: 64 MiB + 37-byte buffered encrypted transfer and SHA-256 validation; host loopback %.2f seconds (not a phone speed measurement)%n",(System.nanoTime()-start)/1e9);
 }
}
