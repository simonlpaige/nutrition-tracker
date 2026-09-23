package com.simon.dailyrhythm;
import androidx.room.*;
import java.util.List;

@Dao
public interface JournalDao {
    @Query("SELECT * FROM journal_snapshot WHERE id=1") JournalSnapshot snapshot();
    @Insert(onConflict=OnConflictStrategy.REPLACE) void snapshot(JournalSnapshot snapshot);
    @Query("SELECT * FROM journal_entries ORDER BY kind, position") List<JournalEntry> entries();
    @Query("DELETE FROM journal_entries") void clearEntries();
    @Insert void insertEntries(List<JournalEntry> entries);
}
