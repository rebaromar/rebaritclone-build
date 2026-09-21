import com.abdulla.clonephone.SecureChannel;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** V2 manifests are separate encrypted records, so large selections do not exceed one frame. */
public class ManifestTest {
 public static void main(String[] args)throws Exception{
  byte[] key=SecureChannel.parseCode(SecureChannel.newCode());ExecutorService pool=Executors.newSingleThreadExecutor();
  try(ServerSocket listener=new ServerSocket(0)){
   Future<Integer> result=pool.submit(()->{
    try(Socket socket=listener.accept()){
     socket.setSoTimeout(5000);SecureChannel c=new SecureChannel(socket.getInputStream(),socket.getOutputStream(),key,true);
     DataInputStream h=new DataInputStream(new ByteArrayInputStream(c.read()));int n=h.readInt();if(n!=2000)throw new AssertionError();
     for(int i=0;i<n;i++){DataInputStream meta=new DataInputStream(new ByteArrayInputStream(c.read()));String name=meta.readUTF();if(!name.equals("وێنەی تاقیکردنەوە-"+i+"-"+String.join("",Collections.nCopies(100,"x"))+".jpg"))throw new AssertionError();if(!meta.readUTF().equals("image/jpeg")||meta.readLong()!=i||meta.available()!=0)throw new AssertionError();}
     c.write(new byte[]{1});try{c.read();throw new AssertionError();}catch(EOFException expected){}return n;
    }
   });
   try(Socket socket=new Socket("127.0.0.1",listener.getLocalPort())){
    socket.setSoTimeout(5000);SecureChannel c=new SecureChannel(socket.getInputStream(),socket.getOutputStream(),key,false);
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(2000);c.write(bytes.toByteArray());
    for(int i=0;i<2000;i++){bytes.reset();out.writeUTF("وێنەی تاقیکردنەوە-"+i+"-"+String.join("",Collections.nCopies(100,"x"))+".jpg");out.writeUTF("image/jpeg");out.writeLong(i);c.write(bytes.toByteArray());}
    if(!Arrays.equals(c.read(),new byte[]{1}))throw new AssertionError();
   }
   if(result.get(10,TimeUnit.SECONDS)!=2000)throw new AssertionError();
  }finally{pool.shutdownNow();}
  System.out.println("PASS: 2000-file Unicode manifest exceeding old 128 KiB limit; mid-session disconnect detected");
 }
}
