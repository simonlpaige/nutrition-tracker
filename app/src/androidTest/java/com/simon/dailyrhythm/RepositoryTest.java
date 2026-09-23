package com.simon.dailyrhythm;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.room.Room;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RepositoryTest {
    RhythmDatabase db;File folder;LoggingRepository repo;
    @Before public void setup()throws Exception {
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        db=Room.inMemoryDatabaseBuilder(c,RhythmDatabase.class).build();folder=Files.createTempDirectory(c.getCacheDir().toPath(),"migration-test").toFile();repo=new LoggingRepository(db,folder);
    }
    @After public void close(){db.close();for(File f:folder.listFiles())f.delete();folder.delete();}
    static String readString(java.nio.file.Path p)throws IOException {return new String(Files.readAllBytes(p),StandardCharsets.UTF_8);}
    static void writeString(java.nio.file.Path p,String s)throws IOException {Files.write(p,s.getBytes(StandardCharsets.UTF_8));}
    String fixture()throws Exception {
        return new JSONObject().put("version",1).put("meals",new JSONArray().put(new JSONObject().put("id","oldest").put("day","2025-01-02").put("calories",JSONObject.NULL).put("notes","Original private note ✓").put("futureField","retained")).put(new JSONObject().put("id","newest").put("day","2026-09-22").put("fiber",12.5)))
            .put("stools",new JSONArray().put(new JSONObject().put("id","stool").put("type",4).put("notes","text history")))
            .put("noBowel",new JSONArray().put("2026-09-20")).put("body",new JSONObject().put("water",new JSONArray().put(new JSONObject().put("id","water").put("ml",355))))
            .put("ai",new JSONObject().put("context",new JSONObject().put("2026-09-22",new JSONObject().put("foodComplete",true))).put("recipes",new JSONArray()).put("reviews",new JSONArray().put(new JSONObject().put("body","Preserve review"))))
            .put("goals",new JSONObject().put("calories",JSONObject.NULL)).toString();
    }
    @Test public void migrationPreservesEveryPayloadAndOriginalFile()throws Exception {
        String raw=fixture();Files.write(new File(folder,"journal.json").toPath(),raw.getBytes(StandardCharsets.UTF_8));
        assertEquals(raw,repo.load());assertEquals(7,db.journal().entries().size());
        assertEquals(raw,readString(new File(folder,"journal.pre-room.json").toPath()));
        assertEquals(raw,db.journal().snapshot().raw);
        assertEquals(raw,repo.load());assertEquals(1,db.journal().snapshot().revision);
        for(JournalEntry e:db.journal().entries())if(e.entryId.equals("oldest")){assertNull(e.calories);assertEquals("retained",new JSONObject(e.payload).getString("futureField"));}
    }
    @Test public void editDeleteAndRestartUseDatabaseAndKeepRollbackOriginal()throws Exception {
        String original=fixture();writeString(new File(folder,"journal.json").toPath(),original);repo.load();
        JSONObject edited=new JSONObject(original);edited.getJSONArray("meals").remove(0);String changed=edited.toString();
        assertEquals("",repo.save(changed));assertEquals(6,db.journal().entries().size());
        assertEquals(changed,new LoggingRepository(db,folder).load());
        assertEquals(original,readString(new File(folder,"journal.pre-room.json").toPath()));
        assertEquals(changed,readString(new File(folder,"journal.json").toPath()));
    }
    @Test public void duplicateEntryRollsBackEntireTransaction()throws Exception {
        assertEquals("",repo.save(fixture()));String before=repo.load();JSONObject bad=new JSONObject(before);bad.getJSONArray("meals").put(bad.getJSONArray("meals").get(0));
        assertFalse(repo.save(bad.toString()).isEmpty());assertEquals(before,repo.load());assertEquals(7,db.journal().entries().size());
    }
    @Test public void corruptLegacyDataDoesNotCreateEmptySuccessfulMigration()throws Exception {
        writeString(new File(folder,"journal.json").toPath(),"broken json");
        assertTrue(repo.load().contains("loadError"));assertNull(db.journal().snapshot());assertFalse(repo.save(fixture()).isEmpty());
        assertEquals("broken json",readString(new File(folder,"journal.json").toPath()));
    }
    @Test public void bristolRangeValidationPreservesExistingRows()throws Exception {
        assertEquals("",repo.save(fixture()));JSONObject bad=new JSONObject(fixture());bad.getJSONArray("stools").getJSONObject(0).put("type",8);
        assertFalse(repo.save(bad.toString()).isEmpty());assertEquals(4,db.journal().entries().stream().filter(e->e.kind.equals("stools")).findFirst().get().bristolType.intValue());
    }
}
