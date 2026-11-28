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
        return route(floor,from,to,trip,landmarks,true);
    }
    public static List<Point> route(long floor,Point from,Point to,ScoutingTrip.Trip trip,boolean landmarks,boolean localShortcuts){
        boolean active=trip!=null&&trip.active();
        if(!landmarks)return active?ScoutingTrip.route(floor,trip,from,to):NpcDailyPlan.route(floor,from,to);
        Site a=at(floor,from.x(),from.z()),b=at(floor,to.x(),to.z());
        if(a==null&&b==null){
            if(active)return ScoutingTrip.route(floor,trip,from,to);
            var ordinary=NpcDailyPlan.route(floor,from,to);
            return localShortcuts?localBuildingApproach(floor,from,to,ordinary):ordinary;
        }
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
    /** Use clear local streets instead of returning through the plaza unnecessarily.
     * Geometry checks are conservative; the live navigator still validates real blocks.
     * Only landmark capitals and existing primary building destinations are eligible. */
    private static List<Point> localBuildingApproach(long floor,Point from,Point to,List<Point> ordinary){
        if(ordinary.isEmpty()||!CityPlan.inside(floor,from.x(),from.z())||!CityPlan.inside(floor,to.x(),to.z()))return ordinary;
        var plot=CityPlan.column(floor,to.x(),to.z()).plot();
        if(plot==null)return ordinary;
        // Start on a main east/west avenue or an existing cross street.
        int startZ;
        if(Math.abs(from.z())<=5)startZ=0;
        else{
            int offset=Math.floorMod(from.z(),CityPlan.LOT)-CityPlan.LOT/2;
            if(Math.abs(offset)>2)return ordinary;
            startZ=from.z()-offset;
        }
        int endZ=plot.z()-CityPlan.LOT/2;
        Point start=new Point(from.x(),startZ),end=new Point(plot.x(),endZ);
        Point door=new Point(plot.x(),plot.z()-plot.depth()-1);
        List<Point> best=ordinary;
        double bestLength=distance(from,ordinary);
        // The primary approach and both side alleys are merely candidates.
        // Reject any occupied cell in the planned two-block-high corridor.
        for(int x:new int[]{plot.x(),plot.x()-12,plot.x()+12}){
            Point entry=new Point(x,startZ),exit=new Point(x,endZ);
            List<Point> corners=List.of(start,entry,exit,end,door);
            Point cursor=from;
            boolean clear=true;
            for(Point next:corners){
                if(!clearStreet(floor,cursor,next)){clear=false;break;}
                cursor=next;
            }
            if(!clear)continue;
            List<Point> candidate=new ArrayList<>();cursor=from;
            for(Point next:corners){segment(candidate,cursor,next);cursor=next;}
            segment(candidate,door,to);
            if(candidate.stream().anyMatch(p->!clearAt(floor,p.x(),p.z(),1)||!clearAt(floor,p.x(),p.z(),2)))continue;
            double length=distance(from,candidate);
            if(length+4<bestLength){bestLength=length;best=List.copyOf(candidate);}
        }
        return best;
    }
    private static double distance(Point from,List<Point> route){
        double n=0;
        for(Point p:route){n+=Math.hypot(p.x()-from.x(),p.z()-from.z());from=p;}
        return n;
    }
    private static boolean clearStreet(long floor,Point a,Point b){
        if(a.x()!=b.x()&&a.z()!=b.z())return false;
        int dx=Integer.compare(b.x(),a.x()),dz=Integer.compare(b.z(),a.z());
        int n=Math.abs(b.x()-a.x())+Math.abs(b.z()-a.z());
        for(int i=0;i<=n;i++){
            int x=a.x()+i*dx,z=a.z()+i*dz;
            if(!clearAt(floor,x,z,1)||!clearAt(floor,x,z,2))return false;
        }
        return true;
    }
    private static void segment(List<Point> out,Point a,Point b){
        int dx=b.x()-a.x(),dz=b.z()-a.z(),n=(int)Math.ceil(Math.hypot(dx,dz)/12.0);
        for(int i=1;i<=n;i++)out.add(new Point(a.x()+(int)Math.round(dx*(double)i/n),a.z()+(int)Math.round(dz*(double)i/n)));
    }
}
