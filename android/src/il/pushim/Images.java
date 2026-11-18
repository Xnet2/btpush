package il.pushim;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** תמונות של עדכונים: נשלחות מהמחשב רק כשמבקשים, ונשמרות בנגן. */
final class Images {
    /** תמונות שנשלחה עליהן בקשה ועדיין לא הגיעו ("id#idx"). */
    static final Set<String> loading = Collections.synchronizedSet(new HashSet<String>());
    /** תמונות שהמחשב לא הצליח להביא (בשאיבה של תמונות לא מנסים אותן שוב). */
    static final Set<String> failed = Collections.synchronizedSet(new HashSet<String>());

    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) { return b.getRowBytes() * b.getHeight(); }
    };

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "img");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static String safe(String id) {
        return id.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    static File file(Context c, String id, int idx) {
        return new File(dir(c), safe(id) + "_" + idx + ".jpg");
    }

    static boolean has(Context c, String id, int idx) {
        return file(c, id, idx).exists();
    }

    static boolean isLoading(String id, int idx) {
        return loading.contains(id + "#" + idx);
    }

    /** כמה תמונות של הפריט עוד לא נטענו. */
    static int missing(Context c, Item it) {
        int n = 0;
        for (int i = 0; i < it.imgCount(); i++) if (!has(c, it.id, i)) n++;
        return n;
    }

    static void save(Context c, String id, int idx, String b64) throws Exception {
        byte[] data = Base64.decode(b64, Base64.DEFAULT);
        File f = file(c, id, idx);
        File tmp = new File(f.getPath() + ".tmp");
        FileOutputStream o = new FileOutputStream(tmp);
        o.write(data);
        o.close();
        if (!tmp.renameTo(f)) throw new Exception("שמירת התמונה נכשלה");
        cache.remove(f.getPath());
    }

    /** טוען תמונה מוקטנת לרוחב maxW (בפיקסלים). */
    static Bitmap load(Context c, String id, int idx, int maxW) {
        File f = file(c, id, idx);
        String key = f.getPath() + "@" + maxW;
        Bitmap b = cache.get(key);
        if (b != null) return b;
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxW) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        b = BitmapFactory.decodeFile(f.getPath(), o2);
        if (b != null) cache.put(key, b);
        return b;
    }

    /** מוחק תמונות של עדכונים שכבר נמחקו. */
    static void cleanup(Context c, Set<String> keepIds) {
        Set<String> keep = new HashSet<>();
        for (String id : keepIds) keep.add(safe(id));
        File[] files = dir(c).listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            int us = n.lastIndexOf('_');
            String id = us > 0 ? n.substring(0, us) : n;
            if (!keep.contains(id)) f.delete();
        }
        cache.evictAll();
    }
}
