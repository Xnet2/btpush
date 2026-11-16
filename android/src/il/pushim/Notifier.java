package il.pushim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import il.pushim.player.R;

/** הפושים בשורת ההתראות. */
final class Notifier {
    static final int ONGOING_ID = 1;
    private static int nextId = 100;

    /** ערוץ לכל שילוב של צליל/רטט, כי באנדרואיד 8+ אי אפשר לשנות ערוץ אחרי שנוצר. */
    private static String channelId(Context c) {
        boolean s = Prefs.sound(c), v = Prefs.vibrate(c);
        return "push_" + (s ? "s" : "") + (v ? "v" : "") + (!s && !v ? "q" : "");
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel link = new NotificationChannel("link", "חיבור למחשב", NotificationManager.IMPORTANCE_MIN);
        link.setShowBadge(false);
        nm.createNotificationChannel(link);
        String[][] all = {{"push_sv", "פושים"}, {"push_s", "פושים (בלי רטט)"}, {"push_v", "פושים (רטט בלבד)"}, {"push_q", "פושים (שקט)"}};
        for (String[] ch : all) {
            NotificationChannel n = new NotificationChannel(ch[0], ch[1], NotificationManager.IMPORTANCE_HIGH);
            boolean sound = ch[0].equals("push_sv") || ch[0].equals("push_s");
            boolean vib = ch[0].endsWith("v");
            if (!sound) n.setSound(null, null);
            n.enableVibration(vib);
            if (vib) n.setVibrationPattern(new long[]{0, 220, 120, 220});
            n.enableLights(true);
            n.setLightColor(Ui.ACCENT);
            nm.createNotificationChannel(n);
        }
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(c, 0, i, flags);
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder builder(Context c, String channel) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
    }

    /** ההתראה הקבועה של השירות שמחכה למחשב. */
    @SuppressWarnings("deprecation")
    static Notification ongoing(Context c, String text) {
        Notification.Builder b = builder(c, "link")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("פושים")
                .setContentText(text)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(openApp(c))
                .setPriority(Notification.PRIORITY_MIN);
        if (Build.VERSION.SDK_INT >= 21) b.setColor(Ui.ACCENT);
        return b.build();
    }

    static void updateOngoing(Context c, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(ONGOING_ID, ongoing(c, text));
    }

    @SuppressWarnings("deprecation")
    static void post(Context c, Item it) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        String title = it.pushTitle(), text = it.pushText();
        Notification.Builder b = builder(c, channelId(c))
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(it.badge())
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setWhen(it.time)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .setPriority(Notification.PRIORITY_HIGH);
        try {
            int size = c.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
            b.setLargeIcon(Icons.get(c, it, size));
        } catch (Exception ignored) { }
        if (Build.VERSION.SDK_INT >= 21) {
            b.setColor(it.color());
            b.setCategory(Notification.CATEGORY_MESSAGE);
            b.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        if (Build.VERSION.SDK_INT < 26) {
            int d = Notification.DEFAULT_LIGHTS;
            if (Prefs.sound(c)) d |= Notification.DEFAULT_SOUND;
            if (Prefs.vibrate(c)) d |= Notification.DEFAULT_VIBRATE;
            b.setDefaults(d);
        }
        nm.notify(nextId++, b.build());
        if (nextId > 100000) nextId = 100;
    }

    /** כשמגיעים הרבה פריטים בבת אחת (אחרי ניתוק) — פוש אחד מסכם. */
    @SuppressWarnings("deprecation")
    static void summary(Context c, int count) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b = builder(c, channelId(c))
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("התקבלו " + count + " פריטים חדשים")
                .setContentText("פריטים שהצטברו בזמן שהנגן לא היה מחובר")
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .setPriority(Notification.PRIORITY_HIGH);
        if (Build.VERSION.SDK_INT >= 21) b.setColor(Ui.ACCENT);
        if (Build.VERSION.SDK_INT < 26) {
            int d = Notification.DEFAULT_LIGHTS;
            if (Prefs.sound(c)) d |= Notification.DEFAULT_SOUND;
            if (Prefs.vibrate(c)) d |= Notification.DEFAULT_VIBRATE;
            b.setDefaults(d);
        }
        nm.notify(2, b.build());
    }

    static void cancelAll(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();
        if (LinkService.self != null) LinkService.self.refreshOngoing();
    }
}
