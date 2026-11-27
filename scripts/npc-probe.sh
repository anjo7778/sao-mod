#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-npc build/npc-probe
if [[ -e run-npc/saves/sao-npc-test ]]; then echo "Refusing old test save"; exit 1; fi
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-npc/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/npc-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runNpcProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+420))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_NPC_PROBE_PASS' "$log"; then
        grep -q 'SAO_NPC_PRESENCE_OK' "$log"
        grep -q 'SAO_NPC_GREETING_OK' "$log"
        grep -q 'SAO_NPC_NIGHT_SCHEDULE_OK' "$log"
        test -s run-npc/npc-probe/01-plaza-npcs.png
        grep 'SAO_NPC_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then tail -n 130 "$log"; exit 1; fi
    sleep 2
done
tail -n 130 "$log"
exit 1
