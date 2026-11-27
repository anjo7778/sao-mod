package dev.anjo7778.sao.simulation;
import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;
import static dev.anjo7778.sao.simulation.NpcDailyPlan.*;
public final class NpcDailyChecks{
    public static void main(String[] args){
        int count=0;
        for(long floor:new long[]{1,2,3,1000,FloorLayout.MAX_FLOOR}){
            for(var state:NpcRoster.generate(42,floor)){
                for(long time:new long[]{2000,6000,12000,20000}){
                    var plan=NpcDailyPlan.at(state,time);
                    var prev=NpcDailyPlan.at(state,20000).destination();
                    var path=NpcDailyPlan.route(floor,prev,plan.destination());
                    for(var p:path){
                        check(CityPlan.inside(floor,p.x(),p.z()),"Route outside city");
                        check(Math.hypot(prev.x()-p.x(),prev.z()-p.z())<=26,"Unbounded waypoint");
                        prev=p;count++;
                    }
                    check(prev.equals(plan.destination()),"Route did not finish");
                    check(NpcDailyPlan.at(state,20000).activity()==Activity.REST,"Night schedule");
                }
            }
        }
        var state=NpcRoster.generate(42,1).getFirst();var player=new UUID(5,6);
        var greet=NpcDialogue.greeting(state.profile().id(),player,1);
        var first=state.remember(greet);
        check(first.relationship(player).trust()==51,"Greeting relationship");
        check(first.remember(greet)==first,"Repeated greeting");
        check(NpcDialogue.greetingText(first,player,Activity.WORK).startsWith("We have met before"),"Contextual recognition");
        check(!NpcDialogue.greeting(state.profile().id(),player,2).eventId().equals(greet.eventId()),"Daily greeting");
        System.out.printf("PASS: %,d bounded schedule waypoints and contextual greeting rules%n",count);
    }
    private static void check(boolean b,String label){if(!b)throw new AssertionError(label);}
}
