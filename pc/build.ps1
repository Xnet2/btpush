# בונה את Pushim.exe עם המהדר של ‎.NET Framework שמגיע עם Windows.
param([string]$Out = (Join-Path $PSScriptRoot 'Pushim.exe'))
$ErrorActionPreference = 'Stop'
$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$fw = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319"
$out = $Out
$ico = Join-Path $PSScriptRoot 'Pushim.ico'

# אייקון: ריבוע מעוגל סגול עם פעמון לבן, נשמר כ-ICO עם תמונות PNG בכמה גדלים.
Add-Type -AssemblyName System.Drawing
function New-Logo([int]$s) {
  $bmp = New-Object Drawing.Bitmap $s, $s
  $g = [Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'
  $g.Clear([Drawing.Color]::Transparent)
  $rect = New-Object Drawing.RectangleF 0, 0, ($s - 1), ($s - 1)
  $br = New-Object Drawing.Drawing2D.LinearGradientBrush $rect, ([Drawing.Color]::FromArgb(91, 91, 246)), ([Drawing.Color]::FromArgb(139, 92, 246)), 45
  $p = New-Object Drawing.Drawing2D.GraphicsPath
  $d = $s * 0.48
  $p.AddArc(0, 0, $d, $d, 180, 90); $p.AddArc($s - 1 - $d, 0, $d, $d, 270, 90)
  $p.AddArc($s - 1 - $d, $s - 1 - $d, $d, $d, 0, 90); $p.AddArc(0, $s - 1 - $d, $d, $d, 90, 90); $p.CloseFigure()
  $g.FillPath($br, $p)
  $w = [Drawing.Brushes]::White
  $b = New-Object Drawing.Drawing2D.GraphicsPath
  $b.AddArc($s * 0.30, $s * 0.22, $s * 0.40, $s * 0.40, 180, 180)
  $b.AddLine($s * 0.70, $s * 0.42, $s * 0.70, $s * 0.58)
  $b.AddLine($s * 0.70, $s * 0.58, $s * 0.79, $s * 0.70)
  $b.AddLine($s * 0.79, $s * 0.70, $s * 0.21, $s * 0.70)
  $b.AddLine($s * 0.21, $s * 0.70, $s * 0.30, $s * 0.58)
  $b.CloseFigure()
  $g.FillPath($w, $b)
  $g.FillEllipse($w, $s * 0.42, $s * 0.72, $s * 0.16, $s * 0.12)
  $g.FillEllipse($w, $s * 0.46, $s * 0.15, $s * 0.08, $s * 0.09)
  $g.Dispose()
  return $bmp
}
$sizes = 16, 32, 48, 256
$pngs = foreach ($s in $sizes) {
  $bmp = New-Logo $s
  $ms = New-Object IO.MemoryStream
  $bmp.Save($ms, [Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
  , $ms.ToArray()
}
$fs = [IO.File]::Create($ico)
$bw = New-Object IO.BinaryWriter $fs
$bw.Write([uint16]0); $bw.Write([uint16]1); $bw.Write([uint16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
  $s = $sizes[$i]; $len = $pngs[$i].Length
  $bw.Write([byte]($s % 256)); $bw.Write([byte]($s % 256)); $bw.Write([byte]0); $bw.Write([byte]0)
  $bw.Write([uint16]1); $bw.Write([uint16]32); $bw.Write([uint32]$len); $bw.Write([uint32]$offset)
  $offset += $len
}
foreach ($b in $pngs) { $bw.Write($b) }
$bw.Close()

& $csc /nologo /target:winexe /platform:anycpu /optimize+ /codepage:65001 "/win32icon:$ico" "/out:$out" `
  "/r:$fw\System.Web.Extensions.dll" /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Xml.dll `
  /r:System.Core.dll /r:Microsoft.CSharp.dll (Join-Path $PSScriptRoot 'Pushim.cs')
if ($LASTEXITCODE) { throw 'csc' }
(Get-Item $out).Length
