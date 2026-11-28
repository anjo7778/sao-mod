#!/usr/bin/env bash
set -euo pipefail
dir=run-regional-journey
out=build/regional-journey-probe
mkdir -p "$dir" "$out"
test ! -e "$dir/sao-regional-journey-test"
printf 'eula=true\n' > "$dir/eula.txt"
cat > "$dir/server.properties" <<'EOF'
level-name=sao-regional-journey-test
level-type=sao:aincrad
level-seed=42
server-ip=127.0.0.1
view-distance=4
simulation-distance=4
difficulty=peaceful
online-mode=true
enable-rcon=false
enable-query=false
EOF
bash ./gradlew --no-daemon --no-configuration-cache -PjourneyPhase=write runRegionalJourneyProbe 2>&1 | tee "$out/write.log"
for phase in OUTBOUND VISITING RETURNING IDLE; do
    grep -q "SAO_JOURNEY_PHASE $phase " "$out/write.log"
done
grep -q 'SAO_JOURNEY_WRITE_OK' "$out/write.log"
bash ./gradlew --no-daemon --no-configuration-cache -PjourneyPhase=read runRegionalJourneyProbe 2>&1 | tee "$out/read.log"
grep -q 'SAO_JOURNEY_READ_OK' "$out/read.log"
python3 - "$out" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1])
def get(name, marker):
    return next(s.split(marker+' ',1)[1] for s in (p/name).read_text().splitlines() if marker+' ' in s)
a=get('write.log','SAO_JOURNEY_WRITE_OK')
b=get('read.log','SAO_JOURNEY_READ_OK')
assert a==b,(a,b)
print('PASS: independent regional journey, exactly one supply debit, no forced chunks or rewards, identical restart:',a)
PY
