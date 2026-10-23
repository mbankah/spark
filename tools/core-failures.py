# Prints every failing core (JVM) test with its message, and the total. Run from the efa folder: python tools/core-failures.py
import glob
import re

total = failed = 0
for f in glob.glob('core/build/test-results/test/*.xml'):
    t = open(f, encoding='utf-8', errors='replace').read()
    m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', t)
    if m:
        total += int(m.group(1)); failed += int(m.group(2)) + int(m.group(3))
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<failure message="([^"]{0,400})', t):
        print('FAIL', m.group(1), '=>', m.group(2))
print('core tests', total, 'failed', failed)
