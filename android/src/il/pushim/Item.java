package il.pushim;

import org.json.JSONObject;

/** פריט אחד: ידיעה מאתר או התראה מהמחשב. */
final class Item {
    String id, src, sub, title, text, icon;
    long time;

    static Item from(JSONObject o) {
        Item it = new Item();
        it.id = o.optString("id", "");
        it.src = o.optString("src", "pc");
        it.sub = o.optString("sub", "");
        it.title = o.optString("title", "");
        it.text = o.optString("text", "");
        it.icon = o.optString("icon", "");
        it.time = o.optLong("time", System.currentTimeMillis());
        if (it.time <= 0) it.time = System.currentTimeMillis();
        return it;
    }

    static String label(String src) {
        if ("hagizra".equals(src)) return "הגיזרה";
        if ("authenti".equals(src)) return "אותנטי";
        return "מחשב";
    }

    static int color(String src) {
        if ("hagizra".equals(src)) return 0xFFE5484D;
        if ("authenti".equals(src)) return 0xFF2F7CF6;
        return 0xFF8B5CF6;
    }

    String label() { return label(src); }
    int color() { return color(src); }

    /** התגית שבראש הכרטיס: "אותנטי · המיטב", "מחשב · Chrome". */
    String badge() {
        return sub == null || sub.length() == 0 ? label() : label() + " · " + sub;
    }

    /** כותרת הפוש. */
    String pushTitle() {
        if (title != null && title.length() > 0) return title;
        if ("authenti".equals(src) && sub != null && sub.length() > 0) return sub;
        return label();
    }

    /** תוכן הפוש. */
    String pushText() {
        if (text != null && text.length() > 0) return text;
        return title != null && title.length() > 0 ? "" : label();
    }
}
