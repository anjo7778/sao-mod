#!/usr/bin/env python3
"""Regenerate original Aincrad menu audio; no sampled source audio.
Run from repository root with Python 3 and ffmpeg/libvorbis. Runtime uses checked-in OGGs.
"""
import math, wave, struct, subprocess
from pathlib import Path
RATE=22050
OUT=Path(__file__).resolve().parent.parent/"src/main/resources/assets/sao/sounds/ui"
OUT.mkdir(parents=True,exist_ok=True)

def ping(t,start,hz,duration,level,glide=0):
    u=t-start
    if u<0 or u>=duration:return 0
    phase=2*math.pi*(hz*u+glide*u*u/2)
    env=min(1,u/.006)*math.exp(-u/(duration*.27))*min(1,(duration-u)/.018)
    return level*env*(math.sin(phase)+.20*math.sin(phase*2.03)+.07*math.sin(phase*3.4))

def air(t,start,duration,level,f0,f1):
    u=t-start
    if u<0 or u>=duration:return 0
    phase=2*math.pi*(f0*u+(f1-f0)*u*u/(2*duration))
    env=math.sin(math.pi*u/duration)**2
    return level*env*(math.sin(phase)*.75+.25*math.sin(1.5*phase))

SOUNDS={
    'menu_open':(.30,[(.018,710,.19,.34,400),(.064,1160,.22,.22,800)],[(0,.19,.07,400,1850)]),
    'menu_select':(.115,[(0,1330,.10,.34,-1000),(.021,1780,.075,.09,-500)],[]),
    'menu_confirm':(.25,[(0,840,.18,.28,0),(.044,1260,.19,.25,260)],[(.03,.13,.04,1050,1780)]),
    'menu_close':(.24,[(0,1180,.15,.26,-2100),(.044,735,.17,.21,-750)],[(0,.18,.065,1550,320)]),
    'level_up':(.40,[(0,670,.17,.24,200),(.065,1005,.20,.23,160),(.132,1340,.25,.23,120)],[(.06,.26,.06,520,1800)])
}

def generate(name,spec):
    duration,pings,airs=spec
    samples=[]
    for i in range(int(duration*RATE)):
        t=i/RATE
        sample=sum(ping(t,*p) for p in pings)+sum(air(t,*a) for a in airs)
        sample*=min(1,i/80,(duration-t)/.012)
        samples.append(max(-32767,min(32767,int(sample*32767))))
    wav=OUT/(name+'.wav')
    with wave.open(str(wav),'wb') as f:
        f.setparams((1,2,RATE,0,'NONE','not compressed'))
        f.writeframes(struct.pack('<%dh'%len(samples),*samples))
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-map_metadata','-1','-c:a','libvorbis','-q:a','2',str(OUT/(name+'.ogg'))],check=True)
    wav.unlink()

if __name__=='__main__':
    for name,spec in SOUNDS.items():generate(name,spec)
