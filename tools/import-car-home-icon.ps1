param(
    [Parameter(Mandatory = $true)]
    [string]$SourcePath
)

Add-Type -AssemblyName System.Drawing

$resolvedSource = (Resolve-Path -LiteralPath $SourcePath).Path
$source = [System.Drawing.Image]::FromFile($resolvedSource)
try {
    $size = 512
    $output = [System.Drawing.Bitmap]::new($size, $size)
    $output.SetResolution(96, 96)
    $graphics = [System.Drawing.Graphics]::FromImage($output)
    try {
        $graphics.Clear([System.Drawing.Color]::White)
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $scale = [Math]::Min($size / $source.Width, $size / $source.Height)
        $width = [int][Math]::Round($source.Width * $scale)
        $height = [int][Math]::Round($source.Height * $scale)
        $left = [int][Math]::Floor(($size - $width) / 2)
        $top = [int][Math]::Floor(($size - $height) / 2)
        $graphics.DrawImage($source, [System.Drawing.Rectangle]::new($left, $top, $width, $height))

        $destination = Join-Path $PSScriptRoot "../common/src/main/res/raw/ic_car_home.png"
        $output.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output $destination
    } finally {
        $graphics.Dispose()
        $output.Dispose()
    }
} finally {
    $source.Dispose()
}
