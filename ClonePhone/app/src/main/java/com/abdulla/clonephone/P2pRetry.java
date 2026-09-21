package com.abdulla.clonephone;
/** Retry transient Android Wi-Fi Direct BUSY (2); do not retry permanent errors. */
public final class P2pRetry {
    public static long delay(int reason,int attempt){
        if(reason!=2||attempt<0||attempt>=3)return -1;
        return 750L<<attempt;
    }
}
