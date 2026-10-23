# Prints a summary and every failure from the last connectedDebugAndroidTest run.
# Run from the efa folder: python tools/device-results.py
import glob
import re

total = failed = 0
for f in glob.glob('app/build/outputs/androidTest-results/connected/**/*.xml', recursive=True):
    t = open(f, encoding='utf-8', errors='replace').read()
    for m in re.finditer(r'<testsuite name="([^"]+)" tests="(\d+)" failures="(\d+)" errors="(\d+)"', t):
        total += int(m.group(2))
        failed += int(m.group(3)) + int(m.group(4))
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<failure[^>]*>([^<]{0,600})', t):
        print('FAIL', m.group(1), '=>', m.group(2).replace('&#10;', '\n'), '\n')
print('device tests', total, 'failed', failed)
