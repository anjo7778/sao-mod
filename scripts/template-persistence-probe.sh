#!/usr/bin/env bash
set -euo pipefail
dir=run-template-persistence
out=build/template-persistence
world="$dir/mts-template-test"
if [[ -e "$world" ]]; then echo "Refusing old test save"; exit 1; fi
mkdir -p "$dir" "$out"
printf '%s\n' eula=true > "$dir/eula.txt"
cat > "$dir/server.properties" <<'PROPERTIES'
level-name=mts-template-test
level-type=sao:aincrad
level-seed=42
server-ip=127.0.0.1
view-distance=4
simulation-distance=4
difficulty=peaceful
online-mode=true
enable-rcon=false
enable-query=false
PROPERTIES
bash ./gradlew --no-daemon --no-configuration-cache -PtemplatePhase=write runTemplatePersistence 2>&1 | tee "$out/write.log"
grep -q 'SAO_TEMPLATE_RESTART_WRITE' "$out/write.log"
test -d "$world/region"
bash ./gradlew --no-daemon --no-configuration-cache -PtemplatePhase=read runTemplatePersistence 2>&1 | tee "$out/read.log"
grep -q 'SAO_TEMPLATE_RESTART_READ' "$out/read.log"
python3 - "$out" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1])
def line(name,marker):
    return next(s.split(marker+' ',1)[1] for s in (p/name).read_text().splitlines() if marker+' ' in s)
a=line('write.log','SAO_TEMPLATE_RESTART_WRITE')
b=line('read.log','SAO_TEMPLATE_RESTART_READ')
assert a==b,(a,b)
print('PASS: real saved template states on two floors, reverse-order chunk loads and preserved edit:',a)
PY
