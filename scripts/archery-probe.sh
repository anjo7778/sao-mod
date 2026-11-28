#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-archery build/archery-probe
if [[ -e run-archery/saves/sao-archery-test ]]; then echo "Refusing old test save"; exit 1; fi
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-archery/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/archery-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runArcheryProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+450))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_ARCHERY_PROBE_PASS' "$log"; then
        grep -q 'SAO_ARCHERY_CONTRACT_OK' "$log"
        grep -q 'SAO_ARCHERY_LAUNCH_OK' "$log"
        grep -q 'SAO_ARCHERY_KILL_OK' "$log"
        test -s run-archery/archery-probe/01-engaged.png
        test -s run-archery/archery-probe/02-resolved.png
        grep 'SAO_ARCHERY_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
        grep -E 'SAO_|Exception|Caused by:|FATAL|ERROR|Description:' "$log" | tail -n 100 || true
        find run-archery/crash-reports -name '*.txt' -print -exec head -n 90 {} \; 2>/dev/null || true
        tail -n 100 "$log"; exit 1
    fi
    sleep 2
done
grep -E 'SAO_|Exception|Caused by:|FATAL|ERROR|Description:' "$log" | tail -n 100 || true
find run-archery/crash-reports -name '*.txt' -print -exec head -n 90 {} \; 2>/dev/null || true
tail -n 100 "$log"
exit 1
