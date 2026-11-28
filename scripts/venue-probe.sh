#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-venue build/venue-probe
if [[ -e run-venue/saves/sao-venue-test ]]; then echo "Refusing old test save"; exit 1; fi
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' >run-venue/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/venue-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runVenueProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+450))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_VENUE_PROBE_PASS' "$log"; then
        grep -q 'SAO_VENUE_ARRIVAL_OK' "$log"
        grep -q 'SAO_VENUE_CREDIT_OK' "$log"
        test -s run-venue/venue-probe/01-adventurer-at-training-yard.png
        test -s run-venue/venue-probe/02-practice-credit.png
        grep 'SAO_VENUE_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then tail -n 180 "$log"; exit 1; fi
    sleep 2
done
tail -n 180 "$log"
exit 1
