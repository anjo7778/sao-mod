#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-regional build/regional-probe
test ! -e run-regional/saves/sao-regional-test
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' >run-regional/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/regional-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runRegionalProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+540))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_REGIONAL_PROBE_PASS' "$log"; then
        for kind in MARKET_TOWN LODGING_VILLAGE CRAFTS_VILLAGE EXPEDITION_REFUGE; do
            grep -q "SAO_REGIONAL_SITE_OK floor=1 kind=$kind " "$log"
        done
        grep -q 'SAO_REGIONAL_SITE_OK floor=2 kind=MARKET_TOWN ' "$log"
        test "$(find run-regional/regional-probe -name '*.png' | wc -l)" -eq 10
        grep 'SAO_REGIONAL_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then tail -n 180 "$log"; exit 1; fi
    sleep 2
done
tail -n 180 "$log"
exit 1
