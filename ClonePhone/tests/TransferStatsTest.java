import com.abdulla.clonephone.TransferStats;
public class TransferStatsTest {
 public static void main(String[] args){
  TransferStats s=new TransferStats(new String[]{"one.jpg","two.mp4","three.heic","app.rbapp","notes.txt"},new String[]{"image/jpeg","video/mp4","application/octet-stream","application/x-rebaritclone-app","text/plain"},new long[]{1000000000L,2000000000L,500000000L,0,0});
  if(s.totalBytes!=3500000000L||!s.totalKnown||s.total(0)!=2||s.total(1)!=1||s.total(2)!=1||s.total(3)!=1)throw new AssertionError("totals");
  if(s.remaining(0,0)!=2||s.remaining(0,1)!=1||s.remaining(1,1)!=1||s.remaining(1,2)!=0)throw new AssertionError("completion boundary");
  if(s.remainingBytes(1250000000L)!=2250000000L||s.remainingBytes(4000000000L)!=0)throw new AssertionError("partial/resumed bytes");
  if(s.remaining(0,5)!=0||s.remaining(3,5)!=0)throw new AssertionError("end");
  TransferStats u=new TransferStats(new String[]{"clip"},new String[]{"video/mp4"},new long[]{-1});if(u.totalKnown||u.remainingBytes(1)!=-1)throw new AssertionError("unknown size fabricated");
  TransferStats z=new TransferStats(new String[]{},new String[]{},new long[]{});if(z.count!=0||z.totalBytes!=0||z.remaining(0,0)!=0)throw new AssertionError("empty");
  if(!"1.000 GB".equals(TransferStats.gb(1000000000L))||!"<0.001 GB".equals(TransferStats.gb(100)))throw new AssertionError("units");
  System.out.println("PASS: mixed media counts, unacknowledged/acknowledged boundaries, partial/resumed bytes, unknown size, empty selection and decimal GB formatting");
 }
}
