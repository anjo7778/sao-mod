package dev.anjo7778.sao.simulation;

import dev.anjo7778.sao.simulation.NpcRoster.*;
import dev.anjo7778.sao.simulation.AdventureProgress.Ledger;

/** Long-lived, bounded personal objectives, separate from quest rewards or combat levels.
 * Credit only existing authoritative practice, completed scouting trips and accepted conversations. */
public final class AdventurerGoals {
    public enum Kind { NONE, PRACTICE, SCOUT, CONNECT }
    public record Goal(Kind kind,int progress,int target,long deadline,long nextPick,int completed,int expired){
        public Goal{
            if(kind==null||progress<0||target<0||progress>target||target>32||deadline<0||nextPick<0
                ||completed<0||expired<0||kind==Kind.NONE&&(target!=0||progress!=0)
                ||kind!=Kind.NONE&&(target==0||progress>=target))
                throw new IllegalArgumentException("Invalid personal goal");
        }
        public boolean active(){return kind!=Kind.NONE;}
    }
    public static final Goal EMPTY=new Goal(Kind.NONE,0,0,0,0,0,0);
    private AdventurerGoals(){}
    public static boolean canScout(State s){
        var n=s.needs();return n.supplies()>=35&&n.equipment()>=40&&n.health()>=70;
    }
    public static Goal choose(State state,Goal old,long epoch){
        if(old.active()||epoch<old.nextPick()||state.profile().occupation()!=Occupation.ADVENTURER)return old;
        var p=state.profile();
        Kind kind=switch(p.ambition()){
            case MASTERY -> Kind.PRACTICE;
            case DISCOVERY -> canScout(state)?Kind.SCOUT:Kind.PRACTICE;
            case FRIENDSHIP,LEADERSHIP -> Kind.CONNECT;
            case WEALTH -> canScout(state)?Kind.SCOUT:Kind.PRACTICE;
            default -> Kind.PRACTICE;
        };
        // Vary the next objective after a successful cycle rather than repeating a single grind forever.
        if(old.completed()>0&&old.completed()%3==2)
            kind=kind==Kind.CONNECT?Kind.PRACTICE:Kind.CONNECT;
        int target=switch(kind){case PRACTICE->6+Math.floorMod(p.id().hashCode(),5);case SCOUT->1;case CONNECT->3;default->throw new IllegalStateException();};
        return new Goal(kind,0,target,epoch+100,epoch,old.completed(),old.expired());
    }
    public static Goal update(State state,Ledger before,Ledger after,long epoch){
        if(epoch<=before.epoch())return before.goal();
        Goal g=before.goal();
        if(!g.active())return choose(state,g,epoch);
        int delta=switch(g.kind()){
            case PRACTICE -> Math.max(0,after.practice()-before.practice());
            case SCOUT -> Math.max(0,after.trip().completed()-before.trip().completed());
            default -> 0;
        };
        g=credit(g,delta,epoch);
        if(g.active()&&epoch>=g.deadline())
            return new Goal(Kind.NONE,0,0,0,epoch+6,g.completed(),increment(g.expired()));
        return g;
    }
    public static Goal conversation(Goal g,long epoch){
        if(g.kind()!=Kind.CONNECT||epoch>=g.deadline())return g;
        return credit(g,1,epoch);
    }
    private static Goal credit(Goal g,int delta,long epoch){
        if(!g.active()||delta<=0)return g;
        int progress=g.progress()+Math.min(g.target(),delta);
        if(progress>=g.target())return new Goal(Kind.NONE,0,0,0,epoch+6,increment(g.completed()),g.expired());
        return new Goal(g.kind(),progress,g.target(),g.deadline(),g.nextPick(),g.completed(),g.expired());
    }
    private static int increment(int n){return n==Integer.MAX_VALUE?n:n+1;}
    public static String description(Goal g){
        return switch(g.kind()){
            case NONE -> "Considering my next objective";
            case PRACTICE -> "Improve my skills ("+g.progress()+"/"+g.target()+" practice)";
            case SCOUT -> "Complete a scouting trip ("+g.progress()+"/"+g.target()+")";
            case CONNECT -> "Meet other adventurers ("+g.progress()+"/"+g.target()+" friendly conversations)";
        };
    }
}
