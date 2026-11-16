package il.pushim;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** ההגדרות של האפליקציה. */
final class Prefs {
    static final int[] KEEP_HOURS = {1, 6, 24, 72, 168, 720, 0};
    static final String[] KEEP_NAMES = {"שעה", "6 שעות", "יום", "3 ימים", "שבוע", "חודש", "ללא הגבלה"};
    static final int[] INTERVALS = {10, 15, 20, 30, 60};
    static final String[] INTERVAL_NAMES = {"כל 10 שניות", "כל 15 שניות", "כל 20 שניות", "כל 30 שניות", "כל דקה"};

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("pushim", Context.MODE_PRIVATE);
    }

    static boolean src(Context c, String s) { return sp(c).getBoolean("src_" + s, true); }
    static void setSrc(Context c, String s, boolean on) { sp(c).edit().putBoolean("src_" + s, on).apply(); }

    static boolean save(Context c) { return sp(c).getBoolean("save", true); }
    static void setSave(Context c, boolean on) { sp(c).edit().putBoolean("save", on).apply(); }

    static int keepHours(Context c) { return sp(c).getInt("keep_hours", 72); }
    static void setKeepHours(Context c, int h) { sp(c).edit().putInt("keep_hours", h).apply(); }

    static int interval(Context c) { return sp(c).getInt("interval", 15); }
    static void setInterval(Context c, int s) { sp(c).edit().putInt("interval", s).apply(); }

    static boolean sound(Context c) { return sp(c).getBoolean("sound", true); }
    static void setSound(Context c, boolean on) { sp(c).edit().putBoolean("sound", on).apply(); }

    static boolean vibrate(Context c) { return sp(c).getBoolean("vibrate", true); }
    static void setVibrate(Context c, boolean on) { sp(c).edit().putBoolean("vibrate", on).apply(); }

    static String pcName(Context c) { return sp(c).getString("pc_name", ""); }
    static void setPcName(Context c, String n) { sp(c).edit().putString("pc_name", n).apply(); }

    static String keepName(Context c) {
        int h = keepHours(c);
        for (int i = 0; i < KEEP_HOURS.length; i++) if (KEEP_HOURS[i] == h) return KEEP_NAMES[i];
        return h + " שעות";
    }

    static String intervalName(Context c) {
        int s = interval(c);
        for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == s) return INTERVAL_NAMES[i];
        return "כל " + s + " שניות";
    }

    // ---- קבוצות (תוכנות מושתקות / ערוצים מושתקים)

    static Set<String> set(Context c, String key) {
        Set<String> out = new LinkedHashSet<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    static void putSet(Context c, String key, Set<String> s) {
        JSONArray a = new JSONArray();
        for (String x : s) a.put(x);
        sp(c).edit().putString(key, a.toString()).apply();
    }

    static Set<String> appsOff(Context c) { return set(c, "apps_off"); }
    static Set<String> channelsOff(Context c) { return set(c, "ch_off"); }

    /** התוכנות שהמחשב דיווח עליהן: [מזהה, שם]. */
    static List<String[]> knownApps(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("known_apps", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.optString("id"), o.optString("name")});
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void setKnownApps(Context c, JSONArray list) {
        if (list != null) sp(c).edit().putString("known_apps", list.toString()).apply();
    }

    static List<String> knownChannels(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("known_ch", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    static void setKnownChannels(Context c, JSONArray list) {
        if (list != null) sp(c).edit().putString("known_ch", list.toString()).apply();
    }

    /** ההגדרות שנשלחות למחשב. */
    static JSONObject config(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("t", "config");
            o.put("hagizra", src(c, "hagizra"));
            o.put("authenti", src(c, "authenti"));
            o.put("pc", src(c, "pc"));
            o.put("interval", interval(c));
            JSONArray apps = new JSONArray();
            for (String s : appsOff(c)) apps.put(s);
            o.put("appsOff", apps);
            JSONArray ch = new JSONArray();
            for (String s : channelsOff(c)) ch.put(s);
            o.put("chOff", ch);
        } catch (Exception ignored) { }
        return o;
    }

    /** בדיקה כפולה בצד הנגן, למקרה שפריט הגיע לפני שההגדרות עודכנו במחשב. */
    static boolean wanted(Context c, Item it) {
        if (!src(c, it.src)) return false;
        if ("authenti".equals(it.src) && channelsOff(c).contains(it.sub)) return false;
        return true;
    }
}
