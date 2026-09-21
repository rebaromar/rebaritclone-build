import com.abdulla.clonephone.PermissionPlan;
import java.util.*;
public class PermissionPlanTest {
 static Set<String> permissions(int sdk,boolean receiver){Set<String> all=new HashSet<>();for(String[] step:PermissionPlan.forRole(sdk,receiver)){if(step.length==0)throw new AssertionError("empty prompt");for(String p:step){if(!all.add(p))throw new AssertionError("duplicate request");}}return all;}
 static void has(Set<String> p,String name,boolean expected){if(p.contains("android.permission."+name)!=expected)throw new AssertionError(name+" "+expected);}
 public static void main(String[] args){
  for(int sdk:new int[]{26,28,29,31,32,33,34,35,36})for(boolean receiver:new boolean[]{true,false}){
   Set<String> p=permissions(sdk,receiver);
   has(p,"POST_NOTIFICATIONS",sdk>=33);has(p,"NEARBY_WIFI_DEVICES",sdk>=33);
   has(p,"ACCESS_FINE_LOCATION",sdk<33);has(p,"ACCESS_COARSE_LOCATION",sdk<33);
   has(p,"READ_EXTERNAL_STORAGE",!receiver&&sdk<33);
   has(p,"READ_MEDIA_IMAGES",!receiver&&sdk>=33);has(p,"READ_MEDIA_VIDEO",!receiver&&sdk>=33);has(p,"READ_MEDIA_AUDIO",!receiver&&sdk>=33);
   has(p,"READ_MEDIA_VISUAL_USER_SELECTED",!receiver&&sdk>=34);
   for(String permission:new String[]{"CAMERA","READ_CONTACTS","READ_CALL_LOG","READ_SMS"})has(p,permission,!receiver);
   for(String permission:new String[]{"SYSTEM_ALERT_WINDOW","MANAGE_EXTERNAL_STORAGE","WRITE_SETTINGS","SEND_SMS"})has(p,permission,false);
  }
  System.out.println("PASS: receiver/sender least-required permissions, SDK boundaries 26–36, no duplicate prompts, no protected-file/overlay/SMS-send permissions");
 }
}
