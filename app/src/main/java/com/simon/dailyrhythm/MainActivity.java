package com.simon.dailyrhythm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView web;
    private HealthSyncManager health;
    private LoggingRepository repository;
    private MealEstimator meals;
    private boolean pendingCheckIn, pendingWeekly;
    private final BroadcastReceiver checkInReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){pendingCheckIn=true;pendingWeekly=i.getBooleanExtra("weekly",false);showCheckIn();}};
    private String pendingExport;
    private boolean ready;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final String ORIGIN = "https://appassets.androidplatform.net/";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        
        repository = new LoggingRepository(this);
        health = new HealthSyncManager(this, repository, this::event);
        meals = new MealEstimator(this,this::event);
        readCheckInIntent(getIntent());
        web = new WebView(this);
        web.setBackgroundColor(0xff191525);
        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(0xff191525);
        getWindow().getDecorView().setSystemUiVisibility(0);
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));
        content.setOnApplyWindowInsetsListener((v,insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(i.left,i.top,i.right,i.bottom); return WindowInsets.CONSUMED;
        });
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(false);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(false); s.setSafeBrowsingEnabled(true);
        web.addJavascriptInterface(new Bridge(), "RhythmAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                Map<String,String> files = Map.ofEntries(Map.entry("index.html","text/html"),Map.entry("style.css","text/css"),Map.entry("theme.css","text/css"),Map.entry("core.js","text/javascript"),Map.entry("app.js","text/javascript"),Map.entry("ai-core.js","text/javascript"),Map.entry("assist.js","text/javascript"),Map.entry("body-core.js","text/javascript"),Map.entry("body.js","text/javascript"),Map.entry("garmin.js","text/javascript"),Map.entry("nutrition.js","text/javascript"));
                String name = url.startsWith(ORIGIN) ? url.substring(ORIGIN.length()) : "";
                if (files.containsKey(name)) {
                    try { return new WebResourceResponse(files.get(name),"UTF-8",getAssets().open(name)); }
                    catch (IOException ignored) { }
                }
                return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Map.of(),new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) { return true; }
            @Override public void onPageFinished(WebView view,String url) { ready=true; event("nativeStatus", status()); event("refreshHealth",new JSONObject()); showCheckIn(); }
        });
        setContentView(content); content.requestApplyInsets(); web.loadUrl(ORIGIN+"index.html");
        ReminderScheduler.schedule(this);
    }
    // Do not put health journal/export payloads in saved instance state (Binder size limit).
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code==101 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) ReminderScheduler.enable(this,true);
        event("nativeStatus",status());
        if(code==100){health.capture();event("refreshHealth",new JSONObject());}
    }
    @Override protected void onResume() {
        super.onResume();
        registerReceiver(checkInReceiver,new IntentFilter(ReminderScheduler.FOREGROUND),Context.RECEIVER_NOT_EXPORTED);
        if(health!=null)health.capture();
        if(web!=null && ready) { ReminderScheduler.schedule(this); event("nativeStatus",status()); event("refreshHealth",new JSONObject()); }
        showCheckIn();
    }
    @Override protected void onPause(){try{unregisterReceiver(checkInReceiver);}catch(IllegalArgumentException ignored){}super.onPause();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readCheckInIntent(intent);showCheckIn();}
    private void readCheckInIntent(Intent intent){if(intent!=null&&intent.getBooleanExtra("checkIn",false)){pendingCheckIn=true;pendingWeekly=intent.getBooleanExtra("weekly",false);intent.removeExtra("checkIn");}}
    private void showCheckIn(){if(!ready||!pendingCheckIn)return;pendingCheckIn=false;try{event("checkIn",new JSONObject().put("weekly",pendingWeekly));}catch(JSONException ignored){}}
    private JSONObject status() {
        JSONObject o=new JSONObject();
        try {
            o.put("native",true).put("reminder",ReminderScheduler.enabled(this))
             .put("notifications",getSystemService(NotificationManager.class).areNotificationsEnabled())
             .put("exact",getSystemService(AlarmManager.class).canScheduleExactAlarms());
        } catch(JSONException ignored) { }
        return o;
    }
    private void event(String name, JSONObject data) {
        runOnUiThread(() -> { if(web!=null && ready) web.evaluateJavascript("window.RhythmEvent("+JSONObject.quote(name)+","+data.toString()+")",null); });
    }
    private void message(String text) {
        JSONObject o=new JSONObject(); try{o.put("message",text);}catch(JSONException ignored){}
        event("message",o);
    }
    public class Bridge {
        @JavascriptInterface public String load() { return repository.load(); }
        @JavascriptInterface public String save(String data) {
            String error=repository.save(data);
            if(error.isEmpty())try {ReminderScheduler.updateReviewPreference(MainActivity.this,new JSONObject(data));}catch(JSONException ignored){}
            return error;
        }
        @JavascriptInterface public String loadGarmin() {
            try {
                File file=new File(getFilesDir(),"garmin-fit.json");
                if(!file.exists()||file.length()>8*1024*1024)return "null";
                try(FileInputStream in=new FileInputStream(file)) {
                    String raw=new String(in.readAllBytes(),StandardCharsets.UTF_8);
                    JSONObject data=new JSONObject(raw);
                    if(!"daily-rhythm.garmin-fit.v1".equals(data.optString("schema")))return "null";
                    return raw;
                }
            }catch(Exception e){return "null";}
        }
        @JavascriptInterface public String backupStatus(){
            try{return new String(java.nio.file.Files.readAllBytes(new File(getFilesDir(),"backup-status.json").toPath()),StandardCharsets.UTF_8);}catch(Exception e){return "null";}
        }
        @JavascriptInterface public String getStatus() { return status().toString(); }
        @JavascriptInterface public String cachedHealth(){return health.cached();}
        @JavascriptInterface public String nutritionStatus(){return meals.status().toString();}
        @JavascriptInterface public String configureNutrition(String key,boolean enabled){return meals.configure(key,enabled);}
        @JavascriptInterface public void clearNutrition(){meals.clear();}
        @JavascriptInterface public void estimateNutrition(String meal){meals.estimate(meal);}
        @JavascriptInterface public String pendingNutrition(){return meals.pending();}
        @JavascriptInterface public void acknowledgeNutrition(String id){meals.acknowledge(id);}
        @JavascriptInterface public void connect() { runOnUiThread(() -> { try { health.requestPermissions(); } catch(Exception e) {message("Health Connect permissions could not open. Try Android Settings → Health Connect.");} }); }
        @JavascriptInterface public void sync(String date) { runOnUiThread(() -> health.readDay(date,data -> event("health",data))); }
        @JavascriptInterface public void syncRange(int days,String end) { runOnUiThread(() -> health.readReview(days,end)); }
        @JavascriptInterface public void cancelReview() { runOnUiThread(() -> health.cancelReview()); }
        @JavascriptInterface public String copyAI(String text) {
            if(text==null||text.length()>400000)return "This prompt is too large. Choose a shorter review window.";
            runOnUiThread(() -> {
                ClipData clip=ClipData.newPlainText("Daily Rhythm AI prompt",text);
                PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);
                clip.getDescription().setExtras(extras);
                try{getSystemService(ClipboardManager.class).setPrimaryClip(clip);message("Prompt copied. Paste it into ChatGPT when ready.");}
                catch(Exception e){message("This prompt could not fit on the clipboard. Choose a shorter review window.");}
            });
            return "";
        }
        @JavascriptInterface public void openChatGPT() { runOnUiThread(() -> {
            try {
                Intent launch=getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
                startActivity(launch!=null?launch:new Intent(Intent.ACTION_VIEW,Uri.parse("https://chatgpt.com/")));
            } catch(Exception e) { message("Open ChatGPT and paste the copied prompt."); }
        }); }
        @JavascriptInterface public void healthSettings() { runOnUiThread(() -> openSettings("android.health.connect.action.HEALTH_HOME_SETTINGS",false)); }
        @JavascriptInterface public void exactAlarmSettings() { runOnUiThread(() -> openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,true)); }
        @JavascriptInterface public void notificationSettings() { runOnUiThread(() -> {
            Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
            try{startActivity(i);}catch(Exception e){message("Open notification settings for Daily Rhythm.");}
        }); }
        @JavascriptInterface public void reminder(boolean enabled) { runOnUiThread(() -> {
            if(enabled && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);
            else ReminderScheduler.enable(MainActivity.this,enabled);
            event("nativeStatus",status());
        }); }
        @JavascriptInterface public void privacy() { runOnUiThread(() -> startActivity(new Intent(MainActivity.this,PrivacyActivity.class))); }
        @JavascriptInterface public void exportFile(String name,String mime,String contents) { runOnUiThread(() -> {
            if(contents.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) { message("Export is too large.");return; }
            if(!mime.equals("application/json")&&!mime.equals("text/csv")) return;
            pendingExport=contents;
            try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,name),201); }
            catch(Exception e) {pendingExport=null;message("No file picker is available.");}
        }); }
        @JavascriptInterface public void importFile() { runOnUiThread(() -> {
            try {startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),202);}
            catch(Exception e){message("No file picker is available.");}
        }); }
    }
    private void openSettings(String action,boolean packageUri) {
        Intent i=new Intent(action); if(packageUri)i.setData(Uri.parse("package:"+getPackageName()));
        try{startActivity(i);}catch(Exception e){message("Open this setting from Android Settings.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent intent) {
        super.onActivityResult(request,result,intent);
        if(result!=RESULT_OK || intent==null || intent.getData()==null) { if(request==201)pendingExport=null;return; }
        if(request==201) {
            if(pendingExport==null){message("Export was interrupted. Please export again.");return;}
            try(OutputStream out=getContentResolver().openOutputStream(intent.getData(),"wt")) {
                if(out==null)throw new IOException(); out.write(pendingExport.getBytes(StandardCharsets.UTF_8)); message("Export saved.");
            } catch(Exception e){message("Could not save the export.");} finally{pendingExport=null;}
        } else if(request==202) {
            try(InputStream in=getContentResolver().openInputStream(intent.getData())) {
                if(in==null)throw new IOException();
                byte[] bytes=in.readNBytes(MAX_BYTES+1); if(bytes.length>MAX_BYTES)throw new IOException();
                JSONObject o=new JSONObject();o.put("text",new String(bytes,StandardCharsets.UTF_8));event("import",o);
            } catch(Exception e){message("Could not read this backup. Choose a JSON backup under 8 MB.");}
        }
    }
    @Override protected void onDestroy(){if(health!=null)health.close();if(meals!=null)meals.close();if(web!=null){web.removeJavascriptInterface("RhythmAndroid");web.destroy();web=null;}super.onDestroy();}
}
