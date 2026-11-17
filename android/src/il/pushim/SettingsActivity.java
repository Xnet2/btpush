package il.pushim;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Set;

/**
 * ההגדרות, מסודרות כמו בטלגרם: עמוד ראשי קצר עם ארבע כניסות, וכל אחת פותחת עמוד משלה.
 * כל שינוי נשמר מיד ונשלח למחשב.
 */
public final class SettingsActivity extends Activity {
    static final String MAIN = "main", PUSH = "push", SOURCES = "sources", STORAGE = "storage", CONNECTION = "connection";
    private static final int C_PUSH = 0xFFF0566A, C_SOURCES = 0xFF3B82F6, C_STORAGE = 0xFF22A06B, C_CONN = 0xFF8B5CF6;

    private String page;
    private LinearLayout content;
    private TextView pushSummary, sourcesSummary, storageSummary, connSummary;
    private TextView appsValue, channelsValue, keepValue, intervalValue, countValue, pushValue;
    private View keepRow, pushRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        page = getIntent().getStringExtra("page");
        if (page == null) page = MAIN;

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.TG_GRAY_BG);
        LinearLayout bar = Ui.actionBar(this);
        bar.setPadding(dp(4), dp(4), dp(8), dp(4));
        bar.addView(Ui.iconButton(this, Glyph.BACK, 0xFF5F6670, v -> finish()));
        TextView title = Ui.text(this, pageTitle(), 19, Ui.TG_NAME, true);
        title.setPadding(0, 0, dp(10), 0);
        bar.addView(title, Ui.lp(0, -2, 1));
        root.addView(bar, Ui.lp(-1, -2));
        root.addView(Ui.shadowLine(this));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        content = Ui.col(this);
        content.setPadding(0, 0, 0, dp(28));
        sv.addView(content);
        root.addView(sv, Ui.lp(-1, 0, 1));
        setContentView(root);

        switch (page) {
            case PUSH: buildPush(); break;
            case SOURCES: buildSources(); break;
            case STORAGE: buildStorage(); break;
            case CONNECTION: buildConnection(); break;
            default: buildMain(); break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateValues();
    }

    private String pageTitle() {
        switch (page) {
            case PUSH: return "פושים";
            case SOURCES: return "מקורות עדכונים";
            case STORAGE: return "שמירה ואחסון";
            case CONNECTION: return "חיבור למחשב";
            default: return "הגדרות";
        }
    }

    // ---------------------------------------------------------------- עמוד ראשי

    private void buildMain() {
        // כרטיס עליון: לוגו, שם, גרסה ומצב חיבור
        LinearLayout top = Ui.col(this);
        top.setBackgroundColor(0xFFFFFFFF);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setPadding(dp(16), dp(22), dp(16), dp(20));
        ImageView logo = new ImageView(this);
        logo.setImageResource(il.pushim.player.R.drawable.icon);
        top.addView(logo, Ui.lp(dp(68), dp(68)));
        TextView name = Ui.center(Ui.text(this, "ביטיפוש", 20, Ui.TG_NAME, true));
        LinearLayout.LayoutParams nl = Ui.lp(-1, -2);
        nl.topMargin = dp(10);
        top.addView(name, nl);
        connSummary = Ui.center(Ui.text(this, "", 14, Ui.TG_GRAY_TEXT, false));
        top.addView(connSummary, Ui.lp(-1, -2));
        content.addView(top, Ui.lp(-1, -2));

        LinearLayout menu = section(null);
        pushSummary = navRow(menu, Glyph.BELL, C_PUSH, "פושים", PUSH);
        divider(menu, 64);
        sourcesSummary = navRow(menu, Glyph.WAVES, C_SOURCES, "מקורות עדכונים", SOURCES);
        divider(menu, 64);
        storageSummary = navRow(menu, Glyph.BOX, C_STORAGE, "שמירה ואחסון", STORAGE);
        divider(menu, 64);
        navRow(menu, Glyph.BT, C_CONN, "חיבור למחשב", CONNECTION).setText("שיוך, בדיקה והוראות");

        TextView ver = Ui.center(Ui.text(this, "גרסה 1.6.1", 13, Ui.TG_GRAY_TEXT, false));
        ver.setPadding(0, dp(18), 0, 0);
        content.addView(ver, Ui.lp(-1, -2));
    }

    // ---------------------------------------------------------------- פושים

    private void buildPush() {
        LinearLayout a = section(null);
        toggleRow(a, "הקפצת פושים", "כשכבוי, העדכונים ממשיכים להגיע לערוצים בשקט", Prefs.pushes(this), on -> {
            Prefs.setPushes(this, on);
            updateValues();
        });
        divider(a, 21);
        pushValue = valueRow(a, "פושים מאילו ערוצים", v -> choosePushChats());
        pushRow = (View) pushValue.getParent();
        hint("ערוץ בלי פושים ממשיך לקבל עדכונים. אפשר גם להשתיק ערוץ בלחיצה ארוכה עליו, או בכפתור שבתחתית הערוץ.");

        LinearLayout s = section("צליל ורטט");
        toggleRow(s, "צליל", null, Prefs.sound(this), on -> Prefs.setSound(this, on));
        divider(s, 21);
        toggleRow(s, "רטט", null, Prefs.vibrate(this), on -> Prefs.setVibrate(this, on));

        LinearLayout t = section(null);
        actionRow(t, "שליחת פוש בדיקה מהמחשב", Ui.ACCENT, v -> {
            if (LinkService.state != LinkService.CONNECTED) {
                Toast.makeText(this, "המחשב לא מחובר כרגע", Toast.LENGTH_SHORT).show();
            } else {
                LinkService.requestTest();
                Toast.makeText(this, "נשלחה בקשה למחשב", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------------------------------------------------------- מקורות

    private void buildSources() {
        LinearLayout h = section("הגיזרה");
        toggleRow(h, "קבלת ידיעות", "hagizra.news", Prefs.src(this, "hagizra"),
                on -> { Prefs.setSrc(this, "hagizra", on); changed(); });

        LinearLayout a = section("אותנטי");
        toggleRow(a, "קבלת מבזקים", "authenti.newsupdates.click", Prefs.src(this, "authenti"),
                on -> { Prefs.setSrc(this, "authenti", on); changed(); });
        divider(a, 21);
        channelsValue = valueRow(a, "ערוצים", v -> chooseChannels());

        LinearLayout p = section("המחשב");
        toggleRow(p, "התראות Windows", "כל התראה שקופצת במחשב", Prefs.src(this, "pc"),
                on -> { Prefs.setSrc(this, "pc", on); changed(); });
        divider(p, 21);
        appsValue = valueRow(p, "תוכנות", v -> chooseApps());

        LinearLayout c = section(null);
        intervalValue = valueRow(c, "בדיקת האתרים", v -> chooseInterval());
        hint("מקור כבוי לא נשלח מהמחשב בכלל. כדי לקבל עדכונים בלי פושים, משאירים אותו פעיל ומשתיקים את הערוץ.");
    }

    // ---------------------------------------------------------------- אחסון

    private void buildStorage() {
        LinearLayout k = section(null);
        toggleRow(k, "לשמור עדכונים", "כשכבוי, הערוצים מתרוקנים כשהאפליקציה נסגרת", Prefs.save(this), this::onSaveToggled);
        divider(k, 21);
        keepValue = valueRow(k, "כמה זמן אחורה", v -> chooseKeep());
        keepRow = (View) keepValue.getParent();

        LinearLayout d = section(null);
        countValue = actionRow(d, "מחיקת כל העדכונים", Ui.RED, v -> confirmClear());
        hint("בתוך כל ערוץ אפשר לשאוב עדכונים אחרונים (עד 3 ימים אחורה) בכפתור השאיבה שלמעלה.");
    }

    // ---------------------------------------------------------------- חיבור

    private void buildConnection() {
        LinearLayout s = section(null);
        connSummary = valueRow(s, "מצב", null);
        divider(s, 21);
        actionRow(s, "להפוך את הנגן לגלוי לשיוך", Ui.ACCENT, v -> discoverable());

        LinearLayout h = section("איך מחברים");
        TextView help = Ui.text(this,
                "1. מפעילים בלוטות' בנגן ובמחשב.\n" +
                "2. במחשב: הגדרות ← Bluetooth ומכשירים ← הוספת מכשיר, ובוחרים את הנגן.\n" +
                "3. מפעילים במחשב את התוכנה ביטיפוש. היא מוצאת את הנגן לבד.\n" +
                "4. האפליקציה הזו מקבלת עדכונים גם כשהיא סגורה.",
                15, Ui.TG_NAME, false);
        help.setLineSpacing(0, 1.3f);
        help.setPadding(dp(21), dp(4), dp(21), dp(16));
        h.addView(help, Ui.lp(-1, -2));
    }

    // ---------------------------------------------------------------- ערכים

    private void changed() {
        LinkService.pushConfig();
        updateValues();
    }

    private void updateValues() {
        String conn = LinkService.state == LinkService.CONNECTED
                ? "מחובר למחשב" + (LinkService.peer.length() > 0 ? " " + LinkService.peer : "")
                : LinkService.state == LinkService.OFF ? "הבלוטות' כבוי" : "ממתין למחשב";
        if (connSummary != null) connSummary.setText(conn);

        List<Store.Chat> chats = Store.get(this).chats(null);
        int muted = 0;
        for (Store.Chat c : chats) if (Prefs.muted(this, c.key)) muted++;
        String pushText = !Prefs.pushes(this) ? "כבויים" : (muted == 0 ? "מכל הערוצים" : (chats.size() - muted) + " מתוך " + chats.size() + " ערוצים");
        if (pushSummary != null) pushSummary.setText(pushText);
        if (pushValue != null) {
            pushValue.setText(chats.isEmpty() ? "אין עדיין" : (muted == 0 ? "מכולם" : (chats.size() - muted) + " מתוך " + chats.size()));
            pushRow.setAlpha(Prefs.pushes(this) ? 1f : 0.4f);
        }
        if (sourcesSummary != null) {
            StringBuilder sb = new StringBuilder();
            if (Prefs.src(this, "hagizra")) sb.append("הגיזרה");
            if (Prefs.src(this, "authenti")) sb.append(sb.length() > 0 ? ", " : "").append("אותנטי");
            if (Prefs.src(this, "pc")) sb.append(sb.length() > 0 ? ", " : "").append("מחשב");
            sourcesSummary.setText(sb.length() == 0 ? "הכול כבוי" : sb.toString());
        }
        if (storageSummary != null) storageSummary.setText(Prefs.save(this) ? "שמירה " + Prefs.keepName(this) + " אחורה" : "בלי שמירה");
        if (appsValue != null) {
            int apps = Prefs.knownApps(this).size(), off = Prefs.appsOff(this).size();
            appsValue.setText(apps == 0 ? "אחרי חיבור" : (off == 0 ? "כולן" : (apps - Math.min(off, apps)) + " מתוך " + apps));
        }
        if (channelsValue != null) {
            int ch = Prefs.knownChannels(this).size(), off = Prefs.channelsOff(this).size();
            channelsValue.setText(ch == 0 ? "אחרי חיבור" : (off == 0 ? "כולם" : (ch - Math.min(off, ch)) + " מתוך " + ch));
        }
        if (intervalValue != null) intervalValue.setText(Prefs.intervalName(this));
        if (keepValue != null) {
            keepValue.setText(Prefs.keepName(this));
            keepRow.setAlpha(Prefs.save(this) ? 1f : 0.4f);
            keepRow.setEnabled(Prefs.save(this));
        }
        if (countValue != null) countValue.setText(String.valueOf(Store.get(this).count()));
    }

    // ---------------------------------------------------------------- בחירות

    private void choosePushChats() {
        List<Store.Chat> chats = Store.get(this).chats(null);
        if (chats.isEmpty()) {
            Toast.makeText(this, "הערוצים יופיעו כאן אחרי שיגיעו עדכונים ראשונים", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[chats.size()];
        boolean[] checked = new boolean[chats.size()];
        for (int i = 0; i < chats.size(); i++) {
            Item it = chats.get(i).last;
            names[i] = it.chatName() + ("hagizra".equals(it.src) ? "" : "  ·  " + it.label());
            checked[i] = !Prefs.muted(this, chats.get(i).key);
        }
        Ui.dialog(this)
                .setTitle("פושים מאילו ערוצים?")
                .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("שמירה", (d, w) -> {
                    for (int i = 0; i < chats.size(); i++) Prefs.setMuted(this, chats.get(i).key, !checked[i]);
                    updateValues();
                })
                .setNeutralButton("מכולם", (d, w) -> {
                    for (Store.Chat c : chats) Prefs.setMuted(this, c.key, false);
                    updateValues();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void chooseApps() {
        List<String[]> apps = Prefs.knownApps(this);
        if (apps.isEmpty()) {
            Toast.makeText(this, "רשימת התוכנות תגיע מהמחשב בחיבור הראשון", Toast.LENGTH_LONG).show();
            return;
        }
        Set<String> off = Prefs.appsOff(this);
        String[] names = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i)[1];
            checked[i] = !off.contains(apps.get(i)[0]);
        }
        Ui.dialog(this)
                .setTitle("מאילו תוכנות לקבל התראות?")
                .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("שמירה", (d, w) -> {
                    Set<String> s = Prefs.appsOff(this);
                    for (int i = 0; i < apps.size(); i++) {
                        if (checked[i]) s.remove(apps.get(i)[0]);
                        else s.add(apps.get(i)[0]);
                    }
                    Prefs.putSet(this, "apps_off", s);
                    changed();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void chooseChannels() {
        List<String> chs = Prefs.knownChannels(this);
        if (chs.isEmpty()) {
            Toast.makeText(this, "רשימת הערוצים תגיע מהמחשב בחיבור הראשון", Toast.LENGTH_LONG).show();
            return;
        }
        Set<String> off = Prefs.channelsOff(this);
        String[] names = chs.toArray(new String[0]);
        boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) checked[i] = !off.contains(names[i]);
        Ui.dialog(this)
                .setTitle("מאילו ערוצים באותנטי לקבל?")
                .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("שמירה", (d, w) -> {
                    Set<String> s = Prefs.channelsOff(this);
                    for (int i = 0; i < names.length; i++) {
                        if (checked[i]) s.remove(names[i]);
                        else s.add(names[i]);
                    }
                    Prefs.putSet(this, "ch_off", s);
                    changed();
                })
                .setNeutralButton("סמן הכל", (d, w) -> {
                    Prefs.putSet(this, "ch_off", new java.util.LinkedHashSet<>());
                    changed();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void chooseInterval() {
        int cur = indexOf(Prefs.INTERVALS, Prefs.interval(this));
        Ui.dialog(this)
                .setTitle("כל כמה זמן לבדוק את האתרים?")
                .setSingleChoiceItems(Prefs.INTERVAL_NAMES, cur, (d, w) -> {
                    Prefs.setInterval(this, Prefs.INTERVALS[w]);
                    changed();
                    d.dismiss();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void chooseKeep() {
        if (!Prefs.save(this)) return;
        int cur = indexOf(Prefs.KEEP_HOURS, Prefs.keepHours(this));
        Ui.dialog(this)
                .setTitle("כמה זמן אחורה לשמור?")
                .setSingleChoiceItems(Prefs.KEEP_NAMES, cur, (d, w) -> {
                    Prefs.setKeepHours(this, Prefs.KEEP_HOURS[w]);
                    Store.get(this).purge();
                    updateValues();
                    d.dismiss();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void onSaveToggled(boolean on) {
        if (on) {
            Prefs.setSave(this, true);
            updateValues();
            return;
        }
        int n = Store.get(this).count();
        if (n == 0) {
            Prefs.setSave(this, false);
            updateValues();
            return;
        }
        Ui.dialog(this)
                .setTitle("לכבות את השמירה?")
                .setMessage("העדכונים השמורים (" + n + ") יימחקו, ומעכשיו הערוצים יתרוקנו כשהאפליקציה נסגרת.")
                .setPositiveButton("כיבוי ומחיקה", (d, w) -> {
                    Store.get(this).clear();
                    Prefs.setSave(this, false);
                    updateValues();
                })
                .setNegativeButton("ביטול", (d, w) -> recreate())
                .setOnCancelListener(d -> recreate())
                .show();
    }

    private void confirmClear() {
        Ui.dialog(this)
                .setTitle("למחוק את כל העדכונים?")
                .setMessage("אי אפשר לשחזר את העדכונים אחרי המחיקה.")
                .setPositiveButton("מחיקה", (d, w) -> {
                    Store.get(this).clear();
                    Notifier.cancelAll(this);
                    updateValues();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void discoverable() {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) {
            Toast.makeText(this, "אין בלוטות' במכשיר הזה", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "לא ניתן לפתוח את חלון הבלוטות'", Toast.LENGTH_LONG).show();
        }
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return -1;
    }

    // ---------------------------------------------------------------- בניית שורות

    private int dp(float v) { return Ui.dp(this, v); }

    /** בלוק לבן ברוחב מלא, עם כותרת קטנה בצבע הראשי (אם יש). */
    private LinearLayout section(String name) {
        LinearLayout c = Ui.col(this);
        c.setBackgroundColor(0xFFFFFFFF);
        if (name != null) {
            TextView t = Ui.text(this, name, 14.5f, Ui.ACCENT, true);
            t.setPadding(dp(21), dp(14), dp(21), dp(2));
            c.addView(t, Ui.lp(-1, -2));
        }
        LinearLayout.LayoutParams l = Ui.lp(-1, -2);
        l.topMargin = dp(12);
        content.addView(c, l);
        return c;
    }

    private void hint(String s) {
        TextView t = Ui.text(this, s, 13.5f, Ui.TG_GRAY_TEXT, false);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(dp(21), dp(9), dp(21), 0);
        content.addView(t, Ui.lp(-1, -2));
    }

    private void divider(LinearLayout card, int startDp) {
        View v = new View(this);
        v.setBackgroundColor(0xFFEBEDF0);
        LinearLayout.LayoutParams l = Ui.lp(-1, 1);
        if (android.os.Build.VERSION.SDK_INT >= 17) l.setMarginStart(dp(startDp));
        else l.setMargins(0, 0, dp(startDp), 0);
        card.addView(v, l);
    }

    private android.graphics.drawable.Drawable rowBg() {
        return Ui.press(new android.graphics.drawable.ColorDrawable(0), new android.graphics.drawable.ColorDrawable(0xFFF3F4F6));
    }

    /** שורה בעמוד הראשי: אייקון צבעוני, כותרת, תקציר מתחת וחץ. */
    private TextView navRow(LinearLayout card, int glyph, int color, String title, String target) {
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(14), dp(10), dp(16), dp(10));
        row.setMinimumHeight(dp(60));
        row.setBackgroundDrawable(rowBg());
        row.addView(Ui.iconTile(this, glyph, color, 34));
        LinearLayout texts = Ui.col(this);
        texts.setPadding(dp(8), 0, dp(14), 0);
        texts.addView(Ui.text(this, title, 16, Ui.TG_NAME, false));
        TextView sub = Ui.text(this, "", 13.5f, Ui.TG_GRAY_TEXT, false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(sub);
        row.addView(texts, Ui.lp(0, -2, 1));
        row.addView(new Glyph(this, Glyph.CHEVRON, 0xFFB4B9C0), Ui.lp(dp(18), dp(18)));
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, SettingsActivity.class);
            i.putExtra("page", target);
            startActivity(i);
        });
        card.addView(row, Ui.lp(-1, -2));
        return sub;
    }

    private void toggleRow(LinearLayout card, String title, String sub, boolean on, Toggle.OnChange onChange) {
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(18), dp(10), dp(21), dp(10));
        row.setMinimumHeight(dp(52));
        LinearLayout texts = Ui.col(this);
        texts.setPadding(dp(12), 0, 0, 0);
        texts.addView(Ui.text(this, title, 16, Ui.TG_NAME, false));
        if (sub != null) {
            TextView s = Ui.text(this, sub, 13, Ui.TG_GRAY_TEXT, false);
            s.setPadding(0, dp(1), 0, 0);
            texts.addView(s);
        }
        row.addView(texts, Ui.lp(0, -2, 1));
        Toggle t = new Toggle(this, on);
        t.setListener(onChange);
        row.addView(t);
        row.setOnClickListener(v -> t.setOn(!t.isOn(), true));
        row.setBackgroundDrawable(rowBg());
        card.addView(row, Ui.lp(-1, -2));
    }

    /** שורה עם ערך משמאל. click = null לשורת מידע בלבד. */
    private TextView valueRow(LinearLayout card, String title, View.OnClickListener click) {
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(16), dp(12), dp(21), dp(12));
        row.setMinimumHeight(dp(52));
        TextView t = Ui.text(this, title, 16, Ui.TG_NAME, false);
        row.addView(t, Ui.lp(0, -2, 1));
        TextView v = Ui.text(this, "", 15, Ui.TG_GRAY_TEXT, false);
        row.addView(v, Ui.lp(-2, -2));
        if (click != null) {
            LinearLayout.LayoutParams cl = Ui.lp(dp(16), dp(16));
            cl.setMargins(0, 0, dp(6), 0);
            row.addView(new Glyph(this, Glyph.CHEVRON, 0xFFB4B9C0), cl);
            row.setBackgroundDrawable(rowBg());
            row.setOnClickListener(click);
        }
        card.addView(row, Ui.lp(-1, -2));
        return v;
    }

    /** שורת פעולה בצבע (כמו "מחיקה" או "בדיקה"). מחזירה את שדה הערך שבצד. */
    private TextView actionRow(LinearLayout card, String title, int color, View.OnClickListener click) {
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(21), dp(12), dp(21), dp(12));
        row.setMinimumHeight(dp(52));
        row.setBackgroundDrawable(rowBg());
        TextView t = Ui.text(this, title, 16, color, false);
        row.addView(t, Ui.lp(0, -2, 1));
        TextView v = Ui.text(this, "", 15, Ui.TG_GRAY_TEXT, false);
        row.addView(v, Ui.lp(-2, -2));
        row.setOnClickListener(click);
        card.addView(row, Ui.lp(-1, -2));
        return v;
    }
}
