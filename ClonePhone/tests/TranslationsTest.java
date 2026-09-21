import com.abdulla.clonephone.Translations;
public class TranslationsTest {
 public static void main(String[] args){
  for(int i=0;i<Translations.COUNT;i++){
   for(String lang:new String[]{"ckb","en","ar"})if(Translations.get(lang,i)==null||Translations.get(lang,i).isEmpty())throw new AssertionError("Missing "+lang+" "+i);
   if(Translations.get("en",i).matches("(?s).*[\\u0600-\\u06ff].*"))throw new AssertionError("Kurdish/Arabic leaked into English "+i);
  }
  if(Translations.rtl("en")||!Translations.rtl("ar")||!Translations.rtl("ckb"))throw new AssertionError("direction");
  if(!Translations.get("unknown",5).equals(Translations.get("ckb",5)))throw new AssertionError("fallback");
  if(!Translations.get("en",13).contains("\n\n")||!Translations.get("ar",145).startsWith("\n"))throw new AssertionError("newline");
  for(int id:new int[]{31,69,70,139,141,144,145,146,148,149,150,151,152,153,154,155,156,206})for(String lang:new String[]{"ckb","en","ar"})if(!Translations.get(lang,id).endsWith(" "))throw new AssertionError("fragment spacing "+lang+" "+id);
  System.out.println("PASS: all "+Translations.COUNT+" strings present in Kurdish/English/Arabic, English isolation, RTL/LTR, fallback, newlines and dynamic-text spacing");
 }
}
