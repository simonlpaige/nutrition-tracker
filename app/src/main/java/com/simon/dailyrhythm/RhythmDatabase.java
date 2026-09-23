package com.simon.dailyrhythm;
import androidx.room.*;

@Database(entities={JournalEntry.class,JournalSnapshot.class},version=1,exportSchema=true)
public abstract class RhythmDatabase extends RoomDatabase {
    public abstract JournalDao journal();
}
