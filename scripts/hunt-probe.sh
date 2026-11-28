#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-hunt build/hunt-probe
if [[ -e run-hunt/saves/sao-hunt-test ]]; then echo "Refusing old test save"; exit 1; fi
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-hunt/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/hunt-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runHuntProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+450))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_HUNT_PROBE_PASS' "$log"; then
        grep -q 'SAO_HUNT_CONTRACT_OK' "$log"
        grep -q 'SAO_HUNT_INJURY_OK' "$log"
        grep -q 'SAO_HUNT_KILL_OK' "$log"
        test -s run-hunt/hunt-probe/01-engaged.png
        test -s run-hunt/hunt-probe/02-resolved.png
        grep 'SAO_HUNT_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then tail -n 150 "$log"; exit 1; fi
    sleep 2
done
tail -n 150 "$log"
exit 1
