package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.NpcDailyPlan.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

/** Bounded autonomous reconnaissance; no combat, loot, dungeon discoveries or victory grants. */
public final class ScoutingTrip {
    public enum Phase { IDLE, OUTBOUND, SURVEY, RETURN }
    public enum Gate { EAST, SOUTH, WEST, NORTH }
    public static final int SUPPLY_COST=8;
    public record Trip(Phase phase,Gate gate,long since,long nextStart,int completed,int observed,int aborted,
                       boolean abstractLeg,boolean surveyed){
        public Trip {
            Objects.requireNonNull(phase);Objects.requireNonNull(gate);
            if(since<0||nextStart<0||completed<0||observed<0||observed>completed||aborted<0||
                phase==Phase.IDLE&&(abstractLeg||surveyed))throw new IllegalArgumentException("Invalid trip");
        }
        public boolean active(){return phase!=Phase.IDLE;}
    }
    public record Result(Trip trip,boolean began){}
    private ScoutingTrip(){}
    public static Trip idle(long epoch){return new Trip(Phase.IDLE,Gate.EAST,epoch,epoch,0,0,0,false,false);}

    public static boolean ready(State s,AdventureProgress.Ledger l,long epoch,long time){
        if(s.profile().occupation()!=Occupation.ADVENTURER||l==null||l.trip().active()
            ||epoch<l.trip().nextStart()||l.updates()<3||l.fatigue()>40||s.needs().health()<70
            ||s.needs().supplies()<35||s.needs().equipment()<40
            ||NpcRoutine.resting(s.profile(),time)||(l.practice()<2&&l.preparations()<2))return false;
        var p=s.profile();
        int drive=p.personality().ambition()+p.personality().courage()/2+(p.ambition()==Ambition.DISCOVERY?25:0);
        int roll=Math.floorMod(mix(p.id().getMostSignificantBits()^epoch*0x9E3779B97F4A7C15L),100);
        return drive>=65&&roll<Math.min(70,drive-40);
    }
    public static Result advance(State s,AdventureProgress.Ledger l,long epoch,long time,boolean absent,boolean reached){
        Trip t=l.trip();
        if(epoch<=l.epoch())return new Result(t,false);
        if(!t.active()){
            if(!ready(s,l,epoch,time))return new Result(t,false);
            int rotation=Math.floorMod(mix(s.profile().id().getLeastSignificantBits()+epoch),4);
            boolean explorer=s.profile().ambition()==Ambition.DISCOVERY||s.profile().personality().courage()>=65;
            Gate gate=l.journal().choose(rotation,explorer);
            return new Result(new Trip(Phase.OUTBOUND,gate,epoch,t.nextStart(),t.completed(),t.observed(),t.aborted(),absent,false),true);
        }
        if(epoch<=t.since())return new Result(t,false);
        boolean abstractLeg=t.abstractLeg()||absent;
        boolean progress=absent||reached;
        boolean recovery=s.needs().health()<35||l.fatigue()>=85;
        boolean timeout=epoch-t.since()>=8;
        Trip next=t;
        switch(t.phase()){
            case OUTBOUND -> {
                if(recovery||timeout&&!progress)next=move(t,Phase.RETURN,epoch,abstractLeg,false);
                else if(progress)next=move(t,Phase.SURVEY,epoch,abstractLeg,false);
            }
            case SURVEY -> {
                if(recovery||timeout&&!progress)next=move(t,Phase.RETURN,epoch,abstractLeg,false);
                else if(progress)next=move(t,Phase.RETURN,epoch,abstractLeg,true);
            }
            case RETURN -> {
                if(progress)next=new Trip(Phase.IDLE,t.gate(),epoch,epoch+6,
                    t.completed()+(t.surveyed()?1:0),t.observed()+(t.surveyed()&&!abstractLeg?1:0),
                    t.aborted()+(t.surveyed()?0:1),false,false);
            }
            default -> {}
        }
        if(next==t&&abstractLeg!=t.abstractLeg())
            next=new Trip(t.phase(),t.gate(),t.since(),t.nextStart(),t.completed(),t.observed(),t.aborted(),true,t.surveyed());
        return new Result(next,false);
    }
    private static Trip move(Trip t,Phase phase,long epoch,boolean abstractLeg,boolean surveyed){
        return new Trip(phase,t.gate(),epoch,t.nextStart(),t.completed(),t.observed(),t.aborted(),abstractLeg,surveyed);
    }
    public static Point gatePoint(long floor,Gate gate,int offset){
        int d=CityPlan.half(floor)+offset;
        return switch(gate){
            case EAST->new Point(d,0);case SOUTH->new Point(0,d);
            case WEST->new Point(-d,0);case NORTH->new Point(0,-d);
        };
    }
    public static Plan plan(long floor,Trip t){
        if(!t.active())throw new IllegalArgumentException("Idle trip");
        return switch(t.phase()){
            case OUTBOUND->new Plan(Activity.EXPLORE,gatePoint(floor,t.gate(),12));
            case SURVEY->new Plan(Activity.EXPLORE,gatePoint(floor,t.gate(),36));
            case RETURN->new Plan(Activity.RETURN,gatePoint(floor,t.gate(),-16));
            default->throw new IllegalStateException("Idle trip");
        };
    }
    public static boolean inRange(long floor,int x,int z){
        return FloorLayout.supported(floor)&&Math.max(Math.abs((long)x),Math.abs((long)z))<=CityPlan.half(floor)+64;
    }
    /** Connect the ordinary city road network with the active gate. No wall crossing shortcut. */
    public static List<Point> route(long floor,Trip t,Point from,Point to){
        if(!t.active()||!inRange(floor,from.x(),from.z())||!inRange(floor,to.x(),to.z()))return List.of();
        boolean insideA=CityPlan.inside(floor,from.x(),from.z()),insideB=CityPlan.inside(floor,to.x(),to.z());
        if(insideA&&insideB)return NpcDailyPlan.route(floor,from,to);
        Point gate=gatePoint(floor,t.gate(),-16);
        List<Point> out=new ArrayList<>();
        if(insideA){
            out.addAll(NpcDailyPlan.route(floor,from,gate));
            segment(out,gate,to);
        }else if(insideB){
            segment(out,from,gate);out.addAll(NpcDailyPlan.route(floor,gate,to));
        }else segment(out,from,to);
        return List.copyOf(out);
    }
    private static void segment(List<Point> out,Point a,Point b){
        int dx=b.x()-a.x(),dz=b.z()-a.z();
        int n=(int)Math.ceil(Math.hypot(dx,dz)/12.0);
        for(int i=1;i<=n;i++)out.add(new Point(a.x()+(int)Math.round(dx*(double)i/n),a.z()+(int)Math.round(dz*(double)i/n)));
    }
    private static long mix(long v){
        v=(v^(v>>>30))*0xBF58476D1CE4E5B9L;v=(v^(v>>>27))*0x94D049BB133111EBL;return v^(v>>>31);
    }
}
