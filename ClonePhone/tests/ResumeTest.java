import com.abdulla.clonephone.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ResumeTest {
 static byte[] source=new byte[3*1048576+37];
 static byte[] key;
 static AtomicInteger published=new AtomicInteger();
 static void transfer(ResumableFile.Entry entry,boolean interrupt,boolean lostAck,byte[] data)throws Exception{
  ExecutorService pool=Executors.newSingleThreadExecutor();
  try(ServerSocket listener=new ServerSocket(0)){
   Future<Long> server=pool.submit(()->{try(Socket s=listener.accept()){
    s.setSoTimeout(3000);SecureChannel c=new SecureChannel(s.getInputStream(),s.getOutputStream(),key,true);
    return ResumableFile.receive(c,entry,data.length,n->{if(interrupt&&n>=1048576){s.close();throw new EOFException("intentional disconnect");}},file->{
     if(!Arrays.equals(data,Files.readAllBytes(file.toPath())))throw new AssertionError("wrong published bytes");published.incrementAndGet();if(lostAck)s.close();
    });
   }});
   Exception clientError=null;
   try(Socket s=new Socket("127.0.0.1",listener.getLocalPort())){
    s.setSoTimeout(3000);SecureChannel c=new SecureChannel(s.getInputStream(),s.getOutputStream(),key,false);
    ResumableFile.send(c,new ByteArrayInputStream(data),data.length,262144,n->{});
   }catch(Exception e){clientError=e;}
   Exception serverError=null;try{server.get(8,TimeUnit.SECONDS);}catch(ExecutionException e){serverError=(Exception)e.getCause();}
   if(interrupt||lostAck){if(clientError==null||serverError==null)throw new AssertionError("expected disconnect");}
   else if(clientError!=null||serverError!=null)throw new AssertionError("unexpected failure",clientError!=null?clientError:serverError);
  }finally{pool.shutdownNow();}
 }
 public static void main(String[] args)throws Exception{
  new Random(123).nextBytes(source);key=SecureChannel.parseCode(SecureChannel.newCode());
  File f=File.createTempFile("resume-test-",".part");ResumableFile.Entry e=new ResumableFile.Entry(f);
  transfer(e,true,false,source);if(e.done||f.length()!=1048576)throw new AssertionError("checkpoint not preserved");
  transfer(e,false,true,source);if(!e.done||published.get()!=1)throw new AssertionError("commit before ACK");
  transfer(e,false,false,source);if(published.get()!=1)throw new AssertionError("duplicate publish");
  // Corrupt retained prefix: neither side may append or publish it.
  ResumableFile.Entry bad=new ResumableFile.Entry(File.createTempFile("resume-bad-",".part"));Files.write(bad.partial.toPath(),new byte[1048576]);
  try{transfer(bad,false,false,source);throw new AssertionError("corrupt prefix accepted");}catch(AssertionError expected){if(!"unexpected failure".equals(expected.getMessage()))throw expected;}
  if(bad.partial.length()!=0||bad.done||published.get()!=1)throw new AssertionError("bad prefix retained");
  transfer(bad,false,false,source);if(published.get()!=2)throw new AssertionError();
  ResumableFile.Entry empty=new ResumableFile.Entry(File.createTempFile("resume-empty-",".part"));transfer(empty,false,false,new byte[0]);transfer(empty,false,false,new byte[0]);if(published.get()!=3)throw new AssertionError("empty file duplicated");
  System.out.println("PASS: mid-file disconnect, exact checkpoint, encrypted resume, SHA-256, lost ACK deduplication, corrupt prefix reset, retry and short tail");
 }
}
