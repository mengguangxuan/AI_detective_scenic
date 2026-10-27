$ErrorActionPreference = 'Stop'
$source = 'C:\Program Files\nodejs'
$destination = 'E:\dev\nodejs'
$cache = 'E:\dev\npm-cache'
$prefix = 'E:\dev\npm-global'
$dependencies = 'E:\dev\project-dependencies\detective-scenic-frontend\node_modules'
if (-not (Test-Path "$source\node.exe")) { throw 'Original Node.js not found.' }
if (Test-Path "$destination\node.exe") {
    $originalHash = (Get-FileHash "$source\node.exe" -Algorithm SHA256).Hash
    $existingHash = (Get-FileHash "$destination\node.exe" -Algorithm SHA256).Hash
    if ($originalHash -ne $existingHash) { throw 'E: already contains a different Node.js; stopped without replacing it.' }
}
New-Item -ItemType Directory -Force -Path $destination,$cache,$prefix,$dependencies | Out-Null
& robocopy $source $destination /E /COPY:DAT /R:1 /W:1 /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -ge 8) { throw 'Node.js copy failed.' }
if ((Get-FileHash "$source\node.exe").Hash -ne (Get-FileHash "$destination\node.exe").Hash) { throw 'Node.js hash mismatch.' }
Write-Output '[OK] Node.js executable hash verified.'
$oldCache = Join-Path $env:LOCALAPPDATA 'npm-cache\_cacache'
if (Test-Path $oldCache) {
    & robocopy $oldCache "$cache\_cacache" /E /COPY:DAT /R:1 /W:1 /NFL /NDL /NJH /NJS /NP
    if ($LASTEXITCODE -ge 8) { throw 'Cache copy failed.' }
}
$projectRoot = Split-Path $PSScriptRoot -Parent
$link = Join-Path $projectRoot 'frontend\node_modules'
if (Test-Path $link) {
    $item = Get-Item $link -Force
    if ($item.LinkType -ne 'Junction' -or $item.Target -notcontains $dependencies) {
        throw 'Project node_modules already exists with another location; stopped without moving it.'
    }
} else {
    New-Item -ItemType Junction -Path $link -Target $dependencies | Out-Null
}
& "$destination\node.exe" --version
& "$destination\node.exe" "$destination\node_modules\npm\bin\npm-cli.js" --version
if ($LASTEXITCODE -ne 0) { throw 'Copied npm verification failed.' }
Write-Output '[OK] E: runtime, cache, global-prefix directory and dependency junction ready.'
Write-Output '[UNCHANGED] C: runtime/cache, system PATH, user PATH and other projects.'
