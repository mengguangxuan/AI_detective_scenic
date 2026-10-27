param([ValidateSet('doctor','install','dev','build','test','preview','browser-test','browser-install')][string]$Task = 'doctor')
$ErrorActionPreference = 'Stop'
$nodeRoot = 'E:\dev\nodejs'
$node = Join-Path $nodeRoot 'node.exe'
$npm = Join-Path $nodeRoot 'node_modules\npm\bin\npm-cli.js'
if (-not (Test-Path $node) -or -not (Test-Path $npm)) { throw 'Run tools/prepare_frontend_environment.ps1 first.' }
$inheritedNode = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
# Process-local configuration only; parent shell and Windows settings are untouched.
$env:PATH = "$nodeRoot;$env:PATH"
$env:npm_config_cache = 'E:\dev\npm-cache'
$env:npm_config_prefix = 'E:\dev\npm-global'
$env:PLAYWRIGHT_BROWSERS_PATH = 'E:\dev\playwright-browsers'
$frontend = Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend'
Push-Location $frontend
try {
    if ($Task -eq 'doctor') {
        Write-Output "Inherited Node: $inheritedNode"
        Write-Output 'Project Node / npm cache / npm prefix:'
        & $node -p 'process.execPath'
        & $node $npm config get cache
        & $node $npm config get prefix
        Get-Item node_modules | Select-Object FullName, LinkType, Target
    } elseif ($Task -eq 'install') {
        $dependencyRoot = 'E:\dev\project-dependencies\detective-scenic-frontend'
        New-Item -ItemType Directory -Force -Path $dependencyRoot | Out-Null
        Copy-Item package.json "$dependencyRoot\package.json" -Force
        if (Test-Path package-lock.json) { Copy-Item package-lock.json "$dependencyRoot\package-lock.json" -Force }
        & $node $npm --prefix $dependencyRoot install --prefer-offline --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'Dependency install failed; original environment untouched.' }
        Copy-Item "$dependencyRoot\package-lock.json" package-lock.json -Force
        $link = Join-Path $frontend 'node_modules'
        $target = Join-Path $dependencyRoot 'node_modules'
        if (-not (Test-Path $link)) { New-Item -ItemType Junction -Path $link -Target $target | Out-Null }
        elseif ((Get-Item $link -Force).Target -notcontains $target) { throw 'Unexpected project node_modules; stopped.' }
    }
    elseif ($Task -eq 'browser-install') { & $node 'node_modules\playwright\cli.js' install chromium --only-shell }
    elseif ($Task -eq 'browser-test') { & $node $npm run test:browser }
    else { & $node $npm run $Task }
    if ($LASTEXITCODE -ne 0) { throw "Frontend task failed: $Task (exit $LASTEXITCODE)" }
} finally { Pop-Location }
