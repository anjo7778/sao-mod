#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-templates build/template-probe
if [[ -e run-templates/saves/sao-template-test ]]; then echo "Refusing old test save"; exit 1; fi
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' >run-templates/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/template-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runTemplateProbe >"$log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+540))
while ((SECONDS<deadline)); do
    if grep -q 'SAO_TEMPLATE_CAPTURE_COMPLETE' "$log"; then
        grep -q 'SAO_TEMPLATE_PROBE_PASS' "$log"
        for style in COTTAGE TOWNHOUSE INN MARKET GUILDHALL FORGE CLINIC QUEST_HALL; do
            grep -q "SAO_TEMPLATE_MODEL_OK floor=1 style=$style " "$log"
        done
        grep -q 'SAO_TEMPLATE_UPPER_OK' "$log"
        grep -q 'SAO_DISTRICT_INFILL_OK floor=1' "$log"
        grep -q 'SAO_DISTRICT_INFILL_OK floor=2' "$log"
        grep -q 'SAO_DISTRICT_STREET_OK' "$log"
        for court in RESIDENTIAL_GARDEN MARKET_COURT GUILD_SQUARE TRAINING_YARD; do
            grep -q "SAO_COURT_OK floor=1 kind=$court " "$log"
        done
        grep -q 'SAO_COURT_OK floor=2' "$log"
        test -s run-templates/template-probe/inn-sleeping-alcove.png
        test "$(find run-templates/template-probe -name '*.png' | wc -l)" -eq 29
        grep -E 'SAO_(TEMPLATE|DISTRICT|COURT)_' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then tail -n 150 "$log"; exit 1; fi
    sleep 2
done
tail -n 150 "$log"
exit 1
