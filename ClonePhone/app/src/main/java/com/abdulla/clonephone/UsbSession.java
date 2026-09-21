package com.abdulla.clonephone;
import java.io.*;
import java.security.SecureRandom;
/** A physically connected, user-approved USB peer is the trust boundary. */
public final class UsbSession {
    public static SecureChannel open(InputStream in,OutputStream out,boolean receiver)throws Exception{
        byte[] key=new byte[16];
        if(receiver){new SecureRandom().nextBytes(key);DataOutputStream wire=new DataOutputStream(out);wire.writeInt(0x52425531);wire.write(key);wire.flush();}
        else{DataInputStream wire=new DataInputStream(in);if(wire.readInt()!=0x52425531)throw new IOException("USB_PEER");wire.readFully(key);}
        return new SecureChannel(in,out,key,receiver);
    }
}
