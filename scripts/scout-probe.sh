#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-scout build/scout-probe
if [[ -e run-scout/saves/sao-scout-test ]]; then echo "Refusing old test save"; exit 1; fi
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-scout/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/scout-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runScoutProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+450))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_SCOUT_PROBE_PASS' "$log"; then
        grep -q 'SAO_SCOUT_CROSSED_GATE' "$log"
        grep -q 'SAO_SCOUT_SURVEY_OK' "$log"
        grep -q 'SAO_SCOUT_RETURN_OK' "$log"
        test -s run-scout/scout-probe/01-outside-gate.png
        test -s run-scout/scout-probe/02-returned-to-capital.png
        grep 'SAO_SCOUT_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then tail -n 150 "$log"; exit 1; fi
    sleep 2
done
tail -n 150 "$log"
exit 1
