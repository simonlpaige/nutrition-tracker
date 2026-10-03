package com.simon.dailyrhythm;

import android.app.Activity;
import android.os.*;
import org.json.*;
import java.time.*;
import java.util.function.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import android.util.AtomicFile;

/** Coordinates foreground health reads, archives, review progress, and cancellation. */
public final class HealthSyncManager {
    private final Activity activity;
    private final HealthReader reader;
    private final HealthArchive archive;
    private final BiConsumer<String,JSONObject> events;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int generation;
    private final AtomicFile cache;

    HealthSyncManager(Activity a,LoggingRepository repository,BiConsumer<String,JSONObject> events) {
        activity=a;reader=new HealthReader(a);archive=new HealthArchive(a);this.events=events;
        cache=new AtomicFile(new File(a.getFilesDir(),"health-daily-cache.json"));
    }
    void capture(){archive.capture();}
    void requestPermissions(){reader.requestPermissions();}
    void readDay(String day,Consumer<JSONObject> callback){reader.readDay(day,data->{remember(data);callback.accept(data);});}
    synchronized String cached(){try{return new String(cache.readFully(),StandardCharsets.UTF_8);}catch(Exception e){return "{}";}}
    private synchronized void remember(JSONObject data){
        try{JSONObject root=new JSONObject(cached()),day=root.optJSONObject(data.getString("date"));if(day==null)day=new JSONObject();day.put(data.getString("key"),data);root.put(data.getString("date"),day);
            java.util.List<String> dates=new java.util.ArrayList<>();for(java.util.Iterator<String> it=root.keys();it.hasNext();)dates.add(it.next());java.util.Collections.sort(dates);while(dates.size()>180)root.remove(dates.remove(0));
            FileOutputStream out=null;try{out=cache.startWrite();out.write(root.toString().getBytes(StandardCharsets.UTF_8));cache.finishWrite(out);}catch(IOException e){if(out!=null)cache.failWrite(out);}
        }catch(Exception ignored){}
    }
    void cancelReview(){generation++;handler.removeCallbacksAndMessages(null);}
    void close(){cancelReview();}
    void readReview(int days,String end) {
        try {
            LocalDate date=LocalDate.parse(end);
            if((days!=14&&days!=28&&days!=56&&days!=84)||date.isAfter(LocalDate.now()))throw new IllegalArgumentException();
            days=Math.min(days,28);
            final int readDays=days;
            cancelReview();int request=generation;
            final boolean[] started={false};
            Runnable begin=()->{if(started[0]||request!=generation||activity.isDestroyed())return;started[0]=true;readReviewDay(date,readDays,0,request);};
            handler.postDelayed(begin,15000);
            reader.readSleepRange(date.minusDays(days-1),date,data->{
                if(started[0]||request!=generation||activity.isDestroyed())return;
                events.accept("reviewSleep",data);handler.removeCallbacks(begin);begin.run();
            });
        } catch(Exception e) {emit("reviewError","message","Choose a supported window ending today or earlier.");}
    }
    private void readReviewDay(LocalDate end,int days,int index,int request) {
        if(request!=generation||activity.isDestroyed())return;
        final int[] received={0};final boolean[] finished={false};
        Runnable advance=()->{
            if(finished[0]||request!=generation||activity.isDestroyed())return;
            finished[0]=true;
            try{events.accept("reviewProgress",new JSONObject().put("completed",index+1).put("total",days));}catch(JSONException ignored){}
            if(index+1<days)handler.postDelayed(()->readReviewDay(end,days,index+1,request),160);
        };
        handler.postDelayed(advance,15000);
        readDay(end.minusDays(days-1-index).toString(),data->{
            if(finished[0]||request!=generation||activity.isDestroyed())return;
            events.accept("reviewHealth",data);
            if(++received[0]==HealthReader.TYPES.length){handler.removeCallbacks(advance);advance.run();}
        });
    }
    private void emit(String name,String key,String value){try{events.accept(name,new JSONObject().put(key,value));}catch(JSONException ignored){}}
}
