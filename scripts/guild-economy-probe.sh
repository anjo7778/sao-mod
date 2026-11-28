#!/usr/bin/env bash
set -euo pipefail
dir=run-guild-economy
out=build/guild-economy-probe
mkdir -p "$dir" "$out"
test ! -e "$dir/sao-guild-economy-test"
printf 'eula=true\n' > "$dir/eula.txt"
cat > "$dir/server.properties" <<'EOF'
level-name=sao-guild-economy-test
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
bash ./gradlew --no-daemon --no-configuration-cache -PguildEconomyPhase=write runGuildEconomyProbe 2>&1 | tee "$out/write.log"
grep -q 'SAO_GUILD_COMMISSION_OK' "$out/write.log"
grep -q 'SAO_GUILD_WRITE_OK' "$out/write.log"
bash ./gradlew --no-daemon --no-configuration-cache -PguildEconomyPhase=read runGuildEconomyProbe 2>&1 | tee "$out/read.log"
grep -q 'SAO_GUILD_READ_OK' "$out/read.log"
python3 - "$out" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1])
def get(name, marker):
    return next(s.split(marker+' ',1)[1] for s in (p/name).read_text().splitlines() if marker+' ' in s)
a=get('write.log','SAO_GUILD_WRITE_OK')
b=get('read.log','SAO_GUILD_READ_OK')
assert a==b,(a,b)
print('PASS: guild commission and paid wholesale restock, conserved currency, identical restart:',a)
PY
