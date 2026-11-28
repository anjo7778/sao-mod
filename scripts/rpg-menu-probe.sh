#!/usr/bin/env bash
set -euo pipefail
mkdir -p run-rpg-menu build/rpg-menu-probe
test ! -e run-rpg-menu/saves/sao-rpg-menu-test
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:8' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' > run-rpg-menu/options.txt
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
log=build/rpg-menu-probe/client.log
setsid bash ./gradlew --no-daemon --no-configuration-cache runRpgMenuProbe >"$log" 2>&1 &
pid=$!
trap 'xdotool mouseup 1 mouseup 3 2>/dev/null || true; kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
deadline=$((SECONDS+540))
disabled_sent=0
key_sent=0
gesture_sent=0
focus_game(){
    local win
    win=$(xdotool search --onlyvisible --name 'Minecraft' | tail -n 1)
    test -n "$win"
    xdotool windowfocus --sync "$win"
}
while ((SECONDS<deadline)); do
    if ((disabled_sent==0)) && grep -q 'SAO_MENU_DISABLED_CHORD_READY' "$log"; then
        focus_game
        xdotool mousedown 1 mousedown 3
        sleep .35
        xdotool mousemove_relative -- 0 160
        sleep .5
        xdotool mouseup 1 mouseup 3
        disabled_sent=1
    fi
    if ((key_sent==0)) && grep -q 'SAO_MENU_KEY_READY' "$log"; then
        focus_game
        xdotool key m
        key_sent=1
    fi
    if ((gesture_sent==0)) && grep -q 'SAO_MENU_GESTURE_READY' "$log"; then
        focus_game
        xdotool mousedown 1 mousedown 3
        sleep .35
        xdotool mousemove_relative -- 0 160
        sleep .5
        xdotool mouseup 1 mouseup 3
        gesture_sent=1
    fi
    if grep -q 'SAO_RPG_MENU_PROBE_PASS' "$log"; then
        for mark in XP_OK DEFAULT_OK KEY_OK ALLOCATION_OK SETTINGS_OK ARM_OK WORLD_OK WORLD_PIXELS_OK HUD_OK GESTURE_OK; do grep -q "SAO_MENU_$mark" "$log"; done
        test "$(find run-rpg-menu/rpg-menu-probe -name '*.png' -size +0c | wc -l)" -eq 10
        grep 'SAO_MENU_\|SAO_RPG_MENU_PROBE_PASS' "$log"
        exit 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 1
done
grep -E 'SAO_|Exception|Caused by:|FATAL|ERROR|Description:' "$log" | tail -n 100 || true
find run-rpg-menu/crash-reports -name '*.txt' -print -exec head -n 90 {} \; 2>/dev/null || true
tail -n 100 "$log"
exit 1
