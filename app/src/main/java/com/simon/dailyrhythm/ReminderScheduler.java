package com.simon.dailyrhythm;
import android.app.*;
import android.content.*;
import java.time.*;
import org.json.*;

/** Owns reminder timing and saved preferences; the receiver only delivers notifications. */
public final class ReminderScheduler {
    static final String ACTION="com.simon.dailyrhythm.CHECK_IN";
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
        ZonedDateTime next = nextCheckIn(now);
        AlarmManager a = c.getSystemService(AlarmManager.class);
        long when = next.toInstant().toEpochMilli();
        if (a.canScheduleExactAlarms()) a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c));
        else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c));
    }
    static boolean reviewDue(Context c) {
        android.content.SharedPreferences prefs=c.getSharedPreferences("reminder",0);
        return reviewDue(prefs.getInt("reviewDays",7),prefs.getString("lastReview",""),prefs.getString("lastReviewNudge",""),LocalDate.now());
    }
    static ZonedDateTime nextCheckIn(ZonedDateTime now) {
        ZonedDateTime next=now.toLocalDate().atTime(20,0).atZone(now.getZone());
        return next.isAfter(now)?next:now.toLocalDate().plusDays(1).atTime(20,0).atZone(now.getZone());
    }
    static boolean reviewDue(int days,String last,String lastNudge,LocalDate today) {
        if(days<=0||(days==7&&today.getDayOfWeek()!=DayOfWeek.SUNDAY)||today.toString().equals(lastNudge))return false;
        try{return last.isEmpty()||!LocalDate.parse(last).plusDays(days).isAfter(today);}catch(Exception ignored){return true;}
    }
    public static void updateReviewPreference(Context c,JSONObject data) {
        JSONObject ai=data.optJSONObject("ai");if(ai==null)return;
        int interval=ai.optInt("reviewEveryDays",7);
        if(interval!=0&&interval!=7&&interval!=14&&interval!=28)return;
        c.getSharedPreferences("reminder",0).edit().putInt("reviewDays",interval)
            .putString("lastReview",ai.isNull("lastReviewDay")?"":ai.optString("lastReviewDay","")).apply();
    }
}
