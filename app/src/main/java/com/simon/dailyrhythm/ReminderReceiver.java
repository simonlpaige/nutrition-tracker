package com.simon.dailyrhythm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import java.time.*;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "daily_check_in";
    @Override public void onReceive(Context c, Intent intent) {
        if (!ReminderScheduler.enabled(c)) return;
        if(ReminderScheduler.ACTION.equals(intent.getAction()))c.sendBroadcast(new Intent(ReminderScheduler.FOREGROUND).setPackage(c.getPackageName()).putExtra("weekly",ReminderScheduler.reviewDue(c)));
        if (ReminderScheduler.ACTION.equals(intent.getAction()) && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManager n = c.getSystemService(NotificationManager.class);
            n.createNotificationChannel(new NotificationChannel(CHANNEL, "Evening check-in", NotificationManager.IMPORTANCE_DEFAULT));
            boolean weekly=ReminderScheduler.reviewDue(c);
            Intent target=new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("checkIn",true).putExtra("weekly",weekly);
            PendingIntent open = PendingIntent.getActivity(c, 21, target, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if(weekly)c.getSharedPreferences("reminder",0).edit().putString("lastReviewNudge",LocalDate.now().toString()).apply();
            n.notify(20, new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Your evening check-in")
                .setContentText(weekly?"Your check-in and a pattern review are ready when you are.":"Take a moment to complete your daily log.")
                .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());
        }
        ReminderScheduler.schedule(c);
    }
}
