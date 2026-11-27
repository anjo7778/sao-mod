#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dir=run-adventure
out=build/adventure-probe
world="$dir/mts-adventure-test"
if [[ -e "$world" ]]; then echo "Refusing old test save"; exit 1; fi
mkdir -p "$dir" "$out"
printf '%s\n' eula=true > "$dir/eula.txt"
cat > "$dir/server.properties" <<'PROPERTIES'
level-name=mts-adventure-test
level-type=sao:aincrad
level-seed=42
server-ip=127.0.0.1
server-port=25565
view-distance=4
simulation-distance=4
difficulty=peaceful
online-mode=true
enable-rcon=false
enable-query=false
PROPERTIES
bash ./gradlew --no-daemon --no-configuration-cache -PadventurePhase=write runAdventureProbe 2>&1 | tee "$out/write.log"
grep -q 'SAO_ADVENTURE_SAVE_OK' "$out/write.log"
test -s "$world/data/sao_adventures.dat"
test -s "$world/data/sao_population.dat"
bash ./gradlew --no-daemon --no-configuration-cache -PadventurePhase=read runAdventureProbe 2>&1 | tee "$out/read.log"
grep -q 'SAO_ADVENTURE_READ_OK' "$out/read.log"
python3 - "$out" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1])
def line(name,marker):
    return next(s.split(marker+' ',1)[1] for s in (p/name).read_text().splitlines() if marker+' ' in s)
a=line('write.log','SAO_ADVENTURE_SAVE_OK')
b=line('read.log','SAO_ADVENTURE_READ_OK')
assert a==b,(a,b)
print('PASS: independent two-floor scouting, supply costs and actual restart preserved identical progress:',a)
PY
