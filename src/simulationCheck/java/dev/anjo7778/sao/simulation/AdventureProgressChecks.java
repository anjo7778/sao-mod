package dev.anjo7778.sao.simulation;

import static dev.anjo7778.sao.simulation.AdventureProgress.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;
public final class AdventureProgressChecks {
    public static void main(String[] args){
        int actors=0,updates=0,practices=0,rests=0,awake=0;
        for(long floor=1;floor<=200;floor++){
            for(State s:NpcRoster.generate(42,floor)){
                if(s.profile().occupation()!=Occupation.ADVENTURER)continue;
                actors++;
                Ledger l=start(0);
                for(int epoch=1;epoch<=120;epoch++){
                    var p=plan(s,epoch*600L,l);
                    if(l.needsRecovery())check(p.activity()==NpcDailyPlan.Activity.REST,"Fatigue overrides personal routine");
                    var next=advance(l,epoch,mode(p.activity()),true);
                    check(next.updates()==l.updates()+1&&next.abstractUpdates()==next.updates(),"Single abstract update");
                    check(advance(next,epoch,Mode.PRACTICE,true)==next,"Duplicate epoch");
                    check(next.fatigue()>=0&&next.fatigue()<=100,"Bounded fatigue");
                    check(next.practice()-l.practice()<=1,"No unearned practice");
                    if(p.activity()!=NpcDailyPlan.Activity.REST&&NpcRoutine.night(epoch*600L))awake++;
                    l=next;updates++;
                }
                practices+=l.practice();rests+=l.restSessions();
                var gap=advance(l,999999,Mode.PRACTICE,false);
                check(gap.updates()==l.updates()+1&&gap.practice()==l.practice()+1,"No unbounded catch-up");
                check(gap.abstractUpdates()==l.abstractUpdates(),"Visible update not counted as abstract");
                check(advance(gap,0,Mode.PRACTICE,true)==gap,"Rewind refused");
            }
        }
        check(practices>0&&rests>0&&awake>0,"Independent progress and routine variety");
        System.out.printf("PASS: %,d adventurers, %,d elapsed routine updates, %,d practice points, %,d rest sessions%n",actors,updates,practices,rests);
    }
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
}
