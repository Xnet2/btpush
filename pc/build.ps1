# בונה את BTPush.exe עם המהדר של ‎.NET Framework שמגיע עם Windows.
param([string]$Out = (Join-Path $PSScriptRoot 'BTPush.exe'))
$ErrorActionPreference = 'Stop'
$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$fw = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319"
$out = $Out
$ico = Join-Path $env:TEMP 'BTPush.ico'

# אייקון: הלוגו של ביטיפוש (store\logo.ps1), נשמר כ-ICO עם תמונות PNG בכמה גדלים.
. (Join-Path $PSScriptRoot '..\store\logo.ps1')
$sizes = 16, 24, 32, 48, 256
$pngs = foreach ($s in $sizes) {
  $bmp = New-BTPushLogo $s
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
  /r:System.Core.dll /r:Microsoft.CSharp.dll "/r:$fw\WPF\PresentationCore.dll" "/r:$fw\WPF\WindowsBase.dll" "/r:$fw\System.Xaml.dll" `
  (Join-Path $PSScriptRoot 'Pushim.cs')
if ($LASTEXITCODE) { throw 'csc' }
(Get-Item $out).Length
