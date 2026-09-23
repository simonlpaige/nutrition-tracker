package com.simon.dailyrhythm;
import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName="journal_snapshot")
public class JournalSnapshot {
    @PrimaryKey public int id=1;
    public int exportVersion=1;
    public long revision;
    @NonNull public String raw="";
    @NonNull public String sha256="";
}
