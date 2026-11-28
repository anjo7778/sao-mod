package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

/** Initial city schedule and road waypoints. No free inventory/economic transactions. */
public final class NpcDailyPlan {
    public enum Activity { REST, WORK, PREPARE, TRAIN, SOCIALIZE, EXPLORE, RETURN }
    public record Point(int x,int z) {}
    public record Plan(Activity activity,Point destination) {}
    private NpcDailyPlan(){}
    public static Plan at(State state,long time){
        var p=state.profile();
        int key=p.id().hashCode();
        long day=Math.floorMod(time,24000L);
        Point home=new Point(p.homeX()+3,p.homeZ()-3);
        var region=p.regionalHome();
        if(region!=null)return RegionalNpcRoutine.plan(state,time,region);
        Point plaza=new Point(Math.floorMod(key,43)-21,Math.floorMod((int)(p.id().getMostSignificantBits()>>>16),43)-21);
        if(state.needs().health()<35 || NpcRoutine.resting(p,time)) return new Plan(Activity.REST,home);
        if(p.occupation()!=Occupation.ADVENTURER){
            boolean nightService=(p.occupation()==Occupation.INNKEEPER||p.occupation()==Occupation.HEALER)
                &&NpcRoutine.habit(p)==NpcRoutine.Habit.NOCTURNAL;
            if(nightService?NpcRoutine.night(time):(day>=1000&&day<11000))
                return new Plan(Activity.WORK,new Point(p.workX()+3,p.workZ()-3));
            return new Plan(Activity.SOCIALIZE,plaza);
        }
        if(state.needs().supplies()<35)return new Plan(Activity.PREPARE,service(p,CityPlan.Role.MARKET,plaza));
        if(state.needs().equipment()<40)return new Plan(Activity.PREPARE,service(p,CityPlan.Role.FORGE,plaza));
        if(NpcRoutine.night(time)){
            return new Plan(NpcRoutine.nightTraining(p,time)?Activity.TRAIN:Activity.SOCIALIZE,plaza);
        }
        if(day<3500)return new Plan(Activity.PREPARE,plaza);
        if(day<8500 && p.personality().ambition()>=45) return new Plan(Activity.TRAIN,plaza);
        return new Plan(Activity.SOCIALIZE,plaza);
    }
    private static Point service(Profile p,CityPlan.Role role,Point fallback){
        int bound=CityPlan.half(p.originFloor())/CityPlan.LOT;
        CityPlan.Plot best=null;long bestDistance=Long.MAX_VALUE;
        for(int x=-bound;x<=bound;x++)for(int z=-bound;z<=bound;z++){
            var lot=CityPlan.plot(p.originFloor(),x,z);
            if(lot==null||lot.role()!=role)continue;
            long dx=lot.x()-p.homeX(),dz=lot.z()-p.homeZ(),dist=dx*dx+dz*dz;
            if(dist<bestDistance){bestDistance=dist;best=lot;}
        }
        return best==null?fallback:new Point(best.x()+3,best.z()-3);
    }
    /** Routes use the central axes and horizontal streets; every segment is at most 24 blocks. */
    public static List<Point> route(long floor,Point from,Point to){
        if(!CityPlan.inside(floor,from.x(),from.z())||!CityPlan.inside(floor,to.x(),to.z()))return List.of();
        List<Point> out=new ArrayList<>();
        if(from.equals(to))return List.of();
        // Main gates share the two broad axes. Avoid backtracking through the plaza.
        if(Math.abs(from.z())<=5&&Math.abs(to.z())<=5){
            Point a=new Point(from.x(),0),b=new Point(to.x(),0);
            segment(out,from,a);segment(out,a,b);segment(out,b,to);return List.copyOf(out);
        }
        if(Math.abs(from.x())<=5&&Math.abs(to.x())<=5){
            Point a=new Point(0,from.z()),b=new Point(0,to.z());
            segment(out,from,a);segment(out,a,b);segment(out,b,to);return List.copyOf(out);
        }
        var a=CityPlan.column(floor,from.x(),from.z());
        var b=CityPlan.column(floor,to.x(),to.z());
        if((a.plot()!=null && a.plot().equals(b.plot())) || a.zone()==CityPlan.Zone.PLAZA&&b.zone()==CityPlan.Zone.PLAZA){
            segment(out,from,to);return List.copyOf(out);
        }
        Point cursor=from;
        if(a.plot()!=null){
            var p=a.plot();
            Point door=new Point(p.x(),p.z()-p.depth()-1);
            segment(out,cursor,door);cursor=door;
            Point road=new Point(p.x(),p.z()-24);
            segment(out,cursor,road);cursor=road;
        }else if(Math.abs(cursor.x())<=7 || Math.abs(cursor.z())<=7 || a.zone()==CityPlan.Zone.PLAZA){
            Point hub=new Point(0,cursor.z());
            segment(out,cursor,hub);cursor=hub;
        }else{
            int h=CityPlan.half(floor);
            int roadZ=Math.max(-h+24,Math.min(h-24,Math.floorDiv(cursor.z(),48)*48+24));
            Point road=new Point(cursor.x(),roadZ);
            segment(out,cursor,road);cursor=road;
        }
        Point axis=new Point(0,cursor.z());
        segment(out,cursor,axis);cursor=axis;
        if(b.plot()!=null){
            var p=b.plot();
            Point cross=new Point(0,p.z()-24);
            segment(out,cursor,cross);cursor=cross;
            Point road=new Point(p.x(),p.z()-24);
            segment(out,cursor,road);cursor=road;
            Point door=new Point(p.x(),p.z()-p.depth()-1);
            segment(out,cursor,door);cursor=door;
        } else {
            Point hub=new Point(0,0);
            segment(out,cursor,hub);cursor=hub;
        }
        segment(out,cursor,to);
        return List.copyOf(out);
    }
    private static void segment(List<Point> out,Point a,Point b){
        int dx=b.x()-a.x(),dz=b.z()-a.z();
        int n=(int)Math.ceil(Math.hypot(dx,dz)/24);
        for(int i=1;i<=n;i++)out.add(new Point(a.x()+(int)Math.round(dx*(double)i/n),a.z()+(int)Math.round(dz*(double)i/n)));
    }
}
