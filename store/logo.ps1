# הלוגו של "ביטיפוש" (BTPush): פעמון לבן ישר על ריבוע מעוגל בגרדיאנט אפרסק-קורל.
# משמש את סקריפטי הבנייה של הנגן ושל המחשב.  New-BTPushLogo -Size 512 [-Mono] [-Padding 0]
Add-Type -AssemblyName System.Drawing

function New-BTPushLogo([int]$Size, [switch]$Mono, [float]$Padding = 0) {
  $bmp = New-Object Drawing.Bitmap $Size, $Size
  $g = [Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'
  $g.PixelOffsetMode = 'HighQuality'
  $g.Clear([Drawing.Color]::Transparent)
  $m = $Size * $Padding
  $w = $Size - 2 * $m

  if (-not $Mono) {
    $rect = New-Object Drawing.RectangleF $m, $m, $w, $w
    $bg = New-Object Drawing.Drawing2D.LinearGradientBrush $rect, ([Drawing.Color]::FromArgb(255, 176, 138)), ([Drawing.Color]::FromArgb(255, 94, 108)), 50
    $p = New-Object Drawing.Drawing2D.GraphicsPath
    $d = $w * 0.48
    $p.AddArc($m, $m, $d, $d, 180, 90); $p.AddArc($m + $w - $d, $m, $d, $d, 270, 90)
    $p.AddArc($m + $w - $d, $m + $w - $d, $d, $d, 0, 90); $p.AddArc($m, $m + $w - $d, $d, $d, 90, 90); $p.CloseFigure()
    $g.FillPath($bg, $p)
    $bx = $m + $w * 0.10; $by = $m + $w * 0.09; $bs = $w * 0.80
  } else {
    $bx = $m - $w * 0.02; $by = $m - $w * 0.04; $bs = $w * 1.04
  }

  # הפעמון (קואורדינטות 0..1 בתוך ריבוע bs)
  function Pt([float]$u, [float]$v) { New-Object Drawing.PointF ($bx + $bs * $u), ($by + $bs * $v) }
  $b = New-Object Drawing.Drawing2D.GraphicsPath
  $b.AddBezier((Pt 0.50 0.22), (Pt 0.64 0.22), (Pt 0.71 0.32), (Pt 0.71 0.46))
  $b.AddBezier((Pt 0.71 0.46), (Pt 0.71 0.56), (Pt 0.72 0.62), (Pt 0.79 0.68))
  $b.AddBezier((Pt 0.79 0.68), (Pt 0.82 0.71), (Pt 0.81 0.74), (Pt 0.76 0.74))
  $b.AddLine((Pt 0.76 0.74), (Pt 0.24 0.74))
  $b.AddBezier((Pt 0.24 0.74), (Pt 0.19 0.74), (Pt 0.18 0.71), (Pt 0.21 0.68))
  $b.AddBezier((Pt 0.21 0.68), (Pt 0.28 0.62), (Pt 0.29 0.56), (Pt 0.29 0.46))
  $b.AddBezier((Pt 0.29 0.46), (Pt 0.29 0.32), (Pt 0.36 0.22), (Pt 0.50 0.22))
  $b.CloseFigure()
  $white = [Drawing.Brushes]::White
  $g.FillPath($white, $b)
  $g.FillEllipse($white, $bx + $bs * 0.445, $by + $bs * 0.15, $bs * 0.11, $bs * 0.10)   # ידית
  $g.FillEllipse($white, $bx + $bs * 0.425, $by + $bs * 0.74, $bs * 0.15, $bs * 0.11)   # ענבל
  $g.Dispose()
  return $bmp
}

function Save-Png($bmp, [string]$path) {
  $bmp.Save($path, [Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}
