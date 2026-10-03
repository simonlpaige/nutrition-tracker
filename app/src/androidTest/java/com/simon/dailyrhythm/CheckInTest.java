package com.simon.dailyrhythm;
import android.app.*;
import android.content.*;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CheckInTest {
    String read(Activity activity,String expression)throws Exception{
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(activity);CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();activity.runOnUiThread(()->web.evaluateJavascript(expression,value->{result.set(value);done.countDown();}));assertTrue(done.await(5,TimeUnit.SECONDS));return result.get();
    }
    @Test public void reminderLaunchOpensCheckInAfterPageInitialization()throws Exception{
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context c=instrumentation.getTargetContext();Activity activity=instrumentation.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("checkIn",true).putExtra("weekly",true));
        try{String value="";for(int i=0;i<50;i++){value=read(activity,"document.querySelector('#ai-dialog')?.open && document.querySelector('#ai-title')?.textContent==='Daily check-in'");if(value.equals("true"))break;Thread.sleep(100);}assertEquals("true",value);assertEquals("true",read(activity,"document.querySelector('#ai-body').textContent.includes('weekly frontier-model review is due')"));}
        finally{activity.runOnUiThread(activity::finish);}
    }
    @Test public void eveningAlarmOpensCheckInWhileAppIsVisible()throws Exception{
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context c=instrumentation.getTargetContext();boolean old=c.getSharedPreferences("reminder",0).getBoolean("enabled",false);c.getSharedPreferences("reminder",0).edit().putBoolean("enabled",true).commit();Activity activity=instrumentation.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{for(int i=0;i<50;i++){if(read(activity,"Boolean(window.RhythmAssist)").equals("true"))break;Thread.sleep(100);}instrumentation.runOnMainSync(()->new ReminderReceiver().onReceive(c,new Intent(ReminderScheduler.ACTION)));String value="";for(int i=0;i<50;i++){value=read(activity,"document.querySelector('#ai-dialog')?.open && document.querySelector('#ai-title')?.textContent==='Daily check-in'");if(value.equals("true"))break;Thread.sleep(100);}assertEquals("true",value);}
        finally{activity.runOnUiThread(activity::finish);c.getSharedPreferences("reminder",0).edit().putBoolean("enabled",old).commit();}
    }
}
