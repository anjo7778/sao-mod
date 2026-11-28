package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

public final class FloorMigrationChecks{
    public static void main(String[] args){
        int migrations=0;
        for(long floor:new long[]{1,2,7,1000,FloorLayout.MAX_FLOOR-1}){
            for(var s:NpcRoster.generate(42,floor)){
                if(s.profile().occupation()!=Occupation.ADVENTURER)continue;
                var p=s.profile();
                var ledger=new AdventureProgress.Ledger(24,8,0,3,2,12,12,AdventureProgress.Mode.IDLE,
                    new ScoutingTrip.Trip(ScoutingTrip.Phase.IDLE,ScoutingTrip.Gate.EAST,24,24,1,0,0,false,false),
                    new SurveyJournal(1,0,2)).onFloor(floor).withGoal(new AdventurerGoals.Goal(AdventurerGoals.Kind.NONE,0,0,0,30,1,0));
                if(!FloorMigration.eligible(s,ledger,25,6000))continue;
                var moved=FloorMigration.relocate(s,floor+1,25);
                var q=moved.profile();
                check(q.id().equals(p.id())&&q.name().equals(p.name())&&q.birthFloor()==floor&&q.originFloor()==floor+1
                    &&q.relocatedAt()==25&&q.personality().equals(p.personality())&&q.combatClass()==p.combatClass()
                    &&moved.needs().equals(s.needs())&&moved.memories().equals(s.memories()),"Identity or history lost");
                check(CityPlan.column(floor+1,q.homeX(),q.homeZ()).plot().role()==CityPlan.Role.INN,"No destination inn");
                check(!FloorMigration.eligible(moved,ledger,100,6000),"Repeated migration");
                var arrival=ledger.onFloor(floor+1);
                check(arrival.journal().equals(SurveyJournal.EMPTY)&&arrival.journalFor(floor).equals(ledger.journal()),"Cross-floor journal contamination");
                check(arrival.practice()==ledger.practice()&&arrival.goal().equals(ledger.goal())&&arrival.trip().equals(ledger.trip()),"Progress lost");
                var survey=arrival.withJournal(SurveyJournal.EMPTY.complete(ScoutingTrip.Gate.NORTH,false));
                check(survey.journalFor(floor).equals(ledger.journal())&&survey.journalFor(floor+1).surveyed()==8,"Per-floor journals");
                check(survey.onFloor(floor).journal().equals(ledger.journal()),"Archive restoration");
                migrations++;
            }
        }
        check(migrations>0,"No eligible migration candidates");
        System.out.printf("PASS: %d eligible upward relocations, stable identities and isolated journal histories%n",migrations);
    }
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
}
