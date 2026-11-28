#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-boar build/boar-probe
if [[ -e run-boar/saves/sao-boar-test ]]; then echo "Refusing old test save"; exit 1; fi
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-boar/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/boar-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runBoarProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+450))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_BOAR_PROBE_PASS' "$log"; then
        grep -q 'SAO_BOAR_SPAWN_OK' "$log"
        grep -q 'SAO_BOAR_RENDER_OK' "$log"
        grep -q 'SAO_BOAR_ATTACK_OK' "$log"
        grep -q 'SAO_BOAR_LOOT_OK' "$log"
        test -s run-boar/boar-probe/01-frenzy-boar.png
        test -s run-boar/boar-probe/02-boar-loot.png
        grep 'SAO_BOAR_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then tail -n 150 "$log"; exit 1; fi
    sleep 2
done
tail -n 150 "$log"
exit 1
