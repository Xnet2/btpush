# הלוגו של "מבזק": ברק זהב-כתום על ריבוע מעוגל בכחול-אינדיגו, עם גלי שידור עדינים.
# משמש את סקריפטי הבנייה של הנגן ושל המחשב.  New-MivzakLogo -Size 512 [-Mono] [-Padding 0]
Add-Type -AssemblyName System.Drawing

function New-MivzakLogo([int]$Size, [switch]$Mono, [float]$Padding = 0) {
  $s = [float]$Size
  $bmp = New-Object Drawing.Bitmap $Size, $Size
  $g = [Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'
  $g.PixelOffsetMode = 'HighQuality'
  $g.Clear([Drawing.Color]::Transparent)
  $m = $s * $Padding
  $w = $s - 2 * $m

  if (-not $Mono) {
    $rect = New-Object Drawing.RectangleF $m, $m, $w, $w
    $bg = New-Object Drawing.Drawing2D.LinearGradientBrush $rect, ([Drawing.Color]::FromArgb(49, 46, 129)), ([Drawing.Color]::FromArgb(79, 70, 229)), 45
    $p = New-Object Drawing.Drawing2D.GraphicsPath
    $d = $w * 0.46
    $p.AddArc($m, $m, $d, $d, 180, 90); $p.AddArc($m + $w - $d, $m, $d, $d, 270, 90)
    $p.AddArc($m + $w - $d, $m + $w - $d, $d, $d, 0, 90); $p.AddArc($m, $m + $w - $d, $d, $d, 90, 90); $p.CloseFigure()
    $g.FillPath($bg, $p)
    # הילה רכה מאחורי הברק
    $glow = New-Object Drawing.Drawing2D.GraphicsPath
    $glow.AddEllipse($m + $w * 0.18, $m + $w * 0.16, $w * 0.64, $w * 0.68)
    $pg = New-Object Drawing.Drawing2D.PathGradientBrush $glow
    $pg.CenterColor = [Drawing.Color]::FromArgb(90, 255, 200, 80)
    $pg.SurroundColors = @([Drawing.Color]::FromArgb(0, 255, 200, 80))
    $g.FillPath($pg, $glow)
  }

  # גלי שידור (בלוטות'/פוש) מימין למעלה
  $waveColor = if ($Mono) { [Drawing.Color]::White } else { [Drawing.Color]::FromArgb(150, 255, 255, 255) }
  $pen = New-Object Drawing.Pen $waveColor, ($w * 0.045)
  $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
  foreach ($r in 0.13, 0.22) {
    $cx = $m + $w * 0.66; $cy = $m + $w * 0.30
    $g.DrawArc($pen, $cx - $w * $r, $cy - $w * $r, 2 * $w * $r, 2 * $w * $r, -70, 70)
  }

  # הברק
  $pts = @(
    @(0.52, 0.13), @(0.25, 0.55), @(0.45, 0.55), @(0.38, 0.88),
    @(0.70, 0.44), @(0.50, 0.44), @(0.60, 0.13)
  ) | ForEach-Object { New-Object Drawing.PointF ($m + $w * ($_[0] + 0.03)), ($m + $w * $_[1]) }
  $bolt = New-Object Drawing.Drawing2D.GraphicsPath
  $bolt.AddPolygon([Drawing.PointF[]]$pts)
  if ($Mono) {
    $g.FillPath([Drawing.Brushes]::White, $bolt)
  } else {
    $br = New-Object Drawing.RectangleF ($m + $w * 0.25), ($m + $w * 0.13), ($w * 0.45), ($w * 0.75)
    $boltBrush = New-Object Drawing.Drawing2D.LinearGradientBrush $br, ([Drawing.Color]::FromArgb(255, 224, 102)), ([Drawing.Color]::FromArgb(255, 146, 43)), 90
    $g.FillPath($boltBrush, $bolt)
    $edge = New-Object Drawing.Pen ([Drawing.Color]::FromArgb(70, 255, 255, 255)), ($w * 0.012)
    $edge.LineJoin = 'Round'
    $g.DrawPath($edge, $bolt)
  }
  $g.Dispose()
  return $bmp
}

function Save-Png($bmp, [string]$path) {
  $bmp.Save($path, [Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}
