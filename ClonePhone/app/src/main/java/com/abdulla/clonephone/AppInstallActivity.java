package com.abdulla.clonephone;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Foreground sequential installer. Android owns every install/update approval. */
@SuppressWarnings("deprecation")
public class AppInstallActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ArrayList<String> uris,names;
    private int index;
    private volatile int sessionId=-1;
    private volatile boolean destroyed;
    private boolean working,resumed,permissionPending,startPending;
    private Intent approvalIntent;
    private PendingIntent callback;
    private TextView state,position,history;
    private int installed,skipped;
    private final StringBuilder results=new StringBuilder();
    private Button retry,next;
    private String action;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        if(!action.equals(intent.getAction())||sessionId<0||intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2)!=sessionId)return;
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            approvalIntent=intent.getParcelableExtra(Intent.EXTRA_INTENT);state.setText(UiText.t(197));
            if(approvalIntent==null){fail(UiText.t(198));return;}launchApproval();return;
        }
        sessionId=-1;working=false;if(callback!=null){callback.cancel();callback=null;}
        finishItem(status==PackageInstaller.STATUS_SUCCESS,status==PackageInstaller.STATUS_SUCCESS?UiText.t(199):reason(status));
    }};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);UiText.init(this);
        uris=getIntent().getStringArrayListExtra("uris");names=getIntent().getStringArrayListExtra("names");
        if(uris==null||names==null||uris.size()!=names.size()||uris.isEmpty()){finish();return;}
        action=getPackageName()+".INSTALL."+UUID.randomUUID();
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,new IntentFilter(action),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,new IntentFilter(action));
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(40,50,40,30);box.setLayoutDirection(UiText.direction());box.setBackgroundColor(0xfff5f7fb);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
        TextView title=new TextView(this);title.setText(UiText.t(129));title.setTextSize(25);box.addView(title);
        position=new TextView(this);position.setTextSize(19);position.setPadding(0,30,0,30);box.addView(position);
        state=new TextView(this);state.setTextSize(16);box.addView(state);
        retry=new Button(this);retry.setText(UiText.t(200));box.addView(retry);retry.setOnClickListener(v->startCurrent());
        next=new Button(this);next.setText(UiText.t(201));box.addView(next);next.setOnClickListener(v->finishItem(false,UiText.t(202)));
        Button stop=new Button(this);stop.setText(UiText.t(29));box.addView(stop);stop.setOnClickListener(v->onBackPressed());
        TextView note=new TextView(this);note.setText(UiText.t(203));box.addView(note);
        history=new TextView(this);history.setTextSize(14);history.setPadding(0,25,0,0);box.addView(history);
        if(saved!=null){installed=saved.getInt("installed",0);skipped=saved.getInt("skipped",0);results.append(saved.getString("results",""));history.setText(results.toString());index=saved.getInt("index",0);int old=saved.getInt("session",-1);if(old>=0)try{getPackageManager().getPackageInstaller().abandonSession(old);}catch(Exception ignored){}state.setText(UiText.t(204));}
        else startPending=true;
        showPosition();
    }
    private void finishItem(boolean success,String message){
        if(destroyed||index>=uris.size())return;
        abandon();working=false;approvalIntent=null;
        if(success)installed++;else skipped++;
        results.append(success?"✓ ":"— ").append(names.get(index)).append(": ").append(message).append("\n\n");history.setText(results.toString());
        index++;showPosition();startPending=index<uris.size();advance();
    }
    private void showPosition(){if(index>=uris.size()){state.setText(UiText.t(205));position.setText(installed+UiText.t(206)+skipped+UiText.t(207));retry.setEnabled(false);next.setEnabled(false);startPending=false;return;}position.setText((index+1)+" / "+uris.size()+"\n"+names.get(index));retry.setEnabled(!working);next.setEnabled(!working);}
    @Override protected void onResume(){super.onResume();resumed=true;launchApproval();advance();}
    @Override protected void onPause(){resumed=false;super.onPause();}
    private void advance(){if(resumed&&startPending&&!working){startPending=false;startCurrent();}}
    private void launchApproval(){if(resumed&&approvalIntent!=null){Intent i=approvalIntent;approvalIntent=null;try{startActivity(i);}catch(Exception e){fail(UiText.t(208));}}}
    private void startCurrent(){
        if(working||index>=uris.size()||destroyed)return;
        if(!getPackageManager().canRequestPackageInstalls()){
            state.setText(UiText.t(209));
            permissionPending=true;try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),42);}catch(Exception e){permissionPending=false;fail(UiText.t(210));}return;
        }
        working=true;showPosition();state.setText(UiText.t(211));
        final String value=uris.get(index),name=names.get(index);
        worker.execute(()->{int created=-1;boolean committed=false;
            try{
                PackageInstaller installer=getPackageManager().getPackageInstaller();PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                created=installer.createSession(params);sessionId=created;if(destroyed)throw new InterruptedIOException();
                try(PackageInstaller.Session session=installer.openSession(created);InputStream source=getContentResolver().openInputStream(Uri.parse(value))){
                    if(source==null)throw new IOException("File unavailable");
                    if(name.toLowerCase(Locale.ROOT).endsWith(".rbapp"))AppBundle.read(source,(part,in)->stage(session,part,in));else stage(session,"base.apk",source);
                    if(destroyed||Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                    Intent result=new Intent(action).setPackage(getPackageName());int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                    callback=PendingIntent.getBroadcast(this,created,result,flags);session.commit(callback.getIntentSender());committed=true;
                }
            }catch(Exception e){if(created>=0)try{getPackageManager().getPackageInstaller().abandonSession(created);}catch(Exception ignored){}sessionId=-1;
                runOnUiThread(()->{if(!destroyed)finishItem(false,UiText.t(212)+e.getMessage());});
            }finally{if(!committed&&callback!=null){callback.cancel();callback=null;}}
        });
    }
    private void stage(PackageInstaller.Session session,String name,InputStream in)throws Exception{
        try(OutputStream out=session.openWrite(name,0,-1)){byte[] data=new byte[1048576];int n;long total=0;
            while((n=in.read(data))!=-1){if(destroyed||Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>AppBundle.MAX_BYTES)throw new IOException("App too large");out.write(data,0,n);}session.fsync(out);
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42&&permissionPending){permissionPending=false;if(getPackageManager().canRequestPackageInstalls()){startPending=true;advance();}else fail(UiText.t(213));}}
    private void fail(String message){abandon();working=false;state.setText(message);retry.setEnabled(index<uris.size());next.setEnabled(index<uris.size());}
    private String reason(int status){switch(status){case PackageInstaller.STATUS_FAILURE_ABORTED:return UiText.t(214);case PackageInstaller.STATUS_FAILURE_STORAGE:return UiText.t(215);case PackageInstaller.STATUS_FAILURE_CONFLICT:return UiText.t(216);case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:return UiText.t(217);case PackageInstaller.STATUS_FAILURE_BLOCKED:return UiText.t(218);default:return UiText.t(219);}}
    private void abandon(){int id=sessionId;sessionId=-1;if(id>=0)try{getPackageManager().getPackageInstaller().abandonSession(id);}catch(Exception ignored){}if(callback!=null){callback.cancel();callback=null;}}
    @Override public void onBackPressed(){new AlertDialog.Builder(UiText.context(this)).setMessage(UiText.t(220)).setPositiveButton(UiText.t(29),(d,w)->{destroyed=true;abandon();finish();}).setNegativeButton(UiText.t(124),null).show();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("installed",installed);state.putInt("skipped",skipped);state.putString("results",results.toString());state.putInt("index",index);state.putInt("session",sessionId);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();abandon();try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onDestroy();}
}
