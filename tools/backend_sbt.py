"""Run the backend with an existing sbt launcher; never downloads a launcher."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / 'backend'


def main() -> int:
    tasks = sys.argv[1:] or ['test']
    if shutil.which('sbt'):
        return subprocess.run(['sbt', *tasks], cwd=BACKEND, check=False).returncode
    override = os.environ.get('SBT_LAUNCH_JAR')
    windows_users = Path('/mnt/c/Users') if sys.platform != 'win32' else Path('C:/Users')
    candidates = [Path(override)] if override else sorted(windows_users.glob(
        '*/AppData/Roaming/JetBrains/IntelliJIdea*/plugins/Scala/launcher/sbt-launch.jar'), reverse=True)
    launcher = next((p for p in candidates if p.is_file()), None)
    if launcher is None:
        print('未找到 sbt。请在 IntelliJ 的 sbt 面板运行，或设置 SBT_LAUNCH_JAR。', file=sys.stderr)
        return 1
    java = shutil.which('java')
    if java is None:
        print('未找到 java，请将本地 JDK 加入 PATH。', file=sys.stderr)
        return 1
    # Use a writable cache; do not alter the Windows installation or its caches.
    cache = Path(os.environ.get('DETECTIVE_BUILD_CACHE', str(Path(tempfile.gettempdir()) / 'detective-sbt')))
    cache.mkdir(parents=True, exist_ok=True)
    if not (cache / 'boot').exists():
        for user in windows_users.glob('*'):
            existing = user / '.sbt/boot'
            if existing.is_dir():
                shutil.copytree(existing, cache / 'boot', ignore=shutil.ignore_patterns('*.lock'))
                break
    repositories = cache / 'repositories'
    if not repositories.exists():
        repositories.write_text('[repositories]\nlocal\nmaven-central: https://repo.maven.apache.org/maven2/\n')
    env = dict(os.environ, COURSIER_CACHE=str(cache / 'coursier'))
    command = [java, '-Dsbt.boot.server=false', '-Dsbt.server.autostart=false',
               f'-Dsbt.boot.directory={cache / "boot"}',
               f'-Dsbt.global.base={cache / "global"}', f'-Dsbt.ivy.home={cache / "ivy"}',
               '-Dsbt.override.build.repos=true', f'-Dsbt.repository.config={repositories}',
               '-jar', str(launcher), *tasks]
    return subprocess.run(command, cwd=BACKEND, env=env, check=False).returncode


if __name__ == '__main__':
    sys.exit(main())
