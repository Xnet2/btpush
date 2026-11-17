package il.pushim;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** צבעים וכלי עזר לבניית המסכים בקוד. */
final class Ui {
    static final int BG = 0xFFF4F6FB;
    static final int CARD = 0xFFFFFFFF;
    static final int TEXT = 0xFF1C2130;
    static final int TEXT2 = 0xFF3D4456;
    static final int SUB = 0xFF6B7385;
    static final int LINE = 0xFFE9ECF3;
    static final int ACCENT = 0xFFF0566A;
    static final int GREEN = 0xFF22C55E;
    static final int AMBER = 0xFFF59E0B;
    static final int GRAY = 0xFFA0A7B6;
    static final int RED = 0xFFE5484D;

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable round(int color, float radiusPx, int stroke, int strokePx) {
        GradientDrawable d = round(color, radiusPx);
        d.setStroke(strokePx, stroke);
        return d;
    }

    /** רקע שמשנה גוון בלחיצה. */
    static Drawable press(Drawable normal, Drawable pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        rtlText(t);
        return t;
    }

    static void rtl(View v) {
        if (Build.VERSION.SDK_INT >= 17) v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    static void rtlText(TextView t) {
        if (Build.VERSION.SDK_INT >= 17) {
            t.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
            t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        rtl(l);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        rtl(l);
        return l;
    }

    static View dot(Context c, int color, int sizeDp) {
        View v = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackgroundDrawable(d);
        v.setLayoutParams(lp(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    @SuppressWarnings("deprecation")
    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
    }
}
