#!/system/bin/sh
# Runs on the phone or emulator: shares a small timetable CSV to Spark as text, the way an AI app would (IM10).
# Usage from the efa folder:  adb push tools/share-smoke.sh /data/local/tmp/ && adb shell sh /data/local/tmp/share-smoke.sh
CSV="$(printf 'kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes\ndeadline,2027-01-08,,12:00,,Smoke test report,,,4,,,\n')"
am start -n app.efa/.MainActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "$CSV"
