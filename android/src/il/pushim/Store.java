package il.pushim;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** שמירת הפריטים. כששמירה כבויה — רק בזיכרון, עד שהאפליקציה נסגרת. */
final class Store extends SQLiteOpenHelper {
    private static Store inst;
    private static final ArrayList<Item> mem = new ArrayList<>();
    private static final int MAX_ROWS = 3000;
    private final Context app;

    /** שורה ברשימת הערוצים. */
    static final class Chat {
        String key;
        Item last;
        int unread, count;
    }

    static synchronized Store get(Context c) {
        if (inst == null) inst = new Store(c.getApplicationContext());
        return inst;
    }

    private Store(Context c) {
        super(c, "pushim.db", null, 4);
        app = c;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY, src TEXT, sub TEXT, title TEXT, body TEXT, time INTEGER, icon TEXT, chat TEXT, recv INTEGER, img TEXT)");
        db.execSQL("CREATE INDEX items_time ON items(time)");
        db.execSQL("CREATE INDEX items_chat ON items(chat, time)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {
        if (o < 2) db.execSQL("ALTER TABLE items ADD COLUMN icon TEXT");
        if (o < 3) {
            db.execSQL("ALTER TABLE items ADD COLUMN chat TEXT");
            db.execSQL("ALTER TABLE items ADD COLUMN recv INTEGER");
            db.execSQL("UPDATE items SET recv = time");
            db.execSQL("UPDATE items SET chat = 'hagizra' WHERE src = 'hagizra'");
            db.execSQL("UPDATE items SET chat = CASE WHEN sub IS NULL OR sub = '' THEN 'authenti' ELSE 'a:' || sub END WHERE src = 'authenti'");
            db.execSQL("UPDATE items SET chat = 'pc:' || CASE WHEN icon IS NULL OR icon = '' THEN IFNULL(sub, '') ELSE icon END WHERE src NOT IN ('hagizra', 'authenti')");
            db.execSQL("CREATE INDEX items_chat ON items(chat, time)");
        }
        if (o < 4) db.execSQL("ALTER TABLE items ADD COLUMN img TEXT");
    }

    /** מוסיף פריט. מחזיר false אם כבר קיים. */
    synchronized boolean add(Item it) {
        return add(it, false);
    }

    /** quiet = עדכון ישן שנשאב: לא נספר כ"לא נקרא". */
    synchronized boolean add(Item it, boolean quiet) {
        long now = System.currentTimeMillis();
        it.recv = quiet ? Math.min(now, it.time) : now;
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
        v.put("chat", it.chat());
        v.put("recv", it.recv);
        v.put("img", it.img);
        long r = getWritableDatabase().insertWithOnConflict("items", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        return r != -1;
    }

    private static Item read(Cursor c) {
        Item it = new Item();
        it.id = c.getString(c.getColumnIndex("id"));
        it.src = c.getString(c.getColumnIndex("src"));
        it.sub = c.getString(c.getColumnIndex("sub"));
        it.title = c.getString(c.getColumnIndex("title"));
        it.text = c.getString(c.getColumnIndex("body"));
        it.time = c.getLong(c.getColumnIndex("time"));
        it.icon = c.getString(c.getColumnIndex("icon"));
        it.recv = c.getLong(c.getColumnIndex("recv"));
        it.img = c.getString(c.getColumnIndex("img"));
        return it;
    }

    /** רשימת הערוצים, מהעדכון האחרון לישן. src = null לכולם, או מקור אחד (תיקיה). */
    synchronized List<Chat> chats(String src) {
        List<Chat> out = new ArrayList<>();
        if (!Prefs.save(app)) {
            Map<String, Chat> map = new LinkedHashMap<>();
            for (Item m : mem) {           // mem מהחדש לישן
                if (src != null && !src.equals(m.src)) continue;
                Chat ch = map.get(m.chat());
                if (ch == null) { ch = new Chat(); ch.key = m.chat(); ch.last = m; map.put(ch.key, ch); }
                ch.count++;
                if (m.recv > Prefs.lastRead(app, ch.key)) ch.unread++;
            }
            out.addAll(map.values());
            return out;
        }
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT chat, MAX(time) t, COUNT(*) n FROM items" +
                (src == null ? "" : " WHERE src = ?") + " GROUP BY chat ORDER BY t DESC", src == null ? null : new String[]{src});
        try {
            while (c.moveToNext()) {
                Chat ch = new Chat();
                ch.key = c.getString(0);
                ch.count = c.getInt(2);
                out.add(ch);
            }
        } finally { c.close(); }
        for (Chat ch : out) {
            Cursor l = db.rawQuery("SELECT * FROM items WHERE chat = ? ORDER BY time DESC LIMIT 1", new String[]{ch.key});
            try { if (l.moveToFirst()) ch.last = read(l); } finally { l.close(); }
            Cursor u = db.rawQuery("SELECT COUNT(*) FROM items WHERE chat = ? AND recv > ?",
                    new String[]{ch.key, String.valueOf(Prefs.lastRead(app, ch.key))});
            try { if (u.moveToFirst()) ch.unread = u.getInt(0); } finally { u.close(); }
        }
        List<Chat> ok = new ArrayList<>();
        for (Chat ch : out) if (ch.last != null) ok.add(ch);
        return ok;
    }

    /** הפריטים של ערוץ אחד, מהישן לחדש (כמו בטלגרם). */
    synchronized List<Item> channel(String chat, int limit) {
        List<Item> out = new ArrayList<>();
        if (!Prefs.save(app)) {
            for (Item m : mem) if (chat.equals(m.chat())) out.add(m);
            Collections.reverse(out);
            return out;
        }
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM items WHERE chat = ? ORDER BY time DESC LIMIT " + limit, new String[]{chat});
        try { while (c.moveToNext()) out.add(read(c)); } finally { c.close(); }
        Collections.reverse(out);
        return out;
    }

    /** חיפוש בהודעות (כותרת, טקסט ושם הערוץ), מהחדש לישן. */
    synchronized List<Item> search(String q, int limit) {
        List<Item> out = new ArrayList<>();
        String needle = q.trim();
        if (needle.length() == 0) return out;
        if (!Prefs.save(app)) {
            String n = needle.toLowerCase();
            for (Item m : mem) {
                String hay = ((m.title == null ? "" : m.title) + " " + (m.text == null ? "" : m.text)).toLowerCase();
                if (hay.contains(n)) out.add(m);
                if (out.size() >= limit) break;
            }
            return out;
        }
        String like = "%" + needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM items WHERE title LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\' ORDER BY time DESC LIMIT " + limit,
                new String[]{like, like});
        try { while (c.moveToNext()) out.add(read(c)); } finally { c.close(); }
        return out;
    }

    synchronized int count() {
        if (!Prefs.save(app)) return mem.size();
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    /** כמה פריטים לא נקראו בכל הערוצים (למונה בתיקיות). */
    synchronized int unread(String src) {
        int n = 0;
        for (Chat ch : chats(src)) n += ch.unread;
        return n;
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
        cleanImages();
    }

    /** מוחק תמונות שמורות של עדכונים שכבר לא קיימים. */
    synchronized void cleanImages() {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (Item m : mem) ids.add(m.id);
        if (Prefs.save(app)) {
            Cursor c = getReadableDatabase().rawQuery("SELECT id FROM items WHERE img IS NOT NULL AND img != ''", null);
            try { while (c.moveToNext()) ids.add(c.getString(0)); } finally { c.close(); }
        }
        Images.cleanup(app, ids);
    }

    synchronized void clearChat(String chat) {
        for (int i = mem.size() - 1; i >= 0; i--) if (chat.equals(mem.get(i).chat())) mem.remove(i);
        getWritableDatabase().delete("items", "chat = ?", new String[]{chat});
        cleanImages();
    }

    synchronized void clear() {
        mem.clear();
        getWritableDatabase().delete("items", null, null);
        cleanImages();
    }
}
