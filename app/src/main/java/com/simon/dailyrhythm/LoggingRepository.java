package com.simon.dailyrhythm;

import android.content.Context;
import android.util.AtomicFile;
import androidx.room.Room;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Room is authoritative. The version-1 JSON mirror remains portable and rollback-compatible. */
public final class LoggingRepository {
    private static final Object LOCK=new Object();
    private static RhythmDatabase shared;
    private final RhythmDatabase database;
    private final AtomicFile journal;
    private final File original;
    private final AtomicFile exportStatus;
    static final int MAX_BYTES=8*1024*1024;

    public LoggingRepository(Context context) {
        synchronized(LOCK) {
            if(shared==null)shared=Room.databaseBuilder(context.getApplicationContext(),RhythmDatabase.class,"rhythm.db").build();
            database=shared;
        }
        journal=new AtomicFile(new File(context.getFilesDir(),"journal.json"));
        original=new File(context.getFilesDir(),"journal.pre-room.json");
        exportStatus=new AtomicFile(new File(context.getFilesDir(),"journal-export-status.json"));
    }
    LoggingRepository(RhythmDatabase db,File folder) {
        database=db;journal=new AtomicFile(new File(folder,"journal.json"));original=new File(folder,"journal.pre-room.json");exportStatus=new AtomicFile(new File(folder,"journal-export-status.json"));
    }
    public String load() {
        synchronized(LOCK) {
            try {
                JournalSnapshot stored=database.journal().snapshot();
                if(stored!=null) {
                    if(!digest(stored.raw).equals(stored.sha256))throw new IOException("Journal checksum mismatch");
                    mirror(stored.raw);return stored.raw;
                }
                String raw;
                try {raw=new String(journal.readFully(),StandardCharsets.UTF_8);}
                catch(FileNotFoundException e){return "null";}
                List<JournalEntry> entries=project(raw);
                if(!original.exists())write(new AtomicFile(original),raw);
                database.runInTransaction(()->store(raw,entries));
                write(new AtomicFile(new File(original.getParentFile(),"room-migration.json")),
                    new JSONObject().put("status","verified").put("entries",entries.size()).put("sha256",digest(raw)).put("at",java.time.Instant.now().toString()).toString());
                mirror(raw);return raw;
            } catch(Exception e) {return "{\"loadError\":true}";}
        }
    }
    public String save(String raw) {
        synchronized(LOCK) {
            try {
                // Initialize before writing so a failed legacy migration cannot be overwritten.
                if(load().contains("\"loadError\":true"))return "Storage needs recovery. Your original journal is retained.";
                List<JournalEntry> entries=project(raw);
                database.runInTransaction(()->store(raw,entries));
                // A failed mirror never rolls back an already committed Room transaction.
                mirror(raw);
                return "";
            } catch(Exception e) {return "Could not save. Your previous journal is intact.";}
        }
    }
    private void store(String raw,List<JournalEntry> entries) {
        try{write(exportStatus,"{\"valid\":false}");}catch(IOException e){throw new IllegalStateException("Cannot mark portable export pending",e);}
        JournalDao dao=database.journal();JournalSnapshot previous=dao.snapshot();
        dao.clearEntries();dao.insertEntries(entries);
        List<JournalEntry> copied=dao.entries();
        if(copied.size()!=entries.size())throw new IllegalStateException("Migration row count differs");
        Map<String,String> expected=new HashMap<>();
        for(JournalEntry e:entries)expected.put(e.kind+"/"+e.entryId,e.payload);
        for(JournalEntry e:copied)if(!e.payload.equals(expected.get(e.kind+"/"+e.entryId)))throw new IllegalStateException("Migration payload differs");
        JournalSnapshot snapshot=new JournalSnapshot();snapshot.raw=raw;snapshot.sha256=digest(raw);snapshot.revision=previous==null?1:previous.revision+1;dao.snapshot(snapshot);
    }
    private void mirror(String raw) {
        try {write(journal,raw);write(exportStatus,"{\"valid\":true,\"sha256\":\""+digest(raw)+"\"}");}catch(IOException ignored){/* Room remains authoritative; pending marker prevents a stale backup being verified. */}
    }
    private static void write(AtomicFile file,String raw)throws IOException {
        FileOutputStream out=null;
        try{out=file.startWrite();out.write(raw.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(IOException e){if(out!=null)file.failWrite(out);throw e;}
    }
    static String digest(String raw) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static List<JournalEntry> project(String raw)throws JSONException {
        if(raw.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new JSONException("Journal too large");
        JSONObject root=new JSONObject(raw);
        if(root.getInt("version")!=1)throw new JSONException("Unsupported export schema");
        List<JournalEntry> result=new ArrayList<>();
        add(result,"meals",root.getJSONArray("meals"));add(result,"stools",root.getJSONArray("stools"));
        add(result,"noBowel",root.getJSONArray("noBowel"));
        JSONObject body=root.optJSONObject("body");
        if(body!=null)for(Iterator<String> keys=body.keys();keys.hasNext();){String key=keys.next();add(result,"body."+key,body.getJSONArray(key));}
        JSONObject ai=root.optJSONObject("ai");
        if(ai!=null){add(result,"recipes",ai.optJSONArray("recipes"));add(result,"reviews",ai.optJSONArray("reviews"));JSONObject context=ai.optJSONObject("context");if(context!=null)for(Iterator<String> keys=context.keys();keys.hasNext();){String day=keys.next();JournalEntry e=entry("context",day,result.size(),context.getJSONObject(day));e.day=day;result.add(e);}}
        return result;
    }
    private static void add(List<JournalEntry> out,String kind,JSONArray array)throws JSONException {
        if(array==null)return;
        for(int i=0;i<array.length();i++){
            Object value=array.get(i);
            if(value instanceof JSONObject){JSONObject o=(JSONObject)value;String id=o.optString("id",Integer.toString(i));out.add(entry(kind,id,i,o));}
            else if(kind.equals("noBowel")&&value instanceof String){JournalEntry e=new JournalEntry();e.kind=kind;e.entryId=(String)value;e.day=(String)value;e.position=i;e.payload=JSONObject.quote((String)value);out.add(e);}
            else throw new JSONException("Invalid event");
        }
    }
    private static Double number(JSONObject o,String key)throws JSONException {if(!o.has(key)||o.isNull(key))return null;Object v=o.get(key);if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue()))throw new JSONException("Invalid number");return ((Number)v).doubleValue();}
    private static JournalEntry entry(String kind,String id,int position,JSONObject o)throws JSONException {
        JournalEntry e=new JournalEntry();e.kind=kind;e.entryId=id;e.position=position;e.payload=o.toString();
        e.day=o.optString("day",null);e.at=o.optString("at",null);e.name=o.optString("name",null);e.notes=o.optString("notes",null);e.portion=o.optString("portion",null);e.ingredients=o.optString("ingredients",null);
        e.servings=number(o,"servings");e.calories=number(o,"calories");e.protein=number(o,"protein");e.carbs=number(o,"carbs");e.fat=number(o,"fat");e.fiber=number(o,"fiber");e.sugar=number(o,"sugar");e.sodium=number(o,"sodium");
        if(kind.equals("stools")){Double bristol=number(o,"type");if(bristol==null||bristol<1||bristol>7||bristol!=Math.rint(bristol))throw new JSONException("Invalid Bristol type");e.bristolType=bristol.intValue();}
        e.discomfort=number(o,"pain");e.waterMl=number(o,"ml");e.caffeineMg=number(o,"mg");e.vapeHits=number(o,"hits");e.orgasmCount=number(o,"count");e.mood=number(o,"mood");e.energy=number(o,"energy");e.anxiety=number(o,"anxiety");e.focus=number(o,"focus");e.stress=number(o,"stress");return e;
    }
}
