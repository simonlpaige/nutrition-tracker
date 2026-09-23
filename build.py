#!/usr/bin/env python3
"""Build on a computer with JDK 17+ and Android SDK platform/build-tools 35 installed.
No Python packages required. Downloads verified Gradle 8.9 from its official host.
"""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
VERSION = '8.9'

def main():
    if not shutil.which('java') and not os.environ.get('JAVA_HOME'):
        sys.exit('Install JDK 17 and add java to PATH, or set JAVA_HOME to Android Studio\'s JDK.')
    candidates = [os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT'),
        str(Path.home() / 'Library/Android/sdk'), str(Path.home() / 'Android/Sdk'),
        str(Path(os.environ.get('LOCALAPPDATA', str(Path.home()))) / 'Android/Sdk')]
    sdk = next((Path(p) for p in candidates if p and (Path(p)/'platforms/android-35/android.jar').is_file()), None)
    if sdk is None:
        sys.exit('In Android Studio > SDK Manager install Android SDK Platform 35 and Build-Tools 35.0.0. Set ANDROID_HOME if your SDK is in a custom location.')
    env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
    cache = Path.home() / '.cache' / 'daily-rhythm-build'
    dist = cache / f'gradle-{VERSION}'
    if not dist.is_dir():
        cache.mkdir(parents=True, exist_ok=True)
        url = f'https://services.gradle.org/distributions/gradle-{VERSION}-bin.zip'
        archive = cache / f'gradle-{VERSION}-bin.zip'
        print(f'Downloading Gradle {VERSION} from services.gradle.org...')
        urllib.request.urlretrieve(url, archive)
        expected = urllib.request.urlopen(url+'.sha256', timeout=30).read().decode().strip().split()[0]
        actual = hashlib.sha256(archive.read_bytes()).hexdigest()
        if actual != expected:
            archive.unlink(missing_ok=True)
            sys.exit('Gradle checksum mismatch. No code was executed.')
        with zipfile.ZipFile(archive) as z:
            for info in z.infolist():
                target = (cache / info.filename).resolve()
                if not target.is_relative_to(cache.resolve()):
                    sys.exit('Invalid Gradle ZIP entry.')
            z.extractall(cache)
        archive.unlink()
    executable = dist / 'bin' / ('gradle.bat' if os.name=='nt' else 'gradle')
    if os.name!='nt': executable.chmod(0o755)
    # Generate the standard Gradle wrapper for subsequent Android Studio use.
    tasks = ['wrapper', '--gradle-version', VERSION, 'assembleDebug', 'lintDebug', '--no-daemon']
    cmd = [str(executable), *tasks]
    if os.name == 'nt': cmd = ['cmd', '/c', *cmd]
    subprocess.run(cmd, cwd=ROOT, env=env, check=True)
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    if not apk.is_file(): sys.exit('Build did not produce an APK.')
    destination = ROOT / 'Daily-Rhythm-debug.apk'
    shutil.copy2(apk, destination)
    print(f'\nAPK ready: {destination}\nInstall on an Android 14+ device. This is a debug build for personal testing.')

if __name__ == '__main__':
    try: main()
    except (OSError, subprocess.CalledProcessError) as exc: sys.exit(f'Build failed: {exc}')
