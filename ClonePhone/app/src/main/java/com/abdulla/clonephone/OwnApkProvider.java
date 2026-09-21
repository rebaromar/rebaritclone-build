package com.abdulla.clonephone;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only access to the installed base APK for the Android share sheet. */
public final class OwnApkProvider extends ContentProvider {
    private static final String TYPE="application/vnd.android.package-archive";

    private File file(Uri uri) throws FileNotFoundException {
        if(uri==null||!"content".equals(uri.getScheme())||getContext()==null
                ||!(getContext().getPackageName()+".share").equals(uri.getAuthority())
                ||!("/apk".equals(uri.getPath())||"/update".equals(uri.getPath())))throw new FileNotFoundException("Unknown APK");
        File apk="/update".equals(uri.getPath())?new File(getContext().getFilesDir(),"online-update.apk"):new File(getContext().getApplicationInfo().sourceDir);
        if(!apk.isFile())throw new FileNotFoundException("Installed APK is unavailable");
        return apk;
    }

    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){try{file(uri);return TYPE;}catch(FileNotFoundException e){return null;}}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{
            File apk=file(uri);
            String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor result=new MatrixCursor(cols);
            Object[] row=new Object[cols.length];
            for(int i=0;i<cols.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]="REBAR-IT-Clone-"+getContext().getPackageManager().getPackageInfo(getContext().getPackageName(),0).versionName+".apk";
                else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=apk.length();
            }
            result.addRow(row);return result;
        }catch(Exception e){return null;}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){throw new UnsupportedOperationException("Read only");}
}
