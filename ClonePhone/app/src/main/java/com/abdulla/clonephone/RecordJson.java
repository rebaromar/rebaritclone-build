package com.abdulla.clonephone;
import java.io.*;
import java.util.*;

/** UTF-8, versioned backup; no restore or messaging side effects. */
public final class RecordJson implements Closeable {
    private final Writer out;private int count;
    public RecordJson(OutputStream output,String kind)throws IOException{
        if(!"sms".equals(kind)&&!"calls".equals(kind))throw new IllegalArgumentException();
        out=new BufferedWriter(new OutputStreamWriter(output,"UTF-8"));out.write("{\"format\":\"rebaritclone-records\",\"version\":1,\"kind\":");string(kind);out.write(",\"records\":[");
    }
    public void record(Map<String,Object> values)throws IOException{
        if(count++>0)out.write(',');out.write('{');boolean first=true;
        for(Map.Entry<String,Object> entry:values.entrySet()){if(!first)out.write(',');first=false;string(entry.getKey());out.write(':');Object value=entry.getValue();
            if(value==null)out.write("null");else if(value instanceof Long||value instanceof Integer)out.write(value.toString());else if(value instanceof String)string((String)value);else throw new IOException("Unsupported value");}
        out.write('}');
    }
    private void string(String value)throws IOException{out.write('"');for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='"'||c=='\\'){out.write('\\');out.write(c);}else if(c<32)out.write(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.write(c);}out.write('"');}
    public int count(){return count;}
    public void close()throws IOException{out.write("],\"count\":"+count+"}");out.close();}
}
