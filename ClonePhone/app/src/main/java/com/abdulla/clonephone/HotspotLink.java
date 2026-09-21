package com.abdulla.clonephone;

import android.content.Context;
import android.net.*;
import android.net.wifi.*;
import android.net.wifi.p2p.*;
import android.os.*;

/** Local-only Android APIs. All public methods and callbacks run on the main thread. */
@SuppressWarnings("deprecation")
final class HotspotLink {
    interface HostCallback {void ready(String ssid,String password);void failed(String reason);}
    interface ClientCallback {void ready(Network network);void failed();}
    private final WifiManager wifi;
    private final ConnectivityManager connectivity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WifiManager.LocalOnlyHotspotReservation reservation;
    private ConnectivityManager.NetworkCallback callback;
    private int generation,groupFrequency;
    private boolean require5,automaticFallback;
    private final WifiP2pManager p2p;
    private WifiP2pManager.Channel p2pChannel;
    private boolean ownsGroup;
    private String failure="",groupAddress="";
    String hostAddress(){return groupAddress;}
    private WifiManager.WifiLock performanceLock;
    HotspotLink(Context context){wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);connectivity=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);p2p=(WifiP2pManager)context.getSystemService(Context.WIFI_P2P_SERVICE);if(p2p!=null)p2pChannel=p2p.initialize(context,Looper.getMainLooper(),()->p2pChannel=null);}
    void start(boolean fast,HostCallback listener){
        close();failure="";automaticFallback=false;final int token=generation;
        require5=fast;
        if(fast){if(Build.VERSION.SDK_INT>=29&&wifi.is5GHzBandSupported())startP2p(listener,token,true,false);else listener.failed("5GHZ_UNAVAILABLE");return;}
        startLocal(listener,token);
    }
    private void startLocal(HostCallback listener,int token){
        try{wifi.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback(){
            @Override public void onStarted(WifiManager.LocalOnlyHotspotReservation value){
                if(token!=generation){value.close();return;}reservation=value;
                String ssid,password;
                if(Build.VERSION.SDK_INT>=30){SoftApConfiguration c=value.getSoftApConfiguration();ssid=c.getSsid();password=c.getPassphrase();}
                else{WifiConfiguration c=value.getWifiConfiguration();ssid=c.SSID;password=c.preSharedKey;}
                if(ssid==null||password==null){failure="HOTSPOT_NO_CONFIG";startP2p(listener,token,false,false);return;}listener.ready(ssid,password);
            }
            @Override public void onFailed(int reason){if(token==generation){failure="HOTSPOT_ERROR_"+reason;startP2p(listener,token,false,false);}}
            @Override public void onStopped(){if(token==generation){close();listener.failed("HOTSPOT_STOPPED");}}
        },main);}catch(SecurityException e){close();listener.failed("WIFI_PERMISSION_OR_LOCATION");}catch(Exception e){failure=e.getClass().getSimpleName();startP2p(listener,token,false,false);}
    }
    private void startP2p(HostCallback listener,int token,boolean band5,boolean localFallback){
        startP2p(listener,token,band5,localFallback,0);
    }
    private void startP2p(HostCallback listener,int token,boolean band5,boolean localFallback,int attempt){
        if(token!=generation)return;
        if(p2p==null||p2pChannel==null){if(localFallback)startLocal(listener,token);else{close();listener.failed(failure+" / P2P_UNAVAILABLE");}return;}
        WifiP2pManager.ActionListener action=new WifiP2pManager.ActionListener(){
            public void onSuccess(){if(token!=generation)return;ownsGroup=true;readGroup(listener,token,0);}
            public void onFailure(int reason){if(token!=generation)return;
                long delay=P2pRetry.delay(reason,attempt);
                if(delay>=0){main.postDelayed(()->startP2p(listener,token,band5,localFallback,attempt+1),delay);return;}
                failure+=" / P2P_ERROR_"+reason;
                if(reason==WifiP2pManager.BUSY&&band5&&!automaticFallback){
                    automaticFallback=true;require5=false;
                    // The service stayed busy: try Android's local hotspot once.
                    // Its actual band is unknown and must not be advertised as 5 GHz.
                    startLocal(listener,token);return;
                }
                if(localFallback)startLocal(listener,token);else{close();listener.failed(failure);}}
        };
        try{
            if(Build.VERSION.SDK_INT>=29){
                String suffix=SecureChannel.newCode().substring(0,6);
                WifiP2pConfig config=new WifiP2pConfig.Builder().setNetworkName("DIRECT-rb-"+suffix).setPassphrase(SecureChannel.newCode().substring(0,16)).setGroupOperatingBand(band5?WifiP2pConfig.GROUP_OWNER_BAND_5GHZ:WifiP2pConfig.GROUP_OWNER_BAND_AUTO).build();
                p2p.createGroup(p2pChannel,config,action);
            }else p2p.createGroup(p2pChannel,action);
        }catch(Exception e){action.onFailure(-1);}
    }
    private void readGroup(HostCallback listener,int token,int tries){
        if(token!=generation)return;
        try{p2p.requestGroupInfo(p2pChannel,group->{
            if(token!=generation)return;
            if(group!=null&&group.isGroupOwner()&&group.getPassphrase()!=null){
                p2p.requestConnectionInfo(p2pChannel,info->{
                    if(token!=generation)return;
                    if(info!=null&&info.groupFormed&&info.isGroupOwner&&info.groupOwnerAddress!=null){groupFrequency=Build.VERSION.SDK_INT>=29?group.getFrequency():0;if(require5&&(groupFrequency<4900||groupFrequency>=5925)){close();listener.failed("5GHZ_NOT_CONFIRMED");return;}groupAddress=info.groupOwnerAddress.getHostAddress();listener.ready(group.getNetworkName(),group.getPassphrase());}
                    else if(tries<20)main.postDelayed(()->readGroup(listener,token,tries+1),500);
                    else{close();listener.failed("P2P_NO_GROUP_ADDRESS");}
                });return;
            }
            if(tries>=20){close();listener.failed("P2P_NO_GROUP_ADDRESS");return;}
            main.postDelayed(()->readGroup(listener,token,tries+1),500);
        });}catch(Exception e){close();listener.failed("P2P_PERMISSION_OR_LOCATION");}
    }
    void performance(boolean enabled){
        if(performanceLock!=null){try{if(performanceLock.isHeld())performanceLock.release();}catch(Exception ignored){}performanceLock=null;}
        if(enabled)try{performanceLock=wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"rebaritclone-transfer");performanceLock.setReferenceCounted(false);performanceLock.acquire();}catch(Exception ignored){}
    }
    String hostInfo(){String band=groupFrequency>=5925?"6 GHz • Wi-Fi Direct • "+groupFrequency+" MHz":groupFrequency>=4900&&groupFrequency<5925?"5 GHz • Wi-Fi Direct • "+groupFrequency+" MHz":UiText.t(224);return (automaticFallback?UiText.t(252)+"\n":"")+band+"\n"+capabilityInfo();}
    private String capabilityInfo(){try{if(Build.VERSION.SDK_INT>=31&&wifi.is6GHzBandSupported())return UiText.t(287);if(wifi.is5GHzBandSupported())return UiText.t(288);return UiText.t(289);}catch(Exception e){return UiText.t(289);}}
    String linkInfo(Network network){
        try{
            WifiInfo info=null;
            if(Build.VERSION.SDK_INT>=29&&network!=null){NetworkCapabilities cap=connectivity.getNetworkCapabilities(network);if(cap!=null&&cap.getTransportInfo() instanceof WifiInfo)info=(WifiInfo)cap.getTransportInfo();}
            if(info==null)return UiText.t(225);
            int f=info.getFrequency(),rate=info.getLinkSpeed();
            String band=f>=5925?"6 GHz":f>=4900?"5 GHz":f>=2400?"2.4 GHz":"Wi-Fi";
            return band+(rate>0?" • Link "+rate+" Mbps":"")+"\n"+(f>=2400&&f<2500?UiText.t(226):UiText.t(227));
        }catch(Exception e){return UiText.t(228);}
    }
    void connect(Pairing peer,ClientCallback listener){
        close();final int token=generation;
        if(Build.VERSION.SDK_INT<29){listener.failed();return;}
        try{
            WifiNetworkSpecifier spec=new WifiNetworkSpecifier.Builder().setSsid(peer.ssid).setWpa2Passphrase(peer.password).build();
            NetworkRequest request=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build();
            callback=new ConnectivityManager.NetworkCallback(){
                @Override public void onAvailable(Network n){main.post(()->{if(token==generation)listener.ready(n);});}
                @Override public void onUnavailable(){main.post(()->{if(token==generation){close();listener.failed();}});}
                @Override public void onLost(Network n){main.post(()->{if(token==generation){close();listener.failed();}});}
            };
            connectivity.requestNetwork(request,callback,60000);
        }catch(Exception e){close();listener.failed();}
    }
    Network currentWifi(){
        for(Network n:connectivity.getAllNetworks()){
            NetworkCapabilities c=connectivity.getNetworkCapabilities(n);
            if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return n;
        }return null;
    }
    void close(){
        generation++;groupAddress="";groupFrequency=0;
        performance(false);
        if(ownsGroup&&p2p!=null&&p2pChannel!=null){try{p2p.removeGroup(p2pChannel,null);}catch(Exception ignored){}ownsGroup=false;}
        if(callback!=null){try{connectivity.unregisterNetworkCallback(callback);}catch(Exception ignored){}callback=null;}
        if(reservation!=null){try{reservation.close();}catch(Exception ignored){}reservation=null;}
    }
}
