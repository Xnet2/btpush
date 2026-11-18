package il.pushim;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** תמונה בפינות מעוגלות, ממלאת את המקום (חיתוך מהמרכז). עובד גם עם האצת חומרה. */
final class RoundImage extends Drawable {
    private final Bitmap bmp;
    private final float radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();

    RoundImage(Bitmap bmp, float radius) {
        this.bmp = bmp;
        this.radius = radius;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        rect.set(b);
        BitmapShader sh = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max(b.width() / (float) bmp.getWidth(), b.height() / (float) bmp.getHeight());
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(b.left + (b.width() - bmp.getWidth() * scale) / 2f, b.top + (b.height() - bmp.getHeight() * scale) / 2f);
        sh.setLocalMatrix(m);
        paint.setShader(sh);
    }

    @Override
    public void draw(Canvas c) {
        c.drawRoundRect(rect, radius, radius, paint);
    }

    @Override public int getIntrinsicWidth() { return bmp.getWidth(); }
    @Override public int getIntrinsicHeight() { return bmp.getHeight(); }
    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    // ---------------------------------------------------------------- טשטוש (לרקע של תמונה במסך מלא)

    /** טשטוש מהיר: מקטינים, מטשטשים בכמה מעברים של ממוצע, ומחזירים תמונה קטנה (מוגדלת בתצוגה). */
    static Bitmap blur(Bitmap src, int factor, int radius) {
        int w = Math.max(1, src.getWidth() / factor), h = Math.max(1, src.getHeight() / factor);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        int[] tmp = new int[w * h];
        for (int pass = 0; pass < 3; pass++) {
            boxH(px, tmp, w, h, radius);
            boxV(tmp, px, w, h, radius);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int ra = 0, ga = 0, ba = 0, row = y * w;
            for (int i = -r; i <= r; i++) {
                int p = in[row + Math.min(w - 1, Math.max(0, i))];
                ra += (p >> 16) & 0xFF; ga += (p >> 8) & 0xFF; ba += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int pOut = in[row + Math.max(0, x - r)];
                int pIn = in[row + Math.min(w - 1, x + r + 1)];
                ra += ((pIn >> 16) & 0xFF) - ((pOut >> 16) & 0xFF);
                ga += ((pIn >> 8) & 0xFF) - ((pOut >> 8) & 0xFF);
                ba += (pIn & 0xFF) - (pOut & 0xFF);
            }
        }
    }

    private static void boxV(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int ra = 0, ga = 0, ba = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[Math.min(h - 1, Math.max(0, i)) * w + x];
                ra += (p >> 16) & 0xFF; ga += (p >> 8) & 0xFF; ba += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = 0xFF000000 | ((ra / div) << 16) | ((ga / div) << 8) | (ba / div);
                int pOut = in[Math.max(0, y - r) * w + x];
                int pIn = in[Math.min(h - 1, y + r + 1) * w + x];
                ra += ((pIn >> 16) & 0xFF) - ((pOut >> 16) & 0xFF);
                ga += ((pIn >> 8) & 0xFF) - ((pOut >> 8) & 0xFF);
                ba += (pIn & 0xFF) - (pOut & 0xFF);
            }
        }
    }
}
