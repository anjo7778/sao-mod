package dev.anjo7778.sao.simulation;

/** Shared tick-driven one-handed sword actions. No world queries or client authority. */
public final class SwordActions {
    public static final int MAX_STAMINA=100, ATTACK_COST=12, GUARD_COST=8, DODGE_COST=24;
    public enum Phase { IDLE, WINDUP, RECOVERY, GUARD, DODGE }
    public enum Defense { NONE, BLOCK, PARRY, EVADE }
    private int stamina=MAX_STAMINA;
    private Phase phase=Phase.IDLE;
    private long last=-1,start,activeAt,end,counterUntil=-1;
    public int stamina(){return stamina;}
    public Phase phase(){return phase;}
    public void advance(long now){
        if(now<0||now<last)throw new IllegalArgumentException("Non-monotonic combat clock");
        if(phase==Phase.WINDUP&&now>=activeAt+2)phase=Phase.RECOVERY;
        if((phase==Phase.GUARD||phase==Phase.DODGE)&&now>=activeAt)phase=Phase.RECOVERY;
        if(phase!=Phase.IDLE&&now>=end)phase=Phase.IDLE;
        if(now>last&&phase==Phase.IDLE&&last>=0)stamina=Math.min(MAX_STAMINA,stamina+2);
        last=now;
    }
    public boolean attack(long now){
        advance(now);
        boolean counter=now<counterUntil;
        int cost=counter?8:ATTACK_COST;
        if(phase!=Phase.IDLE||stamina<cost)return false;
        stamina-=cost;phase=Phase.WINDUP;start=now;activeAt=now+(counter?2:4);end=activeAt+10;counterUntil=-1;
        return true;
    }
    /** The hit may be claimed exactly once, in the two-tick active window. */
    public boolean claimHit(long now){
        advance(now);
        if(phase!=Phase.WINDUP||now<activeAt||now>=activeAt+2)return false;
        phase=Phase.RECOVERY;return true;
    }
    public boolean guard(long now){
        advance(now);
        if(phase!=Phase.IDLE||stamina<GUARD_COST)return false;
        stamina-=GUARD_COST;phase=Phase.GUARD;start=now;activeAt=now+8;end=activeAt+6;
        return true;
    }
    public boolean dodge(long now){
        advance(now);
        if(phase!=Phase.IDLE||stamina<DODGE_COST)return false;
        stamina-=DODGE_COST;phase=Phase.DODGE;start=now;activeAt=now+5;end=activeAt+13;
        return true;
    }
    /** Caller validates source type, direction, equipment and real dodge displacement. */
    public Defense defend(long now,boolean frontal,boolean moved){
        advance(now);
        if(phase==Phase.DODGE&&moved){phase=Phase.RECOVERY;return Defense.EVADE;}
        if(phase!=Phase.GUARD||!frontal)return Defense.NONE;
        if(now-start<3){
            phase=Phase.IDLE;end=now;counterUntil=now+12;return Defense.PARRY;
        }
        if(stamina<8){phase=Phase.RECOVERY;return Defense.NONE;}
        stamina-=8;phase=Phase.RECOVERY;return Defense.BLOCK;
    }
    /** Cancel an action without refunding its cost or resetting the recovery clock. */
    public void cancel(long now){
        advance(now);
        if(phase!=Phase.IDLE){phase=Phase.RECOVERY;end=Math.max(end,now+6);}
        counterUntil=-1;
    }
}
