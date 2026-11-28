package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;
import static dev.anjo7778.sao.simulation.NpcDailyPlan.*;
import static dev.anjo7778.sao.simulation.RegionalSettlements.Kind;

/** Conservative regional trips: abstract wilderness legs, real optional local projections.
 * No unearned gear, food, currency, combat, floor unlock or claim of physical terrain discovery.
 */
public final class RegionalJourney {
    public enum Phase { IDLE, OUTBOUND, VISITING, RETURNING }
    public enum Purpose { EXPLORE, SUPPLIES, EQUIPMENT, MEET_PEOPLE, REFUGE }
    public record Journey(Phase phase,Kind target,Purpose purpose,long since,long nextStart,int visitedMask){
        public Journey{
            Objects.requireNonNull(phase);Objects.requireNonNull(target);Objects.requireNonNull(purpose);
            if(since<0||nextStart<0||(visitedMask&~15)!=0||phase!=Phase.IDLE&&(visitedMask&bit(target))!=0)
                throw new IllegalArgumentException("Invalid regional journey");
        }
        public boolean active(){return phase!=Phase.IDLE;}
        public boolean visiting(){return phase==Phase.VISITING;}
        public int completed(){return Integer.bitCount(visitedMask);}
    }
    public static final Journey EMPTY=new Journey(Phase.IDLE,Kind.MARKET_TOWN,Purpose.EXPLORE,0,0,0);
    public record Result(Journey journey,boolean began,boolean arrived){}
    private RegionalJourney(){}
    public static int bit(Kind k){return 1<<k.ordinal();}
    public static int legEpochs(long floor){
        int distance=RegionalSettlements.distance(floor)-CityPlan.half(floor);
        return Math.max(2,(distance+159)/160);
    }
    public static int supplyCost(long floor){return 6+(RegionalSettlements.distance(floor)+255)/256;}
    public static RegionalSettlements.Site site(long floor,Journey j){
        return j.visiting()?RegionalSettlements.site(floor,j.target()):null;
    }
    private static Kind preferred(State s){
        if(s.needs().supplies()<55)return Kind.MARKET_TOWN;
        if(s.needs().equipment()<60)return Kind.CRAFTS_VILLAGE;
        return switch(s.profile().ambition()){
            case WEALTH->Kind.MARKET_TOWN;
            case MASTERY->Kind.CRAFTS_VILLAGE;
            case FRIENDSHIP->Kind.LODGING_VILLAGE;
            case LEADERSHIP->Kind.EXPEDITION_REFUGE;
            default->null;
        };
    }
    private static Purpose purpose(State s,Kind k){
        if(s.profile().ambition()==Ambition.DISCOVERY&&s.needs().supplies()>=55&&s.needs().equipment()>=60)
            return Purpose.EXPLORE;
        return switch(k){
            case MARKET_TOWN->Purpose.SUPPLIES;
            case CRAFTS_VILLAGE->Purpose.EQUIPMENT;
            case LODGING_VILLAGE->Purpose.MEET_PEOPLE;
            case EXPEDITION_REFUGE->Purpose.REFUGE;
        };
    }
    public static Result advance(State s,AdventureProgress.Ledger l,Journey j,long epoch,long time,boolean absent){
        if(l==null||epoch<=l.epoch()||s.profile().occupation()!=Occupation.ADVENTURER||!absent)return new Result(j,false,false);
        long floor=s.profile().currentFloor();
        if(!j.active()){
            var n=s.needs();
            if(epoch<j.nextStart()||j.visitedMask()==15||l.trip().active()||l.goal().active()
                ||l.updates()<4||l.fatigue()>40||n.health()<70||n.supplies()<35+supplyCost(floor)||n.equipment()<40
                ||NpcRoutine.resting(s.profile(),time)||(l.practice()<2&&l.preparations()<2))
                return new Result(j,false,false);
            int drive=s.profile().personality().ambition()+s.profile().personality().courage()/2;
            int roll=Math.floorMod(s.profile().id().hashCode()^(int)(epoch*0x9E3779B9L),100);
            if(drive<55||roll>=Math.min(75,drive-25))return new Result(j,false,false);
            Kind target=preferred(s);
            if(target==null||(j.visitedMask()&bit(target))!=0){
                int first=Math.floorMod(s.profile().id().hashCode(),4);
                for(int i=0;i<4;i++){
                    var k=Kind.values()[(first+i)%4];
                    if((j.visitedMask()&bit(k))==0){target=k;break;}
                }
            }
            return new Result(new Journey(Phase.OUTBOUND,target,purpose(s,target),epoch,j.nextStart(),j.visitedMask()),true,false);
        }
        if(epoch<=j.since())return new Result(j,false,false);
        long elapsed=epoch-j.since();
        return switch(j.phase()){
            case OUTBOUND->elapsed>=legEpochs(floor)
                ?new Result(new Journey(Phase.VISITING,j.target(),j.purpose(),epoch,j.nextStart(),j.visitedMask()),false,true):new Result(j,false,false);
            case VISITING->elapsed>=2
                ?new Result(new Journey(Phase.RETURNING,j.target(),j.purpose(),epoch,j.nextStart(),j.visitedMask()),false,false):new Result(j,false,false);
            case RETURNING->elapsed>=legEpochs(floor)
                ?new Result(new Journey(Phase.IDLE,j.target(),j.purpose(),epoch,epoch+12,j.visitedMask()|bit(j.target())),false,false):new Result(j,false,false);
            default->new Result(j,false,false);
        };
    }
    /** The local visit uses an actual service building or the unobstructed square. */
    public static Plan visitPlan(Profile p,Journey j){
        var site=site(p.currentFloor(),j);
        if(site==null)throw new IllegalArgumentException("Not visiting");
        CityPlan.Role role=switch(j.purpose()){
            case SUPPLIES->CityPlan.Role.MARKET;case EQUIPMENT->CityPlan.Role.FORGE;
            case REFUGE->CityPlan.Role.INN;default->null;
        };
        if(role!=null)for(var b:RegionalSettlements.buildings(site))if(b.plot().role()==role)
            return new Plan(Activity.PREPARE,new Point(b.x()+3,b.z()-3));
        int hash=p.id().hashCode();
        return new Plan(Activity.SOCIALIZE,new Point(site.x()+Math.floorMod(hash,7)-3,site.z()+Math.floorMod(hash>>>8,7)-3));
    }
}
