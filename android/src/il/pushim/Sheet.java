package il.pushim;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * חלונית מודרנית שעולה מלמטה (במקום חלונות המערכת): כותרת, שורות פעולה, בחירה אחת,
 * בחירה מרובה עם לוגו לכל שורה, וכפתורים. רקע מעומעם, פינות מעוגלות ואנימציה.
 */
final class Sheet {
    interface Pick { void picked(int index); }
    interface Multi { void done(boolean[] checked); }

    private final Activity a;
    private final Dialog dialog;
    private final FrameLayout dim;
    private final LinearLayout card, body, footer;
    private final ScrollView scroll;
    private boolean[] multiState;
    private Runnable onCancel;

    /** נקרא כשסוגרים בלי לבחור (לחיצה בחוץ או "חזרה"). */
    Sheet onCancel(Runnable r) { onCancel = r; return this; }

    private void cancel() {
        dismiss();
        if (onCancel != null) onCancel.run();
    }
    private final List<Mark> marks = new ArrayList<>();

    private Sheet(Activity a, String title, String subtitle) {
        this.a = a;
        dialog = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dim = new FrameLayout(a);
        dim.setBackgroundColor(0x66000000);
        dim.setOnClickListener(v -> cancel());
        dialog.setOnCancelListener(d -> { if (onCancel != null) onCancel.run(); });

        card = Ui.col(a);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        float r = dp(22);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackgroundDrawable(bg);
        card.setClickable(true);
        card.setPadding(0, dp(8), 0, dp(10));

        // ידית קטנה למעלה
        View handle = new View(a);
        handle.setBackgroundDrawable(Ui.round(0xFFDADDE2, dp(2)));
        LinearLayout.LayoutParams hl = Ui.lp(dp(36), dp(4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(10);
        card.addView(handle, hl);

        if (title != null) {
            TextView t = Ui.text(a, title, 18, Ui.TG_NAME, true);
            t.setPadding(dp(22), dp(2), dp(22), subtitle == null ? dp(8) : 0);
            card.addView(t, Ui.lp(-1, -2));
        }
        if (subtitle != null) {
            TextView s = Ui.text(a, subtitle, 14, Ui.TG_GRAY_TEXT, false);
            s.setLineSpacing(0, 1.15f);
            s.setPadding(dp(22), dp(4), dp(22), dp(10));
            card.addView(s, Ui.lp(-1, -2));
        }
        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        body = Ui.col(a);
        scroll.addView(body);
        card.addView(scroll, Ui.lp(-1, -2));
        footer = Ui.row(a);
        footer.setPadding(dp(16), dp(10), dp(16), dp(4));
        footer.setVisibility(View.GONE);
        card.addView(footer, Ui.lp(-1, -2));

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        dim.addView(card, cl);
        dialog.setContentView(dim);
    }

    static Sheet create(Activity a, String title) { return new Sheet(a, title, null); }
    static Sheet create(Activity a, String title, String subtitle) { return new Sheet(a, title, subtitle); }

    private int dp(float v) { return Ui.dp(a, v); }

    // ---------------------------------------------------------------- שורות

    private LinearLayout baseRow(View icon, String text, String sub, int textColor) {
        LinearLayout row = Ui.row(a);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(16), dp(8), dp(22), dp(8));
        row.setBackgroundDrawable(Ui.press(new ColorDrawable(0), new ColorDrawable(0xFFF3F4F6)));
        if (icon != null) {
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(36), dp(36));
            il.setMargins(dp(14), 0, 0, 0);   // רווח בצד של הטקסט (משמאל לאייקון)
            row.addView(icon, il);
        }
        LinearLayout texts = Ui.col(a);
        TextView t = Ui.text(a, text, 16, textColor, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(t);
        if (sub != null && sub.length() > 0) {
            TextView s = Ui.text(a, sub, 13, Ui.TG_GRAY_TEXT, false);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(s);
        }
        row.addView(texts, Ui.lp(0, -2, 1));
        return row;
    }

    /** שורת פעולה: לחיצה מבצעת וסוגרת. */
    Sheet action(View icon, String text, String sub, Runnable run) {
        return action(icon, text, sub, Ui.TG_NAME, run);
    }

    Sheet action(View icon, String text, String sub, int color, Runnable run) {
        LinearLayout row = baseRow(icon, text, sub, color);
        row.setOnClickListener(v -> { dismiss(); if (run != null) run.run(); });
        body.addView(row, Ui.lp(-1, -2));
        return this;
    }

    /** כותרת קבוצה קטנה בתוך הרשימה. */
    Sheet header(String text) {
        TextView t = Ui.text(a, text, 13.5f, Ui.ACCENT, true);
        t.setPadding(dp(22), dp(12), dp(22), dp(4));
        body.addView(t, Ui.lp(-1, -2));
        return this;
    }

    /** בחירה אחת: עיגול מסומן ליד הנבחר; לחיצה בוחרת וסוגרת. */
    Sheet choice(String[] labels, int selected, Pick pick) {
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout row = baseRow(null, labels[i], null, Ui.TG_NAME);
            Mark m = new Mark(a, true);
            m.setChecked(i == selected);
            row.addView(m, markLp());
            row.setOnClickListener(v -> { dismiss(); pick.picked(idx); });
            body.addView(row, Ui.lp(-1, -2));
        }
        return this;
    }

    /** בחירה מרובה: לכל שורה לוגו (אפשר null), טקסט ותת-טקסט. */
    Sheet multi(List<View> icons, String[] labels, String[] subs, boolean[] checked) {
        multiState = checked.clone();
        marks.clear();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout row = baseRow(icons == null ? null : icons.get(i), labels[i], subs == null ? null : subs[i], Ui.TG_NAME);
            Mark m = new Mark(a, false);
            m.setChecked(multiState[i]);
            marks.add(m);
            row.addView(m, markLp());
            row.setOnClickListener(v -> { multiState[idx] = !multiState[idx]; m.setChecked(multiState[idx]); });
            body.addView(row, Ui.lp(-1, -2));
        }
        return this;
    }

    /** מסמן/מנקה את כל השורות בבחירה המרובה. */
    void setAll(boolean on) {
        if (multiState == null) return;
        for (int i = 0; i < multiState.length; i++) { multiState[i] = on; marks.get(i).setChecked(on); }
    }

    boolean[] multiState() { return multiState; }

    /** כפתור בתחתית. kind: 0 = משני (טקסט), 1 = ראשי (מלא), 2 = מחיקה (אדום מלא). */
    Sheet button(String text, int kind, Runnable run) {
        footer.setVisibility(View.VISIBLE);
        TextView b = Ui.center(Ui.text(a, text, 15, kind == 0 ? Ui.ACCENT : 0xFFFFFFFF, true));
        int color = kind == 2 ? Ui.RED : Ui.ACCENT;
        if (kind == 0) b.setBackgroundDrawable(Ui.press(Ui.round(Ui.alpha(Ui.ACCENT, 0x12), dp(22)), Ui.round(Ui.alpha(Ui.ACCENT, 0x24), dp(22))));
        else b.setBackgroundDrawable(Ui.press(Ui.round(color, dp(22)), Ui.round(darker(color), dp(22))));
        b.setOnClickListener(v -> { dismiss(); if (run != null) run.run(); });
        LinearLayout.LayoutParams l = Ui.lp(0, dp(44), 1);
        l.setMargins(dp(5), 0, dp(5), 0);
        footer.addView(b, l);
        return this;
    }

    /** כפתור שלא סוגר את החלונית (למשל "סמן הכל"). */
    Sheet keepOpenButton(String text, Runnable run) {
        footer.setVisibility(View.VISIBLE);
        TextView b = Ui.center(Ui.text(a, text, 15, Ui.TG_GRAY_TEXT, true));
        b.setBackgroundDrawable(Ui.press(Ui.round(0xFFF1F2F5, dp(22)), Ui.round(0xFFE6E8EC, dp(22))));
        b.setOnClickListener(v -> { if (run != null) run.run(); });
        LinearLayout.LayoutParams l = Ui.lp(0, dp(44), 1);
        l.setMargins(dp(5), 0, dp(5), 0);
        footer.addView(b, l);
        return this;
    }

    private static int darker(int c) {
        int r = (int) (((c >> 16) & 0xFF) * 0.88f), g = (int) (((c >> 8) & 0xFF) * 0.88f), b = (int) ((c & 0xFF) * 0.88f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    void show() {
        // גובה מקסימלי: 82% מהמסך
        int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.82f);
        card.measure(View.MeasureSpec.makeMeasureSpec(a.getResources().getDisplayMetrics().widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        if (card.getMeasuredHeight() > maxH) {
            int other = card.getMeasuredHeight() - scroll.getMeasuredHeight();
            scroll.setLayoutParams(Ui.lp(-1, Math.max(dp(120), maxH - other)));
        }
        dialog.show();
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(160).start();
        card.setTranslationY(dp(300));
        card.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    void dismiss() {
        if (!dialog.isShowing()) return;
        card.animate().translationY(card.getHeight()).setDuration(160).start();
        dim.animate().alpha(0f).setDuration(170).withEndAction(() -> {
            try { dialog.dismiss(); } catch (Exception ignored) { }
        }).start();
    }

    // ---------------------------------------------------------------- עזרים

    /** לוגו עגול לערוץ (לשורות ברשימות). */
    static View avatar(Activity a, Item it) {
        ImageView iv = new ImageView(a);
        iv.setImageBitmap(Icons.get(a, it, Ui.dp(a, 36)));
        return iv;
    }

    /** אייקון בקו בתוך עיגול בהיר (לשורות פעולה). */
    static View glyph(Activity a, int kind, int color) {
        FrameLayout f = new FrameLayout(a);
        f.setBackgroundDrawable(Ui.round(Ui.alpha(color, 0x18), Ui.dp(a, 18)));
        Glyph g = new Glyph(a, kind, color);
        g.setWeight(1.15f);
        int p = Ui.dp(a, 8);
        g.setPadding(p, p, p, p);
        f.addView(g, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    /** סימון: עיגול מלא עם וי (בחירה) או ריבוע מעוגל (בחירה מרובה). */
    static final class Mark extends View {
        private final boolean radio;
        private boolean checked;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Mark(Activity a, boolean radio) { super(a); this.radio = radio; }

        void setChecked(boolean c) { checked = c; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), s = Math.min(w, h);
            float cx = w / 2, cy = h / 2, r = s / 2 - 1;
            if (checked) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.ACCENT);
                if (radio) c.drawCircle(cx, cy, r, p);
                else c.drawRoundRect(new android.graphics.RectF(cx - r, cy - r, cx + r, cy + r), s * 0.28f, s * 0.28f, p);
                Glyph.draw(c, Glyph.CHECK, cx, cy, s * 0.62f, 0xFFFFFFFF);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.09f);
                p.setColor(0xFFC3C8CF);
                float rr = r - s * 0.05f;
                if (radio) c.drawCircle(cx, cy, rr, p);
                else c.drawRoundRect(new android.graphics.RectF(cx - rr, cy - rr, cx + rr, cy + rr), s * 0.26f, s * 0.26f, p);
            }
        }
    }

    /** מקום לסימון בצד שמאל של השורה, עם רווח מהטקסט. */
    private LinearLayout.LayoutParams markLp() {
        LinearLayout.LayoutParams l = Ui.lp(dp(22), dp(22));
        l.setMargins(0, 0, dp(12), 0);
        return l;
    }
}
