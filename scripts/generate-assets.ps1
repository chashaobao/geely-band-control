param([switch]$LynkControlsOnly)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$project = Split-Path -Parent $PSScriptRoot

function Write-Png([string]$relative, [int]$width, [int]$height, [scriptblock]$draw) {
    $bitmap = New-Object System.Drawing.Bitmap $width,$height
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([Drawing.Color]::Transparent)
    try {
        & $draw $graphics
        $bitmap.Save((Join-Path $project $relative), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function Draw-Mark($graphics, [int]$size, [double]$paddingRatio = 0.10) {
    $inset = $size * $paddingRatio
    $inner = $size - 2.0 * $inset
    $graphics.TranslateTransform($inset, $inset)
    $graphics.ScaleTransform($inner / 108.0, $inner / 108.0)
    $background = New-Object Drawing.SolidBrush ([Drawing.ColorTranslator]::FromHtml('#143C3C'))
    $ink = New-Object Drawing.Pen ([Drawing.ColorTranslator]::FromHtml('#9AE8CE')),5
    $ink.StartCap = $ink.EndCap = [Drawing.Drawing2D.LineCap]::Round
    try {
        $graphics.FillEllipse($background, 0, 0, 108, 108)
        $graphics.DrawLine($ink, 45, 24, 63, 24)
        $graphics.DrawLine($ink, 45, 84, 63, 84)
        $graphics.DrawEllipse($ink, 33, 33, 42, 42)
        $graphics.DrawArc($ink, 22, 41, 64, 26, 15, 150)
        $graphics.DrawArc($ink, 22, 41, 64, 26, 195, 150)
        $graphics.DrawLine($ink, 54, 43, 54, 54)
        $graphics.DrawLine($ink, 54, 54, 62, 58)
    } finally {
        $background.Dispose()
        $ink.Dispose()
    }
}

if (!$LynkControlsOnly) {
# Fit Band 8 Pro (336x480) unscaled launcher slots while keeping transparent padding for round/squircle masks.
Write-Png 'band/src/common/icon.png' 96 96 { param($g) Draw-Mark $g 96 0.10 }

Write-Png 'band/src/common/images/starshine-8.png' 488 158 {
    param($g)
    $body = New-Object Drawing.SolidBrush ([Drawing.ColorTranslator]::FromHtml('#B6D2D6'))
    $glass = New-Object Drawing.SolidBrush ([Drawing.ColorTranslator]::FromHtml('#35515D'))
    $wheel = New-Object Drawing.SolidBrush ([Drawing.ColorTranslator]::FromHtml('#172128'))
    $rim = New-Object Drawing.SolidBrush ([Drawing.ColorTranslator]::FromHtml('#79959E'))
    $light = New-Object Drawing.SolidBrush ([Drawing.ColorTranslator]::FromHtml('#ECF8E8'))
    try {
        $g.FillPolygon($body, [Drawing.Point[]]@([Drawing.Point]::new(22,83),[Drawing.Point]::new(92,68),[Drawing.Point]::new(164,23),[Drawing.Point]::new(297,23),[Drawing.Point]::new(366,64),[Drawing.Point]::new(450,78),[Drawing.Point]::new(466,111),[Drawing.Point]::new(438,126),[Drawing.Point]::new(42,126),[Drawing.Point]::new(18,109)))
        $g.FillPolygon($glass, [Drawing.Point[]]@([Drawing.Point]::new(117,67),[Drawing.Point]::new(175,34),[Drawing.Point]::new(224,34),[Drawing.Point]::new(224,67)))
        $g.FillPolygon($glass, [Drawing.Point[]]@([Drawing.Point]::new(237,34),[Drawing.Point]::new(291,34),[Drawing.Point]::new(345,67),[Drawing.Point]::new(237,67)))
        foreach ($x in @(102,360)) {
            $g.FillEllipse($wheel, $x - 31, 94, 62, 62)
            $g.FillEllipse($rim, $x - 18, 107, 36, 36)
            $g.FillEllipse($wheel, $x - 7, 118, 14, 14)
        }
        $g.FillRectangle($light, 28, 85, 46, 9)
        $g.FillRectangle($light, 422, 85, 29, 9)
    } finally {
        $body.Dispose(); $glass.Dispose(); $wheel.Dispose(); $rim.Dispose(); $light.Dispose()
    }
}
}

$lynkControls = @('lynk-engine','lynk-sunroof','lynk-charge-port')
$icons = if ($LynkControlsOnly) {
    $lynkControls
} else {
    @('lock','lock-open','window_open','window_vent','trunk','find','heat','cool','purify','defrost','sentry','refresh','settings','energy-electric','energy-fuel','climate') + $lynkControls
}
foreach ($name in $icons) {
    $destination = "band/src/common/images/$name.png"
    Write-Png $destination 96 96 {
        param($g)
        $color = switch ($name) {
            'energy-electric' { '#6FE7A5' }
            'energy-fuel' { '#74B7FF' }
            'refresh' { '#9AE8CE' }
            'settings' { '#9AE8CE' }
            default { '#EEF7F5' }
        }
        $pen = New-Object Drawing.Pen ([Drawing.ColorTranslator]::FromHtml($color)),5
        $pen.StartCap = $pen.EndCap = [Drawing.Drawing2D.LineCap]::Round
        $brush = New-Object Drawing.SolidBrush $pen.Color
        try {
            switch ($name) {
                { $_ -in 'lock','lock-open' } {
                    $g.DrawRectangle($pen, 24, 43, 48, 37)
                    $g.DrawArc($pen, 34, 16, 28, 47, $(if ($name -eq 'lock') {180} else {210}), $(if ($name -eq 'lock') {180} else {160}))
                    $g.DrawLine($pen,48,57,48,68)
                }
                { $_ -in 'window_open','window_vent' } {
                    $g.DrawPolygon($pen, [Drawing.Point[]]@([Drawing.Point]::new(19,45),[Drawing.Point]::new(28,20),[Drawing.Point]::new(70,20),[Drawing.Point]::new(79,45),[Drawing.Point]::new(79,78),[Drawing.Point]::new(19,78)))
                    $g.DrawLine($pen,22,48,76,48)
                    $g.DrawLine($pen,58,62,67,62)
                    if ($name -eq 'window_vent') { $g.DrawLine($pen,30,29,68,29) }
                }
                'energy-electric' {
                    $g.DrawPolygon($pen, [Drawing.Point[]]@([Drawing.Point]::new(54,10),[Drawing.Point]::new(25,52),[Drawing.Point]::new(47,52),[Drawing.Point]::new(39,86),[Drawing.Point]::new(74,41),[Drawing.Point]::new(51,41)))
                }
                { $_ -in 'energy-fuel','purify' } {
                    $path = New-Object Drawing.Drawing2D.GraphicsPath
                    $path.AddBezier(48,12,40,35,20,47,24,64)
                    $path.AddBezier(24,64,31,89,69,89,73,62)
                    $path.AddBezier(73,62,74,48,54,28,48,12)
                    $g.DrawPath($pen,$path)
                    if ($name -eq 'purify') { $g.DrawLine($pen,34,72,62,36) }
                    $path.Dispose()
                }
                'climate' {
                    $g.DrawEllipse($pen,40,40,16,16)
                    foreach ($angle in @(0,90,180,270)) {
                        $g.TranslateTransform(48,48)
                        $g.RotateTransform($angle)
                        $g.DrawEllipse($pen,-12,-34,24,25)
                        $g.ResetTransform()
                    }
                }
                'refresh' {
                    $g.DrawArc($pen,20,20,56,56,45,290)
                    $g.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(61,19),[Drawing.Point]::new(76,19),[Drawing.Point]::new(76,35)))
                }
                'settings' {
                    $g.DrawEllipse($pen,27,27,42,42)
                    $g.DrawEllipse($pen,41,41,14,14)
                    foreach ($angle in @(0,45,90,135,180,225,270,315)) {
                        $g.TranslateTransform(48,48); $g.RotateTransform($angle)
                        $g.DrawLine($pen,0,-22,0,-32); $g.ResetTransform()
                    }
                }
                'sentry' {
                    $g.DrawPolygon($pen,[Drawing.Point[]]@([Drawing.Point]::new(48,12),[Drawing.Point]::new(75,24),[Drawing.Point]::new(71,61),[Drawing.Point]::new(48,83),[Drawing.Point]::new(25,61),[Drawing.Point]::new(21,24)))
                    $g.DrawEllipse($pen,38,35,20,20)
                }
                'trunk' {
                    $g.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(15,61),[Drawing.Point]::new(15,44),[Drawing.Point]::new(31,35),[Drawing.Point]::new(55,35),[Drawing.Point]::new(72,48),[Drawing.Point]::new(81,48),[Drawing.Point]::new(81,64),[Drawing.Point]::new(16,64)))
                    $g.DrawLine($pen,58,28,80,13)
                    $g.DrawEllipse($pen,23,59,15,15); $g.DrawEllipse($pen,61,59,15,15)
                }
                'find' {
                    $g.DrawEllipse($pen,18,18,60,60); $g.DrawEllipse($pen,38,38,20,20)
                    $g.DrawLine($pen,48,9,48,22); $g.DrawLine($pen,48,74,48,87)
                    $g.DrawLine($pen,9,48,22,48); $g.DrawLine($pen,74,48,87,48)
                }
                'heat' {
                    $g.DrawEllipse($pen,33,33,30,30)
                    foreach ($angle in @(0,45,90,135,180,225,270,315)) {
                        $g.TranslateTransform(48,48); $g.RotateTransform($angle)
                        $g.DrawLine($pen,0,-24,0,-34); $g.ResetTransform()
                    }
                }
                'cool' {
                    foreach ($angle in @(0,60,120)) {
                        $g.TranslateTransform(48,48); $g.RotateTransform($angle)
                        $g.DrawLine($pen,0,-34,0,34)
                        $g.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(-9,-26),[Drawing.Point]::new(0,-17),[Drawing.Point]::new(9,-26)))
                        $g.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(-9,26),[Drawing.Point]::new(0,17),[Drawing.Point]::new(9,26)))
                        $g.ResetTransform()
                    }
                }
                'defrost' {
                    $g.DrawArc($pen,15,24,66,54,185,170)
                    $g.DrawLine($pen,15,51,25,78); $g.DrawLine($pen,25,78,71,78); $g.DrawLine($pen,71,78,81,51)
                    foreach ($x in @(32,48,64)) {
                        $g.DrawLine($pen,$x,64,$x,39)
                        $g.DrawLine($pen,$x,39,($x - 5),46); $g.DrawLine($pen,$x,39,($x + 5),46)
                    }
                }
                'lynk-engine' {
                    $g.DrawPolygon($pen,[Drawing.Point[]]@(
                        [Drawing.Point]::new(20,40),[Drawing.Point]::new(30,40),[Drawing.Point]::new(36,32),
                        [Drawing.Point]::new(58,32),[Drawing.Point]::new(66,42),[Drawing.Point]::new(76,42),
                        [Drawing.Point]::new(76,67),[Drawing.Point]::new(66,67),[Drawing.Point]::new(59,73),
                        [Drawing.Point]::new(37,73),[Drawing.Point]::new(29,65),[Drawing.Point]::new(20,65)))
                    $g.DrawLine($pen,13,44,13,60); $g.DrawLine($pen,13,52,20,52)
                    $g.DrawLine($pen,44,23,59,23); $g.DrawLine($pen,51,23,51,32)
                    $g.DrawLine($pen,76,54,84,54)
                }
                'lynk-sunroof' {
                    $g.DrawEllipse($pen,26,11,44,74)
                    $g.DrawPolygon($pen,[Drawing.Point[]]@(
                        [Drawing.Point]::new(35,36),[Drawing.Point]::new(61,36),
                        [Drawing.Point]::new(60,57),[Drawing.Point]::new(36,57)))
                    $g.DrawLine($pen,29,31,67,31); $g.DrawLine($pen,30,62,66,62)
                }
                'lynk-charge-port' {
                    $g.DrawEllipse($pen,15,15,66,66)
                    $g.DrawLines($pen,[Drawing.Point[]]@(
                        [Drawing.Point]::new(38,39),[Drawing.Point]::new(38,47),[Drawing.Point]::new(43,53),
                        [Drawing.Point]::new(53,53),[Drawing.Point]::new(58,47),[Drawing.Point]::new(58,39),
                        [Drawing.Point]::new(38,39)))
                    $g.DrawLine($pen,42,31,42,39); $g.DrawLine($pen,54,31,54,39)
                    $g.DrawLine($pen,48,53,48,65)
                }
            }
        } finally {
            $pen.Dispose()
            $brush.Dispose()
        }
    }
}
