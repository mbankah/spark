# LA4: the launcher's cold start, measured from outside Spark (a test inside Spark cannot kill its own process).
#   python tools/cold-start.py 10
# Stopping the app switches its accessibility service off in Android, so the service is put back at the end.
import re
import statistics
import subprocess
import sys
import time

ADB = r'C:\Users\banmi\AppData\Local\Android\Sdk\platform-tools\adb.exe'
SERVICE = 'app.efa/app.efa.BlockerService'


def sh(cmd):
    return subprocess.run([ADB, 'shell', cmd], capture_output=True, text=True, timeout=120).stdout


n = int(sys.argv[1]) if len(sys.argv) > 1 else 10
times = []
for _ in range(n):
    sh('am force-stop app.efa')
    time.sleep(3)
    out = sh('am start -W -n app.efa/.MainActivity')
    m = re.search(r'TotalTime: (\d+)', out)
    if m and 'LaunchState: COLD' in out:
        times.append(int(m.group(1)))
sh('settings put secure enabled_accessibility_services ' + SERVICE)
print('cold starts (ms):', times)
if times:
    print('median', statistics.median(times), 'max', max(times), 'under 1000 ms:', sum(t < 1000 for t in times), 'of', len(times))
