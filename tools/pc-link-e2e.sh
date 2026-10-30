#!/usr/bin/env bash
# Proves the phone and the Windows service agree (A4, K4, K5): starts Spark Guard on this computer against a TEMPORARY hosts file (never the real one),
# runs the phone tests against it from the emulator (which reaches this computer as 10.0.2.2), then checks what the service wrote. Run from the efa folder.
set -u
export PATH="$LOCALAPPDATA/dotnet-sdk:$PATH" DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
PORT=47999
TMP="$(mktemp -d)"
printf '127.0.0.1 localhost\r\n' > "$TMP/hosts"
(cd windows && dotnet build SparkGuard.sln -v q 2>&1 | grep -E "error" ) && { echo "build failed"; exit 1; }
DLL="windows/SparkGuard.Service/bin/Debug/net8.0-windows/SparkGuard.Service.dll"
dotnet "$DLL" --data "$TMP/data" --hosts "$TMP/hosts" --port $PORT --no-lock > "$TMP/service.out" 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null; rm -rf "$TMP"' EXIT
for i in $(seq 1 30); do [ -f "$TMP/data/pairing.code" ] && break; sleep 1; done
CODE="$(cat "$TMP/data/pairing.code")"
[ -n "$CODE" ] || { echo "the service did not start"; cat "$TMP/service.out"; exit 1; }
./gradlew.bat :app:connectedDebugAndroidTest --console=plain "-Pandroid.testInstrumentationRunnerArguments.class=app.efa.PcLinkE2ETest" "-Pandroid.testInstrumentationRunnerArguments.pcCode=$CODE" "-Pandroid.testInstrumentationRunnerArguments.pcPort=$PORT" 2>&1 | grep -E "BUILD|FAILED"
python tools/device-results.py | tail -3
sleep 3
echo "--- the temporary hosts file after the phone sent its state ---"
cat "$TMP/hosts"
grep -q "0.0.0.0 youtube.com" "$TMP/hosts" && echo "RESULT: the service wrote the block the phone asked for" || { echo "RESULT: NO BLOCK WRITTEN"; exit 1; }
