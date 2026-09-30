Add-Type -AssemblyName System.Drawing

$srcDir = "F:\Marca SoftYorch\StroopOverload\images\googlePlay"
$outDir = Join-Path $srcDir "labeled"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$labels = [ordered]@{
    "00_gameScreen.png"             = "GAME SCREEN (solo)"
    "01_multiplayerGameScreen.png"  = "MULTIPLAYER - Hot Potato"
    "02_soloSurvivlGameScreen.png"  = "MULTIPLAYER - Solo Survival"
    "03_gameModeSelectorScreen.png" = "MODE SELECT"
    "04_leaderboardScreen.png"      = "LEADERBOARD"
    "05_gameOverScreen.png"         = "GAME OVER"
    "06_profileScreen.png"          = "PROFILE"
    "07_homeScreen.png"             = "HOME"
}

foreach ($file in $labels.Keys) {
    $srcPath = Join-Path $srcDir $file
    if (-not (Test-Path $srcPath)) {
        Write-Warning "Missing: $file"
        continue
    }

    $bytes = [System.IO.File]::ReadAllBytes($srcPath)
    $ms = New-Object System.IO.MemoryStream(,$bytes)
    $img = [System.Drawing.Image]::FromStream($ms)
    $bmp = New-Object System.Drawing.Bitmap($img.Width, $img.Height)
    $bmp.SetResolution($img.HorizontalResolution, $img.VerticalResolution)

    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.DrawImage($img, 0, 0, $img.Width, $img.Height)

    $barHeight = [int]($img.Height * 0.055)
    $barY = 0
    $barBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(190, 0, 0, 0))
    $g.FillRectangle($barBrush, 0, $barY, $img.Width, $barHeight)

    $fontSize = [int]($img.Width / 26)
    $font = New-Object System.Drawing.Font("Segoe UI", $fontSize, [System.Drawing.FontStyle]::Bold)
    $textBrush = [System.Drawing.Brushes]::White
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center

    $rect = New-Object System.Drawing.RectangleF(0, $barY, $img.Width, $barHeight)
    $g.DrawString($labels[$file], $font, $textBrush, $rect, $format)

    $outPath = Join-Path $outDir $file
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)

    $g.Dispose()
    $bmp.Dispose()
    $img.Dispose()
    $ms.Dispose()

    Write-Output "OK: $file -> $outPath"
}
