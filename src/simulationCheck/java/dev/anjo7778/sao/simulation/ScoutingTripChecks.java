package dev.anjo7778.sao.simulation;
import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;
import static dev.anjo7778.sao.simulation.AdventureProgress.*;
import static dev.anjo7778.sao.simulation.ScoutingTrip.*;
public final class ScoutingTripChecks {
    public static void main(String[] args){
        int starts=0,completions=0,routes=0;
        for(long floor:new long[]{1,2,3,1000,FloorLayout.MAX_FLOOR}){
            for(var s:NpcRoster.generate(42,floor)){
                if(s.profile().occupation()!=Occupation.ADVENTURER)continue;
                var l=new Ledger(0,3,0,3,0,4,4,Mode.IDLE);
                long e=1;
                while(e<1000&&!ready(s,l,e,e*600L))e++;
                if(e==1000)continue;
                var r=advance(s,l,e,e*600L,true,false);
                check(r.began()&&r.trip().phase()==Phase.OUTBOUND,"Trip starts");
                starts++;
                var trip=r.trip();
                var start=gatePoint(floor,trip.gate(),-16);
                var out=plan(floor,trip).destination();
                check(!CityPlan.inside(floor,out.x(),out.z()),"Outside walls");
                var path=route(floor,trip,start,out);
                check(!path.isEmpty()&&path.getLast().equals(out),"Departure route");
                check(path.size()<=3,"No plaza detour from the gate");
                for(var p:path){
                    check(inRange(floor,p.x(),p.z()),"Bounded route");
                    if(!CityPlan.inside(floor,p.x(),p.z()))check(p.x()==0||p.z()==0,"Through gate axis");
                    routes++;
                }
                l=AdventureProgress.advance(l,e,Mode.TRAVEL,true).withTrip(trip);
                for(Phase phase:new Phase[]{Phase.SURVEY,Phase.RETURN,Phase.IDLE}){
                    check(advance(s,l,e,e*600L,true,true).trip().equals(l.trip()),"Duplicate epoch");
                    e++;
                    var step=advance(s,l,e,e*600L,true,false);
                    check(step.trip().phase()==phase,"Sequential abstract trip");
                    l=AdventureProgress.advance(l,e,Mode.TRAVEL,true).withTrip(step.trip());
                }
                check(l.trip().completed()==1&&l.trip().observed()==0&&l.trip().aborted()==0,"Abstract completion");
                check(!ready(s,l,e+1,6000),"Cooldown");completions++;
                // A visible actor cannot advance until it reaches the target.
                var physical=new Ledger(10,3,0,3,0,4,0,Mode.IDLE,
                    new Trip(Phase.OUTBOUND,Gate.EAST,10,0,0,0,0,false,false));
                check(advance(s,physical,11,6000,false,false).trip().phase()==Phase.OUTBOUND,"No fake visible arrival");
                check(advance(s,physical,11,6000,false,true).trip().phase()==Phase.SURVEY,"Actual arrival");
                var exhausted=new Ledger(10,3,90,3,0,4,0,Mode.IDLE,physical.trip());
                check(advance(s,exhausted,11,6000,false,false).trip().phase()==Phase.RETURN,"Recovery return");
                var returning=new Trip(Phase.RETURN,Gate.EAST,10,0,0,0,0,false,true);
                var finish=new Ledger(10,3,0,3,0,4,0,Mode.TRAVEL,returning);
                check(advance(s,finish,11,6000,false,true).trip().observed()==1,"Entirely observed trip");
                check(advance(s,finish,11,6000,true,false).trip().observed()==0,"Offscreen leg marked");
            }
        }
        check(starts>20&&starts==completions&&routes>0,"Scouting population");
        System.out.printf("PASS: %d autonomous scouting starts, %d abstract returns, %d route waypoints%n",starts,completions,routes);
    }
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
}
