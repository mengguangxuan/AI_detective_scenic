param([string]$InputFile = '', [switch]$Resume, [switch]$Inspect)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$Cache = Join-Path $ProjectRoot 'backend\.build-cache\scene-pipeline'
New-Item -ItemType Directory -Force -Path $Cache | Out-Null
$env:PLAYWRIGHT_BROWSERS_PATH = 'E:\dev\playwright-browsers'
$Arguments = @((Join-Path $PSScriptRoot 'run_scene_pipeline.mjs'))
if ($Inspect) { $Arguments += '--inspect' }
if ($Resume) { $Arguments += '--resume' }
elseif ($InputFile) { $Arguments += @('--input', $InputFile) }
$Process = Start-Process -FilePath 'E:\dev\nodejs\node.exe' -ArgumentList $Arguments -WorkingDirectory $ProjectRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $Cache 'runner.out.log') -RedirectStandardError (Join-Path $Cache 'runner.err.log') -PassThru
Set-Content -LiteralPath (Join-Path $Cache 'runner.pid') -Value $Process.Id -Encoding ASCII
Write-Output "Scene workflow process: $($Process.Id); status: http://127.0.0.1:5190/"
