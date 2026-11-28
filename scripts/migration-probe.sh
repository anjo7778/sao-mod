#!/usr/bin/env bash
set -euo pipefail
dir=run-migration
out=build/migration-probe
mkdir -p "$dir" "$out"
test ! -e "$dir/mts-migration-test"
printf 'eula=true\n' > "$dir/eula.txt"
cat > "$dir/server.properties" <<'EOF'
level-name=mts-migration-test
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
bash ./gradlew --no-daemon --no-configuration-cache -PmigrationPhase=write runMigrationProbe 2>&1 | tee "$out/write.log"
grep -q 'SAO_MIGRATION_LOCKED_OK' "$out/write.log"
grep -q 'SAO_MIGRATION_WRITE_OK' "$out/write.log"
test -d "$dir/mts-migration-test/region"
bash ./gradlew --no-daemon --no-configuration-cache -PmigrationPhase=read runMigrationProbe 2>&1 | tee "$out/read.log"
grep -q 'SAO_MIGRATION_READ_OK' "$out/read.log"
python3 - "$out" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1])
def get(name, marker):
    return next(s.split(marker+' ',1)[1] for s in (p/name).read_text().splitlines() if marker+' ' in s)
a=get('write.log','SAO_MIGRATION_WRITE_OK')
b=get('read.log','SAO_MIGRATION_READ_OK')
assert a==b,(a,b)
print('PASS: real autonomous transfer, no locked-floor bypass or duplicate, identical restart:',a)
PY
