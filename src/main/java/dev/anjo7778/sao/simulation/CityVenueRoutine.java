package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;
import static dev.anjo7778.sao.simulation.NpcDailyPlan.*;
import static dev.anjo7778.sao.simulation.CityCourts.*;

/** Landmark-world activities. No service transactions or new rewards. */
public final class CityVenueRoutine {
    private CityVenueRoutine(){}
    public static Plan plan(State s,long time,AdventureProgress.Ledger ledger,boolean landmarks){
        Plan base=AdventureProgress.plan(s,time,ledger);
        if(!landmarks)return base;
        Profile p=s.profile();
        if(p.regionalHome()!=null)return base;
        if(base.activity()!=Activity.TRAIN&&base.activity()!=Activity.SOCIALIZE)return base;
        boolean adventurer=p.occupation()==Occupation.ADVENTURER;
        Activity activity=base.activity();
        var goal=ledger==null?AdventurerGoals.EMPTY:ledger.goal();
        if(adventurer){
            if(goal.kind()==AdventurerGoals.Kind.PRACTICE)activity=Activity.TRAIN;
            if(goal.kind()==AdventurerGoals.Kind.CONNECT)activity=Activity.SOCIALIZE;
        }
        int roll=Math.floorMod(p.id().hashCode(),100);
        // Keep a real plaza population. A subset prefers dedicated venues.
        if(roll<35)return new Plan(activity,base.destination());
        Kind kind=activity==Activity.TRAIN?Kind.TRAINING_YARD:
            !adventurer?Kind.RESIDENTIAL_GARDEN:
            p.ambition()==Ambition.WEALTH?Kind.MARKET_COURT:Kind.GUILD_SQUARE;
        Site site=nearest(p,kind);
        if(site==null)return new Plan(activity,base.destination());
        int dx=activity==Activity.TRAIN?(Math.floorMod(p.id().hashCode(),3)-1)*4:Math.floorMod(p.id().hashCode(),3)-1;
        int dz=activity==Activity.TRAIN?1:-3+Math.floorMod(p.id().hashCode()>>>4,3);
        Point point=new Point(site.x()+dx,site.z()+dz);
        if(!clearAt(p.originFloor(),point.x(),point.z(),1)||!clearAt(p.originFloor(),point.x(),point.z(),2))
            return new Plan(activity,base.destination());
        return new Plan(activity,point);
    }
    private static Site nearest(Profile p,Kind kind){
        int n=CityPlan.half(p.originFloor())/CityPlan.LOT;
        Site best=null;long distance=Long.MAX_VALUE;
        for(int gap=-n;gap<=n;gap++)for(int row=-n;row<=n;row++){
            Site s=site(p.originFloor(),gap,row);
            if(s==null||s.kind()!=kind)continue;
            long dx=s.x()-p.homeX(),dz=s.z()-p.homeZ(),d=dx*dx+dz*dz;
            if(d<distance){best=s;distance=d;}
        }
        return best;
    }
    private static Point entry(Site s){return new Point(s.x(),s.z()-HALF_Z);}
    private static Point connector(Site s){return new Point(s.x(),s.z()-CityPlan.LOT/2);}
    /** Court-specific connector routes avoid diagonal shortcuts through newly occupied building lots. */
    public static List<Point> route(long floor,Point from,Point to,ScoutingTrip.Trip trip,boolean landmarks){
        boolean active=trip!=null&&trip.active();
        if(!landmarks)return active?ScoutingTrip.route(floor,trip,from,to):NpcDailyPlan.route(floor,from,to);
        Site a=at(floor,from.x(),from.z()),b=at(floor,to.x(),to.z());
        if(a==null&&b==null)return active?ScoutingTrip.route(floor,trip,from,to):NpcDailyPlan.route(floor,from,to);
        List<Point> out=new ArrayList<>();Point cursor=from;
        if(a!=null&&a.equals(b)){segment(out,from,to);return List.copyOf(out);}
        if(a!=null){
            segment(out,cursor,entry(a));cursor=entry(a);
            segment(out,cursor,connector(a));cursor=connector(a);
        }
        if(b==null){
            out.addAll(active?ScoutingTrip.route(floor,trip,cursor,to):NpcDailyPlan.route(floor,cursor,to));
            return List.copyOf(out);
        }
        Point link=connector(b);
        // A character already approaching the court from its own street must not
        // detour to the central axis. Same cross-street connectors can link directly.
        if(Math.abs(cursor.x()-b.x())<=2&&cursor.z()>=link.z()-2&&cursor.z()<=entry(b).z()){
            segment(out,cursor,entry(b));
        }else if(Math.abs(cursor.z()-link.z())<=2){
            Point cross=new Point(cursor.x(),link.z());
            segment(out,cursor,cross);segment(out,cross,link);segment(out,link,entry(b));
        }else{
            Point axis=new Point(0,link.z());
            out.addAll(NpcDailyPlan.route(floor,cursor,axis));
            segment(out,axis,link);segment(out,link,entry(b));
        }
        segment(out,entry(b),to);
        return List.copyOf(out);
    }
    private static void segment(List<Point> out,Point a,Point b){
        int dx=b.x()-a.x(),dz=b.z()-a.z(),n=(int)Math.ceil(Math.hypot(dx,dz)/12.0);
        for(int i=1;i<=n;i++)out.add(new Point(a.x()+(int)Math.round(dx*(double)i/n),a.z()+(int)Math.round(dz*(double)i/n)));
    }
}
