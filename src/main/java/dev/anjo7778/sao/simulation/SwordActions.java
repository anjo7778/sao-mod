package dev.anjo7778.sao.simulation;

/** Shared tick-driven one-handed sword actions. No world queries or client authority. */
public final class SwordActions {
    public static final int MAX_STAMINA=100, ATTACK_COST=12, GUARD_COST=8, DODGE_COST=24;
    public static final int HORIZONTAL_MASTERY=3, HORIZONTAL_COOLDOWN=100;
    public static final int MAX_SKILL_COOLDOWN=160, GLOBAL_SKILL_DELAY=30;
    /** Mastery unlocks are independent of character level. Single-target skills only. */
    public enum Skill {
        HORIZONTAL(Move.HORIZONTAL,3,100),
        SLANT(Move.SLANT,4,90),
        VERTICAL(Move.VERTICAL,5,120),
        SONIC_LEAP(Move.SONIC_LEAP,7,160);
        public final Move move;
        public final int mastery,cooldown;
        Skill(Move move,int mastery,int cooldown){this.move=move;this.mastery=mastery;this.cooldown=cooldown;}
        public boolean unlocked(long level){return level>=mastery;}
        public static Skill fromIndex(int index){return index>=0&&index<values().length?values()[index]:null;}
    }
    public enum Phase { IDLE, WINDUP, RECOVERY, GUARD, DODGE }
    public enum Defense { NONE, BLOCK, PARRY, EVADE }
    public enum Move {
        BASIC(12,4,10,1.0), FOLLOW_UP(14,4,10,1.10),
        FINISHER(18,5,13,1.25), COUNTER(8,2,10,1.0), HORIZONTAL(26,7,16,1.60), SLANT(20,5,13,1.40), VERTICAL(32,10,18,1.85), SONIC_LEAP(34,9,20,1.55);
        public final int cost,windup,recovery;
        public final double damage;
        Move(int cost,int windup,int recovery,double damage){
            this.cost=cost;this.windup=windup;this.recovery=recovery;this.damage=damage;
        }
    }
    private int stamina=MAX_STAMINA,nextCombo;
    private Phase phase=Phase.IDLE;
    private Move move=Move.BASIC;
    private SwordPose.Kind pose;
    private long last=-1,start,activeAt,end,counterUntil=-1,comboUntil=-1,globalSkillReady,claimTick=-1;
    private final long[] skillReady=new long[Skill.values().length];
    public int stamina(){return stamina;}
    public Phase phase(){return phase;}
    public Move move(){return move;}
    public int visualCode(long clockNow,long levelTime){
        if(pose==null||phase==Phase.IDLE||clockNow<start||clockNow>=end)return 0;
        return SwordPose.encode(pose,levelTime-(clockNow-start));
    }
    /** Number of successfully chained ordinary strikes, not client input count. */
    public int combo(){return nextCombo;}
    public int skillCooldown(long now){return skillCooldown(Skill.HORIZONTAL,now);}
    public int skillCooldown(Skill skill,long now){
        return (int)Math.max(0,Math.min(MAX_SKILL_COOLDOWN,Math.max(globalSkillReady,skillReady[skill.ordinal()])-now));
    }
    public void advance(long now){
        if(now<0||now<last)throw new IllegalArgumentException("Non-monotonic combat clock");
        if(claimTick>=0&&now>claimTick){claimTick=-1;breakCombo();}
        if(nextCombo!=0&&now>=comboUntil)breakCombo();
        if(phase==Phase.WINDUP&&now>=activeAt+2){phase=Phase.RECOVERY;breakCombo();}
        if((phase==Phase.GUARD||phase==Phase.DODGE)&&now>=activeAt)phase=Phase.RECOVERY;
        if(phase!=Phase.IDLE&&now>=end){phase=Phase.IDLE;pose=null;}
        if(now>last&&phase==Phase.IDLE&&last>=0)stamina=Math.min(MAX_STAMINA,stamina+2);
        last=now;
    }
    public boolean attack(long now){
        advance(now);
        Move selected=now<counterUntil?Move.COUNTER:nextCombo==1?Move.FOLLOW_UP:nextCombo==2?Move.FINISHER:Move.BASIC;
        return begin(now,selected);
    }
    public boolean horizontal(long now,long swordMastery){
        return skill(now,swordMastery,Skill.HORIZONTAL);
    }
    public boolean skill(long now,long swordMastery,Skill skill){
        advance(now);
        if(skill==null||!skill.unlocked(swordMastery)||skillCooldown(skill,now)>0||!begin(now,skill.move))return false;
        skillReady[skill.ordinal()]=now+skill.cooldown;
        globalSkillReady=now+GLOBAL_SKILL_DELAY;return true;
    }
    private boolean begin(long now,Move selected){
        if(phase!=Phase.IDLE||stamina<selected.cost)return false;
        stamina-=selected.cost;move=selected;pose=SwordPose.Kind.valueOf(selected.name());phase=Phase.WINDUP;start=now;
        activeAt=now+selected.windup;end=activeAt+selected.recovery;
        counterUntil=-1;claimTick=-1;breakCombo();return true;
    }
    /** The hit may be claimed exactly once, in the two-tick active window. */
    public boolean claimHit(long now){
        advance(now);
        if(phase!=Phase.WINDUP||now<activeAt||now>=activeAt+2)return false;
        phase=Phase.RECOVERY;claimTick=now;return true;
    }
    /** Only the authoritative damage resolver may confirm a claimed hit, on that same tick. */
    public boolean confirmHit(long now,boolean landed){
        advance(now);
        if(claimTick!=now)return false;
        claimTick=-1;
        nextCombo=landed?(move==Move.BASIC?1:move==Move.FOLLOW_UP?2:0):0;
        comboUntil=nextCombo>0?end+12:-1;
        return true;
    }
    public void breakCombo(){nextCombo=0;comboUntil=-1;}
    public boolean guard(long now){
        advance(now);
        if(phase!=Phase.IDLE||stamina<GUARD_COST)return false;
        stamina-=GUARD_COST;pose=SwordPose.Kind.GUARD;phase=Phase.GUARD;start=now;activeAt=now+8;end=activeAt+6;
        counterUntil=-1;breakCombo();return true;
    }
    public boolean dodge(long now){
        advance(now);
        if(phase!=Phase.IDLE||stamina<DODGE_COST)return false;
        stamina-=DODGE_COST;pose=SwordPose.Kind.DODGE;phase=Phase.DODGE;start=now;activeAt=now+5;end=activeAt+13;
        counterUntil=-1;breakCombo();return true;
    }
    /** Caller validates source type, direction, equipment and real dodge displacement. */
    public Defense defend(long now,boolean frontal,boolean moved){
        advance(now);
        if(phase==Phase.DODGE&&moved){phase=Phase.RECOVERY;return Defense.EVADE;}
        if(phase!=Phase.GUARD||!frontal)return Defense.NONE;
        if(now-start<3){
            phase=Phase.IDLE;pose=null;end=now;counterUntil=now+12;return Defense.PARRY;
        }
        if(stamina<8){phase=Phase.RECOVERY;return Defense.NONE;}
        stamina-=8;phase=Phase.RECOVERY;return Defense.BLOCK;
    }
    /** Cancel without refunding the cost or resetting recovery or skill cooldowns. */
    public void cancel(long now){
        advance(now);
        if(phase!=Phase.IDLE){phase=Phase.RECOVERY;end=Math.max(end,now+6);}
        claimTick=-1;counterUntil=-1;pose=null;breakCombo();
    }
}
