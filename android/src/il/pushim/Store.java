package il.pushim;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** שמירת הפריטים. כששמירה כבויה — רק בזיכרון, עד שהאפליקציה נסגרת. */
final class Store extends SQLiteOpenHelper {
    private static Store inst;
    private static final ArrayList<Item> mem = new ArrayList<>();
    private static final int MAX_ROWS = 3000;
    private final Context app;

    static synchronized Store get(Context c) {
        if (inst == null) inst = new Store(c.getApplicationContext());
        return inst;
    }

    private Store(Context c) {
        super(c, "pushim.db", null, 2);
        app = c;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY, src TEXT, sub TEXT, title TEXT, body TEXT, time INTEGER, icon TEXT)");
        db.execSQL("CREATE INDEX items_time ON items(time)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) db.execSQL("ALTER TABLE items ADD COLUMN icon TEXT");
    }

    /** מוסיף פריט. מחזיר false אם כבר קיים. */
    synchronized boolean add(Item it) {
        for (Item m : mem) if (m.id.equals(it.id)) return false;
        if (!Prefs.save(app)) {
            mem.add(0, it);
            while (mem.size() > 300) mem.remove(mem.size() - 1);
            return true;
        }
        ContentValues v = new ContentValues();
        v.put("id", it.id);
        v.put("src", it.src);
        v.put("sub", it.sub);
        v.put("title", it.title);
        v.put("body", it.text);
        v.put("time", it.time);
        v.put("icon", it.icon);
        long r = getWritableDatabase().insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return r != -1;
    }

    synchronized List<Item> list(String src, int limit) {
        List<Item> out = new ArrayList<>();
        if (!Prefs.save(app)) {
            for (Item m : mem) if (src == null || src.equals(m.src)) out.add(m);
            return out;
        }
        Cursor c = getReadableDatabase().query("items", null,
                src == null ? null : "src = ?", src == null ? null : new String[]{src},
                null, null, "time DESC", String.valueOf(limit));
        try {
            while (c.moveToNext()) {
                Item it = new Item();
                it.id = c.getString(c.getColumnIndex("id"));
                it.src = c.getString(c.getColumnIndex("src"));
                it.sub = c.getString(c.getColumnIndex("sub"));
                it.title = c.getString(c.getColumnIndex("title"));
                it.text = c.getString(c.getColumnIndex("body"));
                it.time = c.getLong(c.getColumnIndex("time"));
                it.icon = c.getString(c.getColumnIndex("icon"));
                out.add(it);
            }
        } finally {
            c.close();
        }
        return out;
    }

    synchronized int count() {
        if (!Prefs.save(app)) return mem.size();
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    /** מוחק מה שישן מהזמן שנבחר בהגדרות. */
    synchronized void purge() {
        if (!Prefs.save(app)) return;
        SQLiteDatabase db = getWritableDatabase();
        int h = Prefs.keepHours(app);
        if (h > 0) {
            long cut = System.currentTimeMillis() - h * 3600000L;
            db.delete("items", "time < ?", new String[]{String.valueOf(cut)});
        }
        db.execSQL("DELETE FROM items WHERE id NOT IN (SELECT id FROM items ORDER BY time DESC LIMIT " + MAX_ROWS + ")");
    }

    synchronized void delete(String id) {
        for (int i = mem.size() - 1; i >= 0; i--) if (mem.get(i).id.equals(id)) mem.remove(i);
        getWritableDatabase().delete("items", "id = ?", new String[]{id});
    }

    synchronized void clear() {
        mem.clear();
        getWritableDatabase().delete("items", null, null);
    }
}
