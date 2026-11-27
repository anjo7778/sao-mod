package dev.anjo7778.sao.simulation;
import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;
import static dev.anjo7778.sao.simulation.NpcDailyPlan.*;
public final class NpcDailyChecks{
    public static void main(String[] args){
        int count=0;
        int resting=0,awake=0,nightTraining=0,nightSocial=0,dayRest=0,variations=0;
        var habits=EnumSet.noneOf(NpcRoutine.Habit.class);
        for(long floor:new long[]{1,2,3,1000,FloorLayout.MAX_FLOOR}){
            for(var state:NpcRoster.generate(42,floor)){
                var profile=state.profile();
                habits.add(NpcRoutine.habit(profile));
                var night=NpcDailyPlan.at(state,18000);
                if(night.activity()==Activity.REST)resting++;
                else{awake++;if(night.activity()==Activity.TRAIN)nightTraining++;if(night.activity()==Activity.SOCIALIZE)nightSocial++;}
                if(NpcDailyPlan.at(state,6000).activity()==Activity.REST)dayRest++;
                if(!NpcRoutine.window(profile,0).equals(NpcRoutine.window(profile,1)))variations++;
                for(long time:new long[]{0,1000,6000,12000,18000,23999,24000,42000}){
                    check(NpcDailyPlan.at(state,time).equals(NpcDailyPlan.at(state,time)),"Repeatable preference");
                    check(NpcDailyPlan.at(state.withNeeds(new AdventurerMind.Needs(10,100,100,100,0)),time).activity()==Activity.REST,"Poor health overrides routine");
                    check((NpcDailyPlan.at(state,time).activity()==Activity.REST)==NpcRoutine.resting(profile,time),"Rest decision respects preference");
                }
                var window=NpcRoutine.window(profile,0);
                check(NpcRoutine.resting(profile,window.start()),"Rest begins");
                check(NpcRoutine.resting(profile,(long)window.start()+window.duration()-1),"Rest continues across midnight");
                if(profile.occupation()==Occupation.ADVENTURER){
                    for(long time=0;time<24000;time+=1000){
                        if(NpcRoutine.resting(profile,time))continue;
                        var needs=state.needs();
                        var low=state.withNeeds(new AdventurerMind.Needs(needs.health(),5,100,0,0));
                        var plan=NpcDailyPlan.at(low,time);
                        check(plan.activity()==Activity.PREPARE,"Supplies before training");
                        var col=CityPlan.column(floor,plan.destination().x(),plan.destination().z());
                        check(col.plot()!=null&&col.plot().role()==CityPlan.Role.MARKET,"Actual market destination");
                        break;
                    }
                }
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

                }
            }
        }
        check(habits.size()==NpcRoutine.Habit.values().length,"All habits present");
        check(resting>0&&awake>0&&dayRest>0&&nightTraining>0&&nightSocial>0&&variations>0,"Varied routines and activities");
        System.out.printf("PASS: varied midnight schedules: %d resting, %d awake (%d training, %d socializing); %d daytime rest, %d day-to-day changes%n",resting,awake,nightTraining,nightSocial,dayRest,variations);
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
