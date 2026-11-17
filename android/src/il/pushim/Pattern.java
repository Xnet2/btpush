package il.pushim;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * רקע הצ'אט, ברוח טפט ברירת המחדל של טלגרם: גרדיאנט רך באפרסק,
 * ועליו דוגמה חוזרת של ציורים קטנים ושקופים (פעמונים, כוכבים, עיגולים, גלים).
 */
final class Pattern extends Drawable {
    static final int TOP = 0xFFFFE6D6, BOTTOM = 0xFFFFD2C4;
    private final Paint grad = new Paint();
    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private static Bitmap tileBmp;

    Pattern(float density) {
        this.density = density;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        grad.setShader(new LinearGradient(0, b.top, 0, b.bottom, TOP, BOTTOM, Shader.TileMode.CLAMP));
        if (tileBmp == null) tileBmp = makeTile((int) (220 * density), density);
        tile.setShader(new BitmapShader(tileBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawRect(b, grad);
        c.drawRect(b, tile);
    }

    private static Bitmap makeTile(int size, float d) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(0x1C9A4A35);
        Random r = new Random(7);
        int cells = 4;
        float cell = size / (float) cells;
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                float cx = cell * (i + 0.5f) + (r.nextFloat() - 0.5f) * cell * 0.4f;
                float cy = cell * (j + 0.5f) + (r.nextFloat() - 0.5f) * cell * 0.4f;
                float s = (11 + r.nextFloat() * 7) * d;
                c.save();
                c.rotate(r.nextFloat() * 50 - 25, cx, cy);
                switch ((i * 3 + j * 5 + r.nextInt(2)) % 6) {
                    case 0: bell(c, cx, cy, s, p); break;
                    case 1: star(c, cx, cy, s, p); break;
                    case 2: c.drawCircle(cx, cy, s * 0.55f, p); c.drawCircle(cx, cy, s * 0.18f, p); break;
                    case 3: waves(c, cx, cy, s, p); break;
                    case 4: bubble(c, cx, cy, s, p); break;
                    default: dots(c, cx, cy, s, p); break;
                }
                c.restore();
            }
        }
        return bmp;
    }

    private static void bell(Canvas c, float cx, float cy, float s, Paint p) {
        Path b = new Path();
        b.moveTo(cx - s * 0.55f, cy + s * 0.4f);
        b.quadTo(cx - s * 0.4f, cy + s * 0.25f, cx - s * 0.4f, cy - s * 0.05f);
        b.quadTo(cx - s * 0.4f, cy - s * 0.55f, cx, cy - s * 0.55f);
        b.quadTo(cx + s * 0.4f, cy - s * 0.55f, cx + s * 0.4f, cy - s * 0.05f);
        b.quadTo(cx + s * 0.4f, cy + s * 0.25f, cx + s * 0.55f, cy + s * 0.4f);
        b.close();
        c.drawPath(b, p);
        c.drawCircle(cx, cy + s * 0.55f, s * 0.1f, p);
    }

    private static void star(Canvas c, float cx, float cy, float s, Paint p) {
        Path st = new Path();
        float k = s * 0.14f;
        st.moveTo(cx, cy - s * 0.6f);
        st.quadTo(cx + k, cy - k, cx + s * 0.6f, cy);
        st.quadTo(cx + k, cy + k, cx, cy + s * 0.6f);
        st.quadTo(cx - k, cy + k, cx - s * 0.6f, cy);
        st.quadTo(cx - k, cy - k, cx, cy - s * 0.6f);
        st.close();
        c.drawPath(st, p);
    }

    private static void waves(Canvas c, float cx, float cy, float s, Paint p) {
        c.drawCircle(cx - s * 0.35f, cy + s * 0.35f, s * 0.08f, p);
        android.graphics.RectF r1 = new android.graphics.RectF(cx - s * 0.75f, cy - s * 0.05f, cx + s * 0.05f, cy + s * 0.75f);
        c.drawArc(r1, -90, 90, false, p);
        android.graphics.RectF r2 = new android.graphics.RectF(cx - s * 1.1f, cy - s * 0.4f, cx + s * 0.4f, cy + s * 1.1f);
        c.drawArc(r2, -90, 90, false, p);
    }

    private static void bubble(Canvas c, float cx, float cy, float s, Paint p) {
        android.graphics.RectF r = new android.graphics.RectF(cx - s * 0.6f, cy - s * 0.4f, cx + s * 0.6f, cy + s * 0.3f);
        c.drawRoundRect(r, s * 0.25f, s * 0.25f, p);
        c.drawLine(cx - s * 0.3f, cy + s * 0.3f, cx - s * 0.42f, cy + s * 0.55f, p);
    }

    private static void dots(Canvas c, float cx, float cy, float s, Paint p) {
        c.drawCircle(cx - s * 0.35f, cy, s * 0.07f, p);
        c.drawCircle(cx, cy, s * 0.07f, p);
        c.drawCircle(cx + s * 0.35f, cy, s * 0.07f, p);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
