import com.abdulla.clonephone.P2pRetry;
public class P2pRetryTest {
 public static void main(String[] args){
  int attempts=0;long wait=0;
  while(true){long delay=P2pRetry.delay(2,attempts);if(delay<0)break;wait+=delay;attempts++;if(attempts>10)throw new AssertionError("unbounded retry");}
  if(attempts!=3||wait!=5250)throw new AssertionError("busy retry budget");
  for(int reason:new int[]{-1,0,1,3})for(int attempt=0;attempt<5;attempt++)if(P2pRetry.delay(reason,attempt)!=-1)throw new AssertionError("permanent error retried");
  if(P2pRetry.delay(2,-1)!=-1)throw new AssertionError("invalid attempt");
  System.out.println("PASS: BUSY bounded to 3 retries / 5250 ms; permanent errors stop immediately");
 }
}
