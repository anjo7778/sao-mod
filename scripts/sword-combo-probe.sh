#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-sword-combo build/sword-combo-probe
test ! -e run-sword-combo/saves/sao-sword-combo-test
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-sword-combo/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/sword-combo-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runSwordComboProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+540))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_SWORD_COMBO_PROBE_PASS' "$log"; then
        for mark in LOCKED_OK CHAIN_OK HORIZONTAL_OK COOLDOWN_OK SLANT_OK VERTICAL_OK LEAP_OK POSE_OK HORIZONTAL_SQUARE_OK VERTICAL_SQUARE_OK HORIZONTAL_SQUARE_POSE_OK VERTICAL_SQUARE_POSE_OK SQUARE_INTERRUPT_OK; do grep -q "SAO_SWORD_$mark" "$log"; done
        grep 'SAO_SWORD_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 1
done
grep -E 'SAO_|Exception|Caused by:|FATAL|ERROR|Description:' "$log" | tail -n 100 || true
tail -n 100 "$log"
exit 1
