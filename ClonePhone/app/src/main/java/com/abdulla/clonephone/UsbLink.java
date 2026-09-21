package com.abdulla.clonephone;

import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Experimental AOA link: new phone is USB host, old phone is accessory. */
final class UsbLink {
    interface Listener{void ready(InputStream in,OutputStream out);void failed(String reason);}
    private final Context context;
    private final UsbManager manager;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final String action;
    private final Set<String> requested=new HashSet<>();
    private boolean host,running,opening,registered;
    private volatile int generation;
    private long deadline;
    private Listener listener;
    private volatile UsbDeviceConnection connection;
    private volatile ParcelFileDescriptor descriptor;
    private volatile InputStream input;
    private volatile OutputStream output;
    UsbLink(Context context){this.context=context;manager=(UsbManager)context.getSystemService(Context.USB_SERVICE);action=context.getPackageName()+".USB_PERMISSION";}
    private final BroadcastReceiver permissions=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){if(!running||!action.equals(i.getAction()))return;if(!i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)){fail(UiText.t(192));return;}poll();}
    };
    void start(boolean asHost,Listener callback){
        close();host=asHost;listener=callback;running=true;deadline=SystemClock.elapsedRealtime()+120000;requested.clear();
        if(host&&!context.getPackageManager().hasSystemFeature("android.hardware.usb.host")){fail(UiText.t(193));return;}
        IntentFilter filter=new IntentFilter(action);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(permissions,filter,Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(permissions,filter);
        registered=true;poll();
    }
    private PendingIntent permissionIntent(){
        return PendingIntent.getBroadcast(context,generation,new Intent(action).setPackage(context.getPackageName()),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
    }
    private void poll(){
        if(!running||opening)return;
        if(SystemClock.elapsedRealtime()>deadline){fail(UiText.t(194));return;}
        try{
            if(host){
                List<UsbDevice> devices=new ArrayList<>(manager.getDeviceList().values());
                // Prefer an already-switched AOA device; never switch an unrelated hub/storage device.
                devices.sort((a,b)->Boolean.compare(aoa(b),aoa(a)));
                for(UsbDevice d:devices){
                    if(!aoa(d)&&!phoneLike(d))continue;
                    if(!manager.hasPermission(d)){if(requested.add(d.getDeviceName()))manager.requestPermission(d,permissionIntent());continue;}
                    opening=true;int token=generation;executor.execute(()->openHost(d,token));return;
                }
            }else{
                UsbAccessory[] list=manager.getAccessoryList();
                if(list!=null)for(UsbAccessory accessory:list){
                    if(!"rebaritclone".equals(accessory.getManufacturer())||!"Phone Transfer".equals(accessory.getModel()))continue;
                    if(!manager.hasPermission(accessory)){if(requested.add(accessory.toString()))manager.requestPermission(accessory,permissionIntent());continue;}
                    descriptor=manager.openAccessory(accessory);if(descriptor==null)throw new IOException("USB accessory unavailable");
                    input=new BufferedInputStream(new FileInputStream(descriptor.getFileDescriptor()),16384);output=new FileOutputStream(descriptor.getFileDescriptor());running=false;listener.ready(input,output);return;
                }
            }
        }catch(Exception e){fail(UiText.t(195)+e.getClass().getSimpleName());return;}
        main.postDelayed(this::poll,750);
    }
    private static boolean aoa(UsbDevice d){int p=d.getProductId();return d.getVendorId()==0x18d1&&(p==0x2d00||p==0x2d01||p==0x2d04||p==0x2d05);}
    private static boolean phoneLike(UsbDevice d){
        for(int n=0;n<d.getInterfaceCount();n++){int cls=d.getInterface(n).getInterfaceClass();if(cls==6||cls==255)return true;}return d.getInterfaceCount()==0&&d.getDeviceClass()==0;
    }
    private void openHost(UsbDevice d,int token){
        UsbDeviceConnection c=null;
        try{
            c=manager.openDevice(d);if(c==null)throw new IOException("Permission/open failed");
            if(!aoa(d)){
                if(token!=generation){c.close();return;}
                byte[] version=new byte[2];int n=c.controlTransfer(0xc0,51,0,0,version,2,2000);if(n!=2||(version[0]==0&&version[1]==0))throw new IOException("AOA unsupported");
                String[] strings={"rebaritclone","Phone Transfer","Offline phone transfer","1.0","","rebaritclone-usb"};
                for(int k=0;k<strings.length;k++){if(token!=generation){c.close();return;}byte[] value=(strings[k]+"\0").getBytes(StandardCharsets.UTF_8);if(c.controlTransfer(0x40,52,0,k,value,value.length,2000)<0)throw new IOException("AOA setup failed");}
                if(c.controlTransfer(0x40,53,0,0,null,0,2000)<0)throw new IOException("AOA start failed");c.close();
                main.post(()->{if(token==generation){opening=false;requested.remove(d.getDeviceName());main.postDelayed(this::poll,1000);}});return;
            }
            UsbEndpoint read=null,write=null;UsbInterface chosen=null;
            for(int i=0;i<d.getInterfaceCount();i++){
                UsbInterface face=d.getInterface(i);
                if(face.getInterfaceClass()!=255||face.getInterfaceSubclass()!=255)continue;
                for(int j=0;j<face.getEndpointCount();j++){UsbEndpoint ep=face.getEndpoint(j);if(ep.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK)continue;if(ep.getDirection()==UsbConstants.USB_DIR_IN)read=ep;else write=ep;}
                if(read!=null&&write!=null){chosen=face;break;}
            }
            if(chosen==null||!c.claimInterface(chosen,true))throw new IOException("AOA bulk interface unavailable");
            final UsbDeviceConnection link=c;final UsbEndpoint readEp=read,writeEp=write;
            InputStream in=new InputStream(){
                private final byte[] buffer=new byte[16384];private int offset,count;
                public int read()throws IOException{byte[] one=new byte[1];int n=read(one,0,1);return n<0?-1:one[0]&255;}
                public int read(byte[] b,int start,int length)throws IOException{
                    if(length==0)return 0;if(offset==count){int n=link.bulkTransfer(readEp,buffer,buffer.length,180000);if(n<=0)throw new EOFException("USB disconnected or timed out");offset=0;count=n;}
                    int n=Math.min(length,count-offset);System.arraycopy(buffer,offset,b,start,n);offset+=n;return n;
                }
                public void close(){link.close();}
            };
            OutputStream out=new OutputStream(){
                public void write(int b)throws IOException{write(new byte[]{(byte)b});}
                public void write(byte[] b,int offset,int length)throws IOException{while(length>0){int n=link.bulkTransfer(writeEp,b,offset,Math.min(length,16384),180000);if(n<=0)throw new IOException("USB disconnected or timed out");offset+=n;length-=n;}}
                public void close(){link.close();}
            };
            main.post(()->{if(token!=generation){link.close();return;}connection=link;input=in;output=out;opening=false;running=false;listener.ready(in,out);});
        }catch(Exception e){if(c!=null)c.close();main.post(()->{if(token==generation)fail(UiText.t(196));});}
    }
    private void fail(String message){Listener callback=listener;close();if(callback!=null)callback.failed(message);}
    void close(){generation++;running=false;opening=false;main.removeCallbacksAndMessages(null);if(registered){try{context.unregisterReceiver(permissions);}catch(Exception ignored){}registered=false;}closeStreams();}
    void closeStreams(){
        try{if(connection!=null)connection.close();}catch(Exception ignored){}connection=null;
        try{if(descriptor!=null)descriptor.close();}catch(Exception ignored){}descriptor=null;
        try{if(input!=null)input.close();}catch(Exception ignored){}input=null;
        try{if(output!=null)output.close();}catch(Exception ignored){}output=null;
    }
    void destroy(){close();executor.shutdownNow();}
}
