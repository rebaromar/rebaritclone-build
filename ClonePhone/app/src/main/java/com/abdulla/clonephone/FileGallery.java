package com.abdulla.clonephone;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;

/** Session-local media picker; real MediaStore thumbnails and URI selection. */
final class FileGallery extends LinearLayout {
    static final class Entry {Uri uri;String name,mime;long size,date,duration;}
    interface Actions {void changed(List<Entry> selected);void tab(String name);void next();}
    private final Activity activity;private final Actions actions;
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor thumbs=new ThreadPoolExecutor(2,2,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String k,Bitmap v){return v.getByteCount();}};
    private final List<Entry> entries=new ArrayList<>();private final List<Object> rows=new ArrayList<>();
    private final LinkedHashMap<String,Entry> chosen=new LinkedHashMap<>();
    private final ListView list;private final TextView count;private final EditText search;private final CheckBox all;
    private final BaseAdapter adapter;private boolean closed,audio;private int filter,loadVersion;private String query="";
    private final int white=0xfff5f5fa,muted=0xffb4b4c2;private String warning="";
    FileGallery(Activity a,List<Entry> initial,String summary,Actions callback,String initialTab){
        super(a);activity=a;actions=callback;for(Entry e:initial)chosen.put(e.uri.toString(),e);
        audio="audio".equals(initialTab);filter="photos".equals(initialTab)?1:"videos".equals(initialTab)?2:0;
        setOrientation(VERTICAL);setLayoutDirection(UiText.direction());setBackgroundColor(0xff101827);
        HorizontalScrollView tabs=new HorizontalScrollView(a);LinearLayout tabRow=new LinearLayout(a);tabs.addView(tabRow);tabs.setHorizontalScrollBarEnabled(false);addView(tabs);
        String[] ids={"media","audio","files","apps","contacts"};int[] labels={265,20,26,22,24};
        List<TextView> tabViews=new ArrayList<>();
        for(int i=0;i<ids.length;i++){final String id=ids[i];TextView tab=text(UiText.t(labels[i]),16);tab.setPadding(dp(12),dp(16),dp(12),dp(16));tabRow.addView(tab);tabViews.add(tab);tab.setOnClickListener(v->{if(id.equals("media")||id.equals("audio")){audio=id.equals("audio");for(TextView t:tabViews)t.setTextColor(muted);tab.setTextColor(white);load();}else actions.tab(id);});}
        tabViews.get(audio?1:0).setTextColor(white);
        search=new EditText(a);search.setSingleLine(true);search.setTextColor(white);search.setHintTextColor(muted);search.setHint(UiText.t(236));search.setPadding(dp(14),0,dp(14),0);android.graphics.drawable.GradientDrawable searchBg=new android.graphics.drawable.GradientDrawable();searchBg.setColor(0xff202d43);searchBg.setCornerRadius(dp(14));search.setBackground(searchBg);addView(search,new LayoutParams(-1,dp(48)));
        LinearLayout tools=new LinearLayout(a);tools.setGravity(Gravity.CENTER_VERTICAL);addView(tools);
        TextView filterButton=text(UiText.t(266)+" ▾",16);tools.addView(filterButton,new LayoutParams(0,dp(52),1));
        filterButton.setOnClickListener(v->new AlertDialog.Builder(UiText.context(a)).setItems(new String[]{UiText.t(266),UiText.t(16),UiText.t(18)},(d,n)->{filter=n;filterButton.setText(UiText.t(n==0?266:n==1?16:18)+" ▾");rebuild();}).show());
        all=new CheckBox(a);all.setButtonTintList(android.content.res.ColorStateList.valueOf(0xffaaa4ff));all.setContentDescription(UiText.t(237));tools.addView(all);all.setOnClickListener(v->{boolean checked=all.isChecked();for(Entry e:visible())if(checked)chosen.put(e.uri.toString(),e);else chosen.remove(e.uri.toString());changed();});
        list=new ListView(a);list.setDivider(null);addView(list,new LayoutParams(-1,0,1));
        adapter=new BaseAdapter(){public int getCount(){return rows.size();}public Object getItem(int i){return rows.get(i);}public long getItemId(int i){return i;}public boolean isEnabled(int p){return false;}
            public View getView(int position,View reuse,ViewGroup parent){Object row=rows.get(position);
                if(row instanceof String){String day=(String)row;CheckBox header=new CheckBox(a);header.setTextColor(white);header.setTextSize(17);header.setPadding(dp(6),dp(14),0,dp(8));List<Entry> members=new ArrayList<>();for(Entry e:visible())if(day(e).equals(day))members.add(e);boolean selected=!members.isEmpty();for(Entry e:members)selected&=chosen.containsKey(e.uri.toString());header.setText(day+" ("+members.size()+")");header.setChecked(selected);header.setOnClickListener(v->{for(Entry e:members)if(header.isChecked())chosen.put(e.uri.toString(),e);else chosen.remove(e.uri.toString());changed();});return header;}
                @SuppressWarnings("unchecked") List<Entry> cells=(List<Entry>)row;
                LinearLayout line=new LinearLayout(a);line.setLayoutDirection(LAYOUT_DIRECTION_LTR);
                for(Entry e:cells){if(audio){CheckBox item=new CheckBox(a);item.setTextColor(white);item.setText(e.name+"\n"+TransferStats.gb(e.size));item.setChecked(chosen.containsKey(e.uri.toString()));item.setPadding(dp(8),dp(12),dp(8),dp(12));item.setOnClickListener(v->toggle(e));line.addView(item,new LayoutParams(-1,-2));}
                    else{FrameLayout cell=new FrameLayout(a);int side=Math.max(dp(64),(getResources().getDisplayMetrics().widthPixels-dp(44))/4);LayoutParams cp=new LayoutParams(0,side,1);cp.setMargins(1,1,1,1);line.addView(cell,cp);cell.setBackgroundColor(0xff252633);
                        ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(e.name);cell.addView(image,new FrameLayout.LayoutParams(-1,-1));thumbnail(e,image);
                        CheckBox check=new CheckBox(a);check.setChecked(chosen.containsKey(e.uri.toString()));check.setButtonTintList(android.content.res.ColorStateList.valueOf(0xffc1baff));check.setClickable(false);check.setFocusable(false);FrameLayout.LayoutParams ck=new FrameLayout.LayoutParams(dp(38),dp(38),Gravity.TOP|Gravity.RIGHT);cell.addView(check,ck);
                        if(e.mime.startsWith("video/")){TextView length=text(String.format(Locale.US,"%02d:%02d",e.duration/60000,e.duration/1000%60),12);length.setBackgroundColor(0x99000000);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);cell.addView(length,lp);}
                        cell.setOnClickListener(v->toggle(e));cell.setOnLongClickListener(v->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(e.uri,e.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception ignored){Toast.makeText(a,UiText.t(257),Toast.LENGTH_SHORT).show();}return true;});
                    }}
                if(!audio)for(int n=cells.size();n<4;n++)line.addView(new View(a),new LayoutParams(0,1,1));return line;
            }};list.setAdapter(adapter);
        count=text(summary,13);count.setPadding(0,dp(10),0,dp(6));addView(count);
        Button next=new Button(a);next.setText(UiText.t(28));next.setTextColor(white);next.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff7166dc));addView(next,new LayoutParams(-1,dp(52)));next.setOnClickListener(v->actions.next());
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int st,int b,int c){query=s.toString().toLowerCase(Locale.ROOT);rebuild();}});load();
    }
    private int dp(int x){return (int)(getResources().getDisplayMetrics().density*x);}
    private TextView text(String s,int size){TextView t=new TextView(activity);t.setText(s);t.setTextColor(white);t.setTextSize(size);return t;}
    private String day(Entry e){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(e.date*1000));}
    private List<Entry> visible(){List<Entry> out=new ArrayList<>();for(Entry e:entries)if(e.name.toLowerCase(Locale.ROOT).contains(query)&&(audio||filter==0||(filter==1&&e.mime.startsWith("image/"))||(filter==2&&e.mime.startsWith("video/"))))out.add(e);return out;}
    private void rebuild(){if(closed)return;rows.clear();String previous=null;List<Entry> cells=null;List<Entry> shown=visible();for(Entry e:shown){String d=day(e);if(!d.equals(previous)){rows.add(d);previous=d;cells=null;}if(cells==null||cells.size()==(audio?1:4)){cells=new ArrayList<>();rows.add(cells);}cells.add(e);}boolean checked=!shown.isEmpty();for(Entry e:shown)checked&=chosen.containsKey(e.uri.toString());all.setChecked(checked);adapter.notifyDataSetChanged();}
    private void toggle(Entry e){if(chosen.containsKey(e.uri.toString()))chosen.remove(e.uri.toString());else chosen.put(e.uri.toString(),e);changed();}
    private void changed(){actions.changed(new ArrayList<>(chosen.values()));rebuild();}
    void summary(String value){count.setText(warning.isEmpty()?value:warning+"\n"+value);}
    private void load(){int version=++loadVersion;boolean sound=audio;entries.clear();rebuild();warning="";loader.execute(()->{List<Entry> found=new ArrayList<>();boolean denied=false;for(int kind:sound?new int[]{2}:new int[]{0,1}){try{Uri uri=kind==0?MediaStore.Images.Media.EXTERNAL_CONTENT_URI:kind==1?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                String[] projection=kind==0?new String[]{"_id","_display_name","mime_type","_size","date_added"}:new String[]{"_id","_display_name","mime_type","_size","date_added","duration"};
                try(Cursor c=activity.getContentResolver().query(uri,projection,null,null,"date_added DESC")){while(c!=null&&c.moveToNext()&&found.size()<10000&&!closed){Entry e=new Entry();e.uri=ContentUris.withAppendedId(uri,c.getLong(0));e.name=c.isNull(1)?"file":c.getString(1);e.mime=c.isNull(2)?(kind==0?"image/jpeg":kind==1?"video/mp4":"audio/mpeg"):c.getString(2);e.size=c.isNull(3)?-1:c.getLong(3);e.date=c.getLong(4);e.duration=kind==0?0:c.getLong(5);found.add(e);}}
            }catch(Exception e){denied=true;}}
            found.sort((x,y)->Long.compare(y.date,x.date));final boolean restricted=denied;activity.runOnUiThread(()->{if(closed||version!=loadVersion)return;entries.addAll(found);warning=restricted?UiText.t(249):found.size()>=10000?UiText.t(267):found.isEmpty()?UiText.t(47):"";rebuild();actions.changed(new ArrayList<>(chosen.values()));});});}
    private void thumbnail(Entry e,ImageView target){String key=e.uri.toString();target.setTag(key);Bitmap saved=cache.get(key);if(saved!=null){target.setImageBitmap(saved);return;}
        if(closed)return;thumbs.execute(()->{Bitmap b=null;try{if(Build.VERSION.SDK_INT>=29)b=activity.getContentResolver().loadThumbnail(e.uri,new Size(180,180),null);else if(e.mime.startsWith("video/"))b=MediaStore.Video.Thumbnails.getThumbnail(activity.getContentResolver(),ContentUris.parseId(e.uri),MediaStore.Video.Thumbnails.MINI_KIND,null);else b=MediaStore.Images.Thumbnails.getThumbnail(activity.getContentResolver(),ContentUris.parseId(e.uri),MediaStore.Images.Thumbnails.MINI_KIND,null);}catch(Exception ignored){}if(b!=null&&!closed){cache.put(key,b);final Bitmap bitmap=b;activity.runOnUiThread(()->{if(!closed&&key.equals(target.getTag()))target.setImageBitmap(bitmap);});}});}
    void close(){closed=true;loader.shutdownNow();thumbs.shutdownNow();cache.evictAll();}
}
