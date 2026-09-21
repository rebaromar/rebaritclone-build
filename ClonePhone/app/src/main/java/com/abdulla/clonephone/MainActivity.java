package com.abdulla.clonephone;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.*;
import android.provider.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.QRCodeWriter;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int FILES=10,FOLDER=11,SCAN=12,QR_IMAGE=13,WIFI_SETTINGS=14,LOCATION_SETTINGS=15,CAMERA_PERMISSION=20,DATA_PERMISSION=21,HOTSPOT_PERMISSION=22,SETUP_PERMISSION=23,SETUP_SETTINGS=24,FILE_BROWSER_PERMISSION=25,PORT=39841,MAX_FILES=10000;
    private static final int BLUE=0xff4f46e5,INK=0xff152238,MUTED=0xff627089,BG=0xfff3f5fa;
    // A permanent catalogue URL: keep this QR unchanged and publish each new APK
    // (and its version notes) at this address.
    private static final String DOWNLOAD_CATALOG_URL="https://app.mediafire.com/folder/omye30rljf3px";
    // The public release manifest.  It contains only version metadata and the
    // direct, signed APK URL; a missing or unreachable manifest is ignored.
    private static final String UPDATE_MANIFEST_URL="https://rebaritclone-update-admin.rebar4qwrna.workers.dev/update.json";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,List<Item>> groups=new LinkedHashMap<>();
    private final List<Item> selection=new ArrayList<>();
    private final Map<String,Set<String>> selectedRecords=new HashMap<>();
    private final Set<String> selectedApps=new HashSet<>();
    private final LinkedHashMap<String,Item> receivedFiles=new LinkedHashMap<>();
    private final LinkedHashMap<String,String> receivedApps=new LinkedHashMap<>();
    private void installReceivedApps(){if(receivedApps.isEmpty())return;Intent i=new Intent(this,AppInstallActivity.class);i.putStringArrayListExtra("uris",new ArrayList<>(receivedApps.keySet()));i.putStringArrayListExtra("names",new ArrayList<>(receivedApps.values()));startActivity(i);}
    private LinearLayout body,root;
    private FileGallery gallery;
    private boolean fileBrowserMode;
    private String browserMediaCategory;
    private void closeGallery(){if(gallery!=null){gallery.close();gallery=null;}}
    private TextView subtitle,status,percentage,details;
    private ProgressBar progress;
    private String screen="home",pendingCategory,pairText;
    private Uri folder,lastContact;
    private volatile Socket socket;
    private volatile ServerSocket listener;
    private volatile boolean busy,cancelled;
    private volatile CountDownLatch approval;
    private AlertDialog approvalDialog;
    private int completed;
    private long totalBytes,finishedBytes,lastUpdate,startTime;
    private boolean totalKnown;
    private TransferStats transferStats;
    private long resumedBytes,transferStarted;
    private String sendingId="",sendingManifest="",receivingId="",receivingManifest="",receivingBatch;
    private Uri receivingLegacy;
    private final Map<Integer,ResumableFile.Entry> resumeEntries=new HashMap<>();
    private boolean restartAfterStop,receivedRecordBackup;
    private String manifest(List<Item> items,boolean source){StringBuilder b=new StringBuilder();for(Item i:items)b.append(source?i.uri.toString():"").append('\n').append(safeName(i.name)).append('\n').append(validMime(i.mime)).append('\n').append(i.size).append('\n');return b.toString();}
    private HotspotLink hotspot;
    private UsbLink usb;
    private boolean usbMode,fastMode,resumeFolder,resumeUsbFolder;
    private String lastHotspotError="";
    private Network transferNetwork;
    private boolean linking;
    private String hotspotSsid="",hotspotPassword="";
    private Pairing pendingPeer;
    private boolean senderPaired;
    private SecureChannel selectedUsbChannel;
    private Runnable afterWifiPermission;
    private int linkGeneration;
    private List<String[]> setupSteps;
    private int setupIndex;
    private boolean setupReceiving,setupActive,setupWaiting;
    private long lastNotification;
    private static final String TRANSFER_CHANNEL="transfer";
    private void startPermissions(boolean receiving){
        if(setupActive)return;
        setupReceiving=receiving;setupActive=true;setupWaiting=false;setupIndex=0;
        setupSteps=PermissionPlan.forRole(Build.VERSION.SDK_INT,receiving);
        page("permissions",UiText.t(246),UiText.t(247),0);
        note(UiText.t(248));
        nextPermission();
    }
    private void nextPermission(){
        if(!setupActive||isDestroyed()||isFinishing()||setupWaiting)return;
        while(setupIndex<setupSteps.size()){
            String[] step=setupSteps.get(setupIndex++);
            boolean missing=false;for(String permission:step)missing|=checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED;
            if(!missing)continue;
            setupWaiting=true;
            try{requestPermissions(step,SETUP_PERMISSION);return;}
            catch(SecurityException e){setupWaiting=false;}
        }
        permissionSummary();
    }
    private void permissionSummary(){
        boolean missing=false;
        for(String[] step:setupSteps)for(String permission:step)if(!permission.equals(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)&&checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)missing=true;
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(manager!=null&&!manager.areNotificationsEnabled())missing=true;
        if(!missing){finishPermissions();return;}
        page("permissions",UiText.t(246),UiText.t(249),0);
        note(UiText.t(248));
        button(UiText.t(250),false,()->{try{startActivityForResult(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())),SETUP_SETTINGS);}catch(ActivityNotFoundException e){toast(UiText.t(249));}});
        button(UiText.t(28),true,()->finishPermissions());
        button(UiText.t(29),false,()->{setupActive=false;home();});
    }
    private void finishPermissions(){
        boolean receiving=setupReceiving;setupActive=false;setupWaiting=false;
        if(receiving){receiverSetup();String wifi=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
            if(checkSelfPermission(wifi)==PackageManager.PERMISSION_GRANTED)beginHotspot();else note(UiText.t(42));
        }else connectPage();
    }
    private void transferNotification(String message,int percent,boolean finished){
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(manager==null||!manager.areNotificationsEnabled())return;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        long now=SystemClock.elapsedRealtime();if(!finished&&percent>=0&&now-lastNotification<1500)return;lastNotification=now;
        manager.createNotificationChannel(new NotificationChannel(TRANSFER_CHANNEL,UiText.t(251),NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification=new Notification.Builder(this,TRANSFER_CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("rebaritclone").setContentText(message).setContentIntent(tap).setOnlyAlertOnce(true).setOngoing(!finished).setAutoCancel(finished).setVisibility(Notification.VISIBILITY_PRIVATE);
        if(!finished)notification.setProgress(1000,Math.max(0,percent),percent<0);
        try{manager.notify(74,notification.build());}catch(SecurityException ignored){}
    }
    private final Handler main=new Handler(Looper.getMainLooper());

    static class Item {Uri uri;String name,mime;long size;Item(){}Item(Uri u,String n,String m,long s){uri=u;name=n;mime=m;size=s;}}
    @Override public void onCreate(Bundle state){super.onCreate(state);UiText.init(this);hotspot=new HotspotLink(this);usb=new UsbLink(this);fastMode=getPreferences(0).getBoolean("fast5",true);onlineUpdate=new OnlineUpdate(this,()->busy);home();}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable box(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.START);t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);t.setLineSpacing(dp(4),1);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private void gap(int height){Space s=new Space(this);body.addView(s,new LinearLayout.LayoutParams(1,dp(height)));}
    private TextView text(String value,int size,int color,boolean bold){TextView t=label(value,size,color,bold);body.addView(t);return t;}
    private void page(String id,String title,String description,int step){
        closeGallery();
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        screen=id;root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);root.setLayoutDirection(UiText.direction());
        root.setPadding(dp(24),dp(18),dp(24),dp(14));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top);
        TextView brand=label("rebaritclone",19,INK,true);brand.setTextDirection(View.TEXT_DIRECTION_LTR);top.addView(brand,new LinearLayout.LayoutParams(0,dp(40),1));
        TextView badge=label(UiText.t(295)+versionName(),11,BLUE,true);badge.setPadding(dp(10),dp(6),dp(10),dp(6));badge.setBackground(box(0xffe8e8ff,20));top.addView(badge);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body=new LinearLayout(this);body.setOrientation(1);body.setPadding(0,dp(12),0,dp(24));scroll.addView(body);
        // The journey is always shown in the same order:
        // 1. Connect  →  2. Select  →  3. Transfer.
        if(step>0){steps(step);gap(24);}
        text(title,28,INK,true);gap(10);subtitle=text(description,15,MUTED,false);gap(24);
    }
    private void steps(int active){
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);body.addView(line,new LinearLayout.LayoutParams(-1,-2));
        for(int n=1;n<=3;n++){TextView chip=label((n<active?"✓":String.valueOf(n))+"  "+UiText.t(n==1?270:n==2?269:271),11,n==active?Color.WHITE:n<active?BLUE:MUTED,true);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(4),dp(10),dp(4),dp(10));chip.setBackground(box(n==active?BLUE:n<active?0xffe8e8ff:0xffe9edf5,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(2),0,dp(2),0);line.addView(chip,lp);}
    }
    private TextView summaryCard(String message){TextView v=text(message,16,INK,true);v.setPadding(dp(18),dp(18),dp(18),dp(18));v.setBackground(box(Color.WHITE,20));return v;}
    private Button button(String title,boolean primary,Runnable click){
        Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setTypeface(null,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:BLUE);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x222563eb),box(primary?BLUE:0xffeaf0fd,18),null));
        b.setMinHeight(dp(56));b.setPadding(dp(16),dp(14),dp(16),dp(14));b.setElevation(primary?dp(2):0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(7));body.addView(b,lp);b.setOnClickListener(v->click.run());return b;
    }
    private void note(String message){gap(12);TextView t=text(message,13,MUTED,false);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setBackground(box(0xffe9edf5,14));}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void alert(String title,String message){new AlertDialog.Builder(UiText.context(this)).setTitle(title).setMessage(message).setPositiveButton(UiText.t(2),null).show();}
    private void chooseLanguage(){
        String[] codes={"ckb","en","ar"};String[] labels={"کوردی","English","العربية"};int selected=Arrays.asList(codes).indexOf(UiText.language());
        new AlertDialog.Builder(UiText.context(this)).setTitle("زمان / Language / اللغة").setSingleChoiceItems(labels,selected,(dialog,which)->{UiText.select(this,codes[which]);dialog.dismiss();home();}).setNegativeButton(UiText.t(61),null).show();
    }
    private static final int NIGHT=0xff101827,LIGHT=0xfff7f7fc;
    private void darkScreen(String id,String title){
        closeGallery();
        screen=id;root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(NIGHT);root.setLayoutDirection(UiText.direction());root.setPadding(dp(22),dp(12),dp(22),dp(12));setContentView(root);
        getWindow().setStatusBarColor(NIGHT);getWindow().setNavigationBarColor(NIGHT);getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);root.addView(bar,new LinearLayout.LayoutParams(-1,dp(52)));
        if(!id.equals("home")){TextView back=label("‹",32,LIGHT,true);back.setGravity(Gravity.CENTER);back.setContentDescription(UiText.t(29));bar.addView(back,new LinearLayout.LayoutParams(dp(48),-1));back.setOnClickListener(v->home());}
        TextView brand=label(title,20,LIGHT,true);brand.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView installedVersion=label("",12,0xffb3b2c5,false);
        installedVersion.setMaxWidth(dp(155));installedVersion.setPadding(dp(8),0,dp(8),0);bar.addView(installedVersion);
        if(onlineUpdate!=null)onlineUpdate.bind(installedVersion);
        TextView menu=label("⋮",28,LIGHT,true);menu.setGravity(Gravity.CENTER);menu.setContentDescription(UiText.t(262));bar.addView(menu,new LinearLayout.LayoutParams(dp(48),-1));
        menu.setOnClickListener(v->{PopupMenu options=new PopupMenu(this,menu);options.getMenu().add("زمان / Language / اللغة").setOnMenuItemClickListener(i->{chooseLanguage();return true;});options.getMenu().add(onlineUpdate.menuTitle()).setOnMenuItemClickListener(i->{onlineUpdate.checkManually();return true;});options.getMenu().add(UiText.t(310)).setOnMenuItemClickListener(i->{shareOwnApk();return true;});options.show();});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(this);body.setOrientation(1);body.setPadding(0,dp(12),0,dp(16));scroll.addView(body);
    }
    private void appDownloadPage(){
        page("download",UiText.t(290),UiText.t(291),0);
        try{
            int side=620;BitMatrix matrix=new QRCodeWriter().encode(DOWNLOAD_CATALOG_URL,BarcodeFormat.QR_CODE,side,side);int[] pixels=new int[side*side];
            for(int y=0;y<side;y++)for(int x=0;x<side;x++)pixels[y*side+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            ImageView qr=new ImageView(this);qr.setImageBitmap(Bitmap.createBitmap(pixels,side,side,Bitmap.Config.ARGB_8888));qr.setContentDescription(UiText.t(292));qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setBackground(box(Color.WHITE,24));qr.setPadding(dp(10),dp(10),dp(10),dp(10));
            int width=Math.min(dp(286),getResources().getDisplayMetrics().widthPixels-dp(48));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,width);lp.gravity=Gravity.CENTER;body.addView(qr,lp);
        }catch(Exception e){throw new IllegalStateException(e);}
        gap(18);TextView version=text(UiText.t(295)+versionName(),15,BLUE,true);version.setGravity(Gravity.CENTER);
        TextView url=text(DOWNLOAD_CATALOG_URL,13,MUTED,false);url.setTextDirection(View.TEXT_DIRECTION_LTR);url.setGravity(Gravity.CENTER);url.setTextIsSelectable(true);gap(12);
        button(UiText.t(293),true,this::openDownloadCatalog);
        button(UiText.t(310),false,this::shareOwnApk);
        button(UiText.t(294),false,()->{android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("rebaritclone download",DOWNLOAD_CATALOG_URL));toast(UiText.t(296));});
        button(UiText.t(29),false,this::home);
    }
    private String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}}
    private void shareOwnApk(){
        try{
            Uri apk=Uri.parse("content://"+getPackageName()+".share/apk");
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("application/vnd.android.package-archive");
            send.putExtra(Intent.EXTRA_STREAM,apk);
            send.setClipData(ClipData.newRawUri(UiText.t(310),apk));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,UiText.t(310)));
        }catch(ActivityNotFoundException|SecurityException e){toast(UiText.t(311));}
    }
    private void openDownloadCatalog(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(DOWNLOAD_CATALOG_URL)));}catch(Exception e){toast(UiText.t(297));}}
    private OnlineUpdate onlineUpdate;
    @Override protected void onResume(){super.onResume();if(onlineUpdate!=null)onlineUpdate.resume();}
    @Override protected void onPause(){if(onlineUpdate!=null)onlineUpdate.pause();super.onPause();}
    private void home(){
        if(!busy){stopLink();senderPaired=false;selectedUsbChannel=null;}
        fileBrowserMode=false;
        darkScreen("home","rebaritclone");
        View art=new FlowArt(this);body.addView(art,new LinearLayout.LayoutParams(-1,dp(170)));
        text(UiText.t(258),12,0xffb3b2c5,false);gap(8);text(UiText.t(259),25,LIGHT,true);gap(24);
        landingCard(UiText.t(264),UiText.t(260),UiText.t(28),0xff087f78,0xff12958c,()->deviceRoles(false));
        landingCard(UiText.t(261),UiText.t(263),UiText.t(28),0xff5349bd,0xff7563d5,()->{fileBrowserMode=true;fileHub();});
        gap(8);button(UiText.t(310),false,this::shareOwnApk);
    }
    private void landingCard(String title,String description,String action,int first,int second,Runnable click){
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(20),dp(20),dp(20),dp(16));
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});background.setCornerRadius(dp(20));card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),background,null));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(14));body.addView(card,cp);
        TextView heading=label(title,23,LIGHT,true);card.addView(heading);
        TextView sub=label(description,13,Color.WHITE,false);card.addView(sub);
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.END);footer.setPadding(0,dp(16),0,0);card.addView(footer);
        TextView pill=label(action+"  ›",14,Color.WHITE,true);pill.setGravity(Gravity.CENTER);pill.setPadding(dp(20),dp(5),dp(20),dp(5));GradientDrawable border=box(Color.TRANSPARENT,24);border.setStroke(dp(1),0xddffffff);pill.setBackground(border);footer.addView(pill);
        card.setFocusable(true);card.setOnClickListener(v->click.run());
    }
    private void deviceRoles(boolean files){
        darkScreen(files?"fileRoles":"deviceRoles",files?UiText.t(261):UiText.t(264));
        gap(26);text(files?UiText.t(261):UiText.t(264),29,LIGHT,true).setGravity(Gravity.CENTER);gap(28);
        darkRole(UiText.t(7),UiText.t(8),"↓",0xff9690ff,()->startPermissions(true));
        darkRole(UiText.t(5),UiText.t(6),"↑",0xff35d8b4,()->startPermissions(false));
        gap(16);text(UiText.t(9),13,0xffb3b2c5,false).setGravity(Gravity.CENTER);
    }
    private void darkRole(String title,String description,String glyph,int accent,Runnable click){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(18),dp(22),dp(18),dp(22));card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff),box(0xff242632,18),null));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(16));body.addView(card,cp);
        TextView icon=label(glyph,30,NIGHT,true);icon.setGravity(Gravity.CENTER);icon.setBackground(box(accent,15));card.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(54)));
        LinearLayout words=new LinearLayout(this);words.setOrientation(1);words.setPadding(dp(16),0,dp(10),0);card.addView(words,new LinearLayout.LayoutParams(0,-2,1));words.addView(label(title,21,LIGHT,true));words.addView(label(description,13,0xffc0bfce,false));card.setFocusable(true);card.setOnClickListener(v->click.run());
    }
    private static class FlowArt extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        FlowArt(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight();
            paint.setShader(new RadialGradient(w*.85f,h*.7f,w*.7f,new int[]{0xff393451,0xff191a28,NIGHT},new float[]{0,.55f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
            float unit=Math.min(w/340f,h/160f);canvas.save();canvas.translate((w-340*unit)/2,(h-160*unit)/2);canvas.scale(unit,unit);
            for(int i=0;i<2;i++){float x=i==0?62:218;paint.setColor(i==0?0xff293951:0xff254b50);canvas.drawRoundRect(x,12,x+66,144,13,13,paint);paint.setColor(i==0?0xff92a6c4:0xff57dac2);canvas.drawRoundRect(x+7,20,x+59,132,8,8,paint);paint.setColor(0xff152338);canvas.drawRoundRect(x+22,24,x+44,29,3,3,paint);for(int row=0;row<3;row++){paint.setColor(i==0?0xff435a7c:0xff238c82);canvas.drawRoundRect(x+14,47+row*22,x+52,59+row*22,4,4,paint);}}
            paint.setColor(0xffb3a9ff);paint.setStrokeWidth(4);paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(150,68,190,68,paint);canvas.drawLine(182,60,190,68,paint);canvas.drawLine(182,76,190,68,paint);paint.setColor(0xff57dac2);canvas.drawLine(190,91,150,91,paint);canvas.drawLine(158,83,150,91,paint);canvas.drawLine(158,99,150,91,paint);canvas.restore();

        }
    }
    private void role(String title,String description,String icon,Runnable action){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(20),dp(22),dp(20),dp(22));card.setBackground(box(Color.WHITE,22));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(14));body.addView(card,cp);
        TextView glyph=label(icon,32,BLUE,true);glyph.setGravity(Gravity.CENTER);glyph.setBackground(box(0xffedf3ff,16));card.addView(glyph,new LinearLayout.LayoutParams(dp(58),dp(64)));
        LinearLayout words=new LinearLayout(this);words.setOrientation(1);words.setPadding(dp(16),0,dp(16),0);card.addView(words,new LinearLayout.LayoutParams(0,-2,1));words.addView(label(title,21,INK,true));words.addView(label(description,13,MUTED,false));card.setOnClickListener(v->action.run());
    }
    private void categories(){
        if(fileBrowserMode){fileHub();return;}
        page("categories",UiText.t(14),UiText.t(15),2);
        section(UiText.t(284));
        category("photos",UiText.t(16),UiText.t(17),"▧");
        category("videos",UiText.t(18),UiText.t(19),"▷");
        section(UiText.t(285));
        category("files",UiText.t(26),UiText.t(27),"＋");
        category("apps",UiText.t(22),UiText.t(23),"APK");
        category("audio",UiText.t(20),UiText.t(21),"♫");
        section(UiText.t(286));
        category("contacts",UiText.t(24),UiText.t(232),"☏");
        category("calls",UiText.t(229),UiText.t(233),"↔");
        category("sms",UiText.t(230),UiText.t(234),"✉");
        gap(14);summaryCard(selectionSummary(selectedItems()));
        button(UiText.t(28),true,()->prepareSelection());
        button(UiText.t(29),false,()->home());
        note(UiText.t(30));
    }
    /** File-manager style start page for the File transfer card. */
    private void fileHub(){
        closeGallery();fileBrowserMode=true;screen="fileHub";
        getWindow().setStatusBarColor(0xfffafbfe);getWindow().setNavigationBarColor(0xfffafbfe);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xfffafbfe);root.setLayoutDirection(UiText.direction());root.setPadding(dp(16),dp(8),dp(16),dp(12));setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(62)));
        TextView back=label("‹",44,INK,false);back.setGravity(Gravity.CENTER);back.setContentDescription(UiText.t(29));header.addView(back,new LinearLayout.LayoutParams(dp(52),-1));back.setOnClickListener(v->home());
        TextView title=label(UiText.t(26),29,INK,true);title.setGravity(Gravity.CENTER_VERTICAL);header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        TextView refresh=label("↻",30,INK,false);refresh.setGravity(Gravity.CENTER);refresh.setContentDescription(UiText.t(303));header.addView(refresh,new LinearLayout.LayoutParams(dp(52),-1));refresh.setOnClickListener(v->fileHub());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(this);body.setOrientation(1);body.setPadding(0,dp(10),0,dp(18));scroll.addView(body);
        EditText search=new EditText(this);search.setSingleLine(true);search.setFocusable(false);search.setClickable(true);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint(UiText.t(303));search.setTextSize(17);search.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search,0,0,0);search.setCompoundDrawablePadding(dp(10));search.setPadding(dp(16),0,dp(16),0);search.setBackground(box(0xffeef1f6,16));body.addView(search,new LinearLayout.LayoutParams(-1,dp(58)));search.setOnClickListener(v->pickFiles());
        gap(18);TextView hint=text(UiText.t(263),14,MUTED,false);hint.setPadding(dp(4),0,dp(4),dp(8));
        Map<String,TextView> counters=new LinkedHashMap<>();
        LinearLayout first=fileHubRow();
        counters.put("audio",fileHubTile(first,UiText.t(20),"♫",0xffe55245,()->openFileBrowserMedia("audio")));
        counters.put("apps",fileHubTile(first,UiText.t(22),"▦",0xff1686df,this::chooseApps));
        counters.put("videos",fileHubTile(first,UiText.t(18),"▶",0xff7d45d6,()->openFileBrowserMedia("videos")));
        counters.put("photos",fileHubTile(first,UiText.t(16),"▧",0xff36b85b,()->openFileBrowserMedia("photos")));
        LinearLayout second=fileHubRow();
        counters.put("documents",fileHubTile(second,UiText.t(304),"▤",0xffc78627,this::pickFiles));
        counters.put("apk",fileHubTile(second,UiText.t(305),"APK",0xffdc5d45,this::pickFiles));
        counters.put("other",fileHubTile(second,UiText.t(306),"…",0xff4c94d9,this::pickFiles));
        counters.put("downloads",fileHubTile(second,UiText.t(307),"⇩",0xff5756ca,this::pickFiles));
        if(countSelected()>0){gap(18);TextView selected=text(UiText.t(308)+selectionSummary(selectedItems()),15,BLUE,true);selected.setGravity(Gravity.CENTER);button(UiText.t(309),true,this::prepareSelection);}
        loadFileHubCounts(counters);
    }
    private LinearLayout fileHubRow(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(142));lp.setMargins(0,0,0,dp(8));body.addView(row,lp);return row;}
    private TextView fileHubTile(LinearLayout row,String title,String glyph,int color,Runnable click){
        LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setGravity(Gravity.CENTER_HORIZONTAL);tile.setPadding(dp(3),dp(8),dp(3),dp(6));tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x164f46e5),box(0xffffffff,16),null));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(3),0,dp(3),0);row.addView(tile,lp);
        TextView icon=label(glyph,glyph.equals("APK")?13:29,Color.WHITE,true);icon.setGravity(Gravity.CENTER);icon.setTextDirection(View.TEXT_DIRECTION_LTR);icon.setBackground(box(color,17));tile.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));
        TextView name=label(title,13,INK,true);name.setGravity(Gravity.CENTER);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(38));np.setMargins(0,dp(7),0,0);tile.addView(name,np);
        TextView count=label("…",14,MUTED,false);count.setGravity(Gravity.CENTER);tile.addView(count,new LinearLayout.LayoutParams(-1,dp(20)));
        tile.setContentDescription(title);tile.setOnClickListener(v->click.run());return count;
    }
    private void openFileBrowserMedia(String category){
        if(browserMediaAllowed(category)){showFileGallery(category);return;}
        browserMediaCategory=category;List<String> permissions=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=33){permissions.add(category.equals("photos")?Manifest.permission.READ_MEDIA_IMAGES:category.equals("videos")?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_MEDIA_AUDIO);if(Build.VERSION.SDK_INT>=34&&!category.equals("audio"))permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);}
        else permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        requestPermissions(permissions.toArray(new String[0]),FILE_BROWSER_PERMISSION);
    }
    private boolean browserMediaAllowed(String category){
        if(Build.VERSION.SDK_INT<33)return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        String permission=category.equals("photos")?Manifest.permission.READ_MEDIA_IMAGES:category.equals("videos")?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_MEDIA_AUDIO;
        return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED||(Build.VERSION.SDK_INT>=34&&!category.equals("audio")&&checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED);
    }
    private void loadFileHubCounts(Map<String,TextView> counters){worker.execute(()->{Map<String,Integer> values=new LinkedHashMap<>();for(String id:counters.keySet())values.put(id,fileHubCount(id));runOnUiThread(()->{if(!"fileHub".equals(screen))return;for(Map.Entry<String,TextView> entry:counters.entrySet()){Integer value=values.get(entry.getKey());entry.getValue().setText(value==null||value<0?"—":String.valueOf(value));}});});}
    private int fileHubCount(String id){
        try{
            if(id.equals("apps")){int count=0;for(ApplicationInfo app:getPackageManager().getInstalledApplications(0))if(!app.packageName.equals(getPackageName())&&(app.flags&ApplicationInfo.FLAG_SYSTEM)==0&&app.sourceDir!=null)count++;return count;}
            Uri collection;if(id.equals("photos"))collection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;else if(id.equals("videos"))collection=MediaStore.Video.Media.EXTERNAL_CONTENT_URI;else if(id.equals("audio"))collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;else collection=MediaStore.Files.getContentUri("external");
            String selection=null;String[] args=null;
            if(id.equals("apk")){selection=MediaStore.MediaColumns.DISPLAY_NAME+" LIKE ?";args=new String[]{"%.apk"};}
            else if(id.equals("documents")){selection=MediaStore.MediaColumns.MIME_TYPE+" NOT LIKE ? AND "+MediaStore.MediaColumns.MIME_TYPE+" NOT LIKE ? AND "+MediaStore.MediaColumns.MIME_TYPE+" NOT LIKE ?";args=new String[]{"image/%","video/%","audio/%"};}
            else if(id.equals("downloads")){selection=MediaStore.MediaColumns.RELATIVE_PATH+" LIKE ?";args=new String[]{"Download/%"};}
            try(Cursor cursor=getContentResolver().query(collection,new String[]{MediaStore.MediaColumns._ID},selection,args,null)){return cursor==null?0:cursor.getCount();}
        }catch(SecurityException e){return -1;}catch(Exception e){return 0;}
    }
    private void showFileGallery(String initialTab){
        darkScreen("gallery",UiText.t(14));
        root.removeViewAt(root.getChildCount()-1);
        List<FileGallery.Entry> initial=new ArrayList<>();
        for(Item item:groups.getOrDefault("gallery",Collections.emptyList())){FileGallery.Entry e=new FileGallery.Entry();e.uri=item.uri;e.name=item.name;e.mime=item.mime;e.size=item.size;initial.add(e);}
        gallery=new FileGallery(this,initial,selectionSummary(selectedItems()),new FileGallery.Actions(){
            public void changed(List<FileGallery.Entry> selected){List<Item> items=new ArrayList<>();for(FileGallery.Entry e:selected)items.add(new Item(e.uri,e.name,e.mime,e.size));if(items.isEmpty())groups.remove("gallery");else groups.put("gallery",items);if(gallery!=null)gallery.summary(selectionSummary(selectedItems()));}
            public void tab(String id){if(id.equals("files"))pickFiles();else if(id.equals("apps"))chooseApps();else requestCategory("contacts");}
            public void next(){prepareSelection();}
        },initialTab);root.addView(gallery,new LinearLayout.LayoutParams(-1,0,1));
    }
    private List<Item> selectedItems(){LinkedHashMap<String,Item> unique=new LinkedHashMap<>();for(List<Item> list:groups.values())for(Item item:list)unique.put(item.uri.toString(),item);return new ArrayList<>(unique.values());}
    private TransferStats stats(List<Item> items){String[] names=new String[items.size()],mimes=new String[items.size()];long[] sizes=new long[items.size()];for(int n=0;n<items.size();n++){Item i=items.get(n);names[n]=i.name;mimes[n]=i.mime;sizes[n]=i.size;}return new TransferStats(names,mimes,sizes);}
    private String selectionSummary(List<Item> items){TransferStats s=stats(items);return items.size()+UiText.t(31)+TransferStats.gb(s.totalBytes)+(s.totalKnown?"":UiText.t(32));}
    private int countSelected(){HashSet<String> unique=new HashSet<>();for(List<Item> list:groups.values())for(Item i:list)unique.add(i.uri.toString());return unique.size();}
    private void section(String name){TextView t=text(name,13,BLUE,true);t.setPadding(dp(4),dp(10),0,dp(8));}
    private void category(String id,String title,String desc,String glyph){
        boolean chosen=groups.containsKey(id);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable tile=box(chosen?0xffeeedff:Color.WHITE,20);tile.setStroke(dp(1),chosen?0xffb8b3fa:0xffe2e7f0);row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x184f46e5),tile,null));row.setFocusable(true);row.setContentDescription(title+". "+(chosen?UiText.t(272):desc));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));body.addView(row,lp);
        int accent=id.equals("photos")?0xffd4517d:id.equals("videos")?0xff715bd5:id.equals("files")?0xff168477:id.equals("apps")?0xffd58026:BLUE;
        TextView icon=label(glyph,id.equals("apps")?13:24,Color.WHITE,true);icon.setGravity(Gravity.CENTER);icon.setBackground(box(accent,14));row.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout words=new LinearLayout(this);words.setOrientation(1);words.setPadding(dp(12),0,dp(12),0);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));words.addView(label(title,18,INK,true));words.addView(label(chosen?(isRecords(id)?selectedRecords.getOrDefault(id,Collections.emptySet()).size()+UiText.t(238)+" • ":"")+selectionSummary(groups.get(id)):desc,12,MUTED,false));
        TextView tick=label(chosen?"✓":"○",24,chosen?BLUE:0xffb5bfd0,true);row.addView(tick);
        row.setOnClickListener(v->{if(id.equals("apps")){chooseApps();return;}if((id.equals("sms")||id.equals("calls"))&&chosen){groups.remove(id);selectedRecords.remove(id);categories();return;}if(isRecords(id)){requestCategory(id);return;}if(chosen){groups.remove(id);categories();}else if(id.equals("files"))pickFiles();else requestCategory(id);});
    }
    private void chooseApps(){
        if(busy)return;busy=true;cancelled=false;loading(UiText.t(33));
        worker.execute(()->{try{
            PackageManager pm=getPackageManager();LinkedHashMap<String,ApplicationInfo> unique=new LinkedHashMap<>();
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for(ResolveInfo r:pm.queryIntentActivities(query,0)){checkCancel();ApplicationInfo app=r.activityInfo.applicationInfo;
                if(!app.packageName.equals(getPackageName())&&(app.flags&ApplicationInfo.FLAG_SYSTEM)==0&&app.sourceDir!=null)unique.put(app.packageName,app);
            }
            List<ApplicationInfo> apps=new ArrayList<>(unique.values());apps.sort((a,b)->pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
            String[] labels=new String[apps.size()];Drawable[] icons=new Drawable[apps.size()];boolean[] checked=new boolean[apps.size()];
            for(int n=0;n<apps.size();n++){ApplicationInfo app=apps.get(n);labels[n]=pm.getApplicationLabel(app).toString();try{icons[n]=pm.getApplicationIcon(app);}catch(Exception ignored){icons[n]=pm.getDefaultActivityIcon();}checked[n]=selectedApps.contains(app.packageName);}
            runOnUiThread(()->{if(isDestroyed())return;busy=false;categories();if(apps.isEmpty()){toast(UiText.t(34));return;}
                ListView appList=new ListView(UiText.context(this));
                appList.setAdapter(new BaseAdapter(){
                    public int getCount(){return apps.size();}
                    public Object getItem(int position){return apps.get(position);}
                    public long getItemId(int position){return position;}
                    public View getView(int position,View old,android.view.ViewGroup parent){
                        LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setLayoutDirection(UiText.direction());row.setPadding(dp(12),dp(10),dp(12),dp(10));
                        ImageView icon=new ImageView(MainActivity.this);icon.setImageDrawable(icons[position]);row.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
                        TextView name=label(labels[position],17,INK,false);name.setPadding(dp(12),0,dp(12),0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                        CheckBox check=new CheckBox(MainActivity.this);check.setChecked(checked[position]);check.setClickable(false);check.setFocusable(false);row.addView(check);
                        row.setContentDescription(labels[position]);return row;
                    }
                });
                appList.setOnItemClickListener((parent,view,n,id)->{checked[n]=!checked[n];((BaseAdapter)appList.getAdapter()).notifyDataSetChanged();});
                LinearLayout appLayout=new LinearLayout(this);appLayout.setOrientation(1);appLayout.addView(appList,new LinearLayout.LayoutParams(-1,getResources().getDisplayMetrics().heightPixels/2));
                new AlertDialog.Builder(UiText.context(this)).setTitle(UiText.t(35)).setView(appLayout)
                .setPositiveButton(UiText.t(36),(d,w)->{List<ApplicationInfo> chosen=new ArrayList<>();for(int n=0;n<apps.size();n++)if(checked[n])chosen.add(apps.get(n));exportApps(chosen);})
                .setNegativeButton(UiText.t(29),null).show();
            });
        }catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=false;categories();alert(UiText.t(37),friendly(e));});}});
    }
    private void exportApps(List<ApplicationInfo> apps){
        if(apps.isEmpty()){groups.remove("apps");selectedApps.clear();categories();return;}
        busy=true;cancelled=false;loading(UiText.t(38));
        worker.execute(()->{List<Item> exported=new ArrayList<>();Set<String> successful=new HashSet<>();StringBuilder failed=new StringBuilder();
            for(ApplicationInfo old:apps){File bundle=null;try{
                checkCancel();PackageInfo before=getPackageManager().getPackageInfo(old.packageName,0);ApplicationInfo app=before.applicationInfo;
                List<File> parts=new ArrayList<>();parts.add(new File(app.sourceDir));if(app.splitSourceDirs!=null)for(String path:app.splitSourceDirs)parts.add(new File(path));
                bundle=File.createTempFile("app-",".rbapp",getCacheDir());AppBundle.write(parts,bundle);checkCancel();PackageInfo after=getPackageManager().getPackageInfo(app.packageName,0);
                if(before.lastUpdateTime!=after.lastUpdateTime||before.versionCode!=after.versionCode)throw new IOException("App updated during export");
                String label=safeName(getPackageManager().getApplicationLabel(app).toString());if(label.length()>100)label=label.substring(0,100);
                exported.add(new Item(Uri.fromFile(bundle),label+".rbapp",AppBundle.MIME,bundle.length()));successful.add(app.packageName);
            }catch(Exception e){if(bundle!=null)bundle.delete();failed.append(old.packageName).append("\n");if(cancelled)break;}}
            runOnUiThread(()->{if(isDestroyed())return;busy=false;groups.remove("apps");if(!exported.isEmpty())groups.put("apps",exported);selectedApps.clear();selectedApps.addAll(successful);categories();
                if(failed.length()>0)alert(UiText.t(39),UiText.t(40)+failed.toString());});
        });
    }
    private void pickFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.putExtra(Intent.EXTRA_LOCAL_ONLY,true);startActivityForResult(i,FILES);}
    private void requestCategory(String id){
        pendingCategory=id;List<String> permissions=new ArrayList<>();
        if(id.equals("contacts"))permissions.add(Manifest.permission.READ_CONTACTS);
        else if(id.equals("calls"))permissions.add(Manifest.permission.READ_CALL_LOG);
        else if(id.equals("sms"))permissions.add(Manifest.permission.READ_SMS);
        else if(Build.VERSION.SDK_INT>=33){permissions.add(id.equals("photos")?Manifest.permission.READ_MEDIA_IMAGES:id.equals("videos")?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_MEDIA_AUDIO);
            if(Build.VERSION.SDK_INT>=34&&!id.equals("audio"))permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        }else permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if(checkSelfPermission(permissions.get(0))==PackageManager.PERMISSION_GRANTED){loadCategory(id);return;}
        requestPermissions(permissions.toArray(new String[0]),DATA_PERMISSION);
    }
    @Override public void onRequestPermissionsResult(int code,String[] perms,int[] grants){
        super.onRequestPermissionsResult(code,perms,grants);
        if(code==SETUP_PERMISSION){setupWaiting=false;main.post(()->nextPermission());return;}
        if(code==FILE_BROWSER_PERMISSION){String category=browserMediaCategory;browserMediaCategory=null;if(category!=null&&browserMediaAllowed(category))showFileGallery(category);else toast(UiText.t(44));return;}
        if(code==HOTSPOT_PERMISSION){
            String needed=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
            Runnable next=afterWifiPermission;afterWifiPermission=null;
            if(checkSelfPermission(needed)==PackageManager.PERMISSION_GRANTED&&next!=null)next.run();else alert(UiText.t(41),UiText.t(42));
        }
        if(code==CAMERA_PERMISSION){if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)scan();else toast(UiText.t(43));}
        if(code==DATA_PERMISSION){boolean allowed=false;for(int g:grants)allowed|=g==PackageManager.PERMISSION_GRANTED;
            if(allowed)loadCategory(pendingCategory);else if(isRecords(pendingCategory))alert(UiText.t(41),UiText.t(235));else toast(UiText.t(44));}
    }
    private void loading(String message){page("loading",message,UiText.t(45),1);ProgressBar p=new ProgressBar(this);body.addView(p);}
    private void loadCategory(String id){
        if(isRecords(id)){loadRecords(id);return;}
        busy=true;cancelled=false;loading(UiText.t(46));
        worker.execute(()->{
            try{
                List<Item> found=media(id);
                checkCancel();runOnUiThread(()->{if(isDestroyed())return;busy=false;if(!found.isEmpty())groups.put(id,found);categories();if(found.isEmpty())toast(UiText.t(47));
                    else if(Build.VERSION.SDK_INT>=34&&(id.equals("photos")||id.equals("videos"))&&checkSelfPermission(id.equals("photos")?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_MEDIA_VIDEO)!=PackageManager.PERMISSION_GRANTED)toast(UiText.t(48));});
            }catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=false;categories();alert(UiText.t(49),friendly(e));});}
        });
    }
    private List<Item> media(String category)throws Exception{
        Uri collection=category.equals("photos")?MediaStore.Images.Media.EXTERNAL_CONTENT_URI:category.equals("videos")?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        List<Item> out=new ArrayList<>();
        try(Cursor c=getContentResolver().query(collection,new String[]{"_id","_display_name","mime_type","_size"},null,null,"date_added DESC")){
            if(c!=null)while(c.moveToNext()){checkCancel();if(out.size()>=MAX_FILES)throw new IOException("TOO_MANY");out.add(new Item(ContentUris.withAppendedId(collection,c.getLong(0)),safeName(c.getString(1)),validMime(c.getString(2)),c.isNull(3)?-1:c.getLong(3)));}
        }return out;
    }
    private boolean isRecords(String id){return "contacts".equals(id)||"calls".equals(id)||"sms".equals(id);}
    private void loadRecords(String kind){
        if(busy)return;busy=true;cancelled=false;loading(UiText.t(46));
        worker.execute(()->{try{PersonalRecords.Result result=PersonalRecords.load(this,kind,()->checkCancel());
            runOnUiThread(()->{if(isDestroyed())return;busy=false;categories();if(result.rows.isEmpty())toast(UiText.t(47));else if(kind.equals("sms")||kind.equals("calls")){if(result.limited)toast(UiText.t(239));exportRecords(kind,result.rows);}else chooseRecords(kind,result);});
        }catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=false;categories();alert(UiText.t(49),e instanceof SecurityException?UiText.t(235):UiText.t(244));});}});
    }
    private void chooseRecords(String kind,PersonalRecords.Result result){
        Set<String> chosen=new HashSet<>(selectedRecords.getOrDefault(kind,Collections.emptySet()));List<PersonalRecords.Row> visible=new ArrayList<>();Set<String> available=new HashSet<>();for(PersonalRecords.Row row:result.rows)available.add(row.id);chosen.retainAll(available);if(!kind.equals("contacts")&&!selectedRecords.containsKey(kind))chosen.addAll(available);
        LinearLayout layout=new LinearLayout(UiText.context(this));layout.setOrientation(1);layout.setPadding(dp(16),dp(8),dp(16),0);layout.setLayoutDirection(UiText.direction());
        EditText search=new EditText(UiText.context(this));search.setSingleLine(true);search.setHint(UiText.t(236));layout.addView(search);
        TextView note=label(kind.equals("contacts")?UiText.t(232):UiText.t(231),13,MUTED,false);layout.addView(note);if(result.limited)layout.addView(label(UiText.t(239),13,MUTED,false));
        TextView count=label("",15,BLUE,true);layout.addView(count);
        LinearLayout actions=new LinearLayout(this);layout.addView(actions);Button all=new Button(this);all.setText(UiText.t(237));actions.addView(all,new LinearLayout.LayoutParams(0,-2,1));Button none=new Button(this);none.setText(UiText.t(245));actions.addView(none,new LinearLayout.LayoutParams(0,-2,1));
        ListView list=new ListView(UiText.context(this));list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);layout.addView(list,new LinearLayout.LayoutParams(-1,Math.min(dp(340),getResources().getDisplayMetrics().heightPixels/3)));
        Runnable refresh=()->{String term=search.getText().toString().toLowerCase(Locale.ROOT);visible.clear();List<String> labels=new ArrayList<>();for(PersonalRecords.Row row:result.rows)if(row.label.toLowerCase(Locale.ROOT).contains(term)){visible.add(row);labels.add(row.label);}
            list.setAdapter(new ArrayAdapter<>(UiText.context(this),android.R.layout.simple_list_item_multiple_choice,labels));list.clearChoices();for(int n=0;n<visible.size();n++)list.setItemChecked(n,chosen.contains(visible.get(n).id));count.setText(chosen.size()+UiText.t(238));};
        list.setOnItemClickListener((parent,view,pos,id)->{String key=visible.get(pos).id;if(list.isItemChecked(pos))chosen.add(key);else chosen.remove(key);count.setText(chosen.size()+UiText.t(238));});
        all.setOnClickListener(v->{for(PersonalRecords.Row row:visible)chosen.add(row.id);refresh.run();});none.setOnClickListener(v->{chosen.clear();refresh.run();});
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}public void afterTextChanged(android.text.Editable e){}});refresh.run();
        new AlertDialog.Builder(UiText.context(this)).setTitle(UiText.t(kind.equals("contacts")?24:kind.equals("calls")?229:230)).setView(layout)
            .setPositiveButton(UiText.t(36),(d,w)->{List<PersonalRecords.Row> rows=new ArrayList<>();for(PersonalRecords.Row row:result.rows)if(chosen.contains(row.id))rows.add(row);exportRecords(kind,rows);})
            .setNegativeButton(UiText.t(61),null).show();
    }
    private void exportRecords(String kind,List<PersonalRecords.Row> rows){
        if(rows.isEmpty()){groups.remove(kind);selectedRecords.remove(kind);categories();return;}
        busy=true;cancelled=false;loading(UiText.t(186));
        worker.execute(()->{try{File file=PersonalRecords.export(this,kind,rows,()->checkCancel());
            String name=kind.equals("contacts")?"contacts-selected.vcf":kind+"-selected-backup.json";String mime=kind.equals("contacts")?"text/x-vcard":"application/json";
            Set<String> ids=new HashSet<>();for(PersonalRecords.Row row:rows)ids.add(row.id);
            runOnUiThread(()->{if(isDestroyed())return;busy=false;groups.put(kind,new ArrayList<>(Collections.singletonList(new Item(Uri.fromFile(file),name,mime,file.length()))));selectedRecords.put(kind,ids);categories();});
        }catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=false;categories();alert(UiText.t(49),e instanceof SecurityException?UiText.t(235):UiText.t(244));});}});
    }
    private void prepareSelection(){
        if(countSelected()==0){toast(UiText.t(50));return;}
        if(countSelected()>MAX_FILES){toast(UiText.t(51));return;}
        selection.clear();Set<String> unique=new HashSet<>();for(List<Item> list:groups.values())for(Item i:list)if(unique.add(i.uri.toString()))selection.add(i);
        if(!senderPaired){connectPage();return;}
        if(selectedUsbChannel!=null){SecureChannel channel=selectedUsbChannel;selectedUsbChannel=null;usbMode=true;transferPage(true);List<Item> items=new ArrayList<>(selection);job(false,()->sendFiles(channel,items));}
        else if(pendingPeer!=null)send(pendingPeer);else connectPage();
    }
    private void connectPage(){
        senderPaired=false;if(selectedUsbChannel!=null){selectedUsbChannel=null;stopLink();}
        page("connect",UiText.t(52),UiText.t(278),1);
        body.addView(new Phones(this),new LinearLayout.LayoutParams(-1,dp(160)));gap(18);
        text(UiText.t(53),16,INK,false);gap(22);
        button(UiText.t(54),true,()->scan());
        button(UiText.t(29),false,()->home());
        TextView manual=text(UiText.t(58),13,MUTED,false);manual.setPadding(0,dp(14),0,0);manual.setOnClickListener(v->manualPairing());
    }
    private void pairedWifi(Pairing peer){
        if(busy)return;busy=true;cancelled=false;page("pairVerify",UiText.t(64),UiText.t(278),1);
        int token=linkGeneration;
        worker.execute(()->{boolean ok=false;for(String host:peer.hosts){try{checkCancel();socket=transferNetwork.getSocketFactory().createSocket();socket.connect(new InetSocketAddress(host,PORT),4500);socket.setSoTimeout(10000);new SecureChannel(socket.getInputStream(),socket.getOutputStream(),peer.key,false);ok=true;break;}catch(Exception ignored){}finally{try{if(socket!=null)socket.close();}catch(Exception ignored){}}}
            final boolean ready=ok;runOnUiThread(()->{if(isDestroyed()||token!=linkGeneration)return;busy=false;if(ready&&!cancelled){pendingPeer=peer;senderPaired=true;usbMode=false;categories();}else{connectPage();alert(UiText.t(62),UiText.t(63));}});});
    }
    private void scan(){if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);return;}startActivityForResult(new Intent(this,ScanActivity.class),SCAN);}
    private void manualPairing(){EditText e=new EditText(this);e.setHint("rebaritclone:v3:…");e.setTextDirection(View.TEXT_DIRECTION_LTR);new AlertDialog.Builder(UiText.context(this)).setTitle(UiText.t(59)).setView(e).setPositiveButton(UiText.t(60),(d,w)->usePair(e.getText().toString())).setNegativeButton(UiText.t(61),null).show();}
    private void usePair(String value){
        try{Pairing peer=Pairing.parse(value);pendingPeer=peer;
            if(!peer.ssid.isEmpty()&&Build.VERSION.SDK_INT>=29)wifiPermission(()->connectHotspot(peer));
            else {transferNetwork=hotspot.currentWifi();if(transferNetwork==null)manualJoin(peer);else pairedWifi(peer);}
        }catch(Exception e){alert(UiText.t(62),UiText.t(63));}
    }
    private void wifiPermission(Runnable action){
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
        if(checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED){action.run();return;}
        afterWifiPermission=action;
        requestPermissions(Build.VERSION.SDK_INT>=33?new String[]{permission}:new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,permission},HOTSPOT_PERMISSION);
    }
    private void connectHotspot(Pairing peer){
        linking=true;int token=++linkGeneration;
        page("link",UiText.t(64),UiText.t(65),1);
        text(peer.ssid,19,BLUE,true);button(UiText.t(66),false,()->{stopLink();manualJoin(peer);});button(UiText.t(29),false,()->{stopLink();connectPage();});
        hotspot.connect(peer,new HotspotLink.ClientCallback(){
            public void ready(Network network){if(isDestroyed()||token!=linkGeneration||busy)return;linking=false;transferNetwork=network;pairedWifi(peer);}
            public void failed(){if(isDestroyed()||token!=linkGeneration)return;if(busy){closeNetwork();return;}linking=false;manualJoin(peer);}
        });
    }
    private void manualJoin(Pairing peer){
        page("manual",UiText.t(67),UiText.t(68),1);
        if(!peer.ssid.isEmpty()){text(UiText.t(69)+peer.ssid,17,INK,true);gap(12);text(UiText.t(70)+peer.password,16,BLUE,false).setTextIsSelectable(true);}
        button(UiText.t(71),true,()->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        button(UiText.t(72),true,()->{transferNetwork=hotspot.currentWifi();if(transferNetwork==null)toast(UiText.t(73));else pairedWifi(peer);});
        button(UiText.t(29),false,()->connectPage());
    }
    private void stopLink(){linkGeneration++;linking=false;hotspot.close();if(usb!=null)usb.close();transferNetwork=null;}
    private void beginHotspot(){
        if(busy||linking)return;
        if(Build.VERSION.SDK_INT<29&&folder==null){resumeFolder=true;chooseFolder();return;}
        wifiPermission(()->{
            android.net.wifi.WifiManager wm=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            if(!wm.isWifiEnabled()){startActivityForResult(new Intent(Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_WIFI:Settings.ACTION_WIFI_SETTINGS),WIFI_SETTINGS);return;}
            if(Build.VERSION.SDK_INT<33&&!locationEnabled()){startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),LOCATION_SETTINGS);return;}
            lastHotspotError="";linking=true;int token=++linkGeneration;
            main.postDelayed(()->{if(!isDestroyed()&&token==linkGeneration&&linking&&!busy){lastHotspotError="HOTSPOT_TIMEOUT";hotspotFailure();}},45000);
            page("hotspot",UiText.t(74),UiText.t(75),1);
            button(UiText.t(29),false,()->{stopLink();receiverSetup();});
            hotspot.start(fastMode,new HotspotLink.HostCallback(){
                public void ready(String ssid,String password){if(isDestroyed()||token!=linkGeneration)return;hotspotSsid=ssid;hotspotPassword=password;waitForAddress(token,0);}
                public void failed(String reason){if(isDestroyed()||token!=linkGeneration)return;lastHotspotError=reason;linking=false;if(busy){closeNetwork();return;}hotspotFailure();}
            });
        });
    }
    private boolean locationEnabled(){
        android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
        return Build.VERSION.SDK_INT>=28?lm.isLocationEnabled():lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)||lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
    }
    private void hotspotFailure(){
        stopLink();hotspotSsid="";hotspotPassword="";
        page("receiver",fastMode?UiText.t(76):UiText.t(77),UiText.t(78),1);
        note(lastHotspotError+" • "+Build.MANUFACTURER+" "+Build.MODEL+" • Android "+Build.VERSION.RELEASE);
        button(UiText.t(79),true,()->beginHotspot());
        button(UiText.t(80),false,()->{fastMode=false;beginHotspot();});
        button(UiText.t(81),false,()->manualHost());
        button(UiText.t(29),false,()->home());
    }
    private void waitForAddress(int token,int attempt){
        if(isDestroyed()||token!=linkGeneration)return;
        if(!wifiAddresses().isEmpty()){linking=false;receive();return;}
        if(attempt>=15){lastHotspotError="LOCAL_ADDRESS_NOT_FOUND";stopLink();manualHost();return;}
        main.postDelayed(()->waitForAddress(token,attempt+1),1000);
    }
    private void manualHost(){
        stopLink();hotspotSsid="";hotspotPassword="";
        page("manualhost",UiText.t(83),UiText.t(84),1);
        if(!lastHotspotError.isEmpty())note(UiText.t(85)+lastHotspotError+"\n"+Build.MANUFACTURER+" "+Build.MODEL+" • Android "+Build.VERSION.RELEASE);
        button(UiText.t(86),true,()->{try{startActivity(new Intent("android.settings.TETHER_SETTINGS"));}catch(Exception e){startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}});
        button(UiText.t(87),true,()->{if(Build.VERSION.SDK_INT<29&&folder==null)chooseFolder();else receive();});
        note(UiText.t(88));
        button(UiText.t(29),false,()->receiverSetup());
    }
    private void decodeImage(Uri uri){
        busy=true;loading(UiText.t(89));worker.execute(()->{
            try{
                BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opt);}
                opt.inSampleSize=1;while(opt.outWidth/opt.inSampleSize>1600||opt.outHeight/opt.inSampleSize>1600)opt.inSampleSize*=2;opt.inJustDecodeBounds=false;
                Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,opt);}if(bitmap==null)throw new IOException("QR");
                int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);bitmap.recycle();
                Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.TRY_HARDER,true);hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));
                String payload=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels))),hints).getText();Pairing.parse(payload);
                runOnUiThread(()->{if(isDestroyed())return;busy=false;usePair(payload);});
            }catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=false;connectPage();alert(UiText.t(90),UiText.t(91));});}
        });
    }
    private void startUsb(boolean receiving){
        if(busy)return;
        if(receiving&&Build.VERSION.SDK_INT<29&&folder==null){resumeUsbFolder=true;chooseFolder();return;}
        stopLink();usbMode=true;linking=true;int token=++linkGeneration;
        page("usb",UiText.t(92),UiText.t(93),1);
        body.addView(new CableArt(this),new LinearLayout.LayoutParams(-1,dp(150)));gap(12);
        summaryCard(receiving?UiText.t(94):UiText.t(95));
        note(UiText.t(96));
        status=summaryCard(UiText.t(97));status.setTextColor(BLUE);
        button(UiText.t(134),true,()->startUsb(receiving));
        button(UiText.t(29),false,()->{stopLink();if(receiving)usbReceiverSetup();else connectPage();});
        usb.start(receiving,new UsbLink.Listener(){
            public void ready(InputStream in,OutputStream out){
                if(isDestroyed()||token!=linkGeneration)return;linking=false;
                if(!receiving){busy=true;worker.execute(()->{try{SecureChannel channel=UsbSession.open(in,out,false);runOnUiThread(()->{if(isDestroyed()||token!=linkGeneration)return;busy=false;selectedUsbChannel=channel;senderPaired=true;categories();});}catch(Exception e){runOnUiThread(()->{if(isDestroyed()||token!=linkGeneration)return;busy=false;connectPage();alert(UiText.t(98),UiText.t(165));});}});return;}
                page("usbWaiting",UiText.t(97),UiText.t(280),1);button(UiText.t(111),false,()->cancel());
                job(true,()->{SecureChannel channel=UsbSession.open(in,out,true);runOnUiThread(()->{if(!isDestroyed()&&!cancelled){page("selectionWaiting",UiText.t(279),UiText.t(280),2);button(UiText.t(111),false,()->cancel());}});receiveFiles(channel);});
            }
            public void failed(String reason){if(isDestroyed()||token!=linkGeneration)return;linking=false;alert(UiText.t(98),reason);}
        });
    }
    private void usbReceiverSetup(){
        if(busy)return;stopLink();usbMode=true;
        page("usbReceiver",UiText.t(102),UiText.t(273),1);
        body.addView(new CableArt(this),new LinearLayout.LayoutParams(-1,dp(180)));gap(18);
        summaryCard(UiText.t(274));gap(10);summaryCard(UiText.t(275));gap(10);summaryCard(UiText.t(276));gap(16);
        button(UiText.t(277),true,()->startUsb(true));
        note(UiText.t(96));
        button(UiText.t(29),false,()->home());
    }
    private static class CableArt extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        CableArt(Context c){super(c);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas c){float scale=Math.min(getWidth()/340f,getHeight()/175f);c.save();c.translate((getWidth()-340*scale)/2,(getHeight()-175*scale)/2);c.scale(scale,scale);
            p.setColor(0xffe9e8ff);c.drawRoundRect(0,0,340,175,24,24,p);
            for(int n=0;n<2;n++){float x=n==0?53:231;p.setColor(n==0?0xff627089:BLUE);c.drawRoundRect(x,18,x+56,126,10,10,p);p.setColor(Color.WHITE);c.drawRoundRect(x+5,24,x+51,117,6,6,p);p.setColor(0xffd5d9ed);c.drawRoundRect(x+18,28,x+38,32,2,2,p);p.setColor(n==0?0xff627089:BLUE);c.drawCircle(x+28,72,12,p);}
            p.setColor(BLUE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);Path cable=new Path();cable.moveTo(81,125);cable.lineTo(81,142);cable.cubicTo(81,165,259,165,259,142);cable.lineTo(259,125);c.drawPath(cable,p);p.setStyle(Paint.Style.FILL);c.drawRoundRect(73,121,89,132,3,3,p);c.drawRoundRect(251,121,267,132,3,3,p);p.setTextSize(16);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextAlign(Paint.Align.CENTER);c.drawText("USB-C",170,80,p);c.restore();}
    }
    private void receiverSetup(){
        page("receiver",UiText.t(99),UiText.t(100),1);
        body.addView(new Phones(this),new LinearLayout.LayoutParams(-1,dp(140)));gap(14);
        button(UiText.t(101),true,()->beginHotspot());
        button(UiText.t(103),false,()->manualHost());
        note(UiText.t(104));
        button(UiText.t(29),false,()->home());
    }
    private void chooseFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);startActivityForResult(i,FOLDER);}
    private List<String> wifiAddresses(){
        LinkedHashSet<String> values=new LinkedHashSet<>();Set<String> clientInterfaces=new HashSet<>();
        if(hotspot!=null&&Pairing.local(hotspot.hostAddress()))values.add(hotspot.hostAddress());
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            for(Network n:cm.getAllNetworks()){NetworkCapabilities cap=cm.getNetworkCapabilities(n);LinkProperties lp=cm.getLinkProperties(n);
                if(cap!=null&&lp!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)){
                    clientInterfaces.add(lp.getInterfaceName());
                    if(hotspotSsid.isEmpty())for(LinkAddress la:lp.getLinkAddresses())if(la.getAddress() instanceof Inet4Address&&Pairing.local(la.getAddress().getHostAddress()))values.add(la.getAddress().getHostAddress());
                }
            }
            for(NetworkInterface ni:Collections.list(NetworkInterface.getNetworkInterfaces())){
                String name=ni.getName().toLowerCase(Locale.ROOT);
                if(!hotspotSsid.isEmpty()&&clientInterfaces.contains(ni.getName()))continue;
                if(!ni.isUp()||!(name.startsWith("wlan")||name.startsWith("swlan")||name.startsWith("ap")||name.startsWith("wlp")||name.startsWith("p2p")||name.startsWith("br")))continue;
                for(InetAddress a:Collections.list(ni.getInetAddresses()))if(a instanceof Inet4Address&&Pairing.local(a.getHostAddress()))values.add(a.getHostAddress());
            }
        }catch(Exception ignored){}List<String> result=new ArrayList<>(values);return result.subList(0,Math.min(result.size(),8));
    }
    private void qrPage(String payload){
        page("qr",UiText.t(105),UiText.t(106),1);
        try{
            int side=660;BitMatrix matrix=new QRCodeWriter().encode(payload,BarcodeFormat.QR_CODE,side,side);int[] pixels=new int[side*side];for(int y=0;y<side;y++)for(int x=0;x<side;x++)pixels[y*side+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            Bitmap bitmap=Bitmap.createBitmap(pixels,side,side,Bitmap.Config.ARGB_8888);ImageView qr=new ImageView(this);qr.setImageBitmap(bitmap);qr.setContentDescription(UiText.t(107));qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setBackground(box(Color.WHITE,24));qr.setPadding(dp(8),dp(8),dp(8),dp(8));
            int width=Math.min(dp(280),getResources().getDisplayMetrics().widthPixels-dp(48));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,width);lp.gravity=Gravity.CENTER;body.addView(qr,lp);
        }catch(Exception e){throw new IllegalStateException(e);}
        gap(22);status=text(UiText.t(108),17,BLUE,true);status.setGravity(Gravity.CENTER);
        Switch mode=new Switch(this);mode.setText(UiText.t(109));mode.setChecked(fastMode);body.addView(mode);
        mode.setOnCheckedChangeListener((v,on)->{fastMode=on;getPreferences(0).edit().putBoolean("fast5",on).apply();restartAfterStop=true;cancel();});
        note(hotspot.hostInfo()+UiText.t(110));
        button(UiText.t(111),false,()->cancel());
        TextView copy=text(UiText.t(112),13,MUTED,false);copy.setGravity(Gravity.CENTER);copy.setPadding(0,dp(16),0,0);
        copy.setOnClickListener(v->{android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData clip=ClipData.newPlainText("rebaritclone pairing",payload);if(Build.VERSION.SDK_INT>=33){PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}c.setPrimaryClip(clip);toast(UiText.t(113));});
    }
    private void transferPage(boolean sending){
        lastNotification=0;transferNotification(sending?UiText.t(114):UiText.t(115),-1,false);
        page("transfer",sending?UiText.t(114):UiText.t(115),UiText.t(116),3);

        gap(16);percentage=text(UiText.t(118),64,BLUE,true);percentage.setGravity(Gravity.CENTER);gap(20);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xffe0e4f1));progress.setProgressTintList(ColorStateList.valueOf(BLUE));body.addView(progress,new LinearLayout.LayoutParams(-1,dp(14)));gap(26);
        status=text(UiText.t(119),17,INK,true);status.setVisibility(View.GONE);details=summaryCard(UiText.t(120));details.setLineSpacing(dp(10),1);gap(24);
        button(UiText.t(121),false,()->new AlertDialog.Builder(UiText.context(this)).setTitle(UiText.t(122)).setMessage(UiText.t(123)).setPositiveButton(UiText.t(111),(d,w)->cancel()).setNegativeButton(UiText.t(124),null).show());
    }
    private void resultPage(boolean success,String message,boolean receiving){
        transferNotification(success?UiText.t(125):UiText.t(126),0,true);
        page("result",success?UiText.t(125):UiText.t(126),message,0);gap(24);
        TextView icon=text(success?"✓":"!",80,success?0xff12a875:0xffd68122,true);icon.setGravity(Gravity.CENTER);gap(20);
        text(completed+UiText.t(127),22,INK,true).setGravity(Gravity.CENTER);note(UiText.t(128));
        if(transferStats!=null){
            summaryCard(UiText.t(253)+(transferStats.total(0)-transferStats.remaining(0,completed)));gap(8);
            summaryCard(UiText.t(254)+(transferStats.total(1)-transferStats.remaining(1,completed)));gap(8);
            long seconds=transferStarted==0?0:Math.max(0,(SystemClock.elapsedRealtime()-transferStarted)/1000);
            text(UiText.t(255)+String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60),20,INK,true);
        }
        if(receiving&&!receivedFiles.isEmpty())button(UiText.t(256),true,()->showReceivedFiles());
        if(receiving&&receivedRecordBackup)note(UiText.t(231));
        if(receiving&&!receivedApps.isEmpty())button(UiText.t(129),true,()->installReceivedApps());
        if(receiving&&completed>0){note(Build.VERSION.SDK_INT>=29?UiText.t(130):UiText.t(131));
            if(lastContact!=null)button(UiText.t(132),true,()->importContacts());
        }
        button(UiText.t(133),false,()->home());
        if(!success)button(UiText.t(134),false,()->{if(receiving){if(usbMode)startUsb(true);else beginHotspot();}else connectPage();});
        if(!success)note(UiText.t(135));
    }
    private void showReceivedFiles(){
        List<Item> files=new ArrayList<>(receivedFiles.values());String[] names=new String[files.size()];
        for(int n=0;n<files.size();n++)names[n]=files.get(n).name;
        new AlertDialog.Builder(UiText.context(this)).setTitle(UiText.t(256)).setItems(names,(dialog,index)->{
            Item item=files.get(index);
            if(item.name.toLowerCase(Locale.ROOT).endsWith(".rbapp")||item.name.toLowerCase(Locale.ROOT).endsWith(".apk")){
                Intent install=new Intent(this,AppInstallActivity.class);install.putStringArrayListExtra("uris",new ArrayList<>(Collections.singletonList(item.uri.toString())));install.putStringArrayListExtra("names",new ArrayList<>(Collections.singletonList(item.name)));startActivity(install);return;
            }
            try{Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(item.uri,item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);view.setClipData(ClipData.newRawUri("received file",item.uri));startActivity(view);}
            catch(ActivityNotFoundException|SecurityException e){alert(UiText.t(256),UiText.t(257));}
        }).setNegativeButton(UiText.t(29),null).show();
    }
    private void importContacts(){
        try{Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(lastContact,"text/x-vcard").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri("contacts",lastContact));startActivity(i);}
        catch(Exception e){alert(UiText.t(132),UiText.t(136));}
    }
    private void tell(String value){runOnUiThread(()->{if(!isDestroyed()&&status!=null)status.setText(value);});}
    private void resetTotals(List<Item> items){if(transferStarted==0)transferStarted=SystemClock.elapsedRealtime();transferStats=stats(items);totalBytes=transferStats.totalBytes;finishedBytes=0;resumedBytes=0;totalKnown=transferStats.totalKnown;lastUpdate=0;startTime=SystemClock.elapsedRealtime();}
    private String eta(long remaining,double rate,long elapsed){
        if(remaining==0)return UiText.t(137);if(remaining<0||rate<=0||elapsed<3000)return UiText.t(138);
        long seconds=(long)Math.ceil(remaining/rate);return seconds>=3600?(seconds/3600)+UiText.t(139)+(seconds%3600/60)+UiText.t(140):seconds>=60?(seconds/60)+UiText.t(141)+(seconds%60)+UiText.t(142):seconds+UiText.t(142);
    }
    private void updateProgress(int index,int count,Item item,long bytes,boolean force){
        long now=SystemClock.elapsedRealtime();if(!force&&now-lastUpdate<200)return;lastUpdate=now;long moved=finishedBytes+bytes;
        int done=Math.min(count,index-1+(force?1:0));
        int percent=totalKnown&&totalBytes>0?(int)Math.min(done==count?1000:999,moved*1000.0/totalBytes):(int)(done*1000.0/Math.max(1,count));
        long remaining=transferStats.remainingBytes(moved),elapsed=now-startTime;
        double rate=Math.max(0,moved-resumedBytes)/Math.max(1,elapsed/1000.0);
        String all=TransferStats.gb(totalBytes)+(totalKnown?"":UiText.t(143));
        String info=UiText.t(144)+all+UiText.t(145)+TransferStats.gb(moved)+
            UiText.t(155)+String.format(Locale.US,"%.1f MB/s",rate/1000000.0)+UiText.t(156)+(remaining==0&&done<count?UiText.t(157):eta(remaining,rate,elapsed));
        final String display=info;
        runOnUiThread(()->{if(!screen.equals("transfer")||isDestroyed())return;transferNotification((percent/10)+"% • "+done+" / "+count,percent,false);progress.setProgress(percent);percentage.setText((percent/10)+UiText.t(158));status.setText(index+" / "+count+"  •  "+item.name);details.setText(display);});
    }
    interface Work{void run()throws Exception;}
    private void job(boolean receiving,Work work){
        if(busy)return;busy=true;cancelled=false;completed=0;lastContact=null;transferStats=null;transferStarted=0;hotspot.performance(fastMode&&!usbMode);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        worker.execute(()->{boolean ok=false;String message;
            try{checkCancel();work.run();ok=true;message=UiText.t(159);}
            catch(Exception e){message=cancelled?UiText.t(160):friendly(e);}
            finally{closeNetwork();}
            final boolean success=ok;final String result=message;
            runOnUiThread(()->{if(isDestroyed())return;stopLink();busy=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(restartAfterStop){restartAfterStop=false;beginHotspot();}else{resultPage(success,result,receiving);if(success&&receiving&&!receivedApps.isEmpty())installReceivedApps();}});
        });
    }
    private String friendly(Exception e){
        if("SOURCE_CHANGED".equals(e.getMessage()))return UiText.t(161);
        if(e instanceof SecurityException)return UiText.t(162);
        if("TOO_MANY".equals(e.getMessage()))return UiText.t(163);
        if("DECLINED".equals(e.getMessage()))return UiText.t(164);
        if(usbMode)return UiText.t(165);
        if(e instanceof java.net.SocketTimeoutException)return UiText.t(166);
        if(e instanceof java.net.ConnectException||"CONNECT".equals(e.getMessage()))return UiText.t(167);
        if("PAIR".equals(e.getMessage()))return UiText.t(168);
        if(e instanceof EOFException||e instanceof java.net.SocketException)return UiText.t(169);
        return UiText.t(170);
    }
    private void cancel(){cancelled=true;closeNetwork();CountDownLatch a=approval;if(a!=null)a.countDown();if(approvalDialog!=null)approvalDialog.dismiss();tell(UiText.t(171));}
    private void closeNetwork(){if(usb!=null)usb.closeStreams();try{if(socket!=null)socket.close();}catch(Exception ignored){}try{if(listener!=null)listener.close();}catch(Exception ignored){}}
    private void checkCancel()throws IOException{if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Cancelled");}
    private void expect(SecureChannel c,int expected)throws Exception{byte[] p=c.read();if(p.length!=1||p[0]!=expected)throw new IOException("DECLINED");}
    private void send(Pairing peer){
        if(busy)return;usbMode=false;
        transferPage(true);List<Item> items=new ArrayList<>(selection);
        job(false,()->{
            for(int attempt=0;;attempt++)try{
            if(items.isEmpty()||items.size()>MAX_FILES)throw new IOException("TOO_MANY");SecureChannel channel=null;
            for(String host:peer.hosts){
                checkCancel();try{socket=transferNetwork.getSocketFactory().createSocket();socket.setSendBufferSize(1048576);socket.setReceiveBufferSize(1048576);socket.connect(new InetSocketAddress(host,PORT),4500);socket.setSoTimeout(10000);checkCancel();channel=new SecureChannel(socket.getInputStream(),socket.getOutputStream(),peer.key,false);socket.setSoTimeout(180000);break;}
                catch(Exception e){try{socket.close();}catch(Exception ignored){}if(cancelled)throw e;}
            }
            if(channel==null)throw new IOException("CONNECT");
            sendFiles(channel,items);break;
            }catch(IOException e){if(cancelled||attempt>=2||!(e instanceof EOFException||e instanceof SocketException||e instanceof SocketTimeoutException||"CONNECT".equals(e.getMessage())))throw e;
                try{if(socket!=null)socket.close();}catch(Exception ignored){}tell(UiText.t(172));for(int wait=0;wait<20;wait++){checkCancel();Thread.sleep(100);}
            }
        });
    }
    private void sendFiles(SecureChannel channel,List<Item> items)throws Exception{
            if(items.isEmpty()||items.size()>MAX_FILES)throw new IOException("TOO_MANY");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(buffer);String sig=manifest(items,true);if(sendingId.isEmpty()||!sig.equals(sendingManifest)){sendingId=UUID.randomUUID().toString();sendingManifest=sig;}
            out.writeUTF(sendingId);out.writeInt(items.size());channel.write(buffer.toByteArray());
            for(Item item:items){checkCancel();buffer.reset();out.writeUTF(safeName(item.name));out.writeUTF(validMime(item.mime));out.writeLong(item.size);channel.write(buffer.toByteArray());}
            tell(UiText.t(173));expect(channel,1);resetTotals(items);completed=0;int index=0;
            for(Item item:items){checkCancel();index++;final int position=index;final boolean[] first={true};long bytes;
                try(InputStream in=new BufferedInputStream(openSource(item.uri),1048576)){
                    if(in==null)throw new IOException("Source unavailable");
                    bytes=ResumableFile.send(channel,in,item.size,fastMode?1048576:262144,n->{checkCancel();if(n<0)return;if(first[0]){resumedBytes+=n;first[0]=false;}updateProgress(position,items.size(),item,n,false);});
                }
                completed++;updateProgress(index,items.size(),item,bytes,true);finishedBytes+=bytes;
            }expect(channel,3);channel.write(new byte[]{4});sendingId="";sendingManifest="";
    }

    private InputStream openSource(Uri uri)throws Exception{return "file".equals(uri.getScheme())?new FileInputStream(new File(uri.getPath())):getContentResolver().openInputStream(uri);}
    private void receive(){
        if(busy)return;usbMode=false;
        List<String> hosts=wifiAddresses();if(hosts.isEmpty()){alert(UiText.t(174),UiText.t(175));return;}
        String code=SecureChannel.newCode();pairText=hotspotSsid.isEmpty()?Pairing.encode(hosts,code):Pairing.encodeHotspot(hosts,code,hotspotSsid,hotspotPassword);byte[] key=SecureChannel.parseCode(code);
        page("waiting",UiText.t(176),UiText.t(45),1);
        job(true,()->{
            listener=new ServerSocket();checkCancel();listener.setReuseAddress(true);listener.bind(new InetSocketAddress(PORT));listener.setSoTimeout(1000);
            runOnUiThread(()->{if(!isDestroyed()&&!cancelled)qrPage(pairText);});
            for(;;){
            SecureChannel channel=null;long deadline=SystemClock.elapsedRealtime()+600000;
            while(channel==null){checkCancel();if(SystemClock.elapsedRealtime()>deadline)throw new SocketTimeoutException();
                try{socket=listener.accept();socket.setReceiveBufferSize(1048576);socket.setSendBufferSize(1048576);socket.setSoTimeout(10000);channel=new SecureChannel(socket.getInputStream(),socket.getOutputStream(),key,true);}
                catch(SocketTimeoutException e){if(socket!=null)try{socket.close();}catch(Exception ignored){}}
                catch(Exception e){checkCancel();if(socket!=null)try{socket.close();}catch(Exception ignored){}}
            }
            socket.setSoTimeout(180000);checkCancel();
            runOnUiThread(()->{if(!isDestroyed()&&!cancelled){page("selectionWaiting",UiText.t(279),UiText.t(280),2);button(UiText.t(111),false,()->cancel());}});
            try{receiveFiles(channel);break;}
            catch(IOException e){if(cancelled||listener.isClosed()||!(e instanceof EOFException||e instanceof SocketException||e instanceof SocketTimeoutException))throw e;
                try{socket.close();}catch(Exception ignored){}tell(UiText.t(177));
            }
            }
        });
    }
    private void receiveFiles(SecureChannel channel)throws Exception{
            DataInputStream header=new DataInputStream(new ByteArrayInputStream(channel.read()));String session=header.readUTF();if(!session.matches("[a-f0-9-]{36}"))throw new IOException("Invalid session");int count=header.readInt();if(count<1||count>MAX_FILES||header.available()!=0)throw new IOException("TOO_MANY");
            List<Item> items=new ArrayList<>();
            for(int n=0;n<count;n++){checkCancel();DataInputStream meta=new DataInputStream(new ByteArrayInputStream(channel.read()));Item i=new Item();i.name=safeName(meta.readUTF());i.mime=validMime(meta.readUTF());i.size=meta.readLong();if(i.size< -1||meta.available()!=0)throw new IOException("Invalid metadata");items.add(i);}
            if(!(session.equals(receivingId)&&manifest(items,false).equals(receivingManifest))&&!confirm(items)){channel.write(new byte[]{0});throw new IOException("DECLINED");}checkCancel();
            String sig=manifest(items,false);
            if(!session.equals(receivingId)||!sig.equals(receivingManifest)){
                for(ResumableFile.Entry e:resumeEntries.values())e.partial.delete();resumeEntries.clear();receivedFiles.clear();receivedApps.clear();receivedRecordBackup=false;
                receivingId=session;receivingManifest=sig;receivingBatch=Long.toString(System.currentTimeMillis());receivingLegacy=null;
            }
            if(Build.VERSION.SDK_INT<29&&receivingLegacy==null){Uri parent=DocumentsContract.buildDocumentUriUsingTree(folder,DocumentsContract.getTreeDocumentId(folder));receivingLegacy=DocumentsContract.createDocument(getContentResolver(),parent,DocumentsContract.Document.MIME_TYPE_DIR,"rebaritclone-"+receivingBatch);if(receivingLegacy==null)throw new IOException("Folder unavailable");}
            channel.write(new byte[]{1});resetTotals(items);completed=0;runOnUiThread(()->{if(!isDestroyed())transferPage(false);});int index=0;
            for(Item item:items){checkCancel();index++;final int position=index;final boolean[] first={true};
                ResumableFile.Entry entry=resumeEntries.get(index);
                if(entry==null){entry=new ResumableFile.Entry(File.createTempFile("resume-",".part",getCacheDir()));resumeEntries.put(index,entry);}
                long bytes=ResumableFile.receive(channel,entry,item.size,n->{checkCancel();if(n<0)return;if(first[0]){resumedBytes+=n;first[0]=false;}updateProgress(position,count,item,n,false);},partial->{
                    SavedFile file=new SavedFile(item,receivingBatch,receivingLegacy);
                    try{
                        try(InputStream in=new BufferedInputStream(new FileInputStream(partial));OutputStream out=new BufferedOutputStream(getContentResolver().openOutputStream(file.uri,"w"),1048576)){
                            if(out==null)throw new IOException("Storage unavailable");byte[] data=new byte[1048576];int n;while((n=in.read(data))!=-1){checkCancel();out.write(data,0,n);}
                        }
                        file.commit(item.name);receivedFiles.put(file.uri.toString(),new Item(file.uri,item.name,item.mime,item.size));if(item.name.equals("calls-selected-backup.json")||item.name.equals("sms-selected-backup.json"))receivedRecordBackup=true;if(item.name.toLowerCase(Locale.ROOT).endsWith(".rbapp")||item.name.toLowerCase(Locale.ROOT).endsWith(".apk"))receivedApps.put(file.uri.toString(),item.name);if(item.name.toLowerCase(Locale.ROOT).endsWith(".vcf"))lastContact=file.uri;
                    }finally{if(!file.committed)file.discard();}
                });completed++;updateProgress(index,count,item,bytes,true);finishedBytes+=bytes;
            }channel.write(new byte[]{3});expect(channel,4);
    }

    private class SavedFile{
        Uri uri;boolean committed;
        SavedFile(Item item,String batch,Uri legacy)throws Exception{
            if(Build.VERSION.SDK_INT>=29){Uri collection;String directory;
                if(item.mime.startsWith("image/")){collection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;directory=Environment.DIRECTORY_PICTURES;}
                else if(item.mime.startsWith("video/")){collection=MediaStore.Video.Media.EXTERNAL_CONTENT_URI;directory=Environment.DIRECTORY_MOVIES;}
                else if(item.mime.startsWith("audio/")){collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;directory=Environment.DIRECTORY_MUSIC;}
                else{collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI;directory=Environment.DIRECTORY_DOWNLOADS;}
                ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,item.name);values.put(MediaStore.MediaColumns.MIME_TYPE,item.mime);values.put(MediaStore.MediaColumns.RELATIVE_PATH,directory+"/rebaritclone/"+batch);values.put(MediaStore.MediaColumns.IS_PENDING,1);uri=getContentResolver().insert(collection,values);
            }else uri=DocumentsContract.createDocument(getContentResolver(),legacy,"application/octet-stream",item.name+".partial");
            if(uri==null)throw new IOException("Storage unavailable");
        }
        void commit(String name)throws Exception{
            if(Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);if(getContentResolver().update(uri,values,null,null)!=1)throw new IOException("Finalize failed");}
            else{Uri renamed=DocumentsContract.renameDocument(getContentResolver(),uri,name);if(renamed==null)throw new IOException("Finalize failed");uri=renamed;}
            committed=true;
        }
        void discard(){try{if(Build.VERSION.SDK_INT>=29)getContentResolver().delete(uri,null,null);else DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}}
    }
    private boolean confirm(List<Item> items)throws InterruptedException{
        CountDownLatch latch=new CountDownLatch(1);approval=latch;boolean[] yes={false};long bytes=0;boolean unknown=false;
        for(Item i:items){if(i.size<0)unknown=true;else bytes+=i.size;}
        final long total=bytes;final boolean hasUnknown=unknown;
        runOnUiThread(()->{if(isDestroyed()||cancelled){latch.countDown();return;}
            Context themed=UiText.context(this);LinearLayout panel=new LinearLayout(themed);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(22),dp(22),dp(18));panel.setLayoutDirection(UiText.direction());
            TextView icon=label("↓",36,Color.WHITE,true);icon.setGravity(Gravity.CENTER);icon.setBackground(box(BLUE,20));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(68),dp(68));ip.gravity=Gravity.CENTER_HORIZONTAL;panel.addView(icon,ip);
            TextView title=label(UiText.t(281),23,INK,true);title.setGravity(Gravity.CENTER);title.setPadding(0,dp(16),0,dp(4));panel.addView(title);
            TextView sub=label(UiText.t(282),14,MUTED,false);sub.setGravity(Gravity.CENTER);panel.addView(sub);
            LinearLayout stats=new LinearLayout(themed);stats.setGravity(Gravity.CENTER);stats.setPadding(0,dp(20),0,dp(18));
            TextView count=label(items.size()+UiText.t(178),20,BLUE,true);count.setGravity(Gravity.CENTER);count.setPadding(dp(18),dp(14),dp(18),dp(14));count.setBackground(box(0xffeeeefe,16));stats.addView(count,new LinearLayout.LayoutParams(0,-2,1));
            Space between=new Space(themed);stats.addView(between,new LinearLayout.LayoutParams(dp(10),1));
            TextView size=label(TransferStats.gb(total)+(hasUnknown?UiText.t(179):""),20,BLUE,true);size.setGravity(Gravity.CENTER);size.setPadding(dp(18),dp(14),dp(18),dp(14));size.setBackground(box(0xffeeeefe,16));stats.addView(size,new LinearLayout.LayoutParams(0,-2,1));panel.addView(stats);
            LinearLayout files=new LinearLayout(themed);files.setOrientation(LinearLayout.VERTICAL);files.setPadding(dp(14),dp(8),dp(14),dp(8));files.setBackground(box(0xfff3f5fa,16));
            int shown=Math.min(3,items.size());for(int n=0;n<shown;n++){Item item=items.get(n);TextView row=label("•  "+item.name,14,INK,false);row.setTextDirection(View.TEXT_DIRECTION_LTR);row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setPadding(0,dp(6),0,dp(6));files.addView(row);}
            if(items.size()>shown){TextView more=label("+ "+(items.size()-shown)+" "+UiText.t(283),13,MUTED,true);more.setPadding(0,dp(6),0,dp(4));files.addView(more);}panel.addView(files);
            TextView prompt=label(UiText.t(180),15,INK,true);prompt.setGravity(Gravity.CENTER);prompt.setPadding(0,dp(18),0,dp(10));panel.addView(prompt);
            LinearLayout actions=new LinearLayout(themed);actions.setGravity(Gravity.CENTER);Button reject=new Button(themed);reject.setText(UiText.t(184));reject.setTextColor(BLUE);reject.setTextSize(16);reject.setAllCaps(false);reject.setTypeface(null,Typeface.BOLD);reject.setBackground(new RippleDrawable(ColorStateList.valueOf(0x184f46e5),box(0xffeaedff,17),null));actions.addView(reject,new LinearLayout.LayoutParams(0,dp(58),1));Space actionGap=new Space(themed);actions.addView(actionGap,new LinearLayout.LayoutParams(dp(10),1));Button receive=new Button(themed);receive.setText("↓  "+UiText.t(183));receive.setTextColor(Color.WHITE);receive.setTextSize(16);receive.setAllCaps(false);receive.setTypeface(null,Typeface.BOLD);receive.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff),box(BLUE,17),null));actions.addView(receive,new LinearLayout.LayoutParams(0,dp(58),1));panel.addView(actions);
            approvalDialog=new AlertDialog.Builder(themed).setView(panel).setCancelable(false).create();
            reject.setOnClickListener(v->{approvalDialog.dismiss();latch.countDown();});receive.setOnClickListener(v->{yes[0]=true;approvalDialog.dismiss();latch.countDown();});approvalDialog.show();
        });
        boolean ready=latch.await(150,TimeUnit.SECONDS);approval=null;runOnUiThread(()->{if(approvalDialog!=null)approvalDialog.dismiss();});return ready&&yes[0]&&!cancelled;
    }
    private static String safeName(String name){if(name==null)name="file";name=name.replaceAll("[\\p{Cntrl}/\\\\]","_");if(name.isEmpty()||name.equals(".")||name.equals(".."))name="file";return name.length()>160?name.substring(0,160):name;}
    private static String validMime(String mime){return mime!=null&&mime.length()<128&&mime.matches("[a-zA-Z0-9.+_-]+/[a-zA-Z0-9.+_-]+")?mime:"application/octet-stream";}
    private Item inspect(Uri uri){Item item=new Item(uri,"file",validMime(getContentResolver().getType(uri)),-1);try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){item.name=safeName(c.getString(0));if(!c.isNull(1))item.size=c.getLong(1);}}return item;}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==SETUP_SETTINGS){if(setupActive)permissionSummary();return;}
        if(request==WIFI_SETTINGS||request==LOCATION_SETTINGS){
            android.net.wifi.WifiManager wm=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            if((request==WIFI_SETTINGS&&wm.isWifiEnabled())||(request==LOCATION_SETTINGS&&locationEnabled()))beginHotspot();else{receiverSetup();note(UiText.t(185));}return;
        }
        if(request==FOLDER&&result!=RESULT_OK){resumeFolder=false;if(resumeUsbFolder){resumeUsbFolder=false;usbReceiverSetup();}else receiverSetup();return;}
        if(result!=RESULT_OK||data==null)return;
        if(request==FOLDER&&data.getData()!=null){folder=data.getData();if(resumeUsbFolder){resumeUsbFolder=false;startUsb(true);}else if(resumeFolder){resumeFolder=false;beginHotspot();}else receiverSetup();}
        if(request==SCAN){usePair(data.getStringExtra("pairing"));}
        if(request==QR_IMAGE&&data.getData()!=null)decodeImage(data.getData());
        if(request==FILES){List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int n=0;n<data.getClipData().getItemCount();n++)uris.add(data.getClipData().getItemAt(n).getUri());}else if(data.getData()!=null)uris.add(data.getData());
            busy=true;cancelled=false;loading(UiText.t(186));worker.execute(()->{try{List<Item> list=new ArrayList<>();for(Uri uri:uris){checkCancel();list.add(inspect(uri));}runOnUiThread(()->{if(isDestroyed())return;busy=false;groups.put("files",list);categories();});}catch(Exception e){runOnUiThread(()->{if(isDestroyed())return;busy=false;categories();alert(UiText.t(187),friendly(e));});}});
        }
    }
    @Override public void onBackPressed(){if(setupActive){setupActive=false;setupWaiting=false;home();return;}if(linking){stopLink();home();return;}if(busy){new AlertDialog.Builder(UiText.context(this)).setMessage(UiText.t(188)).setPositiveButton(UiText.t(189),(d,w)->cancel()).setNegativeButton(UiText.t(190),null).show();}else if(screen.equals("home"))super.onBackPressed();else if(screen.equals("connect"))home();else home();}
    @Override protected void onDestroy(){if(onlineUpdate!=null)onlineUpdate.destroy();closeGallery();NotificationManager manager=getSystemService(NotificationManager.class);if(manager!=null&&busy)manager.cancel(74);stopLink();cancel();usb.destroy();worker.shutdownNow();super.onDestroy();}
    private static class Phones extends View{
        Paint p=new Paint(3);Phones(Context c){super(c);setContentDescription(UiText.t(191));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float scale=Math.min(getWidth()/340f,getHeight()/180f);c.save();c.translate((getWidth()-340*scale)/2,(getHeight()-180*scale)/2);c.scale(scale,scale);
            p.setColor(0xffe8efff);c.drawCircle(170,90,84,p);phone(c,55,23,0xffd6e5ff);phone(c,207,8,0xff2563eb);
            p.setColor(BLUE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(147,85,193,85,p);c.drawLine(185,77,193,85,p);c.drawLine(185,93,193,85,p);
            p.setColor(0xff99baf9);c.drawLine(147,104,193,104,p);c.drawLine(147,104,155,96,p);c.drawLine(147,104,155,112,p);c.restore();}
        void phone(Canvas c,float x,float y,int color){p.setColor(color);c.drawRoundRect(x,y,x+77,y+140,14,14,p);p.setColor(Color.WHITE);c.drawRoundRect(x+7,y+8,x+70,y+131,10,10,p);p.setColor(INK);c.drawRoundRect(x+28,y+10,x+49,y+15,3,3,p);p.setColor(0xffeaf1ff);c.drawRoundRect(x+14,y+32,x+63,y+75,8,8,p);p.setColor(0xff93b7f6);c.drawRoundRect(x+14,y+86,x+53,y+92,3,3,p);p.setColor(0xffdbe6f8);c.drawRoundRect(x+14,y+100,x+61,y+106,3,3,p);}
    }
}
