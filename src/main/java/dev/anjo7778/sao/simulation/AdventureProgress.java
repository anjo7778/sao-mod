package dev.anjo7778.sao.simulation;

import dev.anjo7778.sao.simulation.NpcRoster.State;

/** Abstract routine progress, not combat XP, inventory, or a claimed real dungeon victory. */
public final class AdventureProgress {
    public static final int EPOCH_TICKS=600, MAX=1_000_000_000;
    public enum Mode { IDLE, PRACTICE, REST, PREPARE, SOCIALIZE, TRAVEL, SCOUT }
    public record Ledger(long epoch,int practice,int fatigue,int preparations,int restSessions,
                         int updates,int abstractUpdates,Mode last,ScoutingTrip.Trip trip,SurveyJournal journal,AdventurerGoals.Goal goal){
        public Ledger(long epoch,int practice,int fatigue,int preparations,int restSessions,int updates,int abstractUpdates,Mode last,ScoutingTrip.Trip trip,SurveyJournal journal){
            this(epoch,practice,fatigue,preparations,restSessions,updates,abstractUpdates,last,trip,journal,AdventurerGoals.EMPTY);
        }
        public Ledger(long epoch,int practice,int fatigue,int preparations,int restSessions,int updates,int abstractUpdates,Mode last,ScoutingTrip.Trip trip){
            this(epoch,practice,fatigue,preparations,restSessions,updates,abstractUpdates,last,trip,SurveyJournal.EMPTY);
        }
        public Ledger(long epoch,int practice,int fatigue,int preparations,int restSessions,int updates,int abstractUpdates,Mode last){
            this(epoch,practice,fatigue,preparations,restSessions,updates,abstractUpdates,last,ScoutingTrip.idle(epoch));
        }
        public Ledger withTrip(ScoutingTrip.Trip t){return new Ledger(epoch,practice,fatigue,preparations,restSessions,updates,abstractUpdates,last,t,journal,goal);}
        public Ledger withJournal(SurveyJournal j){return new Ledger(epoch,practice,fatigue,preparations,restSessions,updates,abstractUpdates,last,trip,j,goal);}
        public Ledger withGoal(AdventurerGoals.Goal g){return new Ledger(epoch,practice,fatigue,preparations,restSessions,updates,abstractUpdates,last,trip,journal,g);}
        public Ledger{
            if(epoch<0||practice<0||practice>MAX||fatigue<0||fatigue>100||preparations<0||preparations>MAX
                ||restSessions<0||restSessions>MAX||updates<0||updates>MAX||abstractUpdates<0||abstractUpdates>updates||last==null||trip==null||journal==null||goal==null)
                throw new IllegalArgumentException("Invalid adventurer progress");
        }
        public int practiceRank(){return 1+practice/20;}
        public boolean needsRecovery(){return fatigue>=80||last==Mode.REST&&fatigue>=40;}
    }
    private AdventureProgress(){}
    public static Ledger start(long epoch){return new Ledger(epoch,0,0,0,0,0,0,Mode.IDLE);}
    public static NpcDailyPlan.Plan plan(State state,long time,Ledger ledger){
        if(ledger!=null&&ledger.trip().active())return ScoutingTrip.plan(state.profile().originFloor(),ledger.trip());
        if(ledger!=null&&ledger.needsRecovery()){
            var p=state.profile();
            return new NpcDailyPlan.Plan(NpcDailyPlan.Activity.REST,new NpcDailyPlan.Point(p.homeX()+3,p.homeZ()-3));
        }
        return NpcDailyPlan.at(state,time);
    }
    public static Mode mode(NpcDailyPlan.Activity a){
        return switch(a){
            case REST->Mode.REST;
            case TRAIN->Mode.PRACTICE;
            case PREPARE->Mode.PREPARE;
            case SOCIALIZE->Mode.SOCIALIZE;
            case EXPLORE->Mode.SCOUT;
            case RETURN->Mode.TRAVEL;
            default->Mode.IDLE;
        };
    }
    public static Ledger advance(Ledger before,long epoch,Mode mode,boolean offscreen){
        if(epoch<=before.epoch())return before;
        // Deliberately at most one sample per update. Never simulate a large offline/backlog burst.
        int practice=before.practice(),fatigue=before.fatigue(),prep=before.preparations(),rest=before.restSessions();
        switch(mode){
            case PRACTICE->{practice=inc(practice,1);fatigue=Math.min(100,fatigue+12);}
            case REST->{rest=inc(rest,1);fatigue=Math.max(0,fatigue-18);}
            case PREPARE->{prep=inc(prep,1);fatigue=Math.min(100,fatigue+5);}
            case SOCIALIZE->fatigue=Math.min(100,fatigue+3);
            case TRAVEL->fatigue=Math.min(100,fatigue+3);
            case SCOUT->fatigue=Math.min(100,fatigue+8);
            case IDLE->{}
        }
        int count=inc(before.updates(),1);
        return new Ledger(epoch,practice,fatigue,prep,rest,count,
            offscreen?inc(before.abstractUpdates(),1):before.abstractUpdates(),mode,before.trip(),before.journal(),before.goal());
    }
    private static int inc(int v,int delta){return Math.min(MAX,v+delta);}
}
