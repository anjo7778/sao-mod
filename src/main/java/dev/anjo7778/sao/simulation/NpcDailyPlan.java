package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

/** Initial city schedule and road waypoints. No free inventory/economic transactions. */
public final class NpcDailyPlan {
    public enum Activity { REST, WORK, PREPARE, TRAIN, SOCIALIZE }
    public record Point(int x,int z) {}
    public record Plan(Activity activity,Point destination) {}
    private NpcDailyPlan(){}
    public static Plan at(State state,long time){
        var p=state.profile();
        int key=p.id().hashCode(),shift=Math.floorMod(key,1200)-600;
        long day=Math.floorMod(time+shift,24000);
        Point home=new Point(p.homeX()+3,p.homeZ()-3);
        Point plaza=new Point(Math.floorMod(key,43)-21,Math.floorMod((int)(p.id().getMostSignificantBits()>>>16),43)-21);
        if(state.needs().health()<35 || day>=13500 || day<1000) return new Plan(Activity.REST,home);
        if(p.occupation()!=Occupation.ADVENTURER){
            if(day<11000) return new Plan(Activity.WORK,new Point(p.workX()+3,p.workZ()-3));
            return new Plan(Activity.SOCIALIZE,plaza);
        }
        if(day<3500||state.needs().supplies()<20||state.needs().equipment()<20)
            return new Plan(Activity.PREPARE,plaza);
        if(day<8500 && p.personality().ambition()>=45) return new Plan(Activity.TRAIN,plaza);
        return new Plan(Activity.SOCIALIZE,plaza);
    }
    /** Routes use the central axes and horizontal streets; every segment is at most 24 blocks. */
    public static List<Point> route(long floor,Point from,Point to){
        if(!CityPlan.inside(floor,from.x(),from.z())||!CityPlan.inside(floor,to.x(),to.z()))return List.of();
        List<Point> out=new ArrayList<>();
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
