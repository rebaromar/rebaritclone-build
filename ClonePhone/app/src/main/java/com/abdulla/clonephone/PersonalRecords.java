package com.abdulla.clonephone;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;

final class PersonalRecords {
    static final int LIMIT=5000;
    interface Check {void run()throws IOException;}
    static class Row {String id,label,lookup;Row(String id,String label,String lookup){this.id=id;this.label=label;this.lookup=lookup;}}
    static class Result {final List<Row> rows=new ArrayList<>();boolean limited;}
    static Uri source(String kind){return "calls".equals(kind)?CallLog.Calls.CONTENT_URI:Telephony.Sms.CONTENT_URI;}
    static String[] fields(String kind){return "calls".equals(kind)?new String[]{"_id","number","name","date","duration","type","new","is_read"}:new String[]{"_id","address","body","date","date_sent","type","read","seen","status","thread_id","service_center"};}
    static Result load(Context context,String kind,Check check)throws Exception{
        Result result=new Result();ContentResolver resolver=context.getContentResolver();
        if("contacts".equals(kind)){
            try(Cursor c=resolver.query(ContactsContract.Contacts.CONTENT_URI,new String[]{"_id","lookup","display_name"},null,null,"display_name ASC")){
                if(c==null)throw new IOException("Provider unavailable");
                while(c.moveToNext()){check.run();if(result.rows.size()==LIMIT){result.limited=true;break;}result.rows.add(new Row(c.getString(0),value(c,2),c.getString(1)));}
            }
            Map<String,Row> byId=new HashMap<>();for(Row row:result.rows)byId.put(row.id,row);Set<String> added=new HashSet<>();
            try(Cursor c=resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{"contact_id","data1"},null,null,null)){
                if(c!=null)while(c.moveToNext()){check.run();String id=c.getString(0);if(byId.containsKey(id)&&added.add(id))byId.get(id).label+="\n"+value(c,1);}
            }
        }else{
            DateFormat dates=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,Locale.forLanguageTag(UiText.language()));
            String[] columns="calls".equals(kind)?new String[]{"_id","number","name","date","duration","type"}:new String[]{"_id","address","body","date","type"};
            try(Cursor c=resolver.query(source(kind),columns,null,null,"date DESC, _id DESC")){
                if(c==null)throw new IOException("Provider unavailable");
                while(c.moveToNext()){check.run();if(result.rows.size()==LIMIT){result.limited=true;break;}
                    String label;if("calls".equals(kind)){int type=c.getInt(5);label=value(c,2)+"  "+value(c,1)+"\n"+dates.format(new Date(c.getLong(3)))+" • "+UiText.t(type==1?240:type==2?241:type==3?242:243)+" • "+c.getLong(4)+UiText.t(142);}
                    else{String body=value(c,2);if(body.length()>100)body=body.substring(0,100)+"…";label=value(c,1)+" • "+dates.format(new Date(c.getLong(3)))+"\n"+body;}
                    result.rows.add(new Row(c.getString(0),label,null));
                }
            }
        }return result;
    }
    private static String value(Cursor c,int n){return c.isNull(n)?"":c.getString(n);}
    static File export(Context context,String kind,List<Row> chosen,Check check)throws Exception{
        File output=File.createTempFile("selected-"+kind+"-","contacts".equals(kind)?".vcf":".json",context.getCacheDir());
        try{
            if("contacts".equals(kind)){
                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(output))){byte[] data=new byte[65536];
                    for(Row row:chosen){check.run();if(row.lookup==null)throw new IOException("Contact unavailable");Uri uri=Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI,Uri.encode(row.lookup));long bytes=0;
                        try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Contact unavailable");int n;while((n=in.read(data))!=-1){check.run();out.write(data,0,n);bytes+=n;}}
                        if(bytes==0)throw new IOException("Contact unavailable");out.write('\n');
                    }
                }
            }else{
                String[] columns=fields(kind);int count=0;
                try(RecordJson json=new RecordJson(new FileOutputStream(output),kind)){
                    for(Row row:chosen){check.run();
                        try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(source(kind),Long.parseLong(row.id)),columns,null,null,null)){
                            if(c==null||!c.moveToFirst())throw new IOException("Selected record no longer available");Map<String,Object> record=new LinkedHashMap<>();
                            for(int n=0;n<columns.length;n++){String field=columns[n];boolean text=field.equals("number")||field.equals("name")||field.equals("address")||field.equals("body")||field.equals("service_center");record.put(field,c.isNull(n)?null:text?c.getString(n):c.getLong(n));}
                            json.record(record);count++;
                        }
                    }if(count!=chosen.size())throw new IOException("Incomplete backup");
                }
            }check.run();return output;
        }catch(Exception e){output.delete();throw e;}
    }
}
