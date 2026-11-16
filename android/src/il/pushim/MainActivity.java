package il.pushim;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** המסך הראשי: רשימת הפושים שהתקבלו. */
public final class MainActivity extends Activity implements LinkService.Listener {
    private static final String[] FILTERS = {null, "hagizra", "authenti", "pc"};
    private static final String[] FILTER_NAMES = {"הכל", "הגיזרה", "אותנטי", "מחשב"};
    private static final String[] DAYS = {"ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת"};

    private String filter;
    private final List<Object> rows = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private Adapter adapter;
    private ListView list;
    private LinearLayout empty;
    private TextView statusText;
    private View statusDot;
    private LinearLayout statusPill;
    private final TextView[] chips = new TextView[FILTERS.length];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinkService.start(this);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        // ---- כותרת
        LinearLayout header = Ui.row(this);
        header.setPadding(dp(20), dp(18), dp(16), dp(8));
        LinearLayout titles = Ui.col(this);
        TextView title = Ui.text(this, "פושים", 28, Ui.TEXT, true);
        titles.addView(title);
        statusPill = Ui.row(this);
        statusPill.setPadding(dp(10), dp(5), dp(12), dp(5));
        statusDot = Ui.dot(this, Ui.AMBER, 8);
        statusPill.addView(statusDot);
        statusText = Ui.text(this, "", 13, Ui.TEXT2, false);
        statusText.setPadding(dp(7), 0, 0, 0);
        statusPill.addView(statusText);
        statusPill.setOnClickListener(v -> onStatusClick());
        LinearLayout.LayoutParams pl = Ui.lp(-2, -2);
        pl.topMargin = dp(6);
        titles.addView(statusPill, pl);
        header.addView(titles, Ui.lp(0, -2, 1));

        TextView settings = Ui.text(this, "הגדרות", 14, Ui.ACCENT, true);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(dp(16), dp(9), dp(16), dp(9));
        settings.setBackgroundDrawable(Ui.press(Ui.round(Ui.alpha(Ui.ACCENT, 0x18), dp(20)), Ui.round(Ui.alpha(Ui.ACCENT, 0x30), dp(20))));
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings, Ui.lp(-2, -2));
        root.addView(header, Ui.lp(-1, -2));

        // ---- סינון לפי מקור
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        Ui.rtl(hs);
        LinearLayout chipRow = Ui.row(this);
        chipRow.setPadding(dp(16), dp(4), dp(16), dp(10));
        for (int i = 0; i < FILTERS.length; i++) {
            final int idx = i;
            TextView chip = Ui.text(this, FILTER_NAMES[i], 14, Ui.SUB, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(7), dp(16), dp(7));
            chip.setOnClickListener(v -> { filter = FILTERS[idx]; paintChips(); refresh(); });
            LinearLayout.LayoutParams cl = Ui.lp(-2, -2);
            cl.setMargins(dp(4), 0, dp(4), 0);
            chipRow.addView(chip, cl);
            chips[i] = chip;
        }
        hs.addView(chipRow);
        root.addView(hs, Ui.lp(-1, -2));

        // ---- הרשימה
        FrameLayout frame = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(16));
        list.setVerticalScrollBarEnabled(false);
        Ui.rtl(list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        frame.addView(list, new FrameLayout.LayoutParams(-1, -1));

        empty = Ui.col(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), 0, dp(32), dp(40));
        android.widget.ImageView bell = new android.widget.ImageView(this);
        bell.setImageResource(il.pushim.player.R.drawable.icon);
        bell.setAlpha(0.9f);
        empty.addView(bell, Ui.lp(dp(76), dp(76)));
        TextView e1 = Ui.text(this, "עדיין אין פושים", 18, Ui.TEXT, true);
        e1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams el = Ui.lp(-2, -2);
        el.topMargin = dp(10);
        empty.addView(e1, el);
        TextView e2 = Ui.text(this, "כשהמחשב יתחבר בבלוטות', כל ידיעה והתראה חדשה תקפוץ כאן", 14, Ui.SUB, false);
        e2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams el2 = Ui.lp(-2, -2);
        el2.topMargin = dp(6);
        empty.addView(e2, el2);
        frame.addView(empty, new FrameLayout.LayoutParams(-1, -1));

        root.addView(frame, Ui.lp(-1, 0, 1));
        setContentView(root);
        paintChips();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LinkService.listener = this;
        LinkService.start(this);
        refresh();
        onLinkChanged();
    }

    @Override
    protected void onPause() {
        if (LinkService.listener == this) LinkService.listener = null;
        super.onPause();
    }

    @Override
    public void onLinkChanged() {
        int s = LinkService.state;
        int color;
        String text;
        if (s == LinkService.CONNECTED) {
            color = Ui.GREEN;
            text = "מחובר" + (LinkService.peer.length() > 0 ? " · " + LinkService.peer : "");
        } else if (s == LinkService.OFF) {
            color = Ui.GRAY;
            text = "הבלוטות' כבוי · להפעלה";
        } else {
            color = Ui.AMBER;
            String pc = Prefs.pcName(this);
            text = "ממתין למחשב" + (pc.length() > 0 ? " · " + pc : "");
        }
        ((android.graphics.drawable.GradientDrawable) statusDot.getBackground()).setColor(color);
        statusText.setText(text);
        statusPill.setBackgroundDrawable(Ui.round(Ui.alpha(color, 0x1F), dp(14)));
    }

    @Override
    public void onItemsChanged() {
        refresh();
    }

    private void onStatusClick() {
        if (LinkService.state == LinkService.OFF) {
            try {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } catch (Exception e) {
                Toast.makeText(this, "יש להפעיל בלוטות' בהגדרות הנגן", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void paintChips() {
        for (int i = 0; i < chips.length; i++) {
            boolean sel = (FILTERS[i] == null && filter == null) || (FILTERS[i] != null && FILTERS[i].equals(filter));
            int color = FILTERS[i] == null ? Ui.ACCENT : Item.color(FILTERS[i]);
            if (sel) {
                chips[i].setBackgroundDrawable(Ui.round(color, dp(18)));
                chips[i].setTextColor(0xFFFFFFFF);
            } else {
                chips[i].setBackgroundDrawable(Ui.round(Ui.CARD, dp(18), Ui.LINE, dp(1)));
                chips[i].setTextColor(Ui.SUB);
            }
        }
    }

    private void refresh() {
        List<Item> items = Store.get(this).list(filter, 600);
        rows.clear();
        String lastDay = null;
        for (Item it : items) {
            String day = dayLabel(it.time);
            if (!day.equals(lastDay)) {
                rows.add(day);
                lastDay = day;
            }
            rows.add(it);
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String dayLabel(long t) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        if (sameDay(now, c)) return "היום";
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(now, c)) return "אתמול";
        return "יום " + DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " +
                String.format(Locale.US, "%d.%d.%d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static String clock(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private int dp(float v) { return Ui.dp(this, v); }

    // ---------------------------------------------------------------- רשימה

    private static final class Holder {
        LinearLayout card;
        TextView badge, time, title, text, more;
        ImageView avatar;
        Item item;
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
                TextView h = (TextView) v;
                if (h == null) {
                    h = Ui.text(MainActivity.this, "", 13, Ui.SUB, true);
                    h.setPadding(dp(22), dp(14), dp(22), dp(6));
                }
                h.setText((String) o);
                return h;
            }
            Holder hd;
            if (v == null) {
                hd = new Holder();
                LinearLayout wrap = Ui.col(MainActivity.this);
                wrap.setPadding(dp(14), dp(5), dp(14), dp(5));
                LinearLayout card = Ui.col(MainActivity.this);
                card.setPadding(dp(16), dp(13), dp(16), dp(14));
                card.setBackgroundDrawable(Ui.press(
                        Ui.round(Ui.CARD, dp(16), Ui.LINE, dp(1)),
                        Ui.round(0xFFF7F8FC, dp(16), Ui.LINE, dp(1))));
                if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(dp(1));

                LinearLayout top = Ui.row(MainActivity.this);
                hd.avatar = new ImageView(MainActivity.this);
                top.addView(hd.avatar, Ui.lp(dp(38), dp(38)));
                LinearLayout names = Ui.col(MainActivity.this);
                names.setPadding(dp(10), 0, dp(8), 0);
                hd.badge = Ui.text(MainActivity.this, "", 14, Ui.ACCENT, true);
                hd.badge.setSingleLine(true);
                hd.badge.setEllipsize(TextUtils.TruncateAt.END);
                names.addView(hd.badge, Ui.lp(-1, -2));
                hd.time = Ui.text(MainActivity.this, "", 12, Ui.SUB, false);
                names.addView(hd.time, Ui.lp(-1, -2));
                top.addView(names, Ui.lp(0, -2, 1));
                card.addView(top, Ui.lp(-1, -2));

                hd.title = Ui.text(MainActivity.this, "", 16.5f, Ui.TEXT, true);
                hd.title.setLineSpacing(0, 1.1f);
                LinearLayout.LayoutParams tl = Ui.lp(-1, -2);
                tl.topMargin = dp(9);
                card.addView(hd.title, tl);

                hd.text = Ui.text(MainActivity.this, "", 15, Ui.TEXT2, false);
                hd.text.setLineSpacing(0, 1.18f);
                hd.text.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams xl = Ui.lp(-1, -2);
                xl.topMargin = dp(5);
                card.addView(hd.text, xl);

                hd.more = Ui.text(MainActivity.this, "עוד…", 13, Ui.ACCENT, true);
                LinearLayout.LayoutParams ml = Ui.lp(-2, -2);
                ml.topMargin = dp(4);
                card.addView(hd.more, ml);

                final Holder fh = hd;
                card.setOnClickListener(x -> toggle(fh));
                card.setOnLongClickListener(x -> { copy(fh.item); return true; });
                wrap.addView(card, Ui.lp(-1, -2));
                hd.card = card;
                v = wrap;
                v.setTag(hd);
            } else {
                hd = (Holder) v.getTag();
            }
            bind(hd, (Item) o);
            return v;
        }
    }

    private void bind(Holder h, Item it) {
        h.item = it;
        int color = it.color();
        h.avatar.setImageBitmap(Icons.get(this, it, dp(38)));
        boolean hasSub = it.sub != null && it.sub.length() > 0;
        h.badge.setText(hasSub ? it.sub : it.label());
        h.badge.setTextColor(color);
        h.time.setText((hasSub ? it.label() + " · " : "") + clock(it.time));
        boolean hasTitle = it.title != null && it.title.length() > 0;
        boolean hasText = it.text != null && it.text.length() > 0;
        h.title.setVisibility(hasTitle ? View.VISIBLE : View.GONE);
        h.title.setText(hasTitle ? it.title : "");
        h.text.setVisibility(hasText ? View.VISIBLE : View.GONE);
        h.text.setText(hasText ? it.text : "");
        ((LinearLayout.LayoutParams) h.text.getLayoutParams()).topMargin = hasTitle ? dp(5) : dp(9);
        boolean open = expanded.contains(it.id);
        h.text.setMaxLines(open ? Integer.MAX_VALUE : 5);
        boolean longText = hasText && (it.text.length() > 220 || countLines(it.text) > 5);
        h.more.setVisibility(longText ? View.VISIBLE : View.GONE);
        h.more.setText(open ? "פחות" : "עוד…");
    }

    private static int countLines(String s) {
        int n = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    private void toggle(Holder h) {
        if (h.item == null) return;
        if (!expanded.remove(h.item.id)) expanded.add(h.item.id);
        bind(h, h.item);
    }

    private void copy(Item it) {
        if (it == null) return;
        String s = (it.title != null && it.title.length() > 0 ? it.title + "\n" : "") + (it.text == null ? "" : it.text);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("פוש", s.trim()));
        Toast.makeText(this, "הטקסט הועתק", Toast.LENGTH_SHORT).show();
    }
}
