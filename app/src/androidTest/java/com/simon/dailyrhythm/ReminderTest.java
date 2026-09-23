package com.simon.dailyrhythm;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReminderTest {
    @Test public void eveningReminderKeepsLocalTimeAcrossDaylightSaving(){
        ZoneId zone=ZoneId.of("America/Chicago");
        ZonedDateTime now=ZonedDateTime.of(2026,10,31,21,0,0,0,zone);
        ZonedDateTime next=ReminderScheduler.nextCheckIn(now);
        assertEquals(20,next.getHour());assertEquals(LocalDate.of(2026,11,1),next.toLocalDate());
        assertEquals(ZoneOffset.ofHours(-6),next.getOffset());
    }
    @Test public void weeklyReviewOnlyNudgesOnSundayOnceAndRespectsOff(){
        LocalDate sunday=LocalDate.of(2026,9,27);
        assertTrue(ReminderScheduler.reviewDue(7,"","",sunday));
        assertFalse(ReminderScheduler.reviewDue(7,"","",sunday.plusDays(1)));
        assertFalse(ReminderScheduler.reviewDue(7,"",sunday.toString(),sunday));
        assertFalse(ReminderScheduler.reviewDue(0,"","",sunday));
        assertFalse(ReminderScheduler.reviewDue(7,"2026-09-25","",sunday));
    }
}
