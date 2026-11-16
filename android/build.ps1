# בונה את Pushim.apk עם ה-JDK וה-Android SDK שכבר מותקנים במחשב (%LOCALAPPDATA%\VODOS-build).
$ErrorActionPreference = 'Stop'
# aapt2 לא מסתדר עם אותיות עבריות בנתיב: מעתיקים את המקורות לתיקייה תחת VODOS-build ועובדים עם נתיבים קצרים (8.3).
$fso = New-Object -ComObject Scripting.FileSystemObject
$local = $fso.GetFolder($env:LOCALAPPDATA).ShortPath
$tools = "$local\VODOS-build"
$staging = "$tools\pushim-src"
Remove-Item $staging -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $staging | Out-Null
Copy-Item "$PSScriptRoot\AndroidManifest.xml", "$PSScriptRoot\src", "$PSScriptRoot\res" $staging -Recurse
$root = $fso.GetFolder($staging).ShortPath
$jdk = "$tools\jdk-17.0.21+9\bin"
$bt = "$tools\sdk\bt\android-14"
$jar = "$tools\sdk\pf\android-34\android.jar"
$env:JAVA_HOME = "$tools\jdk-17.0.21+9"
$out = "$tools\pushim-out"
$keystore = "$tools\lockscreen.keystore"
$pass = 'lockscreen123'

Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\classes", "$out\dex", "$root\res\drawable-xxxhdpi" | Out-Null

# ---- אייקונים: פעמון לבן על ריבוע מעוגל סגול, ואייקון לבן לשורת ההתראות
Add-Type -AssemblyName System.Drawing
function Add-Bell($g, [float]$s, $brush) {
  $b = New-Object Drawing.Drawing2D.GraphicsPath
  $b.AddArc($s * 0.30, $s * 0.22, $s * 0.40, $s * 0.40, 180, 180)
  $b.AddLine($s * 0.70, $s * 0.42, $s * 0.70, $s * 0.58)
  $b.AddLine($s * 0.70, $s * 0.58, $s * 0.79, $s * 0.70)
  $b.AddLine($s * 0.79, $s * 0.70, $s * 0.21, $s * 0.70)
  $b.AddLine($s * 0.21, $s * 0.70, $s * 0.30, $s * 0.58)
  $b.CloseFigure()
  $g.FillPath($brush, $b)
  $g.FillEllipse($brush, $s * 0.42, $s * 0.72, $s * 0.16, $s * 0.12)
  $g.FillEllipse($brush, $s * 0.46, $s * 0.15, $s * 0.08, $s * 0.09)
}
$s = 192
$bmp = New-Object Drawing.Bitmap $s, $s
$g = [Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.Clear([Drawing.Color]::Transparent)
$m = 8
$rect = New-Object Drawing.RectangleF $m, $m, ($s - 2 * $m), ($s - 2 * $m)
$br = New-Object Drawing.Drawing2D.LinearGradientBrush $rect, ([Drawing.Color]::FromArgb(91, 91, 246)), ([Drawing.Color]::FromArgb(139, 92, 246)), 45
$p = New-Object Drawing.Drawing2D.GraphicsPath
$d = 84
$p.AddArc($m, $m, $d, $d, 180, 90); $p.AddArc($s - $m - $d, $m, $d, $d, 270, 90)
$p.AddArc($s - $m - $d, $s - $m - $d, $d, $d, 0, 90); $p.AddArc($m, $s - $m - $d, $d, $d, 90, 90); $p.CloseFigure()
$g.FillPath($br, $p)
$g.TranslateTransform(8, 10)
$g.ScaleTransform(0.92, 0.92)
Add-Bell $g 192 ([Drawing.Brushes]::White)
$g.ResetTransform()
# נקודה כתומה-אדומה של "פוש חדש"
$g.FillEllipse((New-Object Drawing.SolidBrush ([Drawing.Color]::FromArgb(255, 94, 87))), 120, 34, 38, 38)
$g.DrawEllipse((New-Object Drawing.Pen ([Drawing.Color]::White), 6), 120, 34, 38, 38)
$bmp.Save("$root\res\drawable-xxxhdpi\icon.png", [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

$s = 96
$bmp = New-Object Drawing.Bitmap $s, $s
$g = [Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.Clear([Drawing.Color]::Transparent)
$g.TranslateTransform(-14, -10)
$g.ScaleTransform(1.3, 1.3)
Add-Bell $g 96 ([Drawing.Brushes]::White)
$bmp.Save("$root\res\drawable-xxxhdpi\ic_stat.png", [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Copy-Item "$root\res\drawable-xxxhdpi\icon.png" "$PSScriptRoot\icon.png" -Force

# ---- בנייה
& "$bt\aapt2.exe" compile --dir "$root\res" -o "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 compile' }
& "$bt\aapt2.exe" link -I $jar --manifest "$root\AndroidManifest.xml" --java "$root\gen" -o "$out\base.apk" "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 link' }

$sources = @(Get-ChildItem "$root\src", "$root\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$ErrorActionPreference = 'Continue'
& "$jdk\javac.exe" -nowarn -encoding UTF-8 --release 8 -cp $jar -d "$out\classes" $sources 2>&1 | Where-Object { "$_" -notmatch 'warning|^Note:|^Some input|uses or overrides|Recompile with' }
$javacExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($javacExit) { throw 'javac' }
$classes = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --lib $jar --min-api 19 --release --output "$out\dex" $classes
if ($LASTEXITCODE) { throw 'd8' }

& "$jdk\jar.exe" uf "$out\base.apk" -C "$out\dex" classes.dex
if ($LASTEXITCODE) { throw 'jar' }
& "$bt\zipalign.exe" -f 4 "$out\base.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'zipalign' }
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass "pass:$pass" --min-sdk-version 19 --v1-signing-enabled true --v2-signing-enabled true --out "$out\Pushim.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'apksigner' }
& "$bt\aapt2.exe" dump badging "$out\Pushim.apk" | Select-Object -First 3
Copy-Item "$out\Pushim.apk" "$PSScriptRoot\Pushim.apk" -Force
(Get-Item "$PSScriptRoot\Pushim.apk").Length
