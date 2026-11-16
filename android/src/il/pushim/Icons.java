package il.pushim;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

import il.pushim.player.R;

/**
 * הלוגו של כל פריט: הלוגו של האתר (הגיזרה), עיגול עם האות הראשונה של הערוץ (אותנטי, עם הלוגו של האתר בפינה),
 * או האייקון של התוכנה במחשב (נשלח מהמחשב ונשמר בנגן).
 */
final class Icons {
    private static final int[] PALETTE = {
            0xFF2F7CF6, 0xFF8B5CF6, 0xFFE5484D, 0xFF12A594, 0xFFF59E0B,
            0xFFEC4899, 0xFF0EA5E9, 0xFF65A30D, 0xFF6366F1, 0xFFEA580C};
    private static final Map<String, Bitmap> cache = new HashMap<>();
    private static Bitmap authenti;

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "icons");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(Context c, String key) {
        return new File(dir(c), "a" + Integer.toHexString(key.hashCode()) + "_" + key.length() + ".png");
    }

    /** שמירת אייקון שהגיע מהמחשב. */
    static synchronized void save(Context c, String key, String png64) {
        if (key == null || key.length() == 0 || png64 == null) return;
        try {
            byte[] b = Base64.decode(png64, Base64.DEFAULT);
            FileOutputStream o = new FileOutputStream(file(c, key));
            o.write(b);
            o.close();
            // מנקים מהזיכרון גרסאות ישנות של האייקון
            for (String k : cache.keySet().toArray(new String[0])) if (k.startsWith("app:" + key + "|")) cache.remove(k);
        } catch (Exception ignored) { }
    }

    static synchronized Bitmap get(Context c, Item it, int size) {
        String key;
        if ("hagizra".equals(it.src)) key = "hagizra@" + size;
        else if ("authenti".equals(it.src)) key = "ch:" + it.sub + "@" + size;
        else key = "app:" + (it.icon == null ? "" : it.icon) + "|" + it.sub + "@" + size;
        Bitmap b = cache.get(key);
        if (b != null) return b;
        if ("hagizra".equals(it.src)) {
            b = logo(c, R.drawable.logo_hagizra, size, false);
        } else if ("authenti".equals(it.src)) {
            b = (it.sub == null || it.sub.length() == 0) ? logo(c, R.drawable.logo_authenti, size, true)
                    : letter(c, it.sub, size, true);
        } else {
            Bitmap app = null;
            if (it.icon != null && it.icon.length() > 0) {
                File f = file(c, it.icon);
                if (f.exists()) app = BitmapFactory.decodeFile(f.getAbsolutePath());
            }
            b = app != null ? framed(app, size) : letter(c, it.sub == null || it.sub.length() == 0 ? "מחשב" : it.sub, size, false);
        }
        if (cache.size() > 200) cache.clear();
        cache.put(key, b);
        return b;
    }

    /** לוגו של אתר בתוך עיגול לבן. */
    private static Bitmap logo(Context c, int res, int size, boolean pad) {
        Bitmap src = BitmapFactory.decodeResource(c.getResources(), res);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
        float in = pad ? size * 0.14f : 0;
        cv.drawBitmap(src, new Rect(0, 0, src.getWidth(), src.getHeight()), new RectF(in, in, size - in, size - in), p);
        if (pad) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, size / 48f));
            p.setColor(0xFFE3E7EF);
            cv.drawCircle(size / 2f, size / 2f, size / 2f - p.getStrokeWidth(), p);
        }
        return out;
    }

    /** אייקון של תוכנה על רקע עגול בהיר. */
    private static Bitmap framed(Bitmap app, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(0xFFF1F3F8);
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
        float in = size * 0.17f;
        cv.drawBitmap(app, new Rect(0, 0, app.getWidth(), app.getHeight()), new RectF(in, in, size - in, size - in), p);
        return out;
    }

    /** עיגול צבעוני עם האות הראשונה של השם, ואם צריך — הלוגו של אותנטי קטן בפינה. */
    private static Bitmap letter(Context c, String name, int size, boolean authentiBadge) {
        String clean = name.trim();
        String first = "?";
        for (int i = 0; i < clean.length(); i++) {
            char ch = clean.charAt(i);
            if (Character.isLetterOrDigit(ch)) { first = String.valueOf(ch).toUpperCase(); break; }
        }
        int color = PALETTE[(clean.hashCode() & 0x7FFFFFFF) % PALETTE.length];
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(color);
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(size * 0.46f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(first, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, p);
        if (authentiBadge) {
            if (authenti == null) authenti = BitmapFactory.decodeResource(c.getResources(), R.drawable.logo_authenti);
            float bs = size * 0.42f, x = size - bs, y = size - bs;
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(x + bs / 2f, y + bs / 2f, bs / 2f, p);
            float in = bs * 0.14f;
            cv.drawBitmap(authenti, new Rect(0, 0, authenti.getWidth(), authenti.getHeight()),
                    new RectF(x + in, y + in, x + bs - in, y + bs - in), p);
        }
        return out;
    }
}
