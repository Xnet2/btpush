package il.pushim;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** המסך הראשי: רשימת הערוצים בסגנון טלגרם, עם תיקיות וחיפוש. */
public final class MainActivity extends Activity implements LinkService.Listener {
    private static final String[] FILTERS = {null, "hagizra", "authenti", "pc"};
    private static final String[] FILTER_NAMES = {"הכל", "הגיזרה", "אותנטי", "מחשב"};
    private static final String[] DAYS = {"א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"};
    private static final int CHIP_BG = 0xFFF1F2F5, CHIP_TEXT = 0xFF4A4F57;

    private String filter;
    /** שורות הרשימה: Store.Chat (ערוץ), Item (הודעה בתוצאות חיפוש) או String (כותרת קבוצה). */
    private final List<Object> rows = new ArrayList<>();
    private Adapter adapter;
    private ListView list;
    private LinearLayout empty;
    private TextView emptyTitle, emptyText;
    private TextView statusText;
    private View statusDot;
    private LinearLayout normalBar, searchBar, chipsRow;
    private HorizontalScrollView chipsScroll;
    private EditText searchField;
    private Glyph searchClear;
    private boolean searching;
    private LinearLayout emptyPull;
    private FrameLayout fab;
    private String query = "";
    private final LinearLayout[] chips = new LinearLayout[FILTERS.length];
    private final TextView[] chipText = new TextView[FILTERS.length];
    private final TextView[] chipCount = new TextView[FILTERS.length];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinkService.start(this);

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(0xFFFFFFFF);

        LinearLayout header = Ui.col(this);
        header.setBackgroundColor(0xFFFFFFFF);
        if (android.os.Build.VERSION.SDK_INT >= 21) header.setElevation(dp(1.5f));

        // ---- סרגל רגיל: כותרת, מצב חיבור, חיפוש והגדרות
        normalBar = Ui.row(this);
        normalBar.setPadding(dp(6), dp(10), dp(18), dp(4));
        LinearLayout titles = Ui.col(this);
        TextView title = Ui.text(this, "ביטיפוש", 23, Ui.TG_NAME, true);
        if (android.os.Build.VERSION.SDK_INT >= 21) title.setLetterSpacing(-0.01f);
        titles.addView(title);
        LinearLayout status = Ui.row(this);
        status.setPadding(0, dp(1), 0, 0);
        statusDot = Ui.dot(this, Ui.AMBER, 7);
        status.addView(statusDot);
        statusText = Ui.text(this, "", 13, Ui.TG_SUBTITLE, false);
        statusText.setPadding(0, 0, dp(6), 0);
        status.addView(statusText);
        status.setOnClickListener(v -> onStatusClick());
        titles.addView(status);
        normalBar.addView(titles, Ui.lp(0, -2, 1));
        normalBar.addView(Ui.iconButton(this, Glyph.SEARCH, 0xFF5F6670, v -> openSearch()));
        normalBar.addView(Ui.iconButton(this, Glyph.GEAR, 0xFF5F6670, v -> startActivity(new Intent(this, SettingsActivity.class))));
        header.addView(normalBar, Ui.lp(-1, -2));

        // ---- סרגל חיפוש
        searchBar = Ui.row(this);
        searchBar.setPadding(dp(6), dp(8), dp(12), dp(8));
        searchBar.addView(Ui.iconButton(this, Glyph.BACK, 0xFF5F6670, v -> closeSearch()));
        FrameLayout field = new FrameLayout(this);
        field.setBackgroundDrawable(Ui.round(CHIP_BG, dp(20)));
        searchField = new EditText(this);
        searchField.setHint("חיפוש ערוצים והודעות");
        searchField.setHintTextColor(0xFF9AA0A8);
        searchField.setTextColor(Ui.TG_NAME);
        searchField.setTextSize(16);
        searchField.setSingleLine(true);
        searchField.setBackgroundDrawable(null);
        searchField.setPadding(dp(16), dp(8), dp(40), dp(8));
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        Ui.rtlText(searchField);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable e) {
                query = e.toString();
                searchClear.setVisibility(query.length() > 0 ? View.VISIBLE : View.GONE);
                refresh();
            }
        });
        field.addView(searchField, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));
        searchClear = new Glyph(this, Glyph.CLOSE, 0xFF8A9099);
        searchClear.setPadding(dp(10), dp(10), dp(10), dp(10));
        searchClear.setVisibility(View.GONE);
        searchClear.setOnClickListener(v -> searchField.setText(""));
        field.addView(searchClear, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER_VERTICAL | Gravity.LEFT));
        searchBar.addView(field, Ui.lp(0, dp(42), 1));
        searchBar.setVisibility(View.GONE);
        header.addView(searchBar, Ui.lp(-1, -2));

        // ---- תיקיות: גלולות
        chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        Ui.rtl(chipsScroll);
        chipsRow = Ui.row(this);
        chipsRow.setPadding(dp(14), dp(6), dp(14), dp(12));
        for (int i = 0; i < FILTERS.length; i++) {
            final int idx = i;
            LinearLayout chip = Ui.row(this);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(15), 0, dp(15), 0);
            chipText[i] = Ui.center(Ui.text(this, FILTER_NAMES[i], 14.5f, CHIP_TEXT, true));
            chip.addView(chipText[i]);
            chipCount[i] = Ui.center(Ui.text(this, "", 11.5f, 0xFFFFFFFF, true));
            chipCount[i].setMinWidth(dp(19));
            chipCount[i].setPadding(dp(5), 0, dp(5), 0);
            LinearLayout.LayoutParams cl = Ui.lp(-2, dp(19));
            cl.setMargins(0, 0, dp(6), 0);
            chip.addView(chipCount[i], cl);
            chip.setOnClickListener(v -> { filter = FILTERS[idx]; refresh(); });
            LinearLayout.LayoutParams pl = Ui.lp(-2, dp(34));
            pl.setMargins(dp(8), 0, 0, 0);
            chipsRow.addView(chip, pl);
            chips[i] = chip;
        }
        chipsScroll.addView(chipsRow);
        header.addView(chipsScroll, Ui.lp(-1, -2));
        root.addView(header, Ui.lp(-1, -2));
        root.addView(Ui.shadowLine(this));

        // ---- הרשימה
        FrameLayout frame = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(false);
        list.setCacheColorHint(0);
        Ui.rtl(list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        frame.addView(list, new FrameLayout.LayoutParams(-1, -1));

        empty = Ui.col(this);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(40), 0, dp(40), dp(70));
        ImageView logo = new ImageView(this);
        logo.setImageResource(il.pushim.player.R.drawable.icon);
        empty.addView(logo, Ui.lp(dp(72), dp(72)));
        emptyTitle = Ui.center(Ui.text(this, "", 17, Ui.TG_NAME, true));
        LinearLayout.LayoutParams el = Ui.lp(-1, -2);
        el.topMargin = dp(14);
        empty.addView(emptyTitle, el);
        emptyText = Ui.center(Ui.text(this, "", 14.5f, Ui.TG_GRAY_TEXT, false));
        emptyText.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams el2 = Ui.lp(-1, -2);
        el2.topMargin = dp(6);
        empty.addView(emptyText, el2);
        emptyPull = Ui.row(this);
        emptyPull.setGravity(Gravity.CENTER);
        emptyPull.setPadding(dp(20), 0, dp(20), 0);
        emptyPull.setBackgroundDrawable(Ui.press(Ui.round(Ui.ACCENT, dp(20)), Ui.round(0xFFDE465C, dp(20))));
        Glyph epg = new Glyph(this, Glyph.DOWNLOAD, 0xFFFFFFFF);
        epg.setWeight(1.2f);
        emptyPull.addView(epg, Ui.lp(dp(17), dp(17)));
        TextView ept = Ui.text(this, "שאיבת עדכונים אחרונים", 14.5f, 0xFFFFFFFF, true);
        ept.setPadding(0, 0, dp(8), 0);
        emptyPull.addView(ept, Ui.lp(-2, -2));
        emptyPull.setOnClickListener(v -> pickPullChannel());
        LinearLayout.LayoutParams epl = Ui.lp(-2, dp(40));
        epl.topMargin = dp(18);
        empty.addView(emptyPull, epl);
        frame.addView(empty, new FrameLayout.LayoutParams(-1, -1));

        // כפתור שאיבה עגול בפינה (כמו כפתור הכתיבה בטלגרם)
        fab = new FrameLayout(this);
        fab.setBackgroundDrawable(Ui.press(oval(Ui.ACCENT), oval(0xFFDE465C)));
        if (android.os.Build.VERSION.SDK_INT >= 21) fab.setElevation(dp(5));
        Glyph fg = new Glyph(this, Glyph.DOWNLOAD, 0xFFFFFFFF);
        fg.setWeight(1.25f);
        fg.setPadding(dp(16), dp(16), dp(16), dp(16));
        fab.addView(fg, new FrameLayout.LayoutParams(-1, -1));
        fab.setOnClickListener(v -> pickPullChannel());
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.LEFT);
        fl.setMargins(dp(18), 0, 0, dp(18));
        frame.addView(fab, fl);

        root.addView(frame, Ui.lp(-1, 0, 1));
        setContentView(root);
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
    public void onBackPressed() {
        if (searching) closeSearch();
        else super.onBackPressed();
    }

    @Override
    public void onLinkChanged() {
        int s = LinkService.state;
        int color;
        String text;
        if (s == LinkService.CONNECTED) {
            color = Ui.GREEN;
            text = "מחובר" + (LinkService.peer.length() > 0 ? " למחשב " + LinkService.peer : " למחשב");
        } else if (s == LinkService.OFF) {
            color = Ui.GRAY;
            text = "הבלוטות' כבוי · להפעלה";
        } else {
            color = Ui.AMBER;
            text = "ממתין למחשב…";
        }
        ((android.graphics.drawable.GradientDrawable) statusDot.getBackground()).setColor(color);
        statusText.setText(text);
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

    // ---------------------------------------------------------------- חיפוש

    private void openSearch() {
        searching = true;
        normalBar.setVisibility(View.GONE);
        chipsScroll.setVisibility(View.GONE);
        searchBar.setVisibility(View.VISIBLE);
        searchField.requestFocus();
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        im.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
        refresh();
    }

    private void closeSearch() {
        searching = false;
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        searchField.setText("");
        searchBar.setVisibility(View.GONE);
        normalBar.setVisibility(View.VISIBLE);
        chipsScroll.setVisibility(View.VISIBLE);
        refresh();
    }

    // ---------------------------------------------------------------- תוכן הרשימה

    private void refresh() {
        List<Store.Chat> all = Store.get(this).chats(null);
        rows.clear();
        if (searching) {
            String q = query.trim().toLowerCase();
            if (q.length() > 0) {
                List<Store.Chat> found = new ArrayList<>();
                for (Store.Chat ch : all) if (ch.last.chatName().toLowerCase().contains(q)) found.add(ch);
                if (!found.isEmpty()) { rows.add("ערוצים"); rows.addAll(found); }
                List<Item> msgs = Store.get(this).search(query, 120);
                if (!msgs.isEmpty()) { rows.add("הודעות"); rows.addAll(msgs); }
            }
            emptyTitle.setText(q.length() == 0 ? "חיפוש" : "לא נמצאו תוצאות");
            emptyText.setText(q.length() == 0 ? "מקלידים שם של ערוץ או מילה מתוך הודעה" : "נסו מילה אחרת");
        } else {
            for (Store.Chat ch : all) if (filter == null || filter.equals(ch.last.src)) rows.add(ch);
            emptyTitle.setText("עדיין אין עדכונים");
            emptyText.setText("כשהמחשב יתחבר בבלוטות', כל ידיעה והתראה חדשה תופיע כאן בערוץ משלה");
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        emptyPull.setVisibility(searching ? View.GONE : View.VISIBLE);
        fab.setVisibility(searching || rows.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < FILTERS.length; i++) {
            boolean sel = (FILTERS[i] == null && filter == null) || (FILTERS[i] != null && FILTERS[i].equals(filter));
            chips[i].setBackgroundDrawable(Ui.round(sel ? Ui.ACCENT : CHIP_BG, dp(17)));
            chipText[i].setTextColor(sel ? 0xFFFFFFFF : CHIP_TEXT);
            int n = 0;
            for (Store.Chat ch : all)
                if ((FILTERS[i] == null || FILTERS[i].equals(ch.last.src)) && !Prefs.muted(this, ch.key)) n += ch.unread;
            chipCount[i].setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            chipCount[i].setText(n > 999 ? "999+" : String.valueOf(n));
            chipCount[i].setTextColor(sel ? Ui.ACCENT : 0xFFFFFFFF);
            chipCount[i].setBackgroundDrawable(Ui.round(sel ? 0xFFFFFFFF : Ui.ACCENT, dp(9.5f)));
        }
    }

    private static String when(long t) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        if (now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR))
            return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        if (now.getTimeInMillis() - t < 6L * 86400000L) return "יום " + DAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
        return String.format(Locale.US, "%d.%d.%02d", c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) % 100);
    }

    private int dp(float v) { return Ui.dp(this, v); }

    /** מדגיש את מילת החיפוש בטקסט. */
    private CharSequence highlight(String s, String q) {
        if (s == null) return "";
        SpannableString sp = new SpannableString(s);
        String q2 = q.trim().toLowerCase();
        if (q2.length() == 0) return sp;
        int i = s.toLowerCase().indexOf(q2);
        if (i >= 0) {
            sp.setSpan(new ForegroundColorSpan(Ui.ACCENT), i, i + q2.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.BOLD), i, i + q2.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sp;
    }

    /** קטע מהטקסט סביב מילת החיפוש, כדי שהיא תופיע בשורה. */
    private static String snippet(Item it, String q) {
        String s = it.preview();
        String q2 = q.trim().toLowerCase();
        int i = s.toLowerCase().indexOf(q2);
        if (i > 40) s = "…" + s.substring(i - 30);
        return s;
    }

    // ---------------------------------------------------------------- שורות

    private static final class Holder {
        ImageView avatar;
        TextView name, time, preview, badge;
        Glyph mute;
        Object data;
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
                    h = Ui.text(MainActivity.this, "", 14, Ui.ACCENT, true);
                    h.setPadding(dp(18), dp(14), dp(18), dp(6));
                    h.setBackgroundColor(0xFFFFFFFF);
                }
                h.setText((String) o);
                return h;
            }
            Holder h;
            if (v == null) {
                h = new Holder();
                LinearLayout wrap = Ui.col(MainActivity.this);
                LinearLayout row = Ui.row(MainActivity.this);
                row.setMinimumHeight(dp(66));
                row.setPadding(dp(14), dp(9), dp(8), dp(9));   // הלוגו קרוב לקצה הימני
                h.avatar = new ImageView(MainActivity.this);
                row.addView(h.avatar, Ui.lp(dp(48), dp(48)));

                LinearLayout col = Ui.col(MainActivity.this);
                col.setPadding(0, 0, dp(14), 0);   // רווח בין הלוגו לטקסט (בצד של הלוגו)
                LinearLayout top = Ui.row(MainActivity.this);
                h.name = Ui.text(MainActivity.this, "", 16, Ui.TG_NAME, true);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.END);
                h.name.setMaxWidth(getResources().getDisplayMetrics().widthPixels - dp(170));
                top.addView(h.name, Ui.lp(-2, -2));
                h.mute = new Glyph(MainActivity.this, Glyph.MUTE, Ui.TG_MUTE_ICON);
                LinearLayout.LayoutParams ml = Ui.lp(dp(15), dp(15));
                ml.setMargins(0, 0, dp(5), 0);
                top.addView(h.mute, ml);
                top.addView(new View(MainActivity.this), Ui.lp(0, 1, 1));
                h.time = Ui.text(MainActivity.this, "", 12.5f, Ui.TG_DATE, false);
                top.addView(h.time, Ui.lp(-2, -2));
                col.addView(top, Ui.lp(-1, -2));

                LinearLayout bottom = Ui.row(MainActivity.this);
                h.preview = Ui.text(MainActivity.this, "", 14.5f, Ui.TG_MSG, false);
                h.preview.setSingleLine(true);
                h.preview.setEllipsize(TextUtils.TruncateAt.END);
                bottom.addView(h.preview, Ui.lp(0, -2, 1));
                h.badge = Ui.center(Ui.text(MainActivity.this, "", 11.5f, 0xFFFFFFFF, true));
                h.badge.setMinWidth(dp(21));
                h.badge.setPadding(dp(6), 0, dp(6), 0);
                LinearLayout.LayoutParams bl = Ui.lp(-2, dp(21));
                bl.setMargins(0, 0, dp(8), 0);
                bottom.addView(h.badge, bl);
                LinearLayout.LayoutParams btl = Ui.lp(-1, -2);
                btl.topMargin = dp(3);
                col.addView(bottom, btl);
                row.addView(col, Ui.lp(0, -2, 1));
                wrap.addView(row, Ui.lp(-1, -2));

                View div = new View(MainActivity.this);
                div.setBackgroundColor(0xFFEBEDF0);
                LinearLayout.LayoutParams dl = Ui.lp(-1, 1);
                if (android.os.Build.VERSION.SDK_INT >= 17) dl.setMarginStart(dp(70));
                else dl.setMargins(0, 0, dp(70), 0);
                wrap.addView(div, dl);

                wrap.setBackgroundDrawable(Ui.press(new android.graphics.drawable.ColorDrawable(0xFFFFFFFF),
                        new android.graphics.drawable.ColorDrawable(0xFFF3F4F6)));
                final Holder fh = h;
                wrap.setOnClickListener(x -> open(fh.data));
                wrap.setOnLongClickListener(x -> { if (fh.data instanceof Store.Chat) menu((Store.Chat) fh.data); return true; });
                v = wrap;
                v.setTag(h);
            } else {
                h = (Holder) v.getTag();
            }
            if (o instanceof Store.Chat) bindChat(h, (Store.Chat) o);
            else bindMessage(h, (Item) o);
            return v;
        }
    }

    private void bindChat(Holder h, Store.Chat ch) {
        h.data = ch;
        Item it = ch.last;
        boolean muted = Prefs.muted(this, ch.key);
        h.avatar.setImageBitmap(Icons.get(this, it, dp(48)));
        h.name.setText(searching ? highlight(it.chatName(), query) : it.chatName());
        h.mute.setVisibility(muted ? View.VISIBLE : View.GONE);
        h.time.setText(when(it.time));
        h.preview.setText(it.preview());
        h.badge.setVisibility(ch.unread > 0 ? View.VISIBLE : View.GONE);
        h.badge.setText(ch.unread > 999 ? "999+" : String.valueOf(ch.unread));
        h.badge.setBackgroundDrawable(Ui.round(muted ? Ui.TG_COUNTER_MUTED : Ui.ACCENT, dp(10.5f)));
        h.time.setTextColor(ch.unread > 0 && !muted ? Ui.ACCENT : Ui.TG_DATE);
    }

    private void bindMessage(Holder h, Item it) {
        h.data = it;
        h.avatar.setImageBitmap(Icons.get(this, it, dp(48)));
        h.name.setText(it.chatName());
        h.mute.setVisibility(View.GONE);
        h.time.setText(when(it.time));
        h.time.setTextColor(Ui.TG_DATE);
        h.preview.setText(highlight(snippet(it, query), query));
        h.badge.setVisibility(View.GONE);
    }

    private void open(Object data) {
        Intent i = new Intent(this, ChannelActivity.class);
        if (data instanceof Store.Chat) {
            i.putExtra("chat", ((Store.Chat) data).key);
        } else if (data instanceof Item) {
            i.putExtra("chat", ((Item) data).chat());
            i.putExtra("focus", ((Item) data).id);
        } else {
            return;
        }
        startActivity(i);
    }

    private static android.graphics.drawable.GradientDrawable oval(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /**
     * שאיבה מכל ערוץ — גם מערוץ שעוד לא הגיע אליו אף עדכון:
     * הגיזרה, כל ערוצי אותנטי וכל התוכנות שהמחשב מכיר.
     */
    private void pickPullChannel() {
        if (LinkService.state != LinkService.CONNECTED) {
            Toast.makeText(this, "צריך חיבור למחשב כדי לשאוב עדכונים", Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> keys = new ArrayList<>();
        List<String> names = new ArrayList<>();
        keys.add("hagizra");
        names.add("הגיזרה");
        for (String ch : Prefs.knownChannels(this)) {
            keys.add("a:" + ch);
            names.add(ch + "  ·  אותנטי");
        }
        for (String[] app : Prefs.knownApps(this)) {
            keys.add("pc:" + app[0]);
            names.add(app[1] + "  ·  מחשב");
        }
        Ui.dialog(this)
                .setTitle("מאיזה ערוץ לשאוב?")
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    Intent i = new Intent(this, ChannelActivity.class);
                    i.putExtra("chat", keys.get(w));
                    i.putExtra("pull", true);
                    startActivity(i);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    /** לחיצה ארוכה: השתקה, סימון כנקרא, ניקוי היסטוריה. */
    private void menu(Store.Chat ch) {
        boolean muted = Prefs.muted(this, ch.key);
        String[] opts = {muted ? "ביטול השתקה" : "השתקה (בלי פושים)", "סימון כנקרא", "ניקוי היסטוריה"};
        Ui.dialog(this)
                .setTitle(ch.last.chatName())
                .setItems(opts, (d, w) -> {
                    if (w == 0) Prefs.setMuted(this, ch.key, !muted);
                    else if (w == 1) Prefs.markRead(this, ch.key);
                    else Store.get(this).clearChat(ch.key);
                    refresh();
                })
                .show();
    }
}
