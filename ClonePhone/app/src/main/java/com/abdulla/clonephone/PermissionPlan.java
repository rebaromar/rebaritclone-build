package com.abdulla.clonephone;
import java.util.*;
/** SDK/role-dependent sequence. No file access or consent is inferred here. */
public final class PermissionPlan {
    private static String p(String name){return "android.permission."+name;}
    public static List<String[]> forRole(int sdk,boolean receiving){
        List<String[]> steps=new ArrayList<>();
        if(sdk>=33)steps.add(new String[]{p("POST_NOTIFICATIONS")});
        steps.add(sdk>=33?new String[]{p("NEARBY_WIFI_DEVICES")}:new String[]{p("ACCESS_COARSE_LOCATION"),p("ACCESS_FINE_LOCATION")});
        if(!receiving){
            if(sdk>=33){
                steps.add(sdk>=34?new String[]{p("READ_MEDIA_IMAGES"),p("READ_MEDIA_VIDEO"),p("READ_MEDIA_VISUAL_USER_SELECTED")}:new String[]{p("READ_MEDIA_IMAGES"),p("READ_MEDIA_VIDEO")});
                steps.add(new String[]{p("READ_MEDIA_AUDIO")});
            }else steps.add(new String[]{p("READ_EXTERNAL_STORAGE")});
            for(String name:new String[]{"CAMERA","READ_CONTACTS","READ_CALL_LOG","READ_SMS"})steps.add(new String[]{p(name)});
        }
        return steps;
    }
}
