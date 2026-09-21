import com.abdulla.clonephone.RecordJson;
import java.io.*;
import java.util.*;
public class RecordJsonTest {
 public static void main(String[] args)throws Exception{
  List<Map<String,Object>> rows=new ArrayList<>();
  for(int i=1;i<=3;i++){Map<String,Object> r=new LinkedHashMap<>();r.put("_id",(long)i);r.put("address","+96407701234567");r.put("body",i==2?"UNSELECTED":"سڵاو مرحبا 😀 \"quoted\" \\ newline\n\t\u0000");r.put("date",1700000000000L+i);r.put("service_center",null);rows.add(r);}
  try(RecordJson backup=new RecordJson(new FileOutputStream(args[0]),"sms")){for(Map<String,Object> row:rows)if(!row.get("_id").equals(2L))backup.record(row);if(backup.count()!=2)throw new AssertionError();}
  try(RecordJson empty=new RecordJson(new FileOutputStream(args[1]),"calls")){}
  System.out.println("Wrote synthetic selected-record fixtures for independent JSON parsing");
 }
}
