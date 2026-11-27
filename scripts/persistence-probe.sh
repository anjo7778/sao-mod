#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dir=run-persistence
world="$dir/mts-persistence-test"
out=build/persistence-probe
if [[ -e "$world" ]]; then echo "Refusing to reuse an old test save" >&2; exit 1; fi
mkdir -p "$dir" "$out"
printf '%s\n' 'eula=true' > "$dir/eula.txt"
cat > "$dir/server.properties" <<'PROPERTIES'
level-name=mts-persistence-test
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
motd=Isolated SAO persistence test
PROPERTIES
bash ./gradlew --no-daemon --no-configuration-cache -PpersistencePhase=write runPersistenceProbe 2>&1 | tee "$out/write.log"
grep -q 'SAO_PERSISTENCE_WRITE_OK floor=2' "$out/write.log"
test -s "$world/data/sao_world.dat"
test -s "$world/dimensions/sao/upper_floors/region/r.0.0.mca"
bash ./gradlew --no-daemon --no-configuration-cache -PpersistencePhase=read runPersistenceProbe 2>&1 | tee "$out/read.log"
grep -q 'SAO_PERSISTENCE_READ_OK floor=2' "$out/read.log"
grep -h 'SAO_PERSISTENCE_' "$out/write.log" "$out/read.log" | tee "$out/result.txt"
