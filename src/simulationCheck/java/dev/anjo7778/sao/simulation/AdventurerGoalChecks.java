package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.AdventurerGoals.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;
import static dev.anjo7778.sao.simulation.NpcDailyPlan.*;

public final class AdventurerGoalChecks {
    public static void main(String[] args){
        int plans=0,venuePlans=0,goals=0,paths=0,completed=0;
        Set<Kind> kinds=EnumSet.noneOf(Kind.class);
        Set<CityCourts.Kind> venues=EnumSet.noneOf(CityCourts.Kind.class);
        for(long floor:new long[]{1,2,3,6,7,1000,FloorLayout.MAX_FLOOR}){
            for(State s:NpcRoster.generate(42,floor)){
                var l=AdventureProgress.start(0);
                var g=choose(s,EMPTY,1);
                if(s.profile().occupation()==Occupation.ADVENTURER){
                    check(g.active(),"No goal");goals++;kinds.add(g.kind());
                }else check(!g.active(),"Resident received adventurer goal");
                l=l.withGoal(g);
                for(long time:new long[]{1000,6000,12000,18000,22000}){
                    Plan original=AdventureProgress.plan(s,time,l);
                    check(original.equals(CityVenueRoutine.plan(s,time,l,false)),"Legacy schedule changed");
                    Plan next=CityVenueRoutine.plan(s,time,l,true);plans++;
                    if(original.activity()!=Activity.TRAIN&&original.activity()!=Activity.SOCIALIZE)
                        check(original.equals(next),"Priority overridden");
                    var site=CityCourts.at(floor,next.destination().x(),next.destination().z());
                    if(site==null)continue;
                    venues.add(site.kind());venuePlans++;
                    check(CityCourts.clearAt(floor,next.destination().x(),next.destination().z(),1)
                        &&CityCourts.clearAt(floor,next.destination().x(),next.destination().z(),2),"Blocked venue destination");
                    var localStart=new Point(site.x(),site.z()-CityCourts.HALF_Z-8);
                    var local=CityVenueRoutine.route(floor,localStart,next.destination(),null,true);
                    check(local.size()<=4&&local.getLast().equals(next.destination()),"Nearby venue detour");
                    for(var v:local)check(Math.abs(v.x()-site.x())<=4,"Unnecessary central plaza detour");
                    var path=CityVenueRoutine.route(floor,new Point(0,0),next.destination(),null,true);
                    check(path.getLast().equals(next.destination()),"Court route missing");
                    for(var p:path){
                        check(CityPlan.inside(floor,p.x(),p.z()),"Court route outside floor");
                        check(CityCourts.clearAt(floor,p.x(),p.z(),1)&&CityCourts.clearAt(floor,p.x(),p.z(),2),"Blocked waypoint "+p);paths++;
                    }
                }
                if(g.kind()==Kind.PRACTICE){
                    for(int i=1;i<=g.target();i++){
                        var next=AdventureProgress.advance(l,i,AdventureProgress.Mode.PRACTICE,true);
                        l=next.withGoal(update(s,l,next,i));
                    }
                    check(l.goal().completed()==1&&!l.goal().active(),"Practice credit");completed++;
                }else if(g.kind()==Kind.CONNECT){
                    var next=AdventureProgress.advance(l,1,AdventureProgress.Mode.SOCIALIZE,true);
                    check(update(s,l,next,1).progress()==0,"Idle social activity not a conversation");
                    Goal a=g;for(int i=0;i<3;i++)a=conversation(a,2+i);
                    check(a.completed()==1&&!a.active(),"Accepted conversation credit");completed++;
                }else if(g.kind()==Kind.SCOUT){
                    var next=AdventureProgress.advance(l,1,AdventureProgress.Mode.TRAVEL,true);
                    check(update(s,l,next,1).progress()==0,"No trip completion credit from travel");
                    var trip=new ScoutingTrip.Trip(ScoutingTrip.Phase.IDLE,ScoutingTrip.Gate.EAST,1,7,1,0,0,false,false);
                    next=next.withTrip(trip);
                    check(update(s,l,next,1).completed()==1,"Completed trip credit");completed++;
                }
                if(g.active()){
                    var before=AdventureProgress.start(0).withGoal(g);
                    var after=AdventureProgress.advance(before,101,AdventureProgress.Mode.IDLE,true);
                    check(update(s,before,after,101).expired()==1,"Goal expiration");
                    check(update(s,before,after,0).equals(g),"No repeated epoch credit");
                }
            }
        }
        check(kinds.size()==3&&venues.contains(CityCourts.Kind.TRAINING_YARD)&&venues.contains(CityCourts.Kind.GUILD_SQUARE)
            &&venues.contains(CityCourts.Kind.RESIDENTIAL_GARDEN)&&venuePlans>0&&completed==goals,"Goal/venue coverage");
        System.out.printf("PASS: %d personal goals, %d verified goal completions, %,d activity decisions, %,d venue plans, %,d route waypoints%n",goals,completed,plans,venuePlans,paths);
    }
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
}
