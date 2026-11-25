param([string]$Destination)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
if (!$Destination) { $Destination = Join-Path $project '.local/open-source/source' }
$target = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $target) { throw 'Choose a new, empty export directory.' }
Push-Location $project
try {
    $files = @(& git -c core.quotepath=false ls-files)
    if ($LASTEXITCODE -ne 0 -or $files.Count -eq 0) { throw 'Register the reviewed source files with Git before exporting.' }
    $blocked = $files | Where-Object {
        $_ -match '(^|/)(\.local|node_modules|\.gradle|build|dist|sign|artifacts)/' -or
        $_ -match '\.(pem|p12|pfx|jks|keystore|aar|apk|rpk|log)$' -or
        $_ -match '(^|/)(signing|local)\.properties$'
    }
    if ($blocked) { throw ('Private or generated files are registered: ' + ($blocked -join ', ')) }
    foreach ($relative in $files) {
        $output = [IO.Path]::GetFullPath((Join-Path $target $relative))
        if (!$output.StartsWith($target + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Export path escaped the destination.'
        }
        New-Item -ItemType Directory -Path (Split-Path -Parent $output) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $project $relative) -Destination $output
    }
    Write-Output ('Exported {0} source files to {1}' -f $files.Count,$target)
} finally {
    Pop-Location
}
