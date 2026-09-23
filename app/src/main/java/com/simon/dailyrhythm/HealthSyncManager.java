package com.simon.dailyrhythm;

import android.app.Activity;
import android.os.*;
import org.json.*;
import java.time.*;
import java.util.function.*;

/** Coordinates foreground health reads, archives, review progress, and cancellation. */
public final class HealthSyncManager {
    private final Activity activity;
    private final HealthReader reader;
    private final HealthArchive archive;
    private final BiConsumer<String,JSONObject> events;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int generation;

    HealthSyncManager(Activity a,LoggingRepository repository,BiConsumer<String,JSONObject> events) {
        activity=a;reader=new HealthReader(a);archive=new HealthArchive(a);this.events=events;
    }
    void capture(){archive.capture();}
    void requestPermissions(){reader.requestPermissions();}
    void readDay(String day,Consumer<JSONObject> callback){reader.readDay(day,callback);}
    void cancelReview(){generation++;handler.removeCallbacksAndMessages(null);}
    void close(){cancelReview();}
    void readReview(int days,String end) {
        try {
            LocalDate date=LocalDate.parse(end);
            if((days!=14&&days!=28)||date.isAfter(LocalDate.now()))throw new IllegalArgumentException();
            cancelReview();int request=generation;
            final boolean[] started={false};
            Runnable begin=()->{if(started[0]||request!=generation||activity.isDestroyed())return;started[0]=true;readReviewDay(date,days,0,request);};
            handler.postDelayed(begin,15000);
            reader.readSleepRange(date.minusDays(days-1),date,data->{
                if(started[0]||request!=generation||activity.isDestroyed())return;
                events.accept("reviewSleep",data);handler.removeCallbacks(begin);begin.run();
            });
        } catch(Exception e) {emit("reviewError","message","Choose a 14- or 28-day window ending today or earlier.");}
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
