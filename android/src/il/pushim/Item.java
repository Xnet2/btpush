package il.pushim;

import org.json.JSONObject;

/** פריט אחד: ידיעה מאתר או התראה מהמחשב. */
final class Item {
    String id, src, sub, title, text, icon;
    String img;   // כתובות תמונות, מופרדות בשורה (נטענות רק לפי בקשה)
    long time;
    long recv;   // מתי הגיע לנגן (לספירת "לא נקראו")

    /** המפתח של הערוץ: הגיזרה, כל ערוץ באותנטי, וכל תוכנה במחשב הם ערוץ נפרד. */
    String chat() { return chatKey(src, sub, icon); }

    static String chatKey(String src, String sub, String icon) {
        if ("hagizra".equals(src)) return "hagizra";
        if ("authenti".equals(src)) return sub == null || sub.length() == 0 ? "authenti" : "a:" + sub;
        String k = icon != null && icon.length() > 0 ? icon : (sub == null ? "" : sub);
        return "pc:" + k;
    }

    /** שם הערוץ כמו שמוצג ברשימה. */
    String chatName() {
        if ("hagizra".equals(src)) return "הגיזרה";
        if (sub != null && sub.length() > 0) return sub;
        return label();
    }

    /** שורת התצוגה המקדימה ברשימת הערוצים. */
    String preview() {
        String t = title != null && title.length() > 0 ? title : "";
        String x = text != null ? text : "";
        String s = t.length() > 0 && x.length() > 0 ? t + " · " + x : t + x;
        s = s.replace('\n', ' ');
        if (s.length() == 0 && imgCount() > 0) return imgLabel();
        return s;
    }

    String[] imgUrls() {
        if (img == null || img.trim().length() == 0) return new String[0];
        return img.trim().split("\n");
    }

    int imgCount() { return imgUrls().length; }

    String imgLabel() { return imgCount() == 1 ? "תמונה" : imgCount() + " תמונות"; }

    /** פריט "מדומה" לערוץ שעוד אין בו עדכונים — בשביל השם והלוגו. */
    static Item forChat(android.content.Context c, String key) {
        Item it = new Item();
        it.id = "";
        it.title = "";
        it.text = "";
        it.time = System.currentTimeMillis();
        if ("hagizra".equals(key)) {
            it.src = "hagizra"; it.sub = "";
        } else if (key.startsWith("a:")) {
            it.src = "authenti"; it.sub = key.substring(2);
        } else if (key.startsWith("pc:")) {
            it.src = "pc"; it.icon = key.substring(3); it.sub = it.icon;
            for (String[] app : Prefs.knownApps(c)) if (app[0].equals(it.icon)) it.sub = app[1];
        } else {
            it.src = "authenti"; it.sub = "";
        }
        return it;
    }

    static Item from(JSONObject o) {
        Item it = new Item();
        it.id = o.optString("id", "");
        it.src = o.optString("src", "pc");
        it.sub = o.optString("sub", "");
        it.title = o.optString("title", "");
        it.text = o.optString("text", "");
        it.icon = o.optString("icon", "");
        it.img = o.optString("img", "");
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
        if ("hagizra".equals(src)) return 0xFFC62F3B;
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
        if (imgCount() > 0) return imgLabel();
        return title != null && title.length() > 0 ? "" : label();
    }
}
