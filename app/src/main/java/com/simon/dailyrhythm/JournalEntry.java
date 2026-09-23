package com.simon.dailyrhythm;

import androidx.annotation.NonNull;
import androidx.room.*;

/** Indexed event fields plus the complete original payload, including unknown future fields. */
@Entity(tableName="journal_entries", primaryKeys={"kind","entryId"}, indices={@Index({"kind","day"})})
public class JournalEntry {
    @NonNull public String kind="";
    @NonNull public String entryId="";
    public int position;
    public String day, at, name, notes, portion, ingredients;
    public Double servings, calories, protein, carbs, fat, fiber, sugar, sodium;
    public Integer bristolType;
    public Double discomfort, waterMl, caffeineMg, vapeHits, orgasmCount, mood, energy, anxiety, focus, stress;
    @NonNull public String payload="";
}
