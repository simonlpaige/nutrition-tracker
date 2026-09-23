package com.simon.dailyrhythm;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import android.health.connect.datatypes.units.*;
import android.os.OutcomeReceiver;
import org.json.*;
import java.time.*;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/** Foreground, read-only Health Connect integration. No third-party credentials. */
public final class HealthReader {
    static final String[] TYPES = {"STEPS", "DISTANCE", "SLEEP", "HEART_RATE", "RESTING_HEART_RATE", "WEIGHT", "ACTIVE_CALORIES_BURNED", "TOTAL_CALORIES_BURNED", "HYDRATION", "NUTRITION"};
    final Activity activity;
    final HealthConnectManager manager;
    HealthReader(Activity a) { activity = a; manager = a.getSystemService(HealthConnectManager.class); }
    static String permission(String type) { return "android.permission.health.READ_" + type; }
    void requestPermissions() {
        String[] p = new String[TYPES.length];
        for (int i=0;i<TYPES.length;i++) p[i] = permission(TYPES[i]);
        activity.requestPermissions(p, 100);
    }
    void readDay(String day, Consumer<JSONObject> callback) {
        final LocalDate date;
        try { date = LocalDate.parse(day); }
        catch (Exception e) { return; }
        ZoneId zone = ZoneId.systemDefault();
        TimeInstantRangeFilter range = new TimeInstantRangeFilter.Builder()
            .setStartTime(date.atStartOfDay(zone).toInstant())
            .setEndTime(date.plusDays(1).atStartOfDay(zone).toInstant()).build();
        metric(day, "steps", "STEPS", StepsRecord.STEPS_COUNT_TOTAL, range, Long::doubleValue, callback);
        metric(day, "distance", "DISTANCE", DistanceRecord.DISTANCE_TOTAL, range, Length::getInMeters, callback);
        metric(day, "sleep", "SLEEP", SleepSessionRecord.SLEEP_DURATION_TOTAL, range, v -> v / 3600000.0, callback);
        metric(day, "heart", "HEART_RATE", HeartRateRecord.BPM_AVG, range, Long::doubleValue, callback);
        metric(day, "resting", "RESTING_HEART_RATE", RestingHeartRateRecord.BPM_AVG, range, Long::doubleValue, callback);
        metric(day, "weight", "WEIGHT", WeightRecord.WEIGHT_AVG, range, Mass::getInGrams, callback);
        metric(day, "active", "ACTIVE_CALORIES_BURNED", ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, range, Energy::getInCalories, callback);
        metric(day, "burned", "TOTAL_CALORIES_BURNED", TotalCaloriesBurnedRecord.ENERGY_TOTAL, range, Energy::getInCalories, callback);
        metric(day, "water", "HYDRATION", HydrationRecord.VOLUME_TOTAL, range, Volume::getInLiters, callback);
        metric(day, "nutrition", "NUTRITION", NutritionRecord.ENERGY_TOTAL, range, Energy::getInCalories, callback);
    }
    void readSleepRange(LocalDate from,LocalDate end,Consumer<JSONObject> callback) {
        if(manager==null||activity.checkSelfPermission(permission("SLEEP"))!=PackageManager.PERMISSION_GRANTED){sleepResult("permission",new JSONArray(),callback);return;}
        ZoneId zone=ZoneId.systemDefault();
        TimeInstantRangeFilter range=new TimeInstantRangeFilter.Builder()
            .setStartTime(from.minusDays(1).atStartOfDay(zone).toInstant())
            .setEndTime(end.plusDays(1).atStartOfDay(zone).toInstant()).build();
        sleepPage(range,-1,new JSONArray(),from,end,callback);
    }
    private void sleepPage(TimeInstantRangeFilter range,long token,JSONArray sessions,LocalDate from,LocalDate end,Consumer<JSONObject> callback) {
        try {
            ReadRecordsRequestUsingFilters.Builder<SleepSessionRecord> b=new ReadRecordsRequestUsingFilters.Builder<>(SleepSessionRecord.class).setTimeRangeFilter(range).setPageSize(100);
            if(token!=-1)b.setPageToken(token);
            manager.readRecords(b.build(),activity.getMainExecutor(),new OutcomeReceiver<ReadRecordsResponse<SleepSessionRecord>,HealthConnectException>() {
                @Override public void onResult(ReadRecordsResponse<SleepSessionRecord> response) {
                    try {
                        for(SleepSessionRecord record:response.getRecords()) {
                            ZoneOffset zone=record.getEndZoneOffset();
                            java.time.ZoneId effective=zone==null?ZoneId.systemDefault():zone;
                            LocalDate wakeDay=record.getEndTime().atZone(effective).toLocalDate();
                            if(wakeDay.isBefore(from)||wakeDay.isAfter(end))continue;
                            JSONObject stages=new JSONObject();
                            String[] labels={"unknown","awake","sleeping_unspecified","awake_out_of_bed","light","deep","rem","awake_in_bed"};
                            for(SleepSessionRecord.Stage stage:record.getStages()) {
                                int type=stage.getType();String key=type>=0&&type<labels.length?labels[type]:"unknown";
                                double minutes=Duration.between(stage.getStartTime(),stage.getEndTime()).toMillis()/60000.0;
                                stages.put(key,stages.optDouble(key,0)+minutes);
                            }
                            sessions.put(new JSONObject().put("startUtc",record.getStartTime().toString()).put("endUtc",record.getEndTime().toString())
                                .put("wakeDate",wakeDay.toString()).put("endZone",effective.toString())
                                .put("source",record.getMetadata().getDataOrigin().getPackageName()).put("stageMinutes",stages));
                        }
                        if(response.getNextPageToken()!=-1&&sessions.length()<1000)sleepPage(range,response.getNextPageToken(),sessions,from,end,callback);
                        else sleepResult(response.getNextPageToken()!=-1?"partial":sessions.length()==0?"empty":"ok",sessions,callback);
                    } catch(Exception e){sleepResult("error",new JSONArray(),callback);}
                }
                @Override public void onError(HealthConnectException error){sleepResult("error",new JSONArray(),callback);}
            });
        }catch(Exception e){sleepResult("error",new JSONArray(),callback);}
    }
    private void sleepResult(String status,JSONArray sessions,Consumer<JSONObject> callback) {
        JSONObject o=new JSONObject();try{o.put("status",status).put("sessions",sessions).put("readAt",Instant.now().toString());}catch(JSONException ignored){}
        callback.accept(o);
    }
    private <T> void metric(String date, String key, String permission, AggregationType<T> type,
            TimeInstantRangeFilter range, ToDoubleFunction<T> convert, Consumer<JSONObject> callback) {
        if (manager == null) { emit(date,key,"unavailable",null,new JSONArray(),callback); return; }
        if (activity.checkSelfPermission(permission(permission)) != PackageManager.PERMISSION_GRANTED) {
            emit(date,key,"permission",null,new JSONArray(),callback); return;
        }
        try {
            AggregateRecordsRequest<T> request = new AggregateRecordsRequest.Builder<T>(range).addAggregationType(type).build();
            manager.aggregate(request, activity.getMainExecutor(), new OutcomeReceiver<AggregateRecordsResponse<T>,HealthConnectException>() {
                @Override public void onResult(AggregateRecordsResponse<T> response) {
                    T value = response.get(type);
                    JSONArray sources = new JSONArray();
                    for (DataOrigin origin : response.getDataOrigins(type)) sources.put(origin.getPackageName());
                    emit(date,key,value == null ? "empty" : "ok",value == null ? null : convert.applyAsDouble(value),sources,callback);
                }
                @Override public void onError(HealthConnectException error) { emit(date,key,"error",null,new JSONArray(),callback); }
            });
        } catch (Exception e) { emit(date,key,"error",null,new JSONArray(),callback); }
    }
    private void emit(String date, String key, String status, Double value, JSONArray sources, Consumer<JSONObject> callback) {
        JSONObject result = new JSONObject();
        try { result.put("date",date).put("key",key).put("status",status).put("value",value == null ? JSONObject.NULL : value).put("sources",sources).put("at",Instant.now().toString()); }
        catch (JSONException e) { return; }
        callback.accept(result);
    }
}
