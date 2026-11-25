$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$local = Join-Path $project '.local'
$sign = Join-Path $project 'band\sign\debug'
New-Item -ItemType Directory -Force -Path $local,$sign | Out-Null

function Invoke-Checked($program, $arguments) {
    & $program @arguments
    if ($LASTEXITCODE -ne 0) { throw "$program exited with code $LASTEXITCODE" }
}

function Get-Checksum($path) {
    $file = [System.IO.File]::OpenRead($path)
    $digest = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($digest.ComputeHash($file)).Replace('-', '')
    } finally {
        $file.Dispose()
        $digest.Dispose()
    }
}

$sdkHash = '9C40FD1C5409BB948523D503AF71E2978AE522C35636AFE7D64F474C0F6BC195'
$sdkFile = Join-Path $local 'xms-wearable-lib_1.4_release.aar'
if (!(Test-Path -LiteralPath $sdkFile)) {
    $archive = Join-Path $local 'interconnect_demo.zip'
    $archiveEntry = 'interconnect_dev_test_demo/libs/xms-wearable-lib_1.4_release.aar'
    $sdkUrl = 'https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip'
    Invoke-Checked 'curl.exe' @('--ssl-revoke-best-effort','--fail','--silent','--show-error','--location','--max-time','60','--output',$archive,$sdkUrl)
    Invoke-Checked 'tar.exe' @('-xf',$archive,'-C',$local,$archiveEntry)
    $extracted = Join-Path $local $archiveEntry
    if ((Get-Checksum $extracted) -ne $sdkHash) { throw 'Xiaomi SDK checksum mismatch.' }
    Move-Item -LiteralPath $extracted -Destination $sdkFile
}
if ((Get-Checksum $sdkFile) -ne $sdkHash) { throw 'Local Xiaomi SDK checksum mismatch.' }

$certificate = Join-Path $local 'certificate.pem'
$privateKey = Join-Path $local 'private.pem'
$store = Join-Path $local 'companion.p12'
$properties = Join-Path $local 'signing.properties'
$signingFiles = @($certificate,$privateKey,$store,$properties)
$existing = @($signingFiles | Where-Object { Test-Path -LiteralPath $_ })
if ($existing.Count -eq 0) {
    $opensslCommand = Get-Command openssl -ErrorAction SilentlyContinue
    $openssl = if ($opensslCommand) { $opensslCommand.Source } else { 'C:\Program Files\Git\usr\bin\openssl.exe' }
    if (!(Test-Path -LiteralPath $openssl)) { throw 'Install OpenSSL or Git for Windows to generate the shared development certificate.' }
    $stage = Join-Path $local ('signing-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stage | Out-Null
    $stageCert = Join-Path $stage 'certificate.pem'
    $stageKey = Join-Path $stage 'private.pem'
    $stageStore = Join-Path $stage 'companion.p12'
    $stageProperties = Join-Path $stage 'signing.properties'
    $password = [guid]::NewGuid().ToString('N')
    $env:GALAXY_BAND_KEYSTORE_PASSWORD = $password
    try {
        Invoke-Checked $openssl @('req','-newkey','rsa:2048','-nodes','-x509','-sha256','-days','3650','-subj','/CN=Galaxy Band Development','-keyout',$stageKey,'-out',$stageCert)
        Invoke-Checked $openssl @('pkcs12','-export','-name','bandcontrol','-inkey',$stageKey,'-in',$stageCert,'-out',$stageStore,'-passout','env:GALAXY_BAND_KEYSTORE_PASSWORD')
        Set-Content -LiteralPath $stageProperties -Value ('password=' + $password) -Encoding Ascii
        Move-Item -LiteralPath $stageCert,$stageKey,$stageStore,$stageProperties -Destination $local
        Remove-Item -LiteralPath $stage
    } finally {
        Remove-Item Env:GALAXY_BAND_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    }
} elseif ($existing.Count -ne $signingFiles.Count) {
    throw 'The signing set in .local is incomplete. Restore the missing files to keep the installed pair compatible.'
}
Copy-Item -LiteralPath $certificate,$privateKey -Destination $sign -Force
