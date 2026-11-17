// "ביטיפוש" — תוכנת המחשב.
// מושכת ידיעות מ"הגיזרה" ומ"אותנטי", קוראת את התראות Windows, ושולחת כל פריט לנגן בבלוטות' (RFCOMM).
// ההגדרות (אילו מקורות, אילו תוכנות וערוצים, תדירות) נקבעות בנגן ומגיעות לכאן בחיבור.
// נבנה עם csc של ‎.NET Framework 4 (C# 5), בלי ספריות חיצוניות.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using System.Xml;
using Microsoft.Win32;

[assembly: System.Reflection.AssemblyTitle("ביטיפוש")]
[assembly: System.Reflection.AssemblyProduct("ביטיפוש")]
[assembly: System.Reflection.AssemblyVersion("1.3.0.0")]
[assembly: System.Reflection.AssemblyFileVersion("1.3.0.0")]

namespace Pushim
{
    static class Program
    {
        public static readonly Guid ServiceUuid = new Guid("b3a7c2d4-5e6f-4a1b-9c8d-7e6f5a4b3c21");
        public const string Version = "1.3";
        public static bool TestMode;

        [STAThread]
        static void Main(string[] args)
        {
            TestMode = HasArg(args, "/test");
            if (args.Length >= 2 && args[0] == "/selftest")
            {
                ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072;
                File.WriteAllText(args[1], new Core().SelfTest(), Encoding.UTF8);
                return;
            }
            if (args.Length >= 2 && args[0] == "/icons")
            {
                var st = State.Load();
                int n = 0;
                foreach (var kv in st.Apps)
                {
                    AppIcons.Dump(kv.Key, Path.Combine(args[1], (n++) + ".png"));
                    File.AppendAllText(Path.Combine(args[1], "list.txt"), (n - 1) + " " + kv.Key + " " + kv.Value + "\r\n", Encoding.UTF8);
                }
                return;
            }
            if (args.Length >= 2 && args[0] == "/bttest")
            {
                // מנסה להתחבר לשירות של פושים בכל מכשיר משויך ומדווח מה קרה (לבדיקה בלבד).
                var sb = new StringBuilder();
                foreach (var d in Bt.Paired())
                {
                    if (d.IsAccessory) continue;
                    var sw = Stopwatch.StartNew();
                    try
                    {
                        using (var s = new Socket((AddressFamily)32, SocketType.Stream, (ProtocolType)3))
                        {
                            s.Connect(new BtEndPoint(d.Addr, ServiceUuid, 0));
                            sb.AppendLine(d.Name + " " + d.AddrText + ": CONNECTED");
                        }
                    }
                    catch (Exception e)
                    {
                        var se = e as SocketException;
                        sb.AppendLine(d.Name + " " + d.AddrText + ": " + e.GetType().Name + (se != null ? " code=" + se.ErrorCode : "") + " " + e.Message + " (" + sw.ElapsedMilliseconds + "ms)");
                    }
                }
                File.WriteAllText(args[1], sb.ToString(), Encoding.UTF8);
                return;
            }
            bool created;
            var mutex = new Mutex(true, "Local\\Pushim-PC", out created);
            var showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, "Local\\Pushim-PC-Show");
            if (!created)
            {
                // כבר רץ: מבקשים מהעותק הקיים להציג את החלון.
                showEvent.Set();
                return;
            }
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072; // TLS 1.2
            ServicePointManager.DefaultConnectionLimit = 8;
            try { SetProcessDPIAware(); } catch { }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            var core = new Core();
            core.Start();
            var app = new TrayApp(core, !HasArg(args, "/tray"), showEvent);
            Application.Run(app);
            core.Stop();
            GC.KeepAlive(mutex);
        }

        [DllImport("user32.dll")] static extern bool SetProcessDPIAware();

        static bool HasArg(string[] args, string a)
        {
            foreach (var s in args) if (string.Equals(s, a, StringComparison.OrdinalIgnoreCase)) return true;
            return false;
        }
    }

    // ---------------------------------------------------------------- נתונים

    static class Json
    {
        static readonly JavaScriptSerializer js = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
        public static string Ser(object o) { lock (js) return js.Serialize(o); }
        public static object Parse(string s) { lock (js) return js.DeserializeObject(s); }

        public static Dictionary<string, object> Obj(object o) { return o as Dictionary<string, object>; }
        public static object[] Arr(object o) { return o as object[]; }
        public static string Str(Dictionary<string, object> d, string k)
        {
            object v; if (d == null || !d.TryGetValue(k, out v) || v == null) return "";
            return Convert.ToString(v, CultureInfo.InvariantCulture);
        }
        public static long Long(Dictionary<string, object> d, string k)
        {
            object v; if (d == null || !d.TryGetValue(k, out v) || v == null) return 0;
            try { return Convert.ToInt64(v, CultureInfo.InvariantCulture); } catch { return 0; }
        }
        public static bool Bool(Dictionary<string, object> d, string k, bool def)
        {
            object v; if (d == null || !d.TryGetValue(k, out v) || v == null) return def;
            if (v is bool) return (bool)v;
            try { return Convert.ToInt64(v, CultureInfo.InvariantCulture) != 0; } catch { return def; }
        }
    }

    class Item
    {
        public string Id, Src, Sub, Title, Text;
        public string Icon = "";   // מזהה התוכנה (להתראות מחשב), לפיו הנגן מציג את האייקון
        public long Time;          // מילישניות מ-1970 (UTC)
        public DateTime Queued = DateTime.UtcNow;

        public string ToJson(bool backlog)
        {
            var d = new Dictionary<string, object>();
            d["t"] = "item"; d["id"] = Id; d["src"] = Src; d["sub"] = Sub ?? "";
            d["title"] = Title ?? ""; d["text"] = Text ?? ""; d["time"] = Time;
            if (!string.IsNullOrEmpty(Icon)) d["icon"] = Icon;
            if (backlog) d["bl"] = 1;
            return Json.Ser(d);
        }
    }

    class State
    {
        public string DeviceAddr = "";   // ריק = זיהוי אוטומטי
        public string LastAddr = "";     // הנגן האחרון שהתחבר
        public bool AutoStartDone;
        public bool SrcHagizra = true, SrcAuthenti = true, SrcPc = true;
        public HashSet<string> AppsOff = new HashSet<string>();
        public HashSet<string> ChannelsOff = new HashSet<string>();
        public int Interval = 15;
        public Dictionary<string, string> Apps = new Dictionary<string, string>();
        public List<string> Channels = new List<string>();

        public static string Dir
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Pushim"); }
        }
        static string FilePath { get { return Path.Combine(Dir, "state.json"); } }

        public static State Load()
        {
            var s = new State();
            try
            {
                if (!File.Exists(FilePath)) return s;
                var d = Json.Obj(Json.Parse(File.ReadAllText(FilePath, Encoding.UTF8)));
                if (d == null) return s;
                s.DeviceAddr = Json.Str(d, "deviceAddr");
                s.LastAddr = Json.Str(d, "lastAddr");
                s.AutoStartDone = Json.Bool(d, "autoStartDone", false);
                s.SrcHagizra = Json.Bool(d, "hagizra", true);
                s.SrcAuthenti = Json.Bool(d, "authenti", true);
                s.SrcPc = Json.Bool(d, "pc", true);
                s.Interval = (int)Math.Max(5, Json.Long(d, "interval"));
                object o;
                if (d.TryGetValue("appsOff", out o) && o is object[]) foreach (var x in (object[])o) s.AppsOff.Add(Convert.ToString(x));
                if (d.TryGetValue("channelsOff", out o) && o is object[]) foreach (var x in (object[])o) s.ChannelsOff.Add(Convert.ToString(x));
                if (d.TryGetValue("channels", out o) && o is object[]) foreach (var x in (object[])o) s.Channels.Add(Convert.ToString(x));
                var apps = d.ContainsKey("apps") ? Json.Obj(d["apps"]) : null;
                if (apps != null) foreach (var kv in apps) s.Apps[kv.Key] = Convert.ToString(kv.Value);
            }
            catch { }
            return s;
        }

        public void Save()
        {
            try
            {
                Directory.CreateDirectory(Dir);
                var d = new Dictionary<string, object>();
                d["deviceAddr"] = DeviceAddr; d["lastAddr"] = LastAddr; d["autoStartDone"] = AutoStartDone;
                d["hagizra"] = SrcHagizra; d["authenti"] = SrcAuthenti; d["pc"] = SrcPc; d["interval"] = Interval;
                d["appsOff"] = new List<string>(AppsOff); d["channelsOff"] = new List<string>(ChannelsOff);
                d["channels"] = Channels; d["apps"] = Apps;
                var tmp = FilePath + ".tmp";
                File.WriteAllText(tmp, Json.Ser(d), Encoding.UTF8);
                if (File.Exists(FilePath)) File.Delete(FilePath);
                File.Move(tmp, FilePath);
            }
            catch { }
        }
    }

    // ---------------------------------------------------------------- בלוטות'

    class BtDevice
    {
        public ulong Addr; public string Name; public uint Cod; public bool Connected;
        public string AddrText { get { return Addr.ToString("X12"); } }
        // אוזניות, רמקולים, עכברים, מדפסות — לא מנסים אותם בחיפוש האוטומטי הרגיל.
        public bool IsAccessory { get { uint major = (Cod >> 8) & 0x1F; return major == 4 || major == 5 || major == 6; } }
        public override string ToString() { return Name; }
    }

    class BtEndPoint : EndPoint
    {
        public readonly ulong Addr; public readonly Guid Service; public readonly int Port;
        public BtEndPoint(ulong addr, Guid service, int port) { Addr = addr; Service = service; Port = port; }
        public override AddressFamily AddressFamily { get { return (AddressFamily)32; } }
        public override SocketAddress Serialize()
        {
            // SOCKADDR_BTH (ארוז, 30 בתים): family(2) btAddr(8) serviceClassId(16) port(4).
            // port=0 → Windows מוצא את הערוץ לפי ה-UUID (שאילתת SDP).
            var sa = new SocketAddress((AddressFamily)32, 30);
            var a = BitConverter.GetBytes(Addr); for (int i = 0; i < 8; i++) sa[2 + i] = a[i];
            var g = Service.ToByteArray(); for (int i = 0; i < 16; i++) sa[10 + i] = g[i];
            var p = BitConverter.GetBytes(Port); for (int i = 0; i < 4; i++) sa[26 + i] = p[i];
            return sa;
        }
        public override EndPoint Create(SocketAddress sa)
        {
            var a = new byte[8]; for (int i = 0; i < 8; i++) a[i] = sa[2 + i];
            var g = new byte[16]; for (int i = 0; i < 16; i++) g[i] = sa[10 + i];
            var p = new byte[4]; for (int i = 0; i < 4; i++) p[i] = sa[26 + i];
            return new BtEndPoint(BitConverter.ToUInt64(a, 0), new Guid(g), BitConverter.ToInt32(p, 0));
        }
    }

    static class Bt
    {
        [StructLayout(LayoutKind.Sequential)]
        struct SearchParams
        {
            public int dwSize;
            public int fReturnAuthenticated, fReturnRemembered, fReturnUnknown, fReturnConnected, fIssueInquiry;
            public byte cTimeoutMultiplier;
            public IntPtr hRadio;
        }
        [StructLayout(LayoutKind.Sequential)]
        struct SystemTime { public ushort a, b, c, d, e, f, g, h; }
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        struct DeviceInfo
        {
            public int dwSize;
            public ulong Address;
            public uint ulClassofDevice;
            public int fConnected, fRemembered, fAuthenticated;
            public SystemTime stLastSeen, stLastUsed;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 248)] public string szName;
        }
        [DllImport("bthprops.cpl", SetLastError = true)] static extern IntPtr BluetoothFindFirstDevice(ref SearchParams p, ref DeviceInfo info);
        [DllImport("bthprops.cpl", SetLastError = true)] static extern bool BluetoothFindNextDevice(IntPtr h, ref DeviceInfo info);
        [DllImport("bthprops.cpl")] static extern bool BluetoothFindDeviceClose(IntPtr h);

        // המכשירים המשויכים למחשב.
        public static List<BtDevice> Paired()
        {
            var list = new List<BtDevice>();
            try
            {
                var p = new SearchParams();
                p.dwSize = Marshal.SizeOf(typeof(SearchParams));
                p.fReturnAuthenticated = 1; p.fReturnRemembered = 1; p.fReturnConnected = 1;
                var info = new DeviceInfo();
                info.dwSize = Marshal.SizeOf(typeof(DeviceInfo));
                IntPtr h = BluetoothFindFirstDevice(ref p, ref info);
                if (h == IntPtr.Zero) return list;
                do
                {
                    if (info.fRemembered != 0 || info.fAuthenticated != 0)
                    {
                        bool dup = false;
                        foreach (var d in list) if (d.Addr == info.Address) dup = true;
                        if (!dup) list.Add(new BtDevice
                        {
                            Addr = info.Address,
                            Name = string.IsNullOrEmpty(info.szName) ? info.Address.ToString("X12") : info.szName,
                            Cod = info.ulClassofDevice,
                            Connected = info.fConnected != 0
                        });
                    }
                    info = new DeviceInfo(); info.dwSize = Marshal.SizeOf(typeof(DeviceInfo));
                } while (BluetoothFindNextDevice(h, ref info));
                BluetoothFindDeviceClose(h);
            }
            catch { }
            return list;
        }
    }

    // ---------------------------------------------------------------- SQLite (winsqlite3 של Windows)

    static class Sqlite
    {
        const string Dll = "winsqlite3.dll";
        [DllImport(Dll, CallingConvention = CallingConvention.StdCall)] public static extern int sqlite3_open16([MarshalAs(UnmanagedType.LPWStr)] string file, out IntPtr db);
        [DllImport(Dll, CallingConvention = CallingConvention.StdCall)] public static extern int sqlite3_close(IntPtr db);
        [DllImport(Dll, CallingConvention = CallingConvention.StdCall)] public static extern int sqlite3_prepare16_v2(IntPtr db, [MarshalAs(UnmanagedType.LPWStr)] string sql, int nBytes, out IntPtr stmt, IntPtr tail);
        [DllImport(Dll, CallingConvention = CallingConvention.StdCall)] public static extern int sqlite3_step(IntPtr stmt);
        [DllImport(Dll, CallingConvention = CallingConvention.StdCall)] public static extern int sqlite3_finalize(IntPtr stmt);
        [DllImport(Dll, CallingConvention = CallingConvention.StdCall)] public static extern long sqlite3_column_int64(IntPtr stmt, int col);
        [DllImport(Dll, CallingConvention = CallingConvention.StdCall)] public static extern IntPtr sqlite3_column_text16(IntPtr stmt, int col);
        [DllImport(Dll, CallingConvention = CallingConvention.StdCall)] public static extern IntPtr sqlite3_column_blob(IntPtr stmt, int col);
        [DllImport(Dll, CallingConvention = CallingConvention.StdCall)] public static extern int sqlite3_column_bytes(IntPtr stmt, int col);
        public const int ROW = 100;

        public static string Text(IntPtr st, int col)
        {
            IntPtr p = sqlite3_column_text16(st, col);
            return p == IntPtr.Zero ? "" : Marshal.PtrToStringUni(p);
        }
        public static byte[] Blob(IntPtr st, int col)
        {
            IntPtr p = sqlite3_column_blob(st, col);
            int n = sqlite3_column_bytes(st, col);
            var b = new byte[Math.Max(0, n)];
            if (p != IntPtr.Zero && n > 0) Marshal.Copy(p, b, 0, n);
            return b;
        }
    }

    // ---------------------------------------------------------------- הליבה

    class Core
    {
        public State St;
        public event Action Changed;
        readonly object stLock = new object();

        // מצב החיבור
        public volatile bool Connected;
        public string PeerName = "";
        public string Searching = "";
        Stream stream;
        int linkGen;
        readonly object writeLock = new object();
        DateTime lastRecv, lastPing;
        readonly AutoResetEvent linkWake = new AutoResetEvent(false);
        readonly AutoResetEvent newsWake = new AutoResetEvent(false);
        volatile bool running;

        readonly List<Item> queue = new List<Item>();
        public int SentToday; DateTime sentDay = DateTime.Today;
        public readonly Dictionary<string, DateTime> LastFrom = new Dictionary<string, DateTime>();
        public readonly Dictionary<string, string> SourceError = new Dictionary<string, string>();

        readonly List<string> log = new List<string>();
        public List<string> LogLines { get { lock (log) return new List<string>(log); } }

        public void Start()
        {
            St = State.Load();
            running = true;
            Spawn(LinkLoop, "link");
            Spawn(NewsLoop, "news");
            Spawn(NotifLoop, "notif");
            Log("התוכנה הופעלה");
        }

        public void Stop()
        {
            running = false;
            linkWake.Set(); newsWake.Set();
            Drop(null);
            lock (stLock) St.Save();
        }

        static void Spawn(ThreadStart f, string name)
        {
            var t = new Thread(f) { IsBackground = true, Name = name };
            t.Start();
        }

        public void Log(string s)
        {
            lock (log)
            {
                log.Insert(0, DateTime.Now.ToString("HH:mm:ss") + "   " + s);
                if (log.Count > 200) log.RemoveAt(log.Count - 1);
            }
            Fire();
        }

        void Fire()
        {
            var h = Changed;
            if (h != null) try { h(); } catch { }
        }

        public int QueueCount { get { lock (queue) return queue.Count; } }

        public void SaveState() { lock (stLock) St.Save(); }

        public void SetDevice(string addr)
        {
            lock (stLock) { St.DeviceAddr = addr ?? ""; St.Save(); }
            if (Connected) Drop("החלפת נגן");
            linkWake.Set();
        }

        public void Reconnect() { linkWake.Set(); }

        // ---------------- תור ושליחה

        public List<Item> Captured;   // מצב בדיקה עצמית: אוספים במקום לשלוח

        // בודק את שלושת המקורות פעם אחת (5 הפריטים האחרונים מכל אחד) ומחזיר טקסט קריא.
        public string SelfTest()
        {
            St = State.Load();
            Captured = new List<Item>();
            var sb = new StringBuilder();
            try { long l = -1; PollHagizra(ref l); l -= 5; PollHagizra(ref l); } catch (Exception e) { sb.AppendLine("hagizra error: " + e.Message); }
            try { long l = -1; PollAuthenti(ref l); l -= 5; PollAuthenti(ref l); } catch (Exception e) { sb.AppendLine("authenti error: " + e.Message); }
            try { FetchChannels(); sb.AppendLine("channels: " + St.Channels.Count); } catch (Exception e) { sb.AppendLine("channels error: " + e.Message); }
            try
            {
                AppNames.Refresh();
                long o = -1; var seen = new HashSet<long>();
                string tmp = Path.Combine(Path.GetTempPath(), "Pushim-wpn-test");
                ReadNotifications(tmp, ref o, seen);
                o -= 5; seen.Clear();
                ReadNotifications(tmp, ref o, seen);
                sb.AppendLine("apps: " + string.Join(" | ", new List<string>(St.Apps.Values).ToArray()));
            }
            catch (Exception e) { sb.AppendLine("notif error: " + e.Message); }
            foreach (var it in Captured) sb.AppendLine(it.ToJson(false));
            return sb.ToString();
        }

        public void Enqueue(Item it)
        {
            if (Captured != null) { Captured.Add(it); return; }
            lock (queue)
            {
                queue.Add(it);
                // מה שלא נשלח תוך 30 דקות כבר לא רלוונטי
                var limit = DateTime.UtcNow.AddMinutes(-30);
                queue.RemoveAll(x => x.Queued < limit);
                while (queue.Count > 150) queue.RemoveAt(0);
            }
            lock (LastFrom) LastFrom[it.Src] = DateTime.Now;
            Flush(false);
            Fire();
        }

        void Flush(bool backlog)
        {
            if (!Connected) return;
            List<Item> items;
            var limit = DateTime.UtcNow.AddMinutes(-30);
            lock (queue) { queue.RemoveAll(x => x.Queued < limit); items = new List<Item>(queue); queue.Clear(); }
            for (int i = 0; i < items.Count; i++)
            {
                if (!SendLine(items[i].ToJson(backlog && items.Count > 3)))
                {
                    lock (queue) queue.InsertRange(0, items.GetRange(i, items.Count - i));
                    return;
                }
                if (DateTime.Today != sentDay) { sentDay = DateTime.Today; SentToday = 0; }
                SentToday++;
            }
            if (items.Count > 0) Fire();
        }

        bool SendLine(string json)
        {
            lock (writeLock)
            {
                var s = stream;
                if (s == null) return false;
                try
                {
                    var b = Encoding.UTF8.GetBytes(json + "\n");
                    s.Write(b, 0, b.Length);
                    s.Flush();
                    return true;
                }
                catch
                {
                    Drop("הקשר עם הנגן נותק");
                    return false;
                }
            }
        }

        public void SendTest()
        {
            Enqueue(new Item
            {
                Id = "test" + DateTime.UtcNow.Ticks,
                Src = "pc",
                Sub = "ביטיפוש",
                Title = "התראת בדיקה",
                Text = "אם רואים את זה בנגן, הכול עובד.",
                Time = NowMs()
            });
        }

        public static long NowMs() { return (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalMilliseconds; }

        // ---------------- חיבור לנגן

        void Drop(string why)
        {
            Stream s;
            lock (writeLock) { s = stream; stream = null; }
            bool was = Connected;
            Connected = false;
            if (s != null) try { s.Close(); } catch { }
            if (was && why != null) Log(why);
            Fire();
            linkWake.Set();
        }

        void LinkLoop()
        {
            int cycle = 0;
            while (running)
            {
                if (!Connected)
                {
                    try { TryAllDevices(cycle++); } catch (Exception e) { Log("שגיאת בלוטות': " + e.Message); }
                    if (!Connected) { Searching = ""; Fire(); linkWake.WaitOne(10000); }
                }
                else
                {
                    linkWake.WaitOne(5000);
                    if (!Connected) continue;
                    if ((DateTime.UtcNow - lastRecv).TotalSeconds > 65) { Drop("הנגן לא עונה — מתחבר מחדש"); continue; }
                    if ((DateTime.UtcNow - lastPing).TotalSeconds >= 20)
                    {
                        lastPing = DateTime.UtcNow;
                        SendLine("{\"t\":\"ping\"}");
                    }
                    Flush(false);
                }
            }
        }

        public List<BtDevice> PairedDevices() { return Bt.Paired(); }

        void TryAllDevices(int cycle)
        {
            var all = Bt.Paired();
            var cands = new List<BtDevice>();
            string fixedAddr, lastAddr;
            lock (stLock) { fixedAddr = St.DeviceAddr; lastAddr = St.LastAddr; }
            if (fixedAddr.Length > 0)
            {
                foreach (var d in all) if (d.AddrText == fixedAddr) cands.Add(d);
                if (cands.Count == 0) { Searching = "הנגן שנבחר לא משויך למחשב"; return; }
            }
            else
            {
                foreach (var d in all) if (d.AddrText == lastAddr) cands.Add(d);
                foreach (var d in all) if (d.AddrText != lastAddr && !d.IsAccessory) cands.Add(d);
                // פעם בכמה סבבים מנסים גם מכשירים שנראים כמו אביזרים, למקרה שהנגן מזדהה ככזה.
                if (cycle % 4 == 3) foreach (var d in all) if (d.AddrText != lastAddr && d.IsAccessory) cands.Add(d);
            }
            if (all.Count == 0) { Searching = "אין מכשירים משויכים בבלוטות'"; return; }
            foreach (var d in cands)
            {
                if (!running || Connected) return;
                Searching = "מחפש את הנגן… (" + d.Name + ")";
                Fire();
                if (TryConnect(d)) return;
            }
        }

        bool TryConnect(BtDevice d)
        {
            Socket s = null;
            try
            {
                s = new Socket((AddressFamily)32, SocketType.Stream, (ProtocolType)3);
                s.Connect(new BtEndPoint(d.Addr, Program.ServiceUuid, 0));
                var ns = new NetworkStream(s, true);
                int gen;
                lock (writeLock) { stream = ns; gen = ++linkGen; }
                lock (iconsSent) iconsSent.Clear();
                lastRecv = DateTime.UtcNow; lastPing = DateTime.UtcNow;
                PeerName = d.Name;
                Connected = true;
                Searching = "";
                lock (stLock) { St.LastAddr = d.AddrText; St.Save(); }
                Log("התחבר לנגן: " + d.Name);
                var reader = new Thread(() => ReadLoop(ns, gen)) { IsBackground = true, Name = "reader" };
                reader.Start();
                var hello = new Dictionary<string, object>();
                hello["t"] = "hello"; hello["name"] = Environment.MachineName; hello["ver"] = Program.Version;
                SendLine(Json.Ser(hello));
                SendApps();
                SendChannels();
                Flush(true);
                Fire();
                return true;
            }
            catch
            {
                if (s != null) try { s.Close(); } catch { }
                return false;
            }
        }

        void ReadLoop(Stream ns, int gen)
        {
            try
            {
                var r = new StreamReader(ns, new UTF8Encoding(false));
                string line;
                while ((line = r.ReadLine()) != null)
                {
                    lastRecv = DateTime.UtcNow;
                    line = line.Trim();
                    if (line.Length == 0) continue;
                    try { HandleLine(line); } catch { }
                }
            }
            catch { }
            if (gen == linkGen && Connected) Drop("הנגן התנתק");
        }

        void HandleLine(string line)
        {
            var d = Json.Obj(Json.Parse(line));
            if (d == null) return;
            string t = Json.Str(d, "t");
            if (t == "ping") SendLine("{\"t\":\"pong\"}");
            else if (t == "config") ApplyConfig(d);
            else if (t == "test") SendTest();
        }

        void ApplyConfig(Dictionary<string, object> d)
        {
            bool intervalChanged;
            lock (stLock)
            {
                bool h = Json.Bool(d, "hagizra", true), a = Json.Bool(d, "authenti", true), p = Json.Bool(d, "pc", true);
                St.SrcHagizra = h; St.SrcAuthenti = a; St.SrcPc = p;
                int iv = (int)Json.Long(d, "interval");
                if (iv < 5) iv = 15;
                intervalChanged = iv != St.Interval;
                St.Interval = iv;
                St.AppsOff.Clear(); St.ChannelsOff.Clear();
                var ao = Json.Arr(d.ContainsKey("appsOff") ? d["appsOff"] : null);
                if (ao != null) foreach (var x in ao) St.AppsOff.Add(Convert.ToString(x));
                var co = Json.Arr(d.ContainsKey("chOff") ? d["chOff"] : null);
                if (co != null) foreach (var x in co) St.ChannelsOff.Add(Convert.ToString(x));
                St.Save();
            }
            Log("התקבלו הגדרות מהנגן");
            if (intervalChanged) newsWake.Set();
        }

        void SendApps()
        {
            var list = new List<object>();
            lock (stLock)
            {
                var keys = new List<string>(St.Apps.Keys);
                keys.Sort((x, y) => string.Compare(St.Apps[x], St.Apps[y], StringComparison.CurrentCultureIgnoreCase));
                foreach (var k in keys)
                {
                    var o = new Dictionary<string, object>(); o["id"] = k; o["name"] = St.Apps[k]; list.Add(o);
                }
            }
            var d = new Dictionary<string, object>(); d["t"] = "apps"; d["list"] = list;
            SendLine(Json.Ser(d));
            // האייקונים של התוכנות, כדי שהנגן יציג אותם ליד ההתראות
            foreach (Dictionary<string, object> o in list)
            {
                string id = (string)o["id"];
                lock (iconsSent) if (iconsSent.Contains(id)) continue;
                string png = AppIcons.Png64(id);
                if (png == null) continue;
                var m = new Dictionary<string, object>(); m["t"] = "icon"; m["key"] = id; m["png"] = png;
                if (!SendLine(Json.Ser(m))) return;
                lock (iconsSent) iconsSent.Add(id);
            }
        }

        readonly HashSet<string> iconsSent = new HashSet<string>();

        void SendChannels()
        {
            List<string> ch;
            lock (stLock) ch = new List<string>(St.Channels);
            if (ch.Count == 0) return;
            var d = new Dictionary<string, object>(); d["t"] = "channels"; d["list"] = ch;
            SendLine(Json.Ser(d));
        }

        // ---------------- אתרי החדשות

        void NewsLoop()
        {
            long hgLast = -1, auLast = -1;
            DateTime chFetched = DateTime.MinValue;
            while (running)
            {
                bool h, a; int iv;
                lock (stLock) { h = St.SrcHagizra; a = St.SrcAuthenti; iv = St.Interval; }
                if (h) Guard("hagizra", () => PollHagizra(ref hgLast)); else hgLast = -1;
                if (a) Guard("authenti", () => PollAuthenti(ref auLast)); else auLast = -1;
                if ((DateTime.UtcNow - chFetched).TotalMinutes > 60)
                {
                    try { FetchChannels(); chFetched = DateTime.UtcNow; } catch { chFetched = DateTime.UtcNow.AddMinutes(-55); }
                }
                newsWake.WaitOne(Math.Max(5, iv) * 1000);
            }
        }

        void Guard(string src, Action f)
        {
            try
            {
                f();
                lock (SourceError) { if (SourceError.Remove(src)) Fire(); }
            }
            catch (Exception e)
            {
                bool first;
                lock (SourceError) { first = !SourceError.ContainsKey(src); SourceError[src] = e.Message; }
                if (first) Log((src == "hagizra" ? "הגיזרה" : "אותנטי") + ": אין גישה לאתר (" + e.Message + ")");
            }
        }

        static string Http(string url)
        {
            var r = (HttpWebRequest)WebRequest.Create(url);
            r.UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Pushim/" + Program.Version;
            r.Accept = "application/json, */*";
            r.Timeout = 20000; r.ReadWriteTimeout = 20000;
            r.AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate;
            using (var resp = r.GetResponse())
            using (var sr = new StreamReader(resp.GetResponseStream(), Encoding.UTF8))
                return sr.ReadToEnd();
        }

        void PollHagizra(ref long last)
        {
            var root = Json.Obj(Json.Parse(Http("https://hagizra.news/api/v3/messages")));
            var msgs = Json.Arr(root == null ? null : (root.ContainsKey("messages") ? root["messages"] : null));
            if (msgs == null) throw new Exception("תשובה לא צפויה");
            long max = last;
            var fresh = new List<Dictionary<string, object>>();
            foreach (var o in msgs)
            {
                var m = Json.Obj(o); if (m == null) continue;
                long id = Json.Long(m, "id");
                if (id > max) max = id;
                if (last >= 0 && id > last) fresh.Add(m);
            }
            if (last < 0) { last = max; return; }   // הפעלה ראשונה: רק מסמנים מאיפה להתחיל
            last = max;
            fresh.Sort((x, y) => Json.Long(x, "id").CompareTo(Json.Long(y, "id")));
            foreach (var m in fresh)
            {
                if (Json.Bool(m, "deleted", false) || Json.Bool(m, "is_ads", false)) continue;
                string title, body;
                Clean.Hagizra(Json.Str(m, "text"), out title, out body);
                if (title.Length == 0 && body.Length == 0) continue;   // רק תמונה או סרטון
                long time = NowMs();
                DateTime ts;
                if (DateTime.TryParse(Json.Str(m, "timestamp"), CultureInfo.InvariantCulture, DateTimeStyles.AdjustToUniversal | DateTimeStyles.AssumeUniversal, out ts))
                    time = (long)(ts - new DateTime(1970, 1, 1)).TotalMilliseconds;
                Enqueue(new Item { Id = "h" + Json.Long(m, "id"), Src = "hagizra", Sub = "", Title = title, Text = body, Time = time });
            }
        }

        void PollAuthenti(ref long last)
        {
            string url = "https://authenti.newsupdates.click/api/get_messages_optimized.php?source=ALL_CHANNELS&last_id=" + Math.Max(0, last) + "&limit=50";
            var root = Json.Obj(Json.Parse(Http(url)));
            var msgs = Json.Arr(root == null ? null : (root.ContainsKey("messages") ? root["messages"] : null));
            if (msgs == null) throw new Exception("תשובה לא צפויה");
            long max = last;
            var fresh = new List<Dictionary<string, object>>();
            foreach (var o in msgs)
            {
                var m = Json.Obj(o); if (m == null) continue;
                long id = Json.Long(m, "id");
                if (id > max) max = id;
                if (last >= 0 && id > last) fresh.Add(m);
            }
            if (last < 0) { last = max; return; }
            last = max;
            fresh.Sort((x, y) => Json.Long(x, "id").CompareTo(Json.Long(y, "id")));
            HashSet<string> off;
            lock (stLock) off = new HashSet<string>(St.ChannelsOff);
            foreach (var m in fresh)
            {
                if (Json.Bool(m, "admin_only", false)) continue;
                string ch = Json.Str(m, "source");
                if (off.Contains(ch)) continue;
                string text = Clean.Plain(Json.Str(m, "description"));
                if (text.Length == 0) continue;
                long time = NowMs();
                DateTime ts;
                if (DateTime.TryParseExact(Json.Str(m, "created_at"), "yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture, DateTimeStyles.AssumeLocal, out ts))
                    time = (long)(ts.ToUniversalTime() - new DateTime(1970, 1, 1)).TotalMilliseconds;
                Enqueue(new Item { Id = "a" + Json.Long(m, "id"), Src = "authenti", Sub = ch, Title = "", Text = text, Time = time });
            }
        }

        void FetchChannels()
        {
            var root = Json.Obj(Json.Parse(Http("https://authenti.newsupdates.click/api/get_channels_optimized.php")));
            var arr = Json.Arr(root == null ? null : (root.ContainsKey("channels") ? root["channels"] : null));
            if (arr == null) return;
            var names = new List<string>();
            foreach (var o in arr)
            {
                var c = Json.Obj(o);
                string s = Json.Str(c, "source");
                if (s.Length == 0 || s == "ALL_CHANNELS" || s == "APPROVED_VIDEOS") continue;
                names.Add(s);
            }
            names.Sort(StringComparer.CurrentCulture);
            bool changed;
            lock (stLock)
            {
                changed = string.Join("\n", names) != string.Join("\n", St.Channels);
                if (changed) { St.Channels = names; St.Save(); }
            }
            if (changed && Connected) SendChannels();
        }

        // ---------------- התראות Windows

        static string WpnDir
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Microsoft\\Windows\\Notifications"); }
        }

        void NotifLoop()
        {
            long lastOrder = -1;
            var seen = new HashSet<long>();
            bool errLogged = false;
            string tmp = Path.Combine(Path.GetTempPath(), "Pushim-wpn");
            Thread.Sleep(1500);
            AppNames.Refresh();
            while (running)
            {
                bool on;
                lock (stLock) on = St.SrcPc;
                if (on)
                {
                    try
                    {
                        ReadNotifications(tmp, ref lastOrder, seen);
                        errLogged = false;
                    }
                    catch (Exception e)
                    {
                        if (!errLogged) Log("קריאת התראות Windows נכשלה: " + e.Message);
                        errLogged = true;
                    }
                }
                else lastOrder = -1;
                Thread.Sleep(2000);
            }
        }

        void ReadNotifications(string tmp, ref long lastOrder, HashSet<long> seen)
        {
            // מעתיקים את מסד ההתראות לתיקייה זמנית וקוראים את העותק — לא נוגעים בקובץ של המערכת.
            Directory.CreateDirectory(tmp);
            string src = Path.Combine(WpnDir, "wpndatabase.db");
            string dst = Path.Combine(tmp, "wpn.db");
            foreach (var ext in new[] { "", "-wal", "-shm", "-journal" })
                if (File.Exists(dst + ext)) File.Delete(dst + ext);
            File.Copy(src, dst, true);
            if (File.Exists(src + "-wal")) File.Copy(src + "-wal", dst + "-wal", true);

            IntPtr db;
            if (Sqlite.sqlite3_open16(dst, out db) != 0) { Sqlite.sqlite3_close(db); throw new Exception("לא ניתן לפתוח את מסד ההתראות"); }
            var rows = new List<object[]>();
            try
            {
                string sql = lastOrder < 0
                    ? "SELECT n.[Order], n.Id, h.PrimaryId, NULL, n.ArrivalTime FROM Notification n JOIN NotificationHandler h ON h.RecordId = n.HandlerId WHERE n.Type = 'toast'"
                    : "SELECT n.[Order], n.Id, h.PrimaryId, n.Payload, n.ArrivalTime FROM Notification n JOIN NotificationHandler h ON h.RecordId = n.HandlerId WHERE n.Type = 'toast' AND n.[Order] > " + lastOrder + " ORDER BY n.[Order]";
                IntPtr st;
                if (Sqlite.sqlite3_prepare16_v2(db, sql, -1, out st, IntPtr.Zero) != 0) throw new Exception("שאילתה נכשלה");
                try
                {
                    while (Sqlite.sqlite3_step(st) == Sqlite.ROW)
                    {
                        rows.Add(new object[] {
                            Sqlite.sqlite3_column_int64(st, 0), Sqlite.sqlite3_column_int64(st, 1),
                            Sqlite.Text(st, 2), lastOrder < 0 ? null : Sqlite.Blob(st, 3), Sqlite.sqlite3_column_int64(st, 4) });
                    }
                }
                finally { Sqlite.sqlite3_finalize(st); }
            }
            finally { Sqlite.sqlite3_close(db); }

            bool first = lastOrder < 0;
            long max = Math.Max(lastOrder, 0);
            bool appsChanged = false;
            foreach (var r in rows)
            {
                long order = (long)r[0], id = (long)r[1];
                string app = (string)r[2];
                if (order > max) max = order;
                appsChanged |= RememberApp(app);
                if (first) { seen.Add(id); continue; }
                if (seen.Contains(id)) continue;   // עדכון של התראה שכבר נשלחה
                seen.Add(id);
                bool off;
                lock (stLock) off = St.AppsOff.Contains(app);
                if (off) continue;
                string title, body;
                Clean.Toast((byte[])r[3], out title, out body);
                if (title.Length == 0 && body.Length == 0) continue;
                long ft = (long)r[4];
                long time = ft > 116444736000000000L ? (ft - 116444736000000000L) / 10000 : NowMs();
                string name;
                lock (stLock) name = St.Apps.ContainsKey(app) ? St.Apps[app] : app;
                Enqueue(new Item { Id = "w" + id + "_" + (time / 1000), Src = "pc", Sub = name, Title = title, Text = body, Time = time, Icon = app });
            }
            lastOrder = max;
            if (seen.Count > 5000) seen.Clear();
            if (appsChanged) { SaveState(); if (Connected) SendApps(); }
        }

        bool RememberApp(string id)
        {
            if (string.IsNullOrEmpty(id)) return false;
            string name = AppNames.Get(id);
            lock (stLock)
            {
                string old;
                if (St.Apps.TryGetValue(id, out old) && old == name) return false;
                St.Apps[id] = name;
                return true;
            }
        }
    }

    // ---------------------------------------------------------------- שמות תוכנות

    static class AppNames
    {
        static Dictionary<string, string> map = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        static DateTime refreshed = DateTime.MinValue;

        public static void Refresh()
        {
            // רשימת התוכנות של תפריט התחל (shell:AppsFolder) נותנת שם יפה לכל מזהה תוכנה.
            var t = new Thread(() =>
            {
                try
                {
                    var m = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
                    Type type = Type.GetTypeFromProgID("Shell.Application");
                    dynamic sh = Activator.CreateInstance(type);
                    dynamic folder = sh.NameSpace("shell:AppsFolder");
                    foreach (dynamic it in folder.Items())
                    {
                        string path = it.Path; string name = it.Name;
                        if (!string.IsNullOrEmpty(path) && !string.IsNullOrEmpty(name)) m[path] = name;
                    }
                    lock (typeof(AppNames)) map = m;
                }
                catch { }
            });
            t.SetApartmentState(ApartmentState.STA);
            t.IsBackground = true;
            t.Start();
            t.Join(15000);
            refreshed = DateTime.UtcNow;
        }

        public static string Get(string id)
        {
            string n;
            lock (typeof(AppNames)) if (map.TryGetValue(id, out n)) return n;
            if ((DateTime.UtcNow - refreshed).TotalMinutes > 10)
            {
                Refresh();
                lock (typeof(AppNames)) if (map.TryGetValue(id, out n)) return n;
            }
            return Guess(id);
        }

        static string Guess(string id)
        {
            if (id.StartsWith("Windows.SystemToast.", StringComparison.OrdinalIgnoreCase)) return "Windows · " + id.Substring(20);
            int bang = id.IndexOf('!');
            if (bang > 0)
            {
                string pkg = id.Substring(0, bang);
                int us = pkg.LastIndexOf('_'); if (us > 0) pkg = pkg.Substring(0, us);
                int dot = pkg.LastIndexOf('.'); if (dot >= 0 && dot < pkg.Length - 1) pkg = pkg.Substring(dot + 1);
                return pkg;
            }
            if (id.IndexOf('\\') >= 0) return Path.GetFileNameWithoutExtension(id);
            return id;
        }
    }

    // ---------------------------------------------------------------- אייקונים של תוכנות

    static class AppIcons
    {
        [ComImport, Guid("bcc18b79-ba16-442f-80c4-8a59c30c463b"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IShellItemImageFactory { [PreserveSig] int GetImage(Size2 size, int flags, out IntPtr phbm); }
        [StructLayout(LayoutKind.Sequential)] struct Size2 { public int cx, cy; }
        [StructLayout(LayoutKind.Sequential)]
        struct BITMAP { public int bmType, bmWidth, bmHeight, bmWidthBytes; public ushort bmPlanes, bmBitsPixel; public IntPtr bmBits; }
        [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
        static extern int SHCreateItemFromParsingName(string path, IntPtr pbc, ref Guid riid, [MarshalAs(UnmanagedType.Interface)] out IShellItemImageFactory ppv);
        [DllImport("gdi32.dll")] static extern bool DeleteObject(IntPtr h);
        [DllImport("gdi32.dll")] static extern int GetObject(IntPtr h, int size, ref BITMAP bm);

        static readonly Dictionary<string, string> cache = new Dictionary<string, string>();

        /// <summary>האייקון של התוכנה (לפי המזהה שלה ב-Windows), כ-PNG בקידוד base64. null אם אין.</summary>
        public static string Png64(string id)
        {
            lock (cache) { string c; if (cache.TryGetValue(id, out c)) return c; }
            string r = null;
            var t = new Thread(() => { try { r = Make(id); } catch { } });
            t.SetApartmentState(ApartmentState.STA);
            t.IsBackground = true;
            t.Start();
            t.Join(10000);
            lock (cache) cache[id] = r;
            return r;
        }

        static string Make(string id)
        {
            if (id.StartsWith("Windows.SystemToast.", StringComparison.OrdinalIgnoreCase)) return null;
            Guid g = typeof(IShellItemImageFactory).GUID;
            IShellItemImageFactory f;
            if (SHCreateItemFromParsingName("shell:AppsFolder\\" + id, IntPtr.Zero, ref g, out f) != 0 || f == null) return null;
            IntPtr hbm;
            if (f.GetImage(new Size2 { cx = 96, cy = 96 }, 0x5 /* ICONONLY | BIGGERSIZEOK */, out hbm) != 0 || hbm == IntPtr.Zero) return null;
            try
            {
                var bm = new BITMAP();
                GetObject(hbm, Marshal.SizeOf(typeof(BITMAP)), ref bm);
                Bitmap img;
                if (bm.bmBits != IntPtr.Zero && bm.bmBitsPixel == 32)
                {
                    using (var raw = new Bitmap(bm.bmWidth, bm.bmHeight, bm.bmWidthBytes, System.Drawing.Imaging.PixelFormat.Format32bppPArgb, bm.bmBits))
                        img = new Bitmap(raw);
                }
                else img = Image.FromHbitmap(hbm);
                using (img)
                using (var outBmp = new Bitmap(96, 96))
                {
                    using (var gr = Graphics.FromImage(outBmp))
                    {
                        gr.InterpolationMode = InterpolationMode.HighQualityBicubic;
                        gr.Clear(Color.Transparent);
                        gr.DrawImage(img, 0, 0, 96, 96);
                    }
                    using (var ms = new MemoryStream())
                    {
                        outBmp.Save(ms, System.Drawing.Imaging.ImageFormat.Png);
                        return Convert.ToBase64String(ms.ToArray());
                    }
                }
            }
            finally { DeleteObject(hbm); }
        }

        // לבדיקה: שומר את האייקון כקובץ
        public static void Dump(string id, string path)
        {
            string b = Png64(id);
            if (b != null) File.WriteAllBytes(path, Convert.FromBase64String(b));
        }
    }

    // ---------------------------------------------------------------- ניקוי טקסט

    static class Clean
    {
        static readonly Regex Embedded = new Regex(@"\[[a-z\-]+-embedded#[^\]]*\]\(.*?\)(?=[ \t]*(\r?\n|$))", RegexOptions.Singleline | RegexOptions.IgnoreCase);
        static readonly Regex Link = new Regex(@"\[([^\]\n]*)\]\((https?://|tel:|mailto:)[^)\s]*\)");
        static readonly Regex Headline = new Regex(@"^\s*\*{2,}(.+?)\*{2,}[ \t]*", RegexOptions.Singleline);
        static readonly Regex Marks = new Regex(@"\*{2,}|__|~~|\|\|");
        static readonly Regex Tags = new Regex(@"<[^>]+>");
        static readonly Regex Spaces = new Regex(@"[ \t]+");
        static readonly Regex Blank = new Regex(@"\n{3,}");

        public static void Hagizra(string md, out string title, out string body)
        {
            string s = (md ?? "").Replace("\r\n", "\n");
            s = Embedded.Replace(s, "");
            s = Link.Replace(s, "$1");
            title = "";
            var h = Headline.Match(s);
            if (h.Success && h.Groups[1].Value.IndexOf('\n') < 0)
            {
                title = Marks.Replace(h.Groups[1].Value, "").Trim().TrimEnd('|', ':', ' ', '-', '–').Trim();
                s = s.Substring(h.Length);
            }
            body = Tidy(Marks.Replace(s, ""));
        }

        public static string Plain(string s)
        {
            s = WebUtility.HtmlDecode(s ?? "");
            s = s.Replace("<br>", "\n").Replace("<br/>", "\n").Replace("<br />", "\n");
            s = Tags.Replace(s, "");
            s = Link.Replace(s, "$1");
            return Tidy(Marks.Replace(s, ""));
        }

        static string Tidy(string s)
        {
            s = s.Replace("\r\n", "\n");
            var lines = s.Split('\n');
            for (int i = 0; i < lines.Length; i++) lines[i] = Spaces.Replace(lines[i], " ").Trim();
            s = string.Join("\n", lines);
            s = Blank.Replace(s, "\n\n");
            return s.Trim();
        }

        public static void Toast(byte[] payload, out string title, out string body)
        {
            title = ""; body = "";
            if (payload == null || payload.Length == 0) return;
            string xml = Encoding.UTF8.GetString(payload).Trim('﻿', '\0', ' ', '\r', '\n');
            var doc = new XmlDocument();
            try { doc.LoadXml(xml); } catch { return; }
            var binding = doc.SelectSingleNode("/toast/visual/binding");
            if (binding == null) return;
            var texts = new List<string>();
            foreach (XmlNode n in binding.SelectNodes(".//text"))
            {
                var pl = n.Attributes == null ? null : n.Attributes["placement"];
                if (pl != null && pl.Value == "attribution") continue;
                string t = Tidy(n.InnerText);
                if (t.Length > 0) texts.Add(t);
            }
            if (texts.Count == 0) return;
            title = texts[0];
            if (texts.Count > 1) body = string.Join("\n", texts.GetRange(1, texts.Count - 1));
        }
    }

    // ---------------------------------------------------------------- ממשק

    static class Theme
    {
        public static readonly Color Bg = Color.FromArgb(244, 246, 251);
        public static readonly Color Card = Color.White;
        public static readonly Color Text = Color.FromArgb(28, 33, 48);
        public static readonly Color Sub = Color.FromArgb(107, 115, 133);
        public static readonly Color Line = Color.FromArgb(233, 236, 243);
        public static readonly Color Accent = Color.FromArgb(240, 86, 106);
        public static readonly Color Accent2 = Color.FromArgb(255, 176, 138);
        public static readonly Color Green = Color.FromArgb(34, 197, 94);
        public static readonly Color Amber = Color.FromArgb(245, 158, 11);
        public static readonly Color Gray = Color.FromArgb(160, 167, 182);
        public static readonly Color Hagizra = Color.FromArgb(198, 47, 59);
        public static readonly Color Authenti = Color.FromArgb(47, 124, 246);
        public static readonly Color Pc = Color.FromArgb(139, 92, 246);

        public static Font F(float size, bool bold)
        {
            return new Font("Segoe UI", size, bold ? FontStyle.Bold : FontStyle.Regular);
        }

        public static GraphicsPath Round(RectangleF r, float rad)
        {
            var p = new GraphicsPath();
            float d = rad * 2;
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        // הלוגו של "ביטיפוש": פעמון לבן ישר על ריבוע מעוגל בגרדיאנט אפרסק-קורל. (זהה ל-store\logo.ps1)
        public static Bitmap Logo(int size, Color? dot)
        {
            var bmp = new Bitmap(size, size);
            using (var g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.PixelOffsetMode = PixelOffsetMode.HighQuality;
                g.Clear(Color.Transparent);
                float w = size;
                using (var br = new LinearGradientBrush(new RectangleF(0, 0, w, w), Color.FromArgb(255, 176, 138), Color.FromArgb(255, 94, 108), 50f))
                using (var p = Round(new RectangleF(0, 0, w - 1, w - 1), w * 0.24f)) g.FillPath(br, p);
                float bx = w * 0.10f, by = w * 0.09f, bs = w * 0.80f;
                Func<float, float, PointF> P = (u, v) => new PointF(bx + bs * u, by + bs * v);
                using (var b = new GraphicsPath())
                {
                    b.AddBezier(P(0.50f, 0.22f), P(0.64f, 0.22f), P(0.71f, 0.32f), P(0.71f, 0.46f));
                    b.AddBezier(P(0.71f, 0.46f), P(0.71f, 0.56f), P(0.72f, 0.62f), P(0.79f, 0.68f));
                    b.AddBezier(P(0.79f, 0.68f), P(0.82f, 0.71f), P(0.81f, 0.74f), P(0.76f, 0.74f));
                    b.AddLine(P(0.76f, 0.74f), P(0.24f, 0.74f));
                    b.AddBezier(P(0.24f, 0.74f), P(0.19f, 0.74f), P(0.18f, 0.71f), P(0.21f, 0.68f));
                    b.AddBezier(P(0.21f, 0.68f), P(0.28f, 0.62f), P(0.29f, 0.56f), P(0.29f, 0.46f));
                    b.AddBezier(P(0.29f, 0.46f), P(0.29f, 0.32f), P(0.36f, 0.22f), P(0.50f, 0.22f));
                    b.CloseFigure();
                    g.FillPath(Brushes.White, b);
                }
                g.FillEllipse(Brushes.White, bx + bs * 0.445f, by + bs * 0.15f, bs * 0.11f, bs * 0.10f);
                g.FillEllipse(Brushes.White, bx + bs * 0.425f, by + bs * 0.74f, bs * 0.15f, bs * 0.11f);
                if (dot.HasValue)
                {
                    float ds = size * 0.38f;
                    using (var b = new SolidBrush(dot.Value)) g.FillEllipse(b, size - ds, size - ds, ds - 1, ds - 1);
                    using (var pen = new Pen(Color.White, Math.Max(1f, size / 16f))) g.DrawEllipse(pen, size - ds, size - ds, ds - 1, ds - 1);
                }
            }
            return bmp;
        }

        public static Icon IconOf(Bitmap bmp)
        {
            return Icon.FromHandle(bmp.GetHicon());
        }
    }

    class CardPanel : Panel
    {
        public CardPanel()
        {
            DoubleBuffered = true;
            BackColor = Theme.Bg;
            SetStyle(ControlStyles.ResizeRedraw, true);
        }
        protected override void OnPaintBackground(PaintEventArgs e)
        {
            e.Graphics.Clear(Theme.Bg);
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            var r = new RectangleF(0.5f, 0.5f, Width - 2f, Height - 2f);
            using (var p = Theme.Round(r, 14))
            {
                using (var b = new SolidBrush(Theme.Card)) e.Graphics.FillPath(b, p);
                using (var pen = new Pen(Theme.Line)) e.Graphics.DrawPath(pen, p);
            }
        }
    }

    class Dot : Control
    {
        Color color = Theme.Gray;
        public Color Color { get { return color; } set { color = value; Invalidate(); } }
        public Dot() { DoubleBuffered = true; Size = new Size(12, 12); BackColor = Theme.Card; }
        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
            using (var b = new SolidBrush(color)) e.Graphics.FillEllipse(b, 1, 1, Width - 3, Height - 3);
        }
    }

    // טקסט עברי מיושר לימין, מצויר ישירות (תוויות רגילות מתבלבלות בתוך כרטיסים בכיוון ימין-לשמאל).
    class RText : Control
    {
        public RText(string text, Font font, Color color)
        {
            Text = text; Font = font; ForeColor = color;
            BackColor = Theme.Card;
            RightToLeft = RightToLeft.No;
            DoubleBuffered = true;
            SetStyle(ControlStyles.ResizeRedraw | ControlStyles.SupportsTransparentBackColor, true);
        }
        protected override void OnTextChanged(EventArgs e) { base.OnTextChanged(e); Invalidate(); }
        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.Clear(BackColor);
            TextRenderer.DrawText(e.Graphics, Text, Font, ClientRectangle, ForeColor,
                TextFormatFlags.Right | TextFormatFlags.RightToLeft | TextFormatFlags.VerticalCenter |
                TextFormatFlags.SingleLine | TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
        }
    }

    class FlatBtn : Button
    {
        public FlatBtn(string text, bool primary)
        {
            Text = text;
            FlatStyle = FlatStyle.Flat;
            Font = Theme.F(10, true);
            Cursor = Cursors.Hand;
            Height = 38;
            FlatAppearance.BorderSize = primary ? 0 : 1;
            FlatAppearance.BorderColor = Theme.Line;
            BackColor = primary ? Theme.Accent : Theme.Card;
            ForeColor = primary ? Color.White : Theme.Text;
            FlatAppearance.MouseOverBackColor = primary ? Color.FromArgb(222, 70, 92) : Color.FromArgb(246, 247, 252);
            UseVisualStyleBackColor = false;
        }
    }

    class MainForm : Form
    {
        readonly Core core;
        readonly Dot statusDot = new Dot();
        readonly RText statusLabel = new RText("", Theme.F(12, true), Theme.Text), statusSub = new RText("", Theme.F(10, false), Theme.Sub);
        readonly ComboBox deviceBox = new ComboBox();
        readonly RText[] srcState = new RText[3];
        readonly Dot[] srcDot = new Dot[3];
        readonly ListBox logBox = new ListBox();
        readonly CheckBox autoStart = new CheckBox();
        bool loadingDevices;
        public bool AllowClose;

        public MainForm(Core core)
        {
            this.core = core;
            SuspendLayout();
            AutoScaleDimensions = new SizeF(96F, 96F);
            AutoScaleMode = AutoScaleMode.Dpi;
            Text = "ביטיפוש";
            RightToLeft = RightToLeft.Yes;
            RightToLeftLayout = true;
            BackColor = Theme.Bg;
            Font = Theme.F(10, false);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            ClientSize = new Size(470, 596);
            StartPosition = FormStartPosition.CenterScreen;
            using (var b = Theme.Logo(64, null)) Icon = Theme.IconOf(b);

            var logo = new PictureBox { Image = Theme.Logo(128, null), Size = new Size(46, 46), Location = new Point(20, 16), BackColor = Theme.Bg, SizeMode = PictureBoxSizeMode.Zoom };
            var title = new Label { Text = "ביטיפוש", Font = Theme.F(19, true), ForeColor = Theme.Text, AutoSize = true, Location = new Point(74, 8), BackColor = Theme.Bg };
            var sub = new Label { Text = "חדשות והתראות מהמחשב לנגן, בבלוטות'", ForeColor = Theme.Sub, AutoSize = true, Location = new Point(77, 44), BackColor = Theme.Bg };
            Controls.AddRange(new Control[] { logo, title, sub });

            // בתוך הכרטיסים אין שיקוף אוטומטי, אז Put ממקם כל רכיב לפי המרחק מהקצה הימני.
            // כרטיס מצב
            var c1 = new CardPanel { Location = new Point(16, 76), Size = new Size(438, 122) };
            Put(c1, statusDot, 20, 23, 12, 12);
            Put(c1, statusLabel, 40, 14, 380, 28);
            Put(c1, statusSub, 40, 42, 380, 22);
            Put(c1, new RText("הנגן:", Theme.F(10, false), Theme.Text), 20, 78, 46, 28);
            deviceBox.DropDownStyle = ComboBoxStyle.DropDownList;
            deviceBox.SelectedIndexChanged += (s, e) => DeviceChosen();
            Put(c1, deviceBox, 70, 79, 240, 28);
            var refresh = new FlatBtn("רענון", false);
            refresh.Click += (s, e) => { LoadDevices(); core.Reconnect(); };
            Put(c1, refresh, 322, 76, 96, 32);
            Controls.Add(c1);

            // כרטיס מקורות
            var c2 = new CardPanel { Location = new Point(16, 208), Size = new Size(438, 140) };
            Put(c2, new RText("מקורות  ·  מפעילים ומכבים בהגדרות שבנגן", Theme.F(9.5f, true), Theme.Sub), 20, 12, 400, 22);
            string[] names = { "הגיזרה", "אותנטי", "התראות המחשב" };
            for (int i = 0; i < 3; i++)
            {
                int y = 42 + i * 31;
                srcDot[i] = new Dot();
                Put(c2, srcDot[i], 22, y + 6, 12, 12);
                Put(c2, new RText(names[i], Theme.F(10.5f, true), Theme.Text), 42, y, 130, 24);
                srcState[i] = new RText("", Theme.F(10, false), Theme.Sub);
                Put(c2, srcState[i], 175, y, 245, 24);
            }
            Controls.Add(c2);

            var test = new FlatBtn("שליחת התראת בדיקה לנגן", true) { Location = new Point(16, 358), Size = new Size(438, 40) };
            test.Click += (s, e) => { core.SendTest(); if (!core.Connected) core.Log("הנגן לא מחובר — הבדיקה תישלח כשיתחבר"); };
            Controls.Add(test);

            // כרטיס פעילות
            var c3 = new CardPanel { Location = new Point(16, 408), Size = new Size(438, 148) };
            Put(c3, new RText("פעילות אחרונה", Theme.F(9.5f, true), Theme.Sub), 20, 10, 400, 22);
            logBox.BorderStyle = BorderStyle.None;
            logBox.ForeColor = Theme.Text; logBox.BackColor = Theme.Card;
            logBox.Font = Theme.F(9, false);
            logBox.IntegralHeight = false;
            Put(c3, logBox, 16, 34, 406, 104);
            Controls.Add(c3);

            autoStart.Text = "להפעיל את ביטיפוש אוטומטית עם Windows";
            autoStart.AutoSize = true;
            autoStart.Location = new Point(20, 566);
            autoStart.ForeColor = Theme.Text; autoStart.BackColor = Theme.Bg;
            autoStart.Checked = AutoStart.IsOn();
            autoStart.CheckedChanged += (s, e) => AutoStart.Set(autoStart.Checked);
            Controls.Add(autoStart);
            ResumeLayout(false);
            PerformLayout();

            LoadDevices();
            RefreshView();
        }

        static void Put(Control parent, Control c, int right, int y, int w, int h)
        {
            c.SetBounds(parent.Width - right - w, y, w, h);
            parent.Controls.Add(c);
        }

        void LoadDevices()
        {
            loadingDevices = true;
            deviceBox.Items.Clear();
            deviceBox.Items.Add("זיהוי אוטומטי");
            int sel = 0;
            foreach (var d in core.PairedDevices())
            {
                deviceBox.Items.Add(d);
                if (d.AddrText == core.St.DeviceAddr) sel = deviceBox.Items.Count - 1;
            }
            deviceBox.SelectedIndex = sel;
            loadingDevices = false;
        }

        void DeviceChosen()
        {
            if (loadingDevices) return;
            var d = deviceBox.SelectedItem as BtDevice;
            core.SetDevice(d == null ? "" : d.AddrText);
        }

        public void RefreshView()
        {
            if (IsDisposed) return;
            if (core.Connected)
            {
                statusDot.Color = Theme.Green;
                statusLabel.Text = "מחובר לנגן: " + core.PeerName;
            }
            else
            {
                statusDot.Color = Theme.Amber;
                statusLabel.Text = core.Searching.Length > 0 ? core.Searching : "ממתין לנגן…";
            }
            int q = core.QueueCount;
            statusSub.Text = "נשלחו היום: " + core.SentToday + (q > 0 ? "   ·   ממתינים לשליחה: " + q : "");

            bool[] on = { core.St.SrcHagizra, core.St.SrcAuthenti, core.St.SrcPc };
            string[] keys = { "hagizra", "authenti", "pc" };
            Color[] colors = { Theme.Hagizra, Theme.Authenti, Theme.Pc };
            for (int i = 0; i < 3; i++)
            {
                string err = null;
                lock (core.SourceError) if (core.SourceError.ContainsKey(keys[i])) err = core.SourceError[keys[i]];
                DateTime last;
                bool has;
                lock (core.LastFrom) has = core.LastFrom.TryGetValue(keys[i], out last);
                string s;
                if (!on[i]) s = "כבוי";
                else if (err != null) s = "אין גישה לאתר כרגע";
                else s = "פעיל" + (has ? "  ·  אחרון ב-" + last.ToString("HH:mm") : "");
                if (i == 2 && on[i] && core.St.AppsOff.Count > 0) s += "  ·  " + core.St.AppsOff.Count + " תוכנות מושתקות";
                srcState[i].Text = s;
                srcDot[i].Color = on[i] ? (err != null ? Theme.Amber : colors[i]) : Theme.Gray;
            }

            var lines = core.LogLines;
            logBox.BeginUpdate();
            logBox.Items.Clear();
            foreach (var l in lines) logBox.Items.Add(l);
            logBox.EndUpdate();
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (!AllowClose && e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true;
                Hide();
                return;
            }
            base.OnFormClosing(e);
        }
    }

    static class AutoStart
    {
        const string Key = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
        public static bool IsOn()
        {
            try { using (var k = Registry.CurrentUser.OpenSubKey(Key)) return k != null && k.GetValue("Pushim") != null; }
            catch { return false; }
        }
        public static void Set(bool on)
        {
            try
            {
                using (var k = Registry.CurrentUser.CreateSubKey(Key))
                {
                    if (on) k.SetValue("Pushim", "\"" + Application.ExecutablePath + "\" /tray");
                    else if (k.GetValue("Pushim") != null) k.DeleteValue("Pushim");
                }
            }
            catch { }
        }
    }

    class TrayApp : ApplicationContext
    {
        readonly Core core;
        readonly NotifyIcon tray = new NotifyIcon();
        readonly MainForm form;
        readonly ToolStripMenuItem statusItem;
        readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();
        Icon iconOn, iconOff;
        bool dirty = true, lastConnected;

        public TrayApp(Core core, bool showWindow, EventWaitHandle showEvent)
        {
            this.core = core;
            form = new MainForm(core);
            using (var b = Theme.Logo(32, Theme.Green)) iconOn = Theme.IconOf(b);
            using (var b = Theme.Logo(32, Theme.Gray)) iconOff = Theme.IconOf(b);

            var menu = new ContextMenuStrip { RightToLeft = RightToLeft.Yes, Font = Theme.F(10, false) };
            statusItem = new ToolStripMenuItem("ביטיפוש") { Enabled = false };
            menu.Items.Add(statusItem);
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("פתיחת החלון", null, (s, e) => ShowForm());
            menu.Items.Add("שליחת התראת בדיקה", null, (s, e) => core.SendTest());
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("יציאה", null, (s, e) => Exit());
            tray.ContextMenuStrip = menu;
            tray.Icon = iconOff;
            tray.Text = "ביטיפוש";
            tray.Visible = true;
            tray.DoubleClick += (s, e) => ShowForm();
            tray.MouseClick += (s, e) => { if (e.Button == MouseButtons.Left) ShowForm(); };

            if (!core.St.AutoStartDone && !Program.TestMode)
            {
                AutoStart.Set(true);
                core.St.AutoStartDone = true;
                core.SaveState();
            }
            else if (!Program.TestMode && AutoStart.IsOn())
            {
                AutoStart.Set(true);   // מעדכן את הנתיב, למשל אחרי שהקובץ שונה ל-BTPush.exe
            }

            core.Changed += () => { dirty = true; };
            timer.Interval = 500;
            timer.Tick += (s, e) => Tick();
            timer.Start();

            // עותק נוסף שהופעל מבקש להציג את החלון
            var t = new Thread(() =>
            {
                while (true)
                {
                    showEvent.WaitOne();
                    try { form.BeginInvoke(new Action(ShowForm)); } catch { }
                }
            }) { IsBackground = true };
            t.Start();

            form.CreateControl();
            var h = form.Handle;
            if (showWindow) ShowForm();
            else tray.ShowBalloonTip(3000, "ביטיפוש", "ביטיפוש רץ ברקע ושולח לנגן. לחיצה על האייקון פותחת את החלון.", ToolTipIcon.Info);
        }

        void Tick()
        {
            if (core.Connected != lastConnected)
            {
                lastConnected = core.Connected;
                tray.Icon = lastConnected ? iconOn : iconOff;
                dirty = true;
            }
            if (!dirty) return;
            dirty = false;
            string s = core.Connected ? "מחובר לנגן: " + core.PeerName : "ממתין לנגן";
            statusItem.Text = s;
            string tip = "ביטיפוש — " + s;
            tray.Text = tip.Length > 63 ? tip.Substring(0, 63) : tip;
            if (form.Visible) form.RefreshView();
        }

        void ShowForm()
        {
            form.RefreshView();
            form.Show();
            if (form.WindowState == FormWindowState.Minimized) form.WindowState = FormWindowState.Normal;
            form.Activate();
        }

        void Exit()
        {
            tray.Visible = false;
            form.AllowClose = true;
            form.Close();
            ExitThread();
        }
    }
}
