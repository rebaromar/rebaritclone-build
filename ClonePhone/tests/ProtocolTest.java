import com.abdulla.clonephone.SecureChannel;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ProtocolTest {
    static void check(boolean ok) { if(!ok)throw new AssertionError(); }
    static void roundTrip(boolean wrongKey,boolean tamper) throws Exception {
        byte[] key=SecureChannel.parseCode(SecureChannel.newCode());
        byte[] payload=new byte[65536];new Random(5).nextBytes(payload);
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0)) {
            Future<Boolean> result=pool.submit(()->{
                try(Socket s=server.accept()){
                    s.setSoTimeout(3000);
                    SecureChannel c=new SecureChannel(s.getInputStream(),s.getOutputStream(),key,true);
                    for(int n=0;n<32;n++){byte[] p=c.read();check(Arrays.equals(p,payload));c.write(p);}
                    check(c.read().length==0);c.write(new byte[]{3});return true;
                }catch(Exception e){if(wrongKey||tamper)return false;throw e;}
            });
            try(Socket s=new Socket("127.0.0.1",server.getLocalPort())){
                s.setSoTimeout(3000);byte[] clientKey=key.clone();if(wrongKey)clientKey[0]^=1;
                final boolean[] corrupt={false};
                OutputStream out=new FilterOutputStream(s.getOutputStream()){
                    @Override public void write(byte[] b,int off,int len)throws IOException{
                        byte[] copy=Arrays.copyOfRange(b,off,off+len);
                        if(corrupt[0]&&copy.length>16){copy[copy.length-1]^=1;corrupt[0]=false;}
                        this.out.write(copy);
                    }
                };
                try{
                    SecureChannel c=new SecureChannel(s.getInputStream(),out,clientKey,false);corrupt[0]=tamper;
                    for(int n=0;n<32;n++){c.write(payload);check(Arrays.equals(c.read(),payload));}
                    c.write(new byte[0]);check(Arrays.equals(c.read(),new byte[]{3}));check(!wrongKey&&!tamper);
                }catch(Exception e){if(!wrongKey&&!tamper)throw e;}
            }
            check(result.get(5,TimeUnit.SECONDS)==(!wrongKey&&!tamper));
        }finally{pool.shutdownNow();}
    }
    public static void main(String[] args)throws Exception{
        String code=SecureChannel.newCode();check(SecureChannel.parseCode(code).length==16);
        try{SecureChannel.parseCode("123456");throw new AssertionError();}catch(IllegalArgumentException expected){}
        roundTrip(false,false);roundTrip(true,false);roundTrip(false,true);
        System.out.println("PASS: 2 MiB encrypted duplex transfer, empty/end records, invalid code, wrong key, tamper rejection");
    }
}
