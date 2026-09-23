package com.simon.dailyrhythm;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import android.os.OutcomeReceiver;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Foreground capture of permitted source records, with per-category coverage/status. */
final class HealthArchive {
 private final Activity activity;
 private final HealthConnectManager manager;
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 private static volatile boolean running=false;
 private final Class<?>[] classes={StepsRecord.class,DistanceRecord.class,SleepSessionRecord.class,HeartRateRecord.class,RestingHeartRateRecord.class,WeightRecord.class,ActiveCaloriesBurnedRecord.class,TotalCaloriesBurnedRecord.class,HydrationRecord.class,NutritionRecord.class};
 private File directory;
 private JSONObject manifest;
 private TimeInstantRangeFilter range;
 HealthArchive(Activity a){activity=a;manager=a.getSystemService(HealthConnectManager.class);}
 void capture(){
  if(running||manager==null)return;
  String day=LocalDate.now().toString();
  File complete=new File(activity.getFilesDir(),"health-archive/"+day+"/manifest.json");
  if(complete.exists())try{
   JSONObject previous=new JSONObject(new String(java.nio.file.Files.readAllBytes(complete.toPath()),StandardCharsets.UTF_8));JSONObject cats=previous.getJSONObject("categories");boolean okay=true;for(String key:HealthReader.TYPES){String status=cats.getJSONObject(key).getString("status");if(!status.equals("ok")&&!status.equals("empty"))okay=false;}if(okay)return;
  }catch(Exception ignored){}
  running=true;
  worker.execute(()->{
   try{
    directory=complete.getParentFile();if(!directory.exists()&&!directory.mkdirs())throw new IOException();
    Instant end=Instant.now();Instant from=LocalDate.now().minusDays(28).atStartOfDay(ZoneId.systemDefault()).toInstant();
    range=new TimeInstantRangeFilter.Builder().setStartTime(from).setEndTime(end).build();
    manifest=new JSONObject().put("schema","daily-rhythm.health-archive.v1").put("startedAt",end.toString()).put("from",from.toString()).put("through",end.toString()).put("categories",new JSONObject())
      .put("scope","Source records from the ten permitted Health Connect categories in the last 28 days. Missing permissions, source omissions and Android history restrictions are reported. This is not every record in every health account.");
    next(0);
   }catch(Exception e){running=false;}
  });
 }
 @SuppressWarnings({"unchecked","rawtypes"})
 private void next(int index){
  if(index==classes.length){finish();return;}
  String key=HealthReader.TYPES[index];
  if(activity.checkSelfPermission(HealthReader.permission(key))!=PackageManager.PERMISSION_GRANTED){result(index,"permission",0);next(index+1);return;}
  try{
   AtomicFile file=new AtomicFile(new File(directory,key.toLowerCase(Locale.ROOT)+".jsonl"));
   FileOutputStream out=file.startWrite();
   page(index,(Class)classes[index],-1,0,file,out);
  }catch(Exception e){result(index,"error",0);next(index+1);}
 }
 private <T extends android.health.connect.datatypes.Record> void page(int index,Class<T> type,long token,int count,AtomicFile file,FileOutputStream out){
  try{
   ReadRecordsRequestUsingFilters.Builder<T> request=new ReadRecordsRequestUsingFilters.Builder<>(type).setTimeRangeFilter(range).setPageSize(100);
   if(token!=-1)request.setPageToken(token);
   manager.readRecords(request.build(),worker,new OutcomeReceiver<ReadRecordsResponse<T>,HealthConnectException>(){
    public void onResult(ReadRecordsResponse<T> response){
     int total=count;
     try{
      for(T record:response.getRecords()){String line=encode(record,0).toString()+"\n";out.write(line.getBytes(StandardCharsets.UTF_8));total++;}
      if(response.getNextPageToken()!=-1&&total<100000){page(index,type,response.getNextPageToken(),total,file,out);return;}
      file.finishWrite(out);result(index,response.getNextPageToken()!=-1?"partial":total==0?"empty":"ok",total);next(index+1);
     }catch(Exception e){file.failWrite(out);result(index,"error",count);next(index+1);}
    }
    public void onError(HealthConnectException e){file.failWrite(out);result(index,"error",count);next(index+1);}
   });
  }catch(Exception e){file.failWrite(out);result(index,"error",count);next(index+1);}
 }
 private Object encode(Object value,int depth)throws Exception{
  if(value==null)return JSONObject.NULL;
  if(value instanceof String||value instanceof Number||value instanceof Boolean)return value;
  if(value instanceof java.time.temporal.TemporalAccessor||value instanceof ZoneId||value instanceof Duration)return value.toString();
  if(depth>8)throw new IOException("Unsupported nested record");
  if(value instanceof Iterable<?>){JSONArray a=new JSONArray();for(Object x:(Iterable<?>)value)a.put(encode(x,depth+1));return a;}
  if(value.getClass().isArray()){JSONArray a=new JSONArray();for(int i=0;i<Array.getLength(value);i++)a.put(encode(Array.get(value,i),depth+1));return a;}
  if(!value.getClass().getName().startsWith("android.health.connect.datatypes."))return value.toString();
  JSONObject o=new JSONObject().put("recordClass",value.getClass().getSimpleName());
  for(Method method:value.getClass().getMethods()){
   String name=method.getName();
   if(Modifier.isStatic(method.getModifiers())||method.getParameterCount()!=0||name.equals("getClass")||!(name.startsWith("get")||name.startsWith("is")))continue;
   o.put(name,encode(method.invoke(value),depth+1));
  }
  return o;
 }
 private void result(int index,String status,int count){try{manifest.getJSONObject("categories").put(HealthReader.TYPES[index],new JSONObject().put("status",status).put("records",count));}catch(Exception ignored){}}
 private void finish(){
  AtomicFile file=new AtomicFile(new File(directory,"manifest.json"));FileOutputStream out=null;
  try{manifest.put("finishedAt",Instant.now().toString());out=file.startWrite();out.write(manifest.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);}finally{running=false;}
 }
}
