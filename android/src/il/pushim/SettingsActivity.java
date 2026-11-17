package il.pushim;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Set;

/** מסך ההגדרות. כל שינוי נשמר מיד ונשלח למחשב. */
public final class SettingsActivity extends Activity {
    private LinearLayout content;
    private TextView appsValue, channelsValue, keepValue, intervalValue, countValue;
    private View keepRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout header = Ui.row(this);
        header.setPadding(dp(8), dp(12), dp(16), dp(6));
        TextView back = Ui.text(this, "→", 24, Ui.TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setBackgroundDrawable(Ui.press(Ui.round(0, dp(22)), Ui.round(Ui.alpha(Ui.TEXT, 0x12), dp(22))));
        back.setOnClickListener(v -> finish());
        header.addView(back, Ui.lp(dp(44), dp(44)));
        TextView title = Ui.text(this, "הגדרות", 22, Ui.TEXT, true);
        title.setPadding(dp(6), 0, 0, 0);
        header.addView(title, Ui.lp(0, -2, 1));
        root.addView(header, Ui.lp(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        content = Ui.col(this);
        content.setPadding(dp(14), 0, dp(14), dp(24));
        sv.addView(content);
        root.addView(sv, Ui.lp(-1, 0, 1));
        setContentView(root);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateValues();
    }

    private void build() {
        // ---- מקורות
        section("מקורות");
        LinearLayout src = card();
        toggleRow(src, "הגיזרה", "ידיעות מ-hagizra.news", Item.color("hagizra"),
                Prefs.src(this, "hagizra"), on -> { Prefs.setSrc(this, "hagizra", on); changed(); });
        divider(src);
        toggleRow(src, "אותנטי", "מבזקים מ-authenti.newsupdates.click", Item.color("authenti"),
                Prefs.src(this, "authenti"), on -> { Prefs.setSrc(this, "authenti", on); changed(); });
        channelsValue = valueRow(src, "בחירת ערוצים באותנטי", v -> chooseChannels());
        divider(src);
        toggleRow(src, "התראות המחשב", "כל התראה שקופצת ב-Windows", Item.color("pc"),
                Prefs.src(this, "pc"), on -> { Prefs.setSrc(this, "pc", on); changed(); });
        appsValue = valueRow(src, "בחירת תוכנות", v -> chooseApps());

        // ---- בדיקה
        section("בדיקת עדכונים");
        LinearLayout chk = card();
        intervalValue = valueRow(chk, "כל כמה זמן לבדוק את האתרים", v -> chooseInterval());

        // ---- שמירה
        section("שמירת פריטים");
        LinearLayout keep = card();
        toggleRow(keep, "לשמור פריטים", "כשכבוי, הרשימה מתרוקנת כשהאפליקציה נסגרת", 0,
                Prefs.save(this), this::onSaveToggled);
        keepValue = valueRow(keep, "כמה זמן אחורה לשמור", v -> chooseKeep());
        keepRow = (View) keepValue.getParent();
        countValue = valueRow(keep, "מחיקת כל הפריטים", v -> confirmClear());

        // ---- התראות
        section("התראות");
        LinearLayout pn = card();
        toggleRow(pn, "צליל", null, 0, Prefs.sound(this), on -> Prefs.setSound(this, on));
        divider(pn);
        toggleRow(pn, "רטט", null, 0, Prefs.vibrate(this), on -> Prefs.setVibrate(this, on));
        divider(pn);
        valueRow(pn, "שליחת התראת בדיקה מהמחשב", v -> {
            if (LinkService.state != LinkService.CONNECTED) {
                Toast.makeText(this, "המחשב לא מחובר כרגע", Toast.LENGTH_SHORT).show();
            } else {
                LinkService.requestTest();
                Toast.makeText(this, "נשלחה בקשה למחשב", Toast.LENGTH_SHORT).show();
            }
        });

        // ---- חיבור
        section("חיבור למחשב");
        LinearLayout conn = card();
        valueRow(conn, "להפוך את הנגן לגלוי לשיוך (2 דקות)", v -> discoverable());
        divider(conn);
        TextView help = Ui.text(this,
                "1. מפעילים בלוטות' בנגן ובמחשב.\n" +
                "2. במחשב: הגדרות ← Bluetooth ומכשירים ← הוספת מכשיר, ובוחרים את הנגן (אם הנגן לא מופיע, לוחצים כאן למעלה על \"להפוך את הנגן לגלוי\").\n" +
                "3. מפעילים במחשב את התוכנה \"מבזק\". היא מוצאת את הנגן לבד ומתחברת אליו.\n" +
                "4. האפליקציה הזו לא צריכה להיות פתוחה. היא מקבלת התראות גם כשהיא סגורה.",
                14, Ui.TEXT2, false);
        help.setLineSpacing(0, 1.25f);
        help.setPadding(dp(16), dp(12), dp(16), dp(14));
        conn.addView(help, Ui.lp(-1, -2));

        TextView ver = Ui.text(this, "מבזק · גרסה 1.2", 12, Ui.GRAY, false);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(18), 0, 0);
        content.addView(ver, Ui.lp(-1, -2));
    }

    private void changed() {
        Prefs.config(this);
        LinkService.pushConfig();
        updateValues();
    }

    private void updateValues() {
        int apps = Prefs.knownApps(this).size(), appsOff = Prefs.appsOff(this).size();
        appsValue.setText(apps == 0 ? "יופיעו אחרי חיבור" : (appsOff == 0 ? "כולן" : (apps - Math.min(appsOff, apps)) + " מתוך " + apps));
        int ch = Prefs.knownChannels(this).size(), chOff = Prefs.channelsOff(this).size();
        channelsValue.setText(ch == 0 ? "יופיעו אחרי חיבור" : (chOff == 0 ? "כולם" : (ch - Math.min(chOff, ch)) + " מתוך " + ch));
        intervalValue.setText(Prefs.intervalName(this));
        keepValue.setText(Prefs.keepName(this));
        keepRow.setAlpha(Prefs.save(this) ? 1f : 0.4f);
        keepRow.setEnabled(Prefs.save(this));
        int n = Store.get(this).count();
        countValue.setText(n + " פריטים");
    }

    // ---------------------------------------------------------------- בחירות

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
                .setTitle("מאילו תוכנות לשלוח התראות?")
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
                .setTitle("מאילו ערוצים באותנטי?")
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
                .setMessage("הפריטים השמורים (" + n + ") יימחקו, ומעכשיו הרשימה תתרוקן כשהאפליקציה נסגרת.")
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
                .setTitle("למחוק את כל הפריטים?")
                .setMessage("אי אפשר לשחזר את הפריטים אחרי המחיקה.")
                .setPositiveButton("מחיקה", (d, w) -> {
                    Store.get(this).clear();
                    Notifier.cancelAll(this);
                    updateValues();
                    if (LinkService.listener != null) LinkService.listener.onItemsChanged();
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

    private void section(String name) {
        TextView t = Ui.text(this, name, 13, Ui.SUB, true);
        t.setPadding(dp(8), dp(18), dp(8), dp(8));
        content.addView(t, Ui.lp(-1, -2));
    }

    private LinearLayout card() {
        LinearLayout c = Ui.col(this);
        c.setBackgroundDrawable(Ui.round(Ui.CARD, dp(16), Ui.LINE, dp(1)));
        content.addView(c, Ui.lp(-1, -2));
        return c;
    }

    private void divider(LinearLayout card) {
        View v = new View(this);
        v.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams l = Ui.lp(-1, 1);
        l.setMargins(dp(16), 0, dp(16), 0);
        card.addView(v, l);
    }

    private void toggleRow(LinearLayout card, String title, String sub, int color, boolean on, Toggle.OnChange onChange) {
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setMinimumHeight(dp(56));
        if (color != 0) {
            View dot = Ui.dot(this, color, 10);
            LinearLayout.LayoutParams dl = (LinearLayout.LayoutParams) dot.getLayoutParams();
            dl.setMargins(0, 0, 0, 0);
            row.addView(dot, dl);
        }
        LinearLayout texts = Ui.col(this);
        texts.setPadding(color != 0 ? dp(12) : 0, 0, dp(10), 0);
        texts.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        if (sub != null) texts.addView(Ui.text(this, sub, 13, Ui.SUB, false));
        row.addView(texts, Ui.lp(0, -2, 1));
        Toggle t = new Toggle(this, on);
        if (color != 0) t.setColor(color);
        t.setListener(onChange);
        row.addView(t);
        row.setOnClickListener(v -> t.setOn(!t.isOn(), true));
        card.addView(row, Ui.lp(-1, -2));
    }

    private TextView valueRow(LinearLayout card, String title, View.OnClickListener click) {
        LinearLayout row = Ui.row(this);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setMinimumHeight(dp(52));
        row.setBackgroundDrawable(Ui.press(Ui.round(0, dp(16)), Ui.round(Ui.alpha(Ui.TEXT, 0x0A), dp(16))));
        TextView t = Ui.text(this, title, 15, Ui.TEXT, false);
        row.addView(t, Ui.lp(0, -2, 1));
        TextView v = Ui.text(this, "", 14, Ui.ACCENT, true);
        row.addView(v, Ui.lp(-2, -2));
        TextView arrow = Ui.text(this, "‹", 20, Ui.GRAY, false);
        arrow.setPadding(dp(8), 0, 0, 0);
        row.addView(arrow, Ui.lp(-2, -2));
        row.setOnClickListener(click);
        card.addView(row, Ui.lp(-1, -2));
        return v;
    }
}
