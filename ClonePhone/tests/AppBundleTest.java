import com.abdulla.clonephone.AppBundle;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public class AppBundleTest {
 static byte[] zip(String... names)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){for(String name:names){z.putNextEntry(new ZipEntry(name));z.write(new byte[]{1,2,3});z.closeEntry();}}return b.toByteArray();}
 static void reject(byte[] data)throws Exception{try{AppBundle.read(new ByteArrayInputStream(data),(name,in)->{byte[] b=new byte[4096];while(in.read(b)!=-1){}});throw new AssertionError("accepted invalid container");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("apps-test-");byte[] base=Files.readAllBytes(Paths.get(args[0]));byte[] split=new byte[1048576+37];new Random(17).nextBytes(split);
  File a=dir.resolve("base.apk").toFile(),b=dir.resolve("split.apk").toFile(),bundle=dir.resolve("app.rbapp").toFile();Files.write(a.toPath(),base);Files.write(b.toPath(),split);
  AppBundle.write(Arrays.asList(a,b),bundle);Map<String,byte[]> decoded=new HashMap<>();
  AppBundle.read(new FileInputStream(bundle),(name,in)->{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);decoded.put(name,out.toByteArray());});
  if(decoded.size()!=2||!Arrays.equals(base,decoded.get("base.apk"))||!Arrays.equals(split,decoded.get("split-1.apk")))throw new AssertionError("APK bytes changed");
  reject(zip("../base.apk"));reject(zip("split-1.apk"));reject(zip("base.apk","extra.txt"));reject(new byte[]{1,2,3});
  byte[] duplicate=zip("base.apk","baze.apk");for(int i=0;i<duplicate.length-8;i++)if(duplicate[i]=='b'&&duplicate[i+1]=='a'&&duplicate[i+2]=='z'&&duplicate[i+3]=='e')duplicate[i+2]='s';reject(duplicate);
  String[] many=new String[257];many[0]="base.apk";for(int i=1;i<many.length;i++)many[i]="split-"+i+".apk";reject(zip(many));
  byte[] truncated=Files.readAllBytes(bundle.toPath());reject(Arrays.copyOf(truncated,truncated.length/2));
  try{AppBundle.read(new FileInputStream(bundle),(name,in)->{});throw new AssertionError("non-draining sink accepted");}catch(IOException expected){}
  AppBundle.write(Collections.singletonList(a),bundle);AppBundle.read(new FileInputStream(bundle),(name,in)->{byte[] buf=new byte[16384];while(in.read(buf)!=-1){}});
  System.out.println("PASS: real signed APK and split byte preservation; base-only bundle; missing-base, traversal, duplicate, extra-file, count-limit, truncated ZIP and incomplete staging rejection");
 }
}
