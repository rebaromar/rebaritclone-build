package com.abdulla.clonephone;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Strict local-only pairing data; never permits URLs to arbitrary services. */
public final class Pairing {
    public final List<String> hosts;
    public final byte[] key;
    public String ssid="", password="";
    public Pairing(List<String> hosts, byte[] key) { this.hosts=hosts;this.key=key; }
    public static boolean local(String host) {
        if(!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        String[] p=host.split("\\.");int[] n=new int[4];
        for(int i=0;i<4;i++){n[i]=Integer.parseInt(p[i]);if(n[i]>255)return false;}
        return n[0]==10 || (n[0]==192 && n[1]==168) || (n[0]==172 && n[1]>=16 && n[1]<=31);
    }
    public static String encode(List<String> hosts,String key) {
        if(hosts.isEmpty())throw new IllegalArgumentException("No Wi-Fi address");
        String s="rebaritclone:v2:"+String.join(",",hosts)+":"+key;
        parse(s);return s;
    }
    public static String encodeHotspot(List<String> hosts,String key,String ssid,String password) {
        String payload="rebaritclone:v3:"+String.join(",",hosts)+":"+key+":"+
            Base64.getUrlEncoder().withoutPadding().encodeToString(ssid.getBytes(StandardCharsets.UTF_8))+":"+
            Base64.getUrlEncoder().withoutPadding().encodeToString(password.getBytes(StandardCharsets.UTF_8));
        parse(payload);return payload;
    }
    public static Pairing parse(String s) {
        if(s==null||s.length()>700)throw new IllegalArgumentException("Invalid QR");
        String[] p=s.trim().split(":",-1);
        if(!p[0].equals("rebaritclone")||!((p.length==4&&p[1].equals("v2"))||(p.length==6&&p[1].equals("v3"))))throw new IllegalArgumentException("Use a rebaritclone QR");
        List<String> hosts=new ArrayList<>();
        for(String h:p[2].split(",")){if(!local(h)||hosts.size()>=8)throw new IllegalArgumentException("Invalid Wi-Fi address");if(!hosts.contains(h))hosts.add(h);}
        Pairing result=new Pairing(hosts,SecureChannel.parseCode(p[3]));
        if(p.length==6){
            result.ssid=new String(Base64.getUrlDecoder().decode(p[4]),StandardCharsets.UTF_8);
            result.password=new String(Base64.getUrlDecoder().decode(p[5]),StandardCharsets.UTF_8);
            if(result.ssid.isEmpty()||result.ssid.getBytes(StandardCharsets.UTF_8).length>32||result.password.length()<8||result.password.length()>63)throw new IllegalArgumentException("Invalid hotspot credentials");
        }
        return result;
    }
}
