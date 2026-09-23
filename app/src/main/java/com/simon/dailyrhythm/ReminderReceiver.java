package com.simon.dailyrhythm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import java.time.*;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String ACTION = "com.simon.dailyrhythm.CHECK_IN";
    private static final String CHANNEL = "daily_check_in";
    private static PendingIntent pending(Context c) {
        return PendingIntent.getBroadcast(c, 20, new Intent(c, ReminderReceiver.class).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static boolean enabled(Context c) { return c.getSharedPreferences("reminder", 0).getBoolean("enabled", false); }
    public static void enable(Context c, boolean enabled) {
        c.getSharedPreferences("reminder", 0).edit().putBoolean("enabled", enabled).apply();
        if (!enabled) c.getSystemService(AlarmManager.class).cancel(pending(c));
        else schedule(c);
    }
    public static void schedule(Context c) {
        if (!enabled(c)) return;
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.toLocalDate().atTime(20, 0).atZone(now.getZone());
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(20, 0).atZone(now.getZone());
        AlarmManager a = c.getSystemService(AlarmManager.class);
        long when = next.toInstant().toEpochMilli();
        if (a.canScheduleExactAlarms()) a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c));
        else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c));
    }
    @Override public void onReceive(Context c, Intent intent) {
        if (!enabled(c)) return;
        if (ACTION.equals(intent.getAction()) && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManager n = c.getSystemService(NotificationManager.class);
            n.createNotificationChannel(new NotificationChannel(CHANNEL, "Evening check-in", NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open = PendingIntent.getActivity(c, 21, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            boolean weekly=reviewDue(c);
            if(weekly)c.getSharedPreferences("reminder",0).edit().putString("lastReviewNudge",LocalDate.now().toString()).apply();
            n.notify(20, new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Your evening check-in")
                .setContentText(weekly?"Your check-in and a pattern review are ready when you are.":"Take a moment to complete your daily log.")
                .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());
        }
        schedule(c);
    }
    private static boolean reviewDue(Context c) {
        android.content.SharedPreferences prefs=c.getSharedPreferences("reminder",0);
        int days=prefs.getInt("reviewDays",7);if(days<=0)return false;
        if(days==7&&LocalDate.now().getDayOfWeek()!=DayOfWeek.SUNDAY)return false;
        String lastNudge=prefs.getString("lastReviewNudge","");
        if(lastNudge.equals(LocalDate.now().toString()))return false;
        String last=prefs.getString("lastReview","");
        try{return last.isEmpty()||!LocalDate.parse(last).plusDays(days).isAfter(LocalDate.now());}
        catch(Exception ignored){return true;}
    }
}
