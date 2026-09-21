import com.abdulla.clonephone.*;
import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.*;

public class PairingTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static void bad(String value){try{Pairing.parse(value);throw new AssertionError(value);}catch(IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception{
  String key=SecureChannel.newCode();
  String hotspot=Pairing.encodeHotspot(Arrays.asList("192.168.43.1"),key,"Rebar ڕێبەر", "safe12345");
  Pairing hp=Pairing.parse(hotspot);check(hp.ssid.equals("Rebar ڕێبەر")&&hp.password.equals("safe12345"));
  bad("rebaritclone:v3:192.168.43.1:"+key+":@@:@@");
  String payload=Pairing.encode(Arrays.asList("192.168.43.1","10.0.0.5"),key);
  Pairing p=Pairing.parse(payload);check(p.hosts.size()==2);check(Arrays.equals(p.key,SecureChannel.parseCode(key)));
  bad("https://example.com");bad("rebaritclone:v2:127.0.0.1:"+key);bad("rebaritclone:v2:8.8.8.8:"+key);bad("rebaritclone:v2:192.168.999.1:"+key);bad("rebaritclone:v2:172.32.1.1:"+key);bad("rebaritclone:v2:192.168.0.1:1234");bad("rebaritclone:v1:192.168.0.1:"+key);
  for(String qrPayload:new String[]{payload,hotspot})for(int size:new int[]{280,660}){
   BitMatrix matrix=new QRCodeWriter().encode(qrPayload,BarcodeFormat.QR_CODE,size,size);
   for(int rotate=0;rotate<4;rotate++){
    int[] pixels=new int[size*size];byte[] frame=new byte[size*size*3/2];
    for(int y=0;y<size;y++)for(int x=0;x<size;x++){
     int a=x,b=y;for(int r=0;r<rotate;r++){int old=a;a=size-1-b;b=old;}
     boolean black=matrix.get(a,b);pixels[y*size+x]=black?0xff000000:0xffffffff;frame[y*size+x]=(byte)(black?0:255);
    }
    String image=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(size,size,pixels)))).getText();
    String camera=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(frame,size,size,0,0,size,size,false)))).getText();
    check(image.equals(qrPayload));check(camera.equals(qrPayload));
   }
  }
  System.out.println("PASS: strict private-address pairing, invalid input rejection, QR image and camera luminance roundtrips at two sizes in four orientations");
 }
}
