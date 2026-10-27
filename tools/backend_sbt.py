"""Run local sbt; Windows delegates to the native PowerShell entry point."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / 'backend'
BUILD_CACHE = BACKEND / '.build-cache'


def prepare_outputs(platform: str) -> None:
    """Archive only generated outputs; leave demo databases and source intact."""
    marker = BUILD_CACHE / 'output-platform.txt'
    previous = marker.read_text().strip() if marker.exists() else 'unknown'
    BUILD_CACHE.mkdir(parents=True, exist_ok=True)
    if previous != platform:
        backup = BUILD_CACHE / 'output-backups' / uuid.uuid4().hex
        for relative in ('target/out', 'target/streams', 'project/target'):
            source = BACKEND / relative
            if source.exists():
                destination = backup / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                print(f'[repair] Preserve compiler outputs: {source} -> {destination}', flush=True)
                source.rename(destination)
    marker.write_text(platform + '\n')


def main() -> int:
    tasks = sys.argv[1:] or ['doctor']
    if tasks == ['--doctor']:
        tasks = ['doctor']
    print(f'[start] Python: {sys.executable}\n[start] Backend: {BACKEND}', flush=True)
    if sys.platform == 'win32':
        powershell = shutil.which('powershell.exe') or shutil.which('pwsh.exe')
        if not powershell:
            raise RuntimeError('PowerShell not found; use tools/backend.ps1 in a Windows terminal.')
        return subprocess.run([powershell, '-NoProfile', '-ExecutionPolicy', 'Bypass',
                               '-File', str(ROOT / 'tools/backend.ps1'), *tasks], check=False).returncode
    override = os.environ.get('SBT_LAUNCH_JAR')
    candidates = [Path(override)] if override else sorted(Path('/mnt/c/Users').glob(
        '*/AppData/Roaming/JetBrains/IntelliJIdea*/plugins/Scala/launcher/sbt-launch.jar'), reverse=True)
    launcher = next((p for p in candidates if p.is_file()), None)
    java = os.environ.get('DETECTIVE_JAVA') or shutil.which('java')
    if launcher is None or java is None:
        raise RuntimeError('Set SBT_LAUNCH_JAR and DETECTIVE_JAVA, or use the IntelliJ sbt panel.')
    cache = Path(os.environ.get('DETECTIVE_BUILD_CACHE', str(BUILD_CACHE / 'linux'))).resolve()
    print(f'[java] {java}\n[sbt] {launcher}\n[cache] {cache}\n[task] {tasks}', flush=True)
    if tasks == ['doctor']:
        return subprocess.run([java, '-version'], check=False).returncode
    if tasks == ['serve']:
        tasks = ['runMain services.system.api.WebDemo']
    if tasks == ['migrate']:
        tasks = ['runMain services.system.api.MigrateLegacyDemo']
    prepare_outputs('linux')
    cache.mkdir(parents=True, exist_ok=True)
    if not (cache / 'boot').exists():
        for parent in launcher.parents:
            existing = parent / '.sbt/boot'
            if existing.is_dir():
                print(f'[cache] Copy existing sbt boot cache from {existing}', flush=True)
                shutil.copytree(existing, cache / 'boot', ignore=shutil.ignore_patterns('*.lock'))
                break
    repositories = cache / 'repositories'
    repositories.write_text('[repositories]\nlocal\nmaven-central: https://repo.maven.apache.org/maven2/\n')
    env = dict(os.environ, COURSIER_CACHE=str(cache / 'coursier'))
    command = [java, '-Dfile.encoding=UTF-8', '-Dsbt.supershell=false', '-Dsbt.log.noformat=true',
               '-Dsbt.boot.server=false', '-Dsbt.server.autostart=false',
               f'-Dsbt.boot.directory={cache / "boot"}',
               f'-Dsbt.global.base={cache / "global"}', f'-Dsbt.ivy.home={cache / "ivy"}',
               '-Dsbt.override.build.repos=true', f'-Dsbt.repository.config={repositories}',
               '-jar', str(launcher), *tasks]
    print('[info] First dependency resolution may take several minutes. sbt output follows.', flush=True)
    return subprocess.run(command, cwd=BACKEND, env=env, check=False).returncode


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, RuntimeError) as error:
        print(f'[FAILED] {error}', file=sys.stderr, flush=True)
        sys.exit(1)
