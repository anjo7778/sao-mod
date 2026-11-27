#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-city build/city-probe
if [[ -e run-city/saves/sao-city-test ]]; then echo "Refusing old test save"; exit 1; fi
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' >run-city/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/city-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runCityProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+600))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_CITY_PROBE_PASS' "$log"; then
        grep -q 'SAO_CITY_BLOCKS_OK floor=1' "$log"
        grep -q 'SAO_CITY_BLOCKS_OK floor=2' "$log"
        grep -q 'SAO_CITY_SAFE_ZONE_OK' "$log"
        grep -q 'SAO_CITY_GUARDIAN_AND_UPPER_OK' "$log"
        test "$(find run-city/city-probe -name '*.png' | wc -l)" -eq 4
        grep 'SAO_CITY_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then tail -n 130 "$log"; exit 1; fi
    sleep 2
done
tail -n 130 "$log"
exit 1
