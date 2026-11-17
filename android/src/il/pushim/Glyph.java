package il.pushim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** אייקונים בקו דק ומעוגל, מצוירים בקוד (בלי קבצי תמונה). */
final class Glyph extends View {
    static final int GEAR = 0, BACK = 1, MUTE = 2, CHECK = 3, SEARCH = 4, CLOSE = 5, BELL = 6, BELL_OFF = 7,
            DOWNLOAD = 8, CHEVRON = 9, BT = 10, BOX = 11, WAVES = 12;
    private int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color;
    private float weight = 1f;

    Glyph(Context c, int kind, int color) {
        super(c);
        this.kind = kind;
        this.color = color;
    }

    void setColor(int c) { color = c; invalidate(); }
    void setKind(int k) { kind = k; invalidate(); }
    void setWeight(float w) { weight = w; invalidate(); }

    static void draw(Canvas cv, int kind, float cx, float cy, float s, int color) {
        drawWith(new Paint(Paint.ANTI_ALIAS_FLAG), cv, kind, cx, cy, s, color, 1f);
    }

    private static void drawWith(Paint p, Canvas cv, int kind, float cx, float cy, float s, int color, float weight) {
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.085f * weight);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case GEAR: {
                // גלגל שיניים בקו מתאר: 8 שיניים מעוגלות וחור באמצע
                float ro = s * 0.44f, ri = s * 0.33f;
                Path g = new Path();
                for (int i = 0; i < 8; i++) {
                    double base = Math.toRadians(i * 45 - 90);
                    double a0 = base - Math.toRadians(14), a1 = base - Math.toRadians(8), a2 = base + Math.toRadians(8), a3 = base + Math.toRadians(14);
                    float x0 = cx + (float) Math.cos(a0) * ri, y0 = cy + (float) Math.sin(a0) * ri;
                    if (i == 0) g.moveTo(x0, y0); else g.lineTo(x0, y0);
                    g.lineTo(cx + (float) Math.cos(a1) * ro, cy + (float) Math.sin(a1) * ro);
                    g.lineTo(cx + (float) Math.cos(a2) * ro, cy + (float) Math.sin(a2) * ro);
                    g.lineTo(cx + (float) Math.cos(a3) * ri, cy + (float) Math.sin(a3) * ri);
                    RectF in = new RectF(cx - ri, cy - ri, cx + ri, cy + ri);
                    g.arcTo(in, (float) Math.toDegrees(a3), 17f, false);
                }
                g.close();
                cv.drawPath(g, p);
                cv.drawCircle(cx, cy, s * 0.135f, p);
                break;
            }
            case BACK: {   // חץ ימינה (חזרה בעברית)
                cv.drawLine(cx - s * 0.34f, cy, cx + s * 0.32f, cy, p);
                cv.drawLine(cx + s * 0.34f, cy, cx + s * 0.06f, cy - s * 0.28f, p);
                cv.drawLine(cx + s * 0.34f, cy, cx + s * 0.06f, cy + s * 0.28f, p);
                break;
            }
            case MUTE: {   // רמקול מלא עם X (קטן, ליד שם ערוץ מושתק)
                p.setStyle(Paint.Style.FILL);
                Path sp = new Path();
                sp.moveTo(cx - s * 0.42f, cy - s * 0.13f);
                sp.lineTo(cx - s * 0.22f, cy - s * 0.13f);
                sp.lineTo(cx + s * 0.04f, cy - s * 0.36f);
                sp.lineTo(cx + s * 0.04f, cy + s * 0.36f);
                sp.lineTo(cx - s * 0.22f, cy + s * 0.13f);
                sp.lineTo(cx - s * 0.42f, cy + s * 0.13f);
                sp.close();
                cv.drawPath(sp, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.10f);
                cv.drawLine(cx + s * 0.18f, cy - s * 0.15f, cx + s * 0.44f, cy + s * 0.15f, p);
                cv.drawLine(cx + s * 0.44f, cy - s * 0.15f, cx + s * 0.18f, cy + s * 0.15f, p);
                break;
            }
            case CHECK: {
                cv.drawLine(cx - s * 0.30f, cy, cx - s * 0.08f, cy + s * 0.22f, p);
                cv.drawLine(cx - s * 0.08f, cy + s * 0.22f, cx + s * 0.32f, cy - s * 0.20f, p);
                break;
            }
            case SEARCH: {
                cv.drawCircle(cx - s * 0.07f, cy - s * 0.07f, s * 0.27f, p);
                cv.drawLine(cx + s * 0.13f, cy + s * 0.13f, cx + s * 0.36f, cy + s * 0.36f, p);
                break;
            }
            case CLOSE: {
                cv.drawLine(cx - s * 0.26f, cy - s * 0.26f, cx + s * 0.26f, cy + s * 0.26f, p);
                cv.drawLine(cx + s * 0.26f, cy - s * 0.26f, cx - s * 0.26f, cy + s * 0.26f, p);
                break;
            }
            case BELL:
            case BELL_OFF: {
                Path b = new Path();
                b.moveTo(cx - s * 0.34f, cy + s * 0.22f);
                b.quadTo(cx - s * 0.25f, cy + s * 0.12f, cx - s * 0.25f, cy - s * 0.04f);
                b.quadTo(cx - s * 0.25f, cy - s * 0.34f, cx, cy - s * 0.34f);
                b.quadTo(cx + s * 0.25f, cy - s * 0.34f, cx + s * 0.25f, cy - s * 0.04f);
                b.quadTo(cx + s * 0.25f, cy + s * 0.12f, cx + s * 0.34f, cy + s * 0.22f);
                b.close();
                cv.drawPath(b, p);
                cv.drawLine(cx - s * 0.08f, cy + s * 0.36f, cx + s * 0.08f, cy + s * 0.36f, p);
                cv.drawLine(cx, cy - s * 0.42f, cx, cy - s * 0.36f, p);
                if (kind == BELL_OFF) cv.drawLine(cx - s * 0.40f, cy - s * 0.40f, cx + s * 0.40f, cy + s * 0.40f, p);
                break;
            }
            case DOWNLOAD: {   // שאיבה: חץ למטה אל מגש
                cv.drawLine(cx, cy - s * 0.38f, cx, cy + s * 0.12f, p);
                cv.drawLine(cx - s * 0.20f, cy - s * 0.06f, cx, cy + s * 0.14f, p);
                cv.drawLine(cx + s * 0.20f, cy - s * 0.06f, cx, cy + s * 0.14f, p);
                Path t = new Path();
                t.moveTo(cx - s * 0.36f, cy + s * 0.08f);
                t.lineTo(cx - s * 0.36f, cy + s * 0.36f);
                t.lineTo(cx + s * 0.36f, cy + s * 0.36f);
                t.lineTo(cx + s * 0.36f, cy + s * 0.08f);
                cv.drawPath(t, p);
                break;
            }
            case CHEVRON: {   // חץ קטן שמאלה (כניסה בעברית)
                cv.drawLine(cx + s * 0.12f, cy - s * 0.24f, cx - s * 0.12f, cy, p);
                cv.drawLine(cx - s * 0.12f, cy, cx + s * 0.12f, cy + s * 0.24f, p);
                break;
            }
            case BT: {
                Path t = new Path();
                t.moveTo(cx - s * 0.20f, cy - s * 0.18f);
                t.lineTo(cx + s * 0.20f, cy + s * 0.18f);
                t.lineTo(cx, cy + s * 0.38f);
                t.lineTo(cx, cy - s * 0.38f);
                t.lineTo(cx + s * 0.20f, cy - s * 0.18f);
                t.lineTo(cx - s * 0.20f, cy + s * 0.18f);
                cv.drawPath(t, p);
                break;
            }
            case BOX: {   // אחסון: ארגז עם מכסה
                RectF lid = new RectF(cx - s * 0.38f, cy - s * 0.32f, cx + s * 0.38f, cy - s * 0.12f);
                cv.drawRoundRect(lid, s * 0.05f, s * 0.05f, p);
                Path body = new Path();
                body.moveTo(cx - s * 0.32f, cy - s * 0.12f);
                body.lineTo(cx - s * 0.32f, cy + s * 0.34f);
                body.lineTo(cx + s * 0.32f, cy + s * 0.34f);
                body.lineTo(cx + s * 0.32f, cy - s * 0.12f);
                cv.drawPath(body, p);
                cv.drawLine(cx - s * 0.10f, cy + s * 0.04f, cx + s * 0.10f, cy + s * 0.04f, p);
                break;
            }
            case WAVES: {   // מקורות: נקודה וגלים
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx - s * 0.24f, cy + s * 0.24f, s * 0.08f, p);
                p.setStyle(Paint.Style.STROKE);
                RectF r1 = new RectF(cx - s * 0.56f, cy - s * 0.08f, cx + s * 0.08f, cy + s * 0.56f);
                cv.drawArc(r1, -90, 90, false, p);
                RectF r2 = new RectF(cx - s * 0.86f, cy - s * 0.38f, cx + s * 0.38f, cy + s * 0.86f);
                cv.drawArc(r2, -90, 90, false, p);
                break;
            }
            default:
                break;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());
        drawWith(p, c, kind, getWidth() / 2f, getHeight() / 2f, s, color, weight);
    }
}
