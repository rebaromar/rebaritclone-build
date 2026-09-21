package com.abdulla.clonephone;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public class ScanActivity extends Activity implements SurfaceHolder.Callback {
    private Camera camera;
    private SurfaceView preview;
    private TextView hint;
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final AtomicBoolean decoding=new AtomicBoolean();
    private volatile boolean done, active;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);UiText.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setLayoutDirection(UiText.direction());root.setBackgroundColor(Color.rgb(15,25,42));
        hint=new TextView(this);hint.setText(UiText.t(221));hint.setTextColor(Color.WHITE);hint.setTextSize(20);hint.setGravity(Gravity.CENTER);hint.setPadding(24,40,24,24);root.addView(hint);
        preview=new SurfaceView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));preview.getHolder().addCallback(this);
        TextView note=new TextView(this);note.setText(UiText.t(222));note.setTextColor(Color.WHITE);note.setGravity(Gravity.CENTER);note.setPadding(20,24,20,24);root.addView(note);
        Button back=new Button(this);back.setText(UiText.t(29));back.setOnClickListener(v->finish());root.addView(back);setContentView(root);
    }
    @Override protected void onResume(){super.onResume();active=true;if(preview.getHolder().getSurface().isValid())open();}
    @Override protected void onPause(){active=false;release();super.onPause();}
    @Override protected void onDestroy(){decoder.shutdownNow();super.onDestroy();}
    @Override public void surfaceCreated(SurfaceHolder h){if(active)open();}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder h){release();}
    private void open(){
        if(camera!=null||done)return;
        try{
            int id=0;Camera.CameraInfo info=new Camera.CameraInfo();
            for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK){id=i;break;}}
            Camera.getCameraInfo(id,info);camera=Camera.open(id);Camera.Parameters p=camera.getParameters();
            Camera.Size best=null;
            for(Camera.Size s:p.getSupportedPreviewSizes())if(s.width<=1280&&(best==null||s.width*s.height>best.width*best.height))best=s;
            if(best!=null)p.setPreviewSize(best.width,best.height);
            List<String> modes=p.getSupportedFocusModes();if(modes!=null&&modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            p.setPreviewFormat(android.graphics.ImageFormat.NV21);camera.setParameters(p);
            int rotation=getWindowManager().getDefaultDisplay().getRotation();int degrees=rotation==Surface.ROTATION_90?90:rotation==Surface.ROTATION_180?180:rotation==Surface.ROTATION_270?270:0;
            camera.setDisplayOrientation((info.orientation-degrees+360)%360);camera.setPreviewDisplay(preview.getHolder());
            camera.setPreviewCallback((data,c)->{
                if(done||!active||!decoding.compareAndSet(false,true))return;
                Camera.Size s=c.getParameters().getPreviewSize();byte[] frame=data.clone();
                try{decoder.execute(()->decode(frame,s.width,s.height));}catch(RejectedExecutionException e){decoding.set(false);}
            });camera.startPreview();
        }catch(Exception e){release();hint.setText(UiText.t(223));}
    }
    private void decode(byte[] data,int width,int height){
        try{
            MultiFormatReader reader=new MultiFormatReader();Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));
            Result result=reader.decode(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(data,width,height,0,0,width,height,false))),hints);
            Pairing.parse(result.getText());
            if(active&&!done){done=true;runOnUiThread(()->{setResult(RESULT_OK,new Intent().putExtra("pairing",result.getText()));finish();});}
        }catch(Exception ignored){}finally{decoding.set(false);}
    }
    private void release(){if(camera!=null){try{camera.setPreviewCallback(null);camera.stopPreview();camera.release();}catch(Exception ignored){}camera=null;}}
}
