# Adapted for Wrist Lynk from zjs215/geely-band-control (Apache-2.0). See NOTICE.
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'prepare.ps1')

Push-Location (Join-Path $project 'android')
try {
    & .\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :watch:assembleRelease :watch:testDebugUnitTest :watch:lintDebug
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
    & npm.cmd run release
    if ($LASTEXITCODE -ne 0) { throw 'Vela release failed.' }
} finally {
    Pop-Location
}

$artifacts = Join-Path $project 'artifacts'
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
$manifest = Get-Content -LiteralPath (Join-Path $project 'band\src\manifest.json') -Raw | ConvertFrom-Json
$rpk = '{0}.release.{1}.rpk' -f $manifest.package,$manifest.versionName
Copy-Item -LiteralPath (Join-Path $project 'android\app\build\outputs\apk\release\app-release.apk') `
    -Destination (Join-Path $artifacts 'lynk-companion-phone.apk') -Force
Copy-Item -LiteralPath (Join-Path (Join-Path $project 'band\dist') $rpk) `
    -Destination (Join-Path $artifacts 'lynk-band.rpk') -Force
Copy-Item -LiteralPath (Join-Path $project 'android\watch\build\outputs\apk\release\watch-release.apk') -Destination (Join-Path $artifacts 'lynk-watch.apk') -Force
Get-ChildItem -LiteralPath $artifacts -File | Select-Object Name,Length
$checksums = 'lynk-companion-phone.apk','lynk-watch.apk','lynk-band.rpk' | ForEach-Object {
    '{0}  {1}' -f (Get-FileHash -LiteralPath (Join-Path $artifacts $_) -Algorithm SHA256).Hash.ToLowerInvariant(), $_
}
Set-Content -LiteralPath (Join-Path $artifacts 'SHA256SUMS.txt') -Value $checksums -Encoding Ascii
