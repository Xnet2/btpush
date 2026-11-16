package il.pushim;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

/**
 * שירות שרץ ברקע ומחכה שהמחשב יתחבר בבלוטות'.
 * המחשב מתחבר לשירות לפי ה-UUID, שולח פריטים (שורת JSON לכל פריט), והנגן שולח לו את ההגדרות.
 */
public final class LinkService extends Service {
    static final UUID SERVICE_UUID = UUID.fromString("b3a7c2d4-5e6f-4a1b-9c8d-7e6f5a4b3c21");

    static final int OFF = 0, WAITING = 1, CONNECTED = 2;

    interface Listener {
        void onLinkChanged();
        void onItemsChanged();
    }

    static volatile LinkService self;
    static volatile Listener listener;
    static volatile int state = WAITING;
    static volatile String peer = "";

    private Handler main;
    private volatile boolean running;
    private Thread acceptThread;
    private BluetoothServerSocket server;
    private volatile BluetoothSocket conn;
    private volatile OutputStream out;
    private final Object writeLock = new Object();
    private volatile long lastRecv;
    private int backlog;

    static void start(Context c) {
        Intent i = new Intent(c, LinkService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) { }
    }

    /** ההגדרות השתנו בנגן — שולחים למחשב. */
    static void pushConfig() {
        LinkService s = self;
        if (s != null) s.sendConfig();
    }

    static void requestTest() {
        LinkService s = self;
        if (s != null) s.send("{\"t\":\"test\"}");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        main = new Handler(Looper.getMainLooper());
        Notifier.channels(this);
        startForeground(Notifier.ONGOING_ID, Notifier.ongoing(this, statusText()));
        running = true;
        acceptThread = new Thread(this::acceptLoop, "pushim-accept");
        acceptThread.start();
        registerReceiver(btReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        main.postDelayed(watchdog, 30000);
        main.post(purge);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        self = null;
        try { unregisterReceiver(btReceiver); } catch (Exception ignored) { }
        main.removeCallbacksAndMessages(null);
        closeConn();
        closeServer();
        if (acceptThread != null) acceptThread.interrupt();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- מצב

    String statusText() {
        if (state == CONNECTED) return "מחובר למחשב" + (peer.length() > 0 ? ": " + peer : "");
        if (state == OFF) return "הבלוטות' כבוי";
        return "ממתין לחיבור מהמחשב";
    }

    void refreshOngoing() {
        try { Notifier.updateOngoing(this, statusText()); } catch (Exception ignored) { }
    }

    private void setState(int s) {
        if (state == s) return;
        state = s;
        main.post(() -> {
            refreshOngoing();
            Listener l = listener;
            if (l != null) l.onLinkChanged();
        });
    }

    private void itemsChanged() {
        main.post(() -> {
            Listener l = listener;
            if (l != null) l.onItemsChanged();
        });
    }

    // ---------------------------------------------------------------- בלוטות'

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            if (st == BluetoothAdapter.STATE_ON) {
                if (acceptThread != null) acceptThread.interrupt();
            } else if (st == BluetoothAdapter.STATE_TURNING_OFF || st == BluetoothAdapter.STATE_OFF) {
                closeConn();
                closeServer();
                setState(OFF);
            }
        }
    };

    private void acceptLoop() {
        while (running) {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled()) {
                setState(OFF);
                sleep(4000);
                continue;
            }
            try {
                if (server == null) {
                    server = a.listenUsingInsecureRfcommWithServiceRecord("Pushim", SERVICE_UUID);
                }
                if (conn == null) setState(WAITING);
                BluetoothSocket s = server.accept();
                if (s != null) {
                    Thread t = new Thread(() -> handle(s), "pushim-conn");
                    t.start();
                }
            } catch (IOException e) {
                closeServer();
                sleep(2500);
            } catch (Exception e) {
                closeServer();
                sleep(4000);
            }
        }
    }

    private void handle(BluetoothSocket s) {
        // חיבור חדש מחליף חיבור ישן (למשל אחרי שהמחשב התעורר משינה)
        closeConn();
        conn = s;
        try {
            out = s.getOutputStream();
            lastRecv = System.currentTimeMillis();
            String name = "";
            try { name = s.getRemoteDevice().getName(); } catch (Exception ignored) { }
            peer = name == null ? "" : name;
            backlog = 0;
            setState(CONNECTED);
            sendConfig();
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                lastRecv = System.currentTimeMillis();
                line = line.trim();
                if (line.length() == 0) continue;
                try { onLine(new JSONObject(line)); } catch (Exception ignored) { }
            }
        } catch (IOException ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) { }
            if (conn == s) {
                conn = null;
                out = null;
                BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                setState(a != null && a.isEnabled() ? WAITING : OFF);
            }
        }
    }

    private void onLine(JSONObject o) {
        String t = o.optString("t");
        switch (t) {
            case "item": {
                Item it = Item.from(o);
                if (it.id.length() == 0 || !Prefs.wanted(this, it)) return;
                if (!Store.get(this).add(it)) return;
                boolean isBacklog = o.optInt("bl", 0) == 1;
                if (isBacklog) {
                    backlog++;
                    main.removeCallbacks(backlogDone);
                    main.postDelayed(backlogDone, 1500);
                } else {
                    Notifier.post(this, it);
                }
                itemsChanged();
                break;
            }
            case "hello": {
                String n = o.optString("name", "");
                if (n.length() > 0) {
                    peer = n;
                    Prefs.setPcName(this, n);
                    main.post(() -> {
                        refreshOngoing();
                        Listener l = listener;
                        if (l != null) l.onLinkChanged();
                    });
                }
                break;
            }
            case "apps":
                Prefs.setKnownApps(this, o.optJSONArray("list"));
                break;
            case "channels":
                Prefs.setKnownChannels(this, o.optJSONArray("list"));
                break;
            case "icon":
                Icons.save(this, o.optString("key"), o.optString("png", null));
                itemsChanged();
                break;
            case "ping":
                send("{\"t\":\"pong\"}");
                break;
            default:
                break;
        }
    }

    private final Runnable backlogDone = new Runnable() {
        @Override
        public void run() {
            if (backlog > 0) Notifier.summary(LinkService.this, backlog);
            backlog = 0;
        }
    };

    void sendConfig() {
        send(Prefs.config(this).toString());
    }

    void send(String json) {
        OutputStream o = out;
        if (o == null) return;
        new Thread(() -> {
            synchronized (writeLock) {
                try {
                    o.write((json + "\n").getBytes("UTF-8"));
                    o.flush();
                } catch (IOException e) {
                    closeConn();
                }
            }
        }, "pushim-send").start();
    }

    private void closeConn() {
        BluetoothSocket s = conn;
        conn = null;
        out = null;
        if (s != null) try { s.close(); } catch (IOException ignored) { }
    }

    private void closeServer() {
        BluetoothServerSocket s = server;
        server = null;
        if (s != null) try { s.close(); } catch (IOException ignored) { }
    }

    /** אם המחשב לא שלח כלום יותר מדקה — החיבור כנראה מת, סוגרים ומחכים לחיבור חדש. */
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (conn != null && System.currentTimeMillis() - lastRecv > 75000) closeConn();
            main.postDelayed(this, 30000);
        }
    };

    private final Runnable purge = new Runnable() {
        @Override
        public void run() {
            try { Store.get(LinkService.this).purge(); } catch (Exception ignored) { }
            itemsChanged();
            main.postDelayed(this, 15 * 60000);
        }
    };

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
