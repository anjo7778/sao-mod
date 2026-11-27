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
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:6' 'simulationDistance:5' 'maxFps:30' > run-visual/options.txt

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

deadline=$((SECONDS + 540))
while (( SECONDS < deadline )); do
    if grep -q 'SAO_VISUAL_PROBE_WORLD_READY' "$log"; then
        sleep 3
        kill -0 "$pid"
        timeout 15s import -window root "$out/floor-one-spawn.png"
        identify "$out/floor-one-spawn.png"
        grep 'SAO_VISUAL_PROBE_' "$log" | tee "$out/result.txt"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
        echo 'Minecraft client exited before world-ready marker.' >&2
        exit 1
    fi
    sleep 2
done
echo 'Timed out before the Aincrad world was ready.' >&2
exit 1
