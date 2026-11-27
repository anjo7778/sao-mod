#!/usr/bin/env bash
set -euo pipefail

# Only invoked in an ephemeral GitHub-hosted runner.
mkdir -p build/visual-probe
out="$PWD/build/visual-probe"
log="$out/launcher.log"
export LIBGL_ALWAYS_SOFTWARE=1
export MESA_GL_VERSION_OVERRIDE=4.5
export ALSOFT_DRIVERS=null

# The first-run accessibility wizard otherwise blocks the title-screen trigger.
mkdir -p run-visual
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:6' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-visual/options.txt
if [[ -e run-visual/saves/sao-visual-test ]]; then
    echo 'Refusing to reuse a previous visual-test save.' >&2
    exit 1
fi
rm -f run-visual/probe-first-captured run-visual/probe-upper-captured run-visual/probe-castle-captured run-visual/probe-guardian-captured

# Capture only the isolated virtual display. Do not use a real desktop or user account.
setsid bash ./gradlew --no-daemon --no-configuration-cache runVisualProbe >"$log" 2>&1 &
pid=$!
cleanup() {
    local status=$?
    if (( status != 0 )); then
        timeout 10s import -window root "$out/failure-display.png" 2>/dev/null || true
        tail -n 100 "$log" || true
    fi
    kill -TERM -- "-$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
}
trap cleanup EXIT

capture() {
    sleep 2
    kill -0 "$pid"
    timeout 15s import -window root "$out/$1.png"
    identify "$out/$1.png"
}

deadline=$((SECONDS + 540))
while (( SECONDS < deadline )); do
    if [[ ! -e run-visual/probe-first-captured ]] && grep -q 'SAO_VISUAL_PROBE_FIRST_READY' "$log"; then
        capture floor-one-spawn
        touch run-visual/probe-first-captured
    fi
    if [[ ! -e run-visual/probe-castle-captured ]] && grep -q 'SAO_VISUAL_PROBE_CASTLE_READY' "$log"; then
        capture floor-one-castle
        touch run-visual/probe-castle-captured
    fi
    if [[ ! -e run-visual/probe-guardian-captured ]] && grep -q 'SAO_VISUAL_PROBE_GUARDIAN_READY' "$log"; then
        capture floor-one-guardian
        touch run-visual/probe-guardian-captured
    fi
    if [[ ! -e run-visual/probe-upper-captured ]] && grep -q 'SAO_VISUAL_PROBE_UPPER_READY' "$log"; then
        capture floor-two-arrival
        touch run-visual/probe-upper-captured
    fi
    if grep -q 'SAO_VISUAL_PROBE_RETURN_READY' "$log"; then
        grep -q 'SAO_VISUAL_PROBE_LOCKED_REJECTED' "$log"
        grep -q 'SAO_VISUAL_PROBE_REAL_GUARDIAN_DEATH_UNLOCKED' "$log"
        test -s "$out/floor-one-spawn.png"
        test -s "$out/floor-two-arrival.png"
        test -s "$out/floor-one-castle.png"
        test -s "$out/floor-one-guardian.png"
        grep -q 'SAO_VISUAL_PROBE_UPPER_BIOME' "$log"
        grep -q 'SAO_VISUAL_PROBE_ARRIVAL_DAYLIGHT_VERIFIED floor=2' "$log"
        grep -q 'SAO_VISUAL_PROBE_RETURN_DAYLIGHT_VERIFIED' "$log"
        grep -q 'SAO_VISUAL_PROBE_CASTLE_BLOCKS_VERIFIED' "$log"
        capture floor-one-return
        grep 'SAO_VISUAL_PROBE_' "$log" | tee "$out/result.txt"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
        echo 'Minecraft client exited before world-ready marker.' >&2
        exit 1
    fi
    sleep 2
done
echo 'Timed out before the complete Aincrad travel round trip.' >&2
exit 1
