package il.pushim;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** מתג מעוצב (נראה אותו דבר בכל גרסת אנדרואיד). */
final class Toggle extends View {
    interface OnChange { void changed(boolean on); }

    private boolean on;
    private float pos;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private OnChange listener;
    private int color = Ui.ACCENT;

    Toggle(Context c, boolean initial) {
        super(c);
        on = initial;
        pos = on ? 1f : 0f;
        setClickable(true);
        setOnClickListener(v -> setOn(!on, true));
    }

    void setListener(OnChange l) { listener = l; }
    void setColor(int c) { color = c; invalidate(); }
    boolean isOn() { return on; }

    void setOn(boolean value, boolean notify) {
        if (value == on) return;
        on = value;
        ValueAnimator a = ValueAnimator.ofFloat(pos, on ? 1f : 0f);
        a.setDuration(160);
        a.addUpdateListener(an -> { pos = (Float) an.getAnimatedValue(); invalidate(); });
        a.start();
        if (notify && listener != null) listener.changed(on);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Ui.dp(getContext(), 48), Ui.dp(getContext(), 28));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pad = Ui.dp(getContext(), 2);
        rect.set(0, 0, w, h);
        paint.setColor(blend(0xFFD5D9E3, color, pos));
        c.drawRoundRect(rect, h / 2, h / 2, paint);
        float r = h / 2 - pad;
        // במצב "פועל" העיגול זז לצד שמאל (כמו במתגים בעברית)
        float cx = (w - pad - r) - pos * (w - 2 * pad - 2 * r);
        paint.setColor(0x22000000);
        c.drawCircle(cx, h / 2 + pad / 2, r, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(cx, h / 2, r, paint);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
