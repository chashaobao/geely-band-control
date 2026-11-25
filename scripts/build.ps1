$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'prepare.ps1')

Push-Location (Join-Path $project 'android')
try {
    & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :watch:assembleDebug :watch:testDebugUnitTest :watch:lintDebug
    if ($LASTEXITCODE -ne 0) { throw 'Android build or tests failed.' }
} finally {
    Pop-Location
}

Push-Location (Join-Path $project 'band')
try {
    & npm.cmd ci --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'Vela dependency installation failed.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Vela build failed.' }
    & node --test test/garage.test.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Vela protocol or control tests failed.' }
} finally {
    Pop-Location
}

$artifacts = Join-Path $project 'artifacts'
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
$manifest = Get-Content -LiteralPath (Join-Path $project 'band\src\manifest.json') -Raw | ConvertFrom-Json
$rpk = '{0}.debug.{1}.rpk' -f $manifest.package,$manifest.versionName
Copy-Item -LiteralPath (Join-Path $project 'android\app\build\outputs\apk\debug\app-debug.apk') `
    -Destination (Join-Path $artifacts 'galaxy-band-companion.apk') -Force
Copy-Item -LiteralPath (Join-Path $project 'android\watch\build\outputs\apk\debug\watch-debug.apk') `
    -Destination (Join-Path $artifacts 'galaxy-watch-companion.apk') -Force
Copy-Item -LiteralPath (Join-Path (Join-Path $project 'band\dist') $rpk) `
    -Destination (Join-Path $artifacts 'galaxy-band.rpk') -Force
Get-ChildItem -LiteralPath $artifacts -File | Select-Object Name,Length
$checksums = Get-ChildItem -LiteralPath $artifacts -File | Where-Object { $_.Extension -in '.apk','.rpk' } | ForEach-Object {
    '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name
}
Set-Content -LiteralPath (Join-Path $artifacts 'SHA256SUMS.txt') -Value $checksums -Encoding Ascii
