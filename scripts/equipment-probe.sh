#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-equipment-probe build/equipment-probe
test ! -e run-equipment-probe/saves/sao-equipment-test
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:6' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-equipment-probe/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/equipment-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runEquipmentProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+540))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_EQUIPMENT_PROBE_PASS' "$log"; then
        for mark in TOOL_STOCK_OK TOOL_BUY_OK BLADE_STOCK_OK BLADE_BUY_OK SAVE_OK; do grep -q "SAO_EQUIPMENT_$mark" "$log"; done
        grep 'SAO_EQUIPMENT_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 1
done
grep -E 'SAO_|Exception|Caused by:|FATAL|ERROR|Description:' "$log" | tail -n 100 || true
tail -n 100 "$log"
exit 1
