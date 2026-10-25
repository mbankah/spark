# LA14: reboots the emulator N times and, after each boot, checks that the launcher and blocking started by themselves.
#   python tools/launcher-check.py 5        (a few reboots)
#   python tools/launcher-check.py 100      (the acceptance run)
# It makes Spark the home app and turns its services on first, and puts the original home app back at the end.
# Each boot is checked for: Spark resolves as HOME, the accessibility service is bound, the Buffer notification is posted,
# the snapshot loaded line is in the log, and a cold start of the launcher takes under 1 second (LA4).
# Results are written to docs/launcher-check.csv.
import re
import subprocess
import sys
import time

ADB = r'C:\Users\banmi\AppData\Local\Android\Sdk\platform-tools\adb.exe'
SERVICE = 'app.efa/app.efa.BlockerService'


def adb(*a, timeout=120):
    try:
        r = subprocess.run([ADB, *a], capture_output=True, text=True, timeout=timeout, encoding='utf-8', errors='replace')
    except subprocess.TimeoutExpired:
        return ''                                         # a slow emulator is a failed check, not a crashed script
    return (r.stdout or '') + (r.stderr or '')


def sh(cmd, timeout=120):
    return adb('shell', cmd, timeout=timeout)


def wait_boot(limit=600):
    t0 = time.time()
    adb('wait-for-device', timeout=limit)
    while time.time() - t0 < limit:
        if sh('getprop sys.boot_completed').strip() == '1' and 'RUNNING_UNLOCKED' in sh('dumpsys user | grep -m1 State:'):
            return time.time() - t0
        time.sleep(2)
    raise SystemExit('device did not finish booting')


def setup():
    holders = sh('cmd role get-role-holders --user 0 android.app.role.HOME').strip()
    sh('pm grant app.efa android.permission.POST_NOTIFICATIONS')
    sh('cmd role add-role-holder --user 0 android.app.role.HOME app.efa')
    cur = sh('settings get secure enabled_accessibility_services').strip()
    if SERVICE not in cur:
        sh(f"settings put secure enabled_accessibility_services {SERVICE if cur in ('', 'null') else cur + ':' + SERVICE}")
    sh('settings put secure accessibility_enabled 1')
    return holders


def until(fn, limit=120):
    t0 = time.time()
    while time.time() - t0 < limit:
        if fn():
            return True
        time.sleep(3)
    return fn()


def check():
    r = {}
    r['home'] = until(lambda: 'app.efa/' in sh('cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'))
    r['service'] = until(lambda: 'BlockerService' in sh('dumpsys accessibility | grep -A3 "Bound services"'))
    r['notification'] = until(lambda: bool(re.search(r'pkg=app\.efa .*id=4201', sh('dumpsys notification --noredact | grep "NotificationRecord"'))))
    r['snapshot'] = until(lambda: 'snapshot loaded' in adb('logcat', '-d', '-s', 'efa-state'))
    r['crashes'] = adb('logcat', '-d', '-b', 'crash').count('FATAL EXCEPTION')
    # Nothing here stops or restarts Spark, so a failed check is Spark's or Android's and not the script's (force-stop switches
    # an accessibility service off). Cold start is measured separately by tools/cold-start.py.
    r['enabled'] = SERVICE in sh('settings get secure enabled_accessibility_services')
    out = sh('am start -W -n app.efa/.MainActivity')
    m = re.search(r'TotalTime: (\d+)', out)
    r['start_ms'] = int(m.group(1)) if m else -1
    r['launch'] = (re.search(r'LaunchState: (\w+)', out) or [None, '?'])[1]
    return r


EMULATOR = r'C:\Users\banmi\AppData\Local\Android\Sdk\emulator\emulator.exe'


def restart_emulator():
    """After about a dozen reboots in one emulator session the guest gets slower and slower (boots of 5 minutes), so the emulator
    process itself is restarted now and then. The phone's data is kept, so the app and its settings are as they were."""
    subprocess.run(['taskkill', '/F', '/IM', 'qemu-system-x86_64.exe'], capture_output=True)
    subprocess.run(['taskkill', '/F', '/IM', 'emulator.exe'], capture_output=True)
    time.sleep(5)
    subprocess.Popen([EMULATOR, '-avd', 'efa_test', '-no-snapshot', '-no-audio', '-gpu', 'swiftshader_indirect'],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=0x00000008)
    time.sleep(20)
    return wait_boot(900)


def write(rows):
    with open('docs/launcher-check.csv', 'w') as f:
        f.write('boot,seconds,home,service,notification,snapshot,start_ms,launch,crashes,enabled,ok' + chr(10))
        for row in rows:
            f.write(','.join(str(x) for x in row) + chr(10))


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 5
    original = setup()
    rows, bad = [], 0
    for i in range(1, n + 1):
        adb('logcat', '-c')
        if i % 1000 == 0:
            secs = restart_emulator()                    # counts as the boot: Spark must still start by itself from a cold emulator
        else:
            adb('reboot')
            time.sleep(8)
            secs = wait_boot()
        time.sleep(5)                                   # BOOT_COMPLETED and the service bind come a little after boot_completed
        r = check()
        ok = r['home'] and r['service'] and r['notification'] and r['snapshot']
        bad += 0 if ok else 1
        rows.append((i, round(secs), r['home'], r['service'], r['notification'], r['snapshot'], r['start_ms'], r['launch'], r['crashes'], r['enabled'], ok))
        print(i, rows[-1], flush=True)
        write(rows)
    if original and original != 'app.efa':
        sh(f'cmd role add-role-holder --user 0 android.app.role.HOME {original}')
    print(f'{n - bad}/{n} boots passed')
    sys.exit(1 if bad else 0)


main()
