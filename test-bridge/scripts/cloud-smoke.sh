#!/usr/bin/env bash
set -euo pipefail
scenario="${1:?scenario required}"
case "$scenario" in terrain|animation|benchmark) ;; *) exit 2;; esac
cd "$(dirname "$0")/.."
mkdir -p run-client/mts-bridge build/smoke
out="$PWD/build/smoke"
printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'pauseOnLostFocus:false' 'renderDistance:6' 'simulationDistance:5' 'maxFps:30' 'tutorialStep:none' >run-client/options.txt
id=$(python3 - "$scenario" <<'PY'
import json,time,uuid,sys,pathlib
s=sys.argv[1];i=uuid.uuid4().hex
pathlib.Path('run-client/mts-bridge/request.json').write_text(json.dumps(dict(protocol=1,id=i,scenario=s,world='flat' if s=='animation' else 'normal',seed=42,interval_ticks=20,samples=3,issued_at=int(time.time()))))
print(i)
PY
)
export LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 ALSOFT_DRIVERS=null
setsid bash ../gradlew --no-daemon --no-configuration-cache -p . runClient >"$out/client.log" 2>&1 &
pid=$!
trap 'kill -TERM -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true' EXIT
result="run-client/mts-bridge/$id/result.json"
deadline=$((SECONDS+420))
while [[ ! -f "$result" ]]; do
  if ! kill -0 "$pid" 2>/dev/null || (( SECONDS>=deadline )); then tail -n 80 "$out/client.log"; exit 1; fi
  sleep 2
done
python3 - "$result" "$scenario" <<'PY'
import json,sys,pathlib,math
p=pathlib.Path(sys.argv[1]);r=json.loads(p.read_text());print(json.dumps(r,indent=2))
assert r['status']=='passed',r
expected={'terrain':6,'animation':24,'benchmark':3}[sys.argv[2]]
assert len(r['captures'])==len(r['samples'])==expected
for f,s in zip(r['captures'],r['samples']):
    assert (p.parent/f).read_bytes()[:8]==b'\x89PNG\r\n\x1a\n'
    assert s['frame']==f and math.isfinite(s['mspt']) and s['heap_used_mib']>0
if sys.argv[2]=='terrain': assert len({round(s['x']) for s in r['samples']})>=3
if sys.argv[2]=='animation': assert len({round(s['yaw']) for s in r['samples']})>=2
assert r['visual_verified'] is False
PY
cp -r "run-client/mts-bridge/$id" "$out/results"
