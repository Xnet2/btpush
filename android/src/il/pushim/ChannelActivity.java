package il.pushim;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** מסך ערוץ בסגנון טלגרם: בועות לבנות על רקע מעוטר, תאריכים בגלולות, השתקה ושאיבה. */
public final class ChannelActivity extends Activity implements LinkService.Listener, LinkService.BackfillCallback, LinkService.ImageCallback {
    /** הערוץ שפתוח עכשיו — עליו לא מקפיצים פוש (כמו בטלגרם). */
    static volatile String openChat;

    private static final String[] DAYS = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};
    private static final String[] MONTHS = {"בינואר", "בפברואר", "במרץ", "באפריל", "במאי", "ביוני", "ביולי", "באוגוסט", "בספטמבר", "באוקטובר", "בנובמבר", "בדצמבר"};
    private static final int[] PULL_HOURS = {6, 12, 24, 48, 72};
    private static final String[] PULL_NAMES = {"6 השעות האחרונות", "12 השעות האחרונות", "יום אחרון", "יומיים אחרונים", "3 ימים אחרונים"};

    private String chat, focusId;
    private final List<Object> rows = new ArrayList<>();
    private final Handler handler = new Handler();
    private Adapter adapter;
    private ListView list;
    private TextView subtitle, muteText, name, pullText;
    private Glyph muteIcon;
    private ImageView avatar;
    private LinearLayout pullStrip, emptyCard;
    private int count;
    private boolean pulling;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        chat = getIntent().getStringExtra("chat");
        focusId = getIntent().getStringExtra("focus");
        if (chat == null) { finish(); return; }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(0xFFFFFFFF);

        // ---- סרגל עליון: חזרה, לוגו, שם, ושאיבה
        LinearLayout header = Ui.col(this);
        header.setBackgroundColor(0xFFFFFFFF);
        if (android.os.Build.VERSION.SDK_INT >= 21) header.setElevation(dp(1.5f));
        LinearLayout bar = Ui.row(this);
        bar.setMinimumHeight(dp(56));
        bar.setPadding(dp(4), dp(4), dp(6), dp(4));
        bar.addView(Ui.iconButton(this, Glyph.BACK, 0xFF5F6670, v -> finish()));
        avatar = new ImageView(this);
        LinearLayout.LayoutParams al = Ui.lp(dp(38), dp(38));
        al.setMargins(dp(10), 0, dp(2), 0);
        bar.addView(avatar, al);
        LinearLayout titles = Ui.col(this);
        name = Ui.text(this, "", 16.5f, Ui.TG_NAME, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(name);
        subtitle = Ui.text(this, "", 13, Ui.TG_SUBTITLE, false);
        titles.addView(subtitle);
        bar.addView(titles, Ui.lp(0, -2, 1));
        bar.addView(Ui.iconButton(this, Glyph.DOWNLOAD, 0xFF5F6670, v -> choosePull()));
        header.addView(bar, Ui.lp(-1, -2));

        // פס התקדמות של שאיבה
        pullStrip = Ui.row(this);
        pullStrip.setGravity(Gravity.CENTER);
        pullStrip.setPadding(dp(12), dp(7), dp(12), dp(8));
        pullStrip.setBackgroundColor(Ui.alpha(Ui.ACCENT, 0x14));
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        pullStrip.addView(pb, Ui.lp(dp(18), dp(18)));
        pullText = Ui.text(this, "שואב עדכונים מהמחשב…", 13.5f, Ui.ACCENT, true);
        pullText.setPadding(0, 0, dp(8), 0);
        pullStrip.addView(pullText);
        pullStrip.setVisibility(View.GONE);
        header.addView(pullStrip, Ui.lp(-1, -2));
        root.addView(header, Ui.lp(-1, -2));
        root.addView(Ui.shadowLine(this));

        // ---- ההודעות
        FrameLayout body = new FrameLayout(this);
        body.setBackgroundDrawable(new Pattern(getResources().getDisplayMetrics().density));
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(0);
        list.setVerticalScrollBarEnabled(false);
        list.setStackFromBottom(true);
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL);
        list.setClipToPadding(false);
        list.setPadding(0, dp(6), 0, dp(8));
        Ui.rtl(list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));

        // ערוץ ריק: כרטיס באמצע עם כפתור שאיבה
        emptyCard = Ui.col(this);
        emptyCard.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyCard.setPadding(dp(22), dp(18), dp(22), dp(18));
        emptyCard.setBackgroundDrawable(Ui.round(0xF2FFFFFF, dp(18)));
        TextView et = Ui.center(Ui.text(this, "אין עדיין עדכונים בערוץ", 16, Ui.TG_NAME, true));
        emptyCard.addView(et, Ui.lp(-1, -2));
        TextView es = Ui.center(Ui.text(this, "אפשר לשאוב מהמחשב את העדכונים האחרונים, עד 3 ימים אחורה", 14, Ui.TG_GRAY_TEXT, false));
        es.setPadding(0, dp(4), 0, dp(14));
        emptyCard.addView(es, Ui.lp(-1, -2));
        LinearLayout eb = Ui.row(this);
        eb.setGravity(Gravity.CENTER);
        eb.setPadding(dp(20), 0, dp(20), 0);
        eb.setBackgroundDrawable(Ui.press(Ui.round(Ui.ACCENT, dp(20)), Ui.round(0xFFDE465C, dp(20))));
        Glyph eg = new Glyph(this, Glyph.DOWNLOAD, 0xFFFFFFFF);
        eg.setWeight(1.2f);
        eb.addView(eg, Ui.lp(dp(17), dp(17)));
        TextView ebt = Ui.text(this, "שאיבת עדכונים אחרונים", 14.5f, 0xFFFFFFFF, true);
        ebt.setPadding(0, 0, dp(8), 0);
        eb.addView(ebt, Ui.lp(-2, -2));
        eb.setOnClickListener(v -> choosePull());
        emptyCard.addView(eb, Ui.lp(-2, dp(40)));
        FrameLayout.LayoutParams ecl = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        ecl.setMargins(dp(28), 0, dp(28), 0);
        emptyCard.setVisibility(View.GONE);
        body.addView(emptyCard, ecl);
        root.addView(body, Ui.lp(-1, 0, 1));

        // ---- סרגל השתקה דק בתחתית, תוכן ממורכז
        View line = new View(this);
        line.setBackgroundColor(0xFFE6E8EB);
        root.addView(line, Ui.lp(-1, 1));
        LinearLayout muteBar = Ui.row(this);
        muteBar.setGravity(Gravity.CENTER);
        muteBar.setBackgroundDrawable(Ui.press(new android.graphics.drawable.ColorDrawable(0xFFFFFFFF),
                new android.graphics.drawable.ColorDrawable(0xFFF3F4F6)));
        muteIcon = new Glyph(this, Glyph.BELL_OFF, Ui.ACCENT);
        muteBar.addView(muteIcon, Ui.lp(dp(18), dp(18)));
        muteText = Ui.text(this, "", 14.5f, Ui.ACCENT, true);
        muteText.setPadding(0, 0, dp(7), 0);
        muteBar.addView(muteText, Ui.lp(-2, -2));
        muteBar.setOnClickListener(v -> {
            boolean m = !Prefs.muted(this, chat);
            Prefs.setMuted(this, chat, m);
            Toast.makeText(this, m ? "הערוץ הושתק: העדכונים ממשיכים להגיע, בלי פושים" : "הפושים מהערוץ הופעלו", Toast.LENGTH_SHORT).show();
            updateHeader();
        });
        root.addView(muteBar, Ui.lp(-1, dp(42)));
        setContentView(root);
        // נפתח מכפתור השאיבה שבמסך הראשי: מציגים מיד את בחירת הזמן
        if (getIntent().getBooleanExtra("pull", false)) list.post(this::choosePull);
    }

    @Override
    protected void onResume() {
        super.onResume();
        openChat = chat;
        LinkService.listener = this;
        LinkService.backfillCallback = this;
        LinkService.imageCallback = this;
        reload();
        Notifier.cancelChat(this, chat);
    }

    @Override
    protected void onPause() {
        if (chat != null && chat.equals(openChat)) openChat = null;
        if (LinkService.listener == this) LinkService.listener = null;
        if (LinkService.backfillCallback == this) LinkService.backfillCallback = null;
        if (LinkService.imageCallback == this) LinkService.imageCallback = null;
        super.onPause();
    }

    @Override public void onLinkChanged() { adapter.notifyDataSetChanged(); }

    @Override
    public void failed(String id, String error) {
        if (bulkTotal > 0) return;   // בטעינה מרוכזת לא מציפים בהודעות; הסיכום בסוף
        Toast.makeText(this, "התמונה לא נטענה" + (error != null && error.length() > 0 ? ": " + error : ""), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onItemsChanged() {
        reload();
        updateBulk();
    }

    // ---------------------------------------------------------------- שאיבה

    private static final int MODE_MESSAGES = 0, MODE_BOTH = 1, MODE_IMAGES = 2;
    private int pullMode, pullHours;

    /** שלב 1: מה לשאוב. שלב 2: כמה זמן אחורה. */
    private void choosePull() {
        if (pulling || bulkTotal > 0) return;
        if (LinkService.state != LinkService.CONNECTED) {
            Toast.makeText(this, "צריך חיבור למחשב כדי לשאוב עדכונים", Toast.LENGTH_LONG).show();
            return;
        }
        boolean pc = chat.startsWith("pc:");
        Sheet s = Sheet.create(this, "שאיבת עדכונים", pc ? "מהמחשב יגיעו ההתראות ששמורות במרכז ההודעות של Windows." : "העדכונים נשמרים בערוץ בשקט, בלי פושים.");
        s.action(Sheet.glyph(this, Glyph.DOWNLOAD, Ui.ACCENT), "הודעות", "רק הטקסט, הכי מהיר", () -> chooseHours(MODE_MESSAGES));
        if (!pc) {
            s.action(Sheet.glyph(this, Glyph.DOWNLOAD, 0xFF3B82F6), "הודעות ותמונות", "ההודעות, ואחריהן התמונות שלהן", () -> chooseHours(MODE_BOTH));
            s.action(Sheet.glyph(this, Glyph.BOX, 0xFF22A06B), "תמונות להודעות קיימות", "טעינת התמונות שחסרות בהודעות שכבר בערוץ", () -> chooseHours(MODE_IMAGES));
        }
        s.show();
    }

    private void chooseHours(int mode) {
        pullMode = mode;
        String title = mode == MODE_IMAGES ? "תמונות מכמה זמן אחורה?" : "מכמה זמן אחורה?";
        Sheet.create(this, title).choice(PULL_NAMES, -1, w -> {
            pullHours = PULL_HOURS[w];
            if (mode == MODE_IMAGES) startImages(pullHours);
            else startPull(pullHours);
        }).show();
    }

    // ---- טעינת תמונות מרוכזת, עם התקדמות

    private int bulkTotal;
    private final java.util.Set<String> bulkKeys = new java.util.HashSet<>();

    private void startImages(int hours) {
        long cut = System.currentTimeMillis() - hours * 3600000L;
        int n = 0;
        bulkKeys.clear();
        for (Item it : Store.get(this).channel(chat, 3000)) {
            if (it.time < cut || it.imgCount() == 0) continue;
            for (int i = 0; i < it.imgCount(); i++) {
                String k = it.id + "#" + i;
                if (Images.has(this, it.id, i) || Images.failed.contains(k)) continue;
                bulkKeys.add(k);
                n++;
            }
            if (Images.missing(this, it) > 0) LinkService.requestImages(this, it);
        }
        if (n == 0) {
            Toast.makeText(this, "אין תמונות חסרות בזמן הזה", Toast.LENGTH_SHORT).show();
            return;
        }
        bulkTotal = n;
        pullStrip.setVisibility(View.VISIBLE);
        updateBulk();
        adapter.notifyDataSetChanged();
    }

    private void updateBulk() {
        if (bulkTotal == 0) return;
        int left = 0;
        for (String k : bulkKeys) if (Images.loading.contains(k)) left++;
        int done = bulkTotal - left;
        pullText.setText("טוען תמונות " + done + " מתוך " + bulkTotal + "…");
        if (left == 0) {
            int failed = 0;
            for (String k : bulkKeys) if (Images.failed.contains(k)) failed++;
            int ok = bulkTotal - failed;
            bulkTotal = 0;
            bulkKeys.clear();
            pullStrip.setVisibility(View.GONE);
            Toast.makeText(this, (ok == 1 ? "נטענה תמונה אחת" : "נטענו " + ok + " תמונות") + (failed > 0 ? " (" + failed + " לא זמינות)" : ""), Toast.LENGTH_SHORT).show();
        }
    }

    private void startPull(int hours) {
        if (!LinkService.requestBackfill(chat, hours)) {
            Toast.makeText(this, "המחשב לא מחובר", Toast.LENGTH_LONG).show();
            return;
        }
        pulling = true;
        pullText.setText(chat.startsWith("pc:")
                ? "שואב את ההתראות ששמורות ב-Windows…"
                : "שואב עדכונים מהמחשב, בלי פושים…");
        pullStrip.setVisibility(View.VISIBLE);
        emptyCard.setVisibility(View.GONE);
        // אם המחשב לא ענה תוך דקה וחצי — מסתירים את הפס
        handler.postDelayed(pullTimeout, 90000);
    }

    private final Runnable pullTimeout = () -> {
        if (!pulling) return;
        pulling = false;
        pullStrip.setVisibility(View.GONE);
        reload();
        Toast.makeText(this, "המחשב לא השלים את השאיבה. אולי צריך לעדכן את התוכנה במחשב", Toast.LENGTH_LONG).show();
    };

    @Override
    public void done(String c, int n, String error) {
        if (!chat.equals(c)) return;
        pulling = false;
        handler.removeCallbacks(pullTimeout);
        pullStrip.setVisibility(View.GONE);
        reload();
        if (error != null && n == 0) Toast.makeText(this, "השאיבה נכשלה: " + error, Toast.LENGTH_LONG).show();
        else Toast.makeText(this, n == 0 ? "לא נמצאו עדכונים נוספים" : "נשאבו " + n + " עדכונים", Toast.LENGTH_SHORT).show();
        // "הודעות ותמונות": אחרי ההודעות טוענים את התמונות שלהן
        if (pullMode == MODE_BOTH && error == null) handler.postDelayed(() -> startImages(pullHours), 400);
    }

    // ---------------------------------------------------------------- תוכן

    private void reload() {
        List<Item> items = Store.get(this).channel(chat, 1500);
        Prefs.markRead(this, chat);
        count = items.size();
        emptyCard.setVisibility(items.isEmpty() && !pulling ? View.VISIBLE : View.GONE);
        if (items.isEmpty()) {
            rows.clear();
            Item fake = Item.forChat(this, chat);
            avatar.setImageBitmap(Icons.get(this, fake, dp(38)));
            name.setText(fake.chatName());
            updateHeader();
            adapter.notifyDataSetChanged();
            return;
        }
        rows.clear();
        String lastDay = null;
        int focusRow = -1;
        for (Item it : items) {
            String day = dayLabel(it.time);
            if (!day.equals(lastDay)) { rows.add(day); lastDay = day; }
            if (focusId != null && focusId.equals(it.id)) focusRow = rows.size();
            rows.add(it);
        }
        Item last = items.get(items.size() - 1);
        avatar.setImageBitmap(Icons.get(this, last, dp(38)));
        name.setText(last.chatName());
        updateHeader();
        adapter.notifyDataSetChanged();
        if (focusRow >= 0) {
            final int fr = focusRow;
            list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
            list.post(() -> list.setSelectionFromTop(fr, dp(80)));
            handler.postDelayed(() -> { focusId = null; adapter.notifyDataSetChanged(); }, 2500);
        }
    }

    private void updateHeader() {
        boolean m = Prefs.muted(this, chat);
        subtitle.setText((count == 0 ? "אין עדיין עדכונים" : count == 1 ? "עדכון אחד" : count + " עדכונים") + (m ? " · מושתק" : ""));
        muteText.setText(m ? "ביטול השתקה" : "השתקה");
        muteIcon.setKind(m ? Glyph.BELL : Glyph.BELL_OFF);
    }

    private static String dayLabel(long t) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        if (same(now, c)) return "היום";
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (same(now, c)) return "אתמול";
        return "יום " + DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + c.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[c.get(Calendar.MONTH)];
    }

    private static boolean same(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static String clock(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private int dp(float v) { return Ui.dp(this, v); }

    // ---------------------------------------------------------------- בועות

    private static final class Holder {
        LinearLayout bubble, images;
        TextView title, text, time;
        Item item;
    }

    /** רוחב תמונה בבועה: כמעט כל רוחב הבועה המקסימלי. */
    private int imageWidth() {
        return getResources().getDisplayMetrics().widthPixels - dp(48 + 8 + 12 + 18);
    }

    /** התמונות מתחת לטקסט, בפינות מעוגלות: מה שנטען מוצג, ואם חסר — כפתור "טעינת תמונה". */
    private void bindImages(Holder h, Item it) {
        h.images.removeAllViews();
        int n = it.imgCount();
        if (n == 0) { h.images.setVisibility(View.GONE); return; }
        h.images.setVisibility(View.VISIBLE);
        int w = imageWidth();
        boolean anyLoading = false;
        int missing = 0;
        boolean hasText = (it.title != null && it.title.length() > 0) || (it.text != null && it.text.length() > 0);
        for (int i = 0; i < n; i++) {
            if (Images.isLoading(it.id, i)) anyLoading = true;
            android.graphics.Bitmap b = Images.load(this, it.id, i, w);
            if (b == null) { missing++; continue; }
            View iv = new View(this);
            iv.setBackgroundDrawable(new RoundImage(b, dp(12)));
            int hgt = Math.max(dp(40), Math.round(w * (b.getHeight() / (float) b.getWidth())));
            LinearLayout.LayoutParams l = Ui.lp(w, Math.min(hgt, dp(420)));
            l.topMargin = (hasText || h.images.getChildCount() > 0) ? dp(7) : dp(2);
            final int idx = i;
            iv.setOnClickListener(v -> showFull(it, idx));
            h.images.addView(iv, l);
        }
        if (missing > 0) {
            LinearLayout btn = Ui.row(this);
            btn.setGravity(Gravity.CENTER);
            btn.setMinimumWidth(dp(190));
            btn.setPadding(dp(16), 0, dp(16), 0);
            btn.setBackgroundDrawable(Ui.press(Ui.round(Ui.alpha(Ui.ACCENT, 0x14), dp(19)), Ui.round(Ui.alpha(Ui.ACCENT, 0x28), dp(19))));
            if (anyLoading) {
                ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
                btn.addView(pb, Ui.lp(dp(16), dp(16)));
            } else {
                Glyph g = new Glyph(this, Glyph.DOWNLOAD, Ui.ACCENT);
                g.setWeight(1.2f);
                btn.addView(g, Ui.lp(dp(17), dp(17)));
            }
            TextView t = Ui.text(this, anyLoading ? "טוען…" : (missing == 1 ? "טעינת תמונה" : "טעינת " + missing + " תמונות"), 14, Ui.ACCENT, true);
            t.setPadding(0, 0, dp(8), 0);
            btn.addView(t, Ui.lp(-2, -2));
            final boolean busy = anyLoading;
            btn.setOnClickListener(v -> {
                if (busy) return;
                if (!LinkService.requestImages(this, it)) {
                    Toast.makeText(this, "צריך חיבור למחשב כדי לטעון תמונות", Toast.LENGTH_LONG).show();
                    return;
                }
                adapter.notifyDataSetChanged();
            });
            LinearLayout.LayoutParams bl = Ui.lp(-2, dp(38));
            bl.topMargin = hasText || h.images.getChildCount() > 0 ? dp(8) : dp(2);
            h.images.addView(btn, bl);
        }
    }

    /** תמונה במסך מלא על רקע מטושטש של המסך (לא שחור). לחיצה סוגרת. */
    private void showFull(Item it, int idx) {
        android.graphics.Bitmap img = Images.load(this, it.id, idx, getResources().getDisplayMetrics().widthPixels);
        if (img == null) return;
        android.graphics.Bitmap bg = null;
        try {
            View root = getWindow().getDecorView();
            android.graphics.Bitmap shot = android.graphics.Bitmap.createBitmap(Math.max(1, root.getWidth() / 2), Math.max(1, root.getHeight() / 2), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(shot);
            c.scale(0.5f, 0.5f);
            root.draw(c);
            bg = RoundImage.blur(shot, 5, 4);
            shot.recycle();
        } catch (Throwable ignored) { }

        android.app.Dialog d = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        FrameLayout f = new FrameLayout(this);
        if (bg != null) {
            android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(getResources(), bg);
            bd.setFilterBitmap(true);
            f.setBackgroundDrawable(bd);
        } else {
            f.setBackgroundColor(0xFFEDE7E3);
        }
        View tint = new View(this);
        tint.setBackgroundColor(0x33000000);   // הכהיה קלה בלבד, כדי שהתמונה תבלוט
        f.addView(tint, new FrameLayout.LayoutParams(-1, -1));

        int sw = getResources().getDisplayMetrics().widthPixels - dp(24);
        int sh = getResources().getDisplayMetrics().heightPixels - dp(80);
        float scale = Math.min(sw / (float) img.getWidth(), sh / (float) img.getHeight());
        View pic = new View(this);
        pic.setBackgroundDrawable(new RoundImage(img, dp(14)));
        if (android.os.Build.VERSION.SDK_INT >= 21) pic.setElevation(dp(8));
        f.addView(pic, new FrameLayout.LayoutParams(Math.round(img.getWidth() * scale), Math.round(img.getHeight() * scale), Gravity.CENTER));
        f.setOnClickListener(v -> d.dismiss());
        d.setContentView(f);
        d.show();
        f.setAlpha(0f);
        f.animate().alpha(1f).setDuration(180).start();
        pic.setScaleX(0.92f);
        pic.setScaleY(0.92f);
        pic.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return rows.get(p) instanceof String ? 0 : 1; }
        @Override public boolean isEnabled(int p) { return false; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            Object o = rows.get(p);
            if (o instanceof String) {
                // גלולת תאריך באמצע (הודעת שירות של טלגרם)
                FrameLayout f = (FrameLayout) v;
                TextView t;
                if (f == null) {
                    f = new FrameLayout(ChannelActivity.this);
                    f.setPadding(0, dp(8), 0, dp(6));
                    t = Ui.center(Ui.text(ChannelActivity.this, "", 13.5f, 0xFFFFFFFF, true));
                    t.setPadding(dp(11), dp(3), dp(11), dp(4));
                    t.setBackgroundDrawable(Ui.round(0x70A45A44, dp(13)));
                    f.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
                } else {
                    t = (TextView) f.getChildAt(0);
                }
                t.setText((String) o);
                return f;
            }
            Holder h;
            if (v == null) {
                h = new Holder();
                LinearLayout wrap = Ui.row(ChannelActivity.this);
                wrap.setPadding(dp(48), dp(2), dp(8), dp(3));   // הבועה צמודה לימין, מרווח משמאל
                LinearLayout bubble = Ui.col(ChannelActivity.this);
                bubble.setBackgroundDrawable(new Bubble(getResources().getDisplayMetrics().density));
                bubble.setPadding(dp(12), dp(7), dp(12 + 6), dp(6));   // מקום לזנב בצד ימין
                h.title = Ui.text(ChannelActivity.this, "", 15.5f, Ui.TG_NAME, true);
                bubble.addView(h.title, Ui.lp(-2, -2));
                h.text = Ui.text(ChannelActivity.this, "", 15.5f, 0xFF111418, false);
                h.text.setLineSpacing(0, 1.12f);
                bubble.addView(h.text, Ui.lp(-2, -2));
                // התמונות מתחת לטקסט
                h.images = Ui.col(ChannelActivity.this);
                h.images.setGravity(Gravity.CENTER_HORIZONTAL);
                bubble.addView(h.images, Ui.lp(-2, -2));
                h.time = Ui.text(ChannelActivity.this, "", 11.5f, Ui.TG_IN_TIME, false);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
                tl.gravity = Gravity.END;
                tl.topMargin = dp(2);
                bubble.addView(h.time, tl);
                final Holder fh = h;
                bubble.setOnLongClickListener(x -> { copy(fh.item); return true; });
                wrap.addView(bubble, Ui.lp(-2, -2));
                h.bubble = bubble;
                v = wrap;
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            Item it = (Item) o;
            h.item = it;
            boolean hasTitle = it.title != null && it.title.length() > 0;
            boolean hasText = it.text != null && it.text.length() > 0;
            h.title.setVisibility(hasTitle ? View.VISIBLE : View.GONE);
            h.title.setText(hasTitle ? it.title : "");
            h.text.setVisibility(hasText ? View.VISIBLE : View.GONE);
            h.text.setText(hasText ? it.text : "");
            ((LinearLayout.LayoutParams) h.text.getLayoutParams()).topMargin = hasTitle && hasText ? dp(2) : 0;
            h.time.setText(clock(it.time));
            bindImages(h, it);
            ((Bubble) h.bubble.getBackground()).setHighlight(focusId != null && focusId.equals(it.id));
            return v;
        }
    }

    private void copy(Item it) {
        if (it == null) return;
        String s = (it.title != null && it.title.length() > 0 ? it.title + "\n" : "") + (it.text == null ? "" : it.text);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("ביטיפוש", s.trim()));
        Toast.makeText(this, "הטקסט הועתק", Toast.LENGTH_SHORT).show();
    }

    /** בועה לבנה של הודעה נכנסת, עם זנב קטן בפינה התחתונה בצד ימין וצל עדין. */
    static final class Bubble extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private final Path path = new Path();

        Bubble(float density) {
            d = density;
            fill.setColor(0xFFFFFFFF);
            shadow.setColor(0x221D3753);   // chat_inBubbleShadow בשקיפות
        }

        void setHighlight(boolean on) {
            fill.setColor(on ? 0xFFFFEDE6 : 0xFFFFFFFF);
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(Rect b) {
            path.reset();
            float r = 16 * d, tail = 6 * d;
            float left = b.left, top = b.top, right = b.right - tail, bottom = b.bottom - 1 * d;
            path.moveTo(left + r, top);
            path.lineTo(right - r, top);
            path.quadTo(right, top, right, top + r);
            path.lineTo(right, bottom - 8 * d);
            path.quadTo(right, bottom - 1 * d, right + tail, bottom);
            path.lineTo(right - 6 * d, bottom);
            path.lineTo(left + r, bottom);
            path.quadTo(left, bottom, left, bottom - r);
            path.lineTo(left, top + r);
            path.quadTo(left, top, left + r, top);
            path.close();
        }

        @Override
        public void draw(Canvas c) {
            c.save();
            c.translate(0, 1 * d);
            c.drawPath(path, shadow);
            c.restore();
            c.drawPath(path, fill);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
