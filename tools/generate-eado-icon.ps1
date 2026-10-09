Add-Type -AssemblyName System.Drawing

$size = 512
$bitmap = [System.Drawing.Bitmap]::new($size, $size)
$bitmap.SetResolution(96, 96)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$bounds = [System.Drawing.RectangleF]::new(0, 0, $size, $size)
$background = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    $bounds,
    [System.Drawing.Color]::FromArgb(255, 13, 68, 142),
    [System.Drawing.Color]::FromArgb(255, 0, 158, 211),
    45
)
$graphics.FillRectangle($background, $bounds)

# A simple, original Eado badge: stylised road/wing mark above the model name.
$whitePen = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 32)
$whitePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$whitePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$whitePen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$mark = [System.Drawing.PointF[]]@(
    [System.Drawing.PointF]::new(112, 150),
    [System.Drawing.PointF]::new(256, 286),
    [System.Drawing.PointF]::new(400, 150)
)
$graphics.DrawLines($whitePen, $mark)

$innerPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(210, 191, 232, 255), 16)
$innerPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$innerPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$graphics.DrawLine($innerPen, 174, 160, 256, 236)
$graphics.DrawLine($innerPen, 338, 160, 256, 236)

$font = [System.Drawing.Font]::new("Arial", 70, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$format = [System.Drawing.StringFormat]::new()
$format.Alignment = [System.Drawing.StringAlignment]::Center
$format.LineAlignment = [System.Drawing.StringAlignment]::Center
$textBounds = [System.Drawing.RectangleF]::new(0, 310, $size, 118)
$graphics.DrawString("EADO", $font, [System.Drawing.Brushes]::White, $textBounds, $format)

$output = Join-Path $PSScriptRoot "../common/src/main/res/raw/ic_car_home.png"
$bitmap.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)

$format.Dispose()
$font.Dispose()
$innerPen.Dispose()
$whitePen.Dispose()
$background.Dispose()
$graphics.Dispose()
$bitmap.Dispose()

Write-Output $output
