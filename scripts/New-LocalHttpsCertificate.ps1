<#
.SYNOPSIS
Generates localhost HTTPS files for Docker Compose using an installed mkcert.
.DESCRIPTION
Run mkcert -install separately to explicitly trust your machine's development CA.
This script does not install software or change trusted certificate stores.
#>
[CmdletBinding()]
param([switch]$ReplaceExisting)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command mkcert -ErrorAction SilentlyContinue)) {
    throw 'mkcert is missing. Follow docs/local-https.md to install and trust it first.'
}

$certificateDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../frontend/ssl'))
$certificatePath = Join-Path $certificateDirectory 'cert.crt'
$keyPath = Join-Path $certificateDirectory 'cert.key'
$existingPaths = @($certificatePath, $keyPath) | Where-Object { Test-Path -LiteralPath $_ }
if ($existingPaths -and -not $ReplaceExisting) {
    throw 'Local SSL files already exist. Use -ReplaceExisting to back them up and generate replacements.'
}

New-Item -ItemType Directory -Path $certificateDirectory -Force | Out-Null
if ($existingPaths) {
    $backupDirectory = Join-Path $certificateDirectory ('backup-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $backupDirectory | Out-Null
    foreach ($existingPath in $existingPaths) {
        Copy-Item -LiteralPath $existingPath -Destination $backupDirectory
    }
    Write-Host "Previous SSL files backed up to $backupDirectory"
}

& mkcert -cert-file $certificatePath -key-file $keyPath localhost 127.0.0.1 ::1
if ($LASTEXITCODE -ne 0) {
    throw 'Certificate generation failed. Do not restart the frontend until the SSL files are valid.'
}
Write-Host 'Certificate generated. Rebuild/recreate the frontend using the local HTTPS guide.'
