#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-material-probe build/material-probe
test ! -e run-material-probe/saves/sao-material-test
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:6' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-material-probe/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/material-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runMaterialProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+540))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_MATERIAL_PROBE_PASS' "$log"; then
        for mark in FLOOR_ONE_OK CAPITAL_OK FLOOR_RANGE_OK ADVANCED_ORE_OK HARVEST_OK SILK_OK MINING_XP_OK METALLURGY_XP_OK SWORD_CRAFT_OK; do grep -q "SAO_MATERIAL_$mark" "$log"; done
        grep 'SAO_MATERIAL_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 1
done
grep -E 'SAO_|Exception|Caused by:|FATAL|ERROR|Description:' "$log" | tail -n 100 || true
tail -n 100 "$log"
exit 1
