# בונה את Mivzak.apk עם ה-JDK וה-Android SDK שכבר מותקנים במחשב (%LOCALAPPDATA%\VODOS-build).
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

# ---- אייקונים: הלוגו של מבזק (store\logo.ps1), ואייקון לבן לשורת ההתראות
. (Join-Path $PSScriptRoot '..\store\logo.ps1')
Save-Png (New-MivzakLogo 192 -Padding 0.04) "$root\res\drawable-xxxhdpi\icon.png"
Save-Png (New-MivzakLogo 96 -Mono -Padding 0.02) "$root\res\drawable-xxxhdpi\ic_stat.png"
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
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass "pass:$pass" --min-sdk-version 19 --v1-signing-enabled true --v2-signing-enabled true --out "$out\Mivzak.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'apksigner' }
& "$bt\aapt2.exe" dump badging "$out\Mivzak.apk" | Select-Object -First 3
Copy-Item "$out\Mivzak.apk" "$PSScriptRoot\Mivzak.apk" -Force
(Get-Item "$PSScriptRoot\Mivzak.apk").Length
