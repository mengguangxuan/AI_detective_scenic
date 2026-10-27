param(
    [ValidateSet('doctor', 'test', 'run', 'migrate', 'serve', 'stop-serve')]
    [string]$Task = 'doctor'
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$Backend = Join-Path $ProjectRoot 'backend'
$BuildCache = Join-Path $Backend '.build-cache'

# Graceful stop of this project's web demo, without process-name killing.
if ($Task -eq 'stop-serve') {
    $StopDirectory = Join-Path $Backend 'target\web-demo\windows'
    if (-not (Test-Path -LiteralPath $StopDirectory)) { throw 'Web demo has not been initialized.' }
    Set-Content -LiteralPath (Join-Path $StopDirectory 'stop-requested') -Value 'stop' -Encoding ASCII
    Write-Host '[OK] Graceful stop requested for this project web demo.'
    exit 0
}

function Find-Java {
    if ($env:DETECTIVE_JAVA) {
        if (-not (Test-Path -LiteralPath $env:DETECTIVE_JAVA -PathType Leaf)) {
            throw 'DETECTIVE_JAVA does not point to java.exe.'
        }
        return $env:DETECTIVE_JAVA
    }
    if ($env:JAVA_HOME) {
        $Candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path -LiteralPath $Candidate) { return $Candidate }
    }
    $Candidates = @(
        'C:\Program Files\Java\jdk-21\bin\java.exe'
        'C:\Program Files\Eclipse Adoptium\jdk-21*\bin\java.exe'
        'E:\Intellij\IntelliJ IDEA *\jbr\bin\java.exe'
    )
    foreach ($Pattern in $Candidates) {
        $Match = Get-ChildItem -Path $Pattern -File -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if ($Match) { return $Match.FullName }
    }
    $Command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($Command) { return $Command.Source }
    throw 'Java not found. Set DETECTIVE_JAVA to the full path of java.exe.'
}

function Find-Launcher {
    if ($env:SBT_LAUNCH_JAR) {
        if (-not (Test-Path -LiteralPath $env:SBT_LAUNCH_JAR -PathType Leaf)) {
            throw 'SBT_LAUNCH_JAR does not point to an existing file.'
        }
        return $env:SBT_LAUNCH_JAR
    }
    $Pattern = Join-Path $env:APPDATA 'JetBrains\IntelliJIdea*\plugins\Scala\launcher\sbt-launch.jar'
    $Match = Get-ChildItem -Path $Pattern -File -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($Match) { return $Match.FullName }
    throw 'sbt launcher not found. Set SBT_LAUNCH_JAR to the local sbt-launch.jar.'
}

# Only generated compiler outputs are archived. target/fixture-demo stays intact.
function Prepare-Outputs {
    $Marker = Join-Path $BuildCache 'output-platform.txt'
    $OldPlatform = if (Test-Path -LiteralPath $Marker) { (Get-Content -LiteralPath $Marker -Raw).Trim() } else { 'unknown' }
    $Outputs = @('target\out', 'target\streams', 'project\target')
    if ($OldPlatform -ne 'windows') {
        $Backup = Join-Path $BuildCache ('output-backups\' + [Guid]::NewGuid().ToString('N'))
        foreach ($Relative in $Outputs) {
            $Source = Join-Path $Backend $Relative
            if (Test-Path -LiteralPath $Source) {
                $Destination = Join-Path $Backup $Relative
                New-Item -ItemType Directory -Force -Path (Split-Path $Destination -Parent) | Out-Null
                Write-Host "[repair] Preserving old compiler output: $Source -> $Destination"
                Move-Item -LiteralPath $Source -Destination $Destination
            }
        }
    }
    Set-Content -LiteralPath $Marker -Value 'windows' -Encoding ASCII
}

try {
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $OutputEncoding = [Console]::OutputEncoding
    Write-Host "[start] Backend: $Backend"
    $Java = Find-Java
    $Launcher = Find-Launcher
    Write-Host "[java]  $Java"
    Write-Host "[sbt]   $Launcher"
    Write-Host "[task]  $Task"
    Write-Host '[info] Windows and WSL must not build this checkout simultaneously.'
    if ($Task -eq 'doctor') {
        $SavedPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $Java -version 2>&1 | ForEach-Object { Write-Host "$_" }
        $Code = $LASTEXITCODE
        $ErrorActionPreference = $SavedPreference
        if ($Code -ne 0) { throw 'java -version failed.' }
        $Marker = Join-Path $BuildCache 'output-platform.txt'
        if (-not (Test-Path -LiteralPath $Marker) -or (Get-Content -LiteralPath $Marker -Raw).Trim() -ne 'windows') {
            Write-Host '[repair] Old compiler outputs will be archived automatically before test/run.'
        }
        Write-Host '[OK] Environment located. Run this script with test, then run.'
        exit 0
    }
    New-Item -ItemType Directory -Force -Path $BuildCache | Out-Null
    Prepare-Outputs
    $Log = Join-Path $BuildCache ('windows-' + $Task + '.log')
    $Repositories = Join-Path $BuildCache 'repositories'
    @('[repositories]', 'local', 'maven-central: https://repo.maven.apache.org/maven2/') |
        Set-Content -LiteralPath $Repositories -Encoding ASCII
    $JavaArgs = @(
        '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8'
        '-Dsbt.boot.server=false', '-Dsbt.server.autostart=false'
        '-Dsbt.supershell=false', '-Dsbt.log.noformat=true'
        '-Dsbt.override.build.repos=true', "-Dsbt.repository.config=$Repositories"
        '-jar', $Launcher, $(if ($Task -eq 'migrate') { 'runMain services.system.api.MigrateLegacyDemo' } elseif ($Task -eq 'serve') { 'runMain services.system.api.WebDemo' } else { $Task })
    )
    Write-Host "[log]   $Log"
    Write-Host '[info] First dependency resolution may take several minutes. sbt output follows.'
    Push-Location $Backend
    try {
        # PS 5.1 treats Java stderr as NativeCommandError under Stop. Preserve it as log output.
        $SavedPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $Java @JavaArgs 2>&1 | ForEach-Object { "$_" -replace '\x1B\[[0-?]*[ -/]*[@-~]', '' } | Tee-Object -FilePath $Log
        $Code = $LASTEXITCODE
        $ErrorActionPreference = $SavedPreference
    } finally {
        Pop-Location
    }
    if ($Code -ne 0) {
        Write-Host "[FAILED] sbt exit code $Code. Copy the full log: $Log"
    } else {
        Write-Host "[OK] $Task completed. Log: $Log"
    }
    exit $Code
} catch {
    Write-Host ('[FAILED] ' + $_.Exception.Message)
    exit 1
}
