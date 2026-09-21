package com.abdulla.clonephone;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Checks while the app is visible; Android retains installation approval. */
@SuppressWarnings("deprecation")
final class OnlineUpdate {
    private static final String HOST="rebaritclone-update-admin.rebar4qwrna.workers.dev";
    private final Activity activity;
    private final BooleanSupplier busy;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private TextView label;
    private volatile boolean destroyed;
    private boolean visible,checking,downloading,manualRequested;
    private int promptedCode=-1;
    private JSONObject pending;
    private File ready;
    private AlertDialog dialog;
    private String message="";
    private final Runnable poll=()->{offer(false);check(false);schedule();};
    OnlineUpdate(Activity a,BooleanSupplier b){activity=a;busy=b;}
    String words(String ku,String en,String ar){return "en".equals(UiText.language())?en:"ar".equals(UiText.language())?ar:ku;}
    String menuTitle(){return words("وەشانی نوێ","New version","إصدار جديد");}
    private String version(){try{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}catch(Exception e){return "";}}
    void bind(TextView v){label=v;render();v.setOnClickListener(w->checkManually());}
    private void render(){if(label!=null)label.setText(words("وەشان ","Version ","الإصدار ")+version()+(message.isEmpty()?"":" · "+message));}
    private void status(String s){message=s;render();}
    private boolean canShow(){return visible&&!destroyed&&!busy.getAsBoolean()&&!activity.isFinishing()&&(dialog==null||!dialog.isShowing());}
    void checkManually(){if(downloading)return;if(pending!=null){offer(true);return;}check(true);}
    void resume(){visible=true;offer(false);check(false);schedule();}
    void pause(){visible=false;ui.removeCallbacks(poll);}
    private void schedule(){ui.removeCallbacks(poll);if(visible&&!destroyed)ui.postDelayed(poll,300000);}
    void destroy(){destroyed=true;pause();if(dialog!=null)dialog.dismiss();executor.shutdownNow();}
    private void notice(String text){if(!canShow())return;dialog=new AlertDialog.Builder(UiText.context(activity)).setTitle(menuTitle()).setMessage(text).setPositiveButton(android.R.string.ok,null).show();}
    private HttpURLConnection connect(String address)throws Exception{
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol())||!HOST.equals(url.getHost())||(url.getPort()!=-1&&url.getPort()!=443))throw new IOException("Untrusted update URL");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setUseCaches(false);
        if(c.getResponseCode()!=200){c.disconnect();throw new IOException("Update unavailable");}return c;
    }
    private void validate(File file,int code)throws Exception{
        PackageManager pm=activity.getPackageManager();
        PackageInfo current=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
        PackageInfo next=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        if(next==null||!current.packageName.equals(next.packageName)||next.versionCode!=code||code<=current.versionCode
            ||current.signatures==null||current.signatures.length==0||next.signatures==null||!new HashSet<>(Arrays.asList(current.signatures)).equals(new HashSet<>(Arrays.asList(next.signatures))))throw new IOException("Invalid update");
    }
    private void check(boolean manual){
        if(destroyed||downloading||!visible||busy.getAsBoolean())return;
        manualRequested|=manual;
        if(checking)return;
        checking=true;
        executor.execute(()->{
            HttpURLConnection c=null;
            try{
                c=connect("https://"+HOST+"/update.json");ByteArrayOutputStream json=new ByteArrayOutputStream();
                try(InputStream in=c.getInputStream()){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){if(json.size()+n>65536)throw new IOException("Manifest too large");json.write(bytes,0,n);}}
                JSONObject manifest=new JSONObject(json.toString("UTF-8"));int code=manifest.getInt("versionCode");
                if(code<=0)throw new IOException("Invalid version");
                int installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode;
                if(code>installed){manifest.getString("apkUrl");manifest.getString("versionName");}
                ui.post(()->{
                    if(destroyed)return;
                    checking=false;boolean requested=manualRequested;manualRequested=false;
                    if(code<=installed){pending=null;ready=null;String latest=words("لە نوێترین وەشاندایت","You're on the latest version","أنت تستخدم أحدث إصدار");status(latest);if(requested)notice(latest);}
                    else{if(pending==null||pending.optInt("versionCode")!=code)ready=null;pending=manifest;status(words("وەشانی نوێ بەردەستە","Update available","يتوفر تحديث"));offer(requested);}
                });
            }catch(Exception e){ui.post(()->{if(destroyed)return;checking=false;boolean requested=manualRequested;manualRequested=false;String error=words("پشکنین سەرکەوتوو نەبوو؛ دووبارە هەوڵ بدە","Couldn't check. Try again.","تعذر التحقق. حاول مجدداً.");status(error);if(requested)notice(error);});}
            finally{if(c!=null)c.disconnect();}
        });
    }
    private void offer(boolean manual){
        if(pending==null||downloading||!canShow()||(!manual&&promptedCode==pending.optInt("versionCode")))return;
        final JSONObject release=pending;
        promptedCode=release.optInt("versionCode");
        String title=ready!=null?words("نوێکردنەوە ئامادەیە","Update ready","التحديث جاهز"):menuTitle();
        String details=words("وەشان ","Version ","الإصدار ")+release.optString("versionName")+"\n"+release.optString("notes","");
        dialog=new AlertDialog.Builder(UiText.context(activity)).setTitle(title).setMessage(details)
            .setPositiveButton(ready!=null?words("دامەزراندن","Install","تثبيت"):words("نوێکردنەوە","Update","تحديث"),(d,w)->{if(ready!=null)install();else download(release);})
            .setNegativeButton(words("دواتر","Later","لاحقاً"),null).show();
    }
    private void download(JSONObject release){
        if(downloading||destroyed)return;
        downloading=true;status(words("داگرتنی نوێکردنەوە…","Downloading update…","جارٍ تنزيل التحديث…"));
        executor.execute(()->{
            HttpURLConnection c=null;File part=new File(activity.getFilesDir(),"online-update.part");
            try{
                int code=release.getInt("versionCode");File target=new File(activity.getFilesDir(),"online-update.apk");
                boolean cached=false;if(target.isFile())try{validate(target,code);cached=true;}catch(Exception ignored){target.delete();}
                if(!cached){
                    c=connect(release.getString("apkUrl"));long expected=c.getContentLengthLong(),total=0;
                    try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){if(destroyed||Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>50L*1024*1024)throw new IOException("APK too large");out.write(bytes,0,n);}}
                    if(expected>=0&&total!=expected)throw new IOException("Incomplete APK");validate(part,code);
                    if(!part.renameTo(target))throw new IOException("Cannot save APK");
                }
                ui.post(()->{if(destroyed)return;downloading=false;ready=target;promptedCode=-1;status(words("نوێکردنەوە ئامادەیە","Update ready","التحديث جاهز"));offer(false);});
            }catch(Exception e){ui.post(()->{if(destroyed)return;downloading=false;String error=words("داگرتن سەرکەوتوو نەبوو؛ دووبارە هەوڵ بدە","Download failed. Try again.","فشل التنزيل. حاول مجدداً.");status(error);notice(error);});}
            finally{if(c!=null)c.disconnect();part.delete();}
        });
    }
    private void install(){
        Intent intent=new Intent(activity,AppInstallActivity.class);
        intent.putStringArrayListExtra("uris",new ArrayList<>(Collections.singletonList("content://"+activity.getPackageName()+".share/update")));
        intent.putStringArrayListExtra("names",new ArrayList<>(Collections.singletonList("REBAR IT Clone")));activity.startActivity(intent);
    }
}
