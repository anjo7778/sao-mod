package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

/** Conservative first migration slice: one upward relocation, never unlocks floors.
 * No physical portal crossing, free equipment, currency or rewards.
 */
public final class FloorMigration {
    public static final int MIN_SOURCE_ADVENTURERS=12,MAX_DEST_ADVENTURERS=48;
    private FloorMigration(){}
    public static boolean eligible(State s,AdventureProgress.Ledger l,long epoch,long time){
        var p=s.profile();var n=s.needs();
        return p.occupation()==Occupation.ADVENTURER&&p.originFloor()==p.birthFloor()&&p.relocatedAt()==0
            &&p.originFloor()<FloorLayout.MAX_FLOOR&&l!=null&&!l.trip().active()&&!l.goal().active()
            &&l.goal().completed()>0&&l.trip().completed()>0&&l.fatigue()<=45
            &&n.health()>=70&&n.supplies()>=35&&n.equipment()>=40&&!NpcRoutine.resting(p,time)
            &&epoch>=24&&(p.ambition()==Ambition.DISCOVERY||p.ambition()==Ambition.WEALTH||p.ambition()==Ambition.LEADERSHIP)
            &&p.personality().courage()>=45;
    }
    public static State relocate(State state,long destination,long epoch){
        var p=state.profile();
        if(p.occupation()!=Occupation.ADVENTURER||p.originFloor()!=p.birthFloor()||p.relocatedAt()!=0
            ||!FloorLayout.supported(destination)||destination!=p.originFloor()+1||epoch<=0)
            throw new IllegalArgumentException("Invalid migration");
        List<CityPlan.Plot> inns=new ArrayList<>();
        int n=CityPlan.half(destination)/CityPlan.LOT;
        for(int x=-n;x<=n;x++)for(int z=-n;z<=n;z++){
            var plot=CityPlan.plot(destination,x,z);
            if(plot!=null&&plot.role()==CityPlan.Role.INN)inns.add(plot);
        }
        if(inns.isEmpty())throw new IllegalStateException("Destination has no lodging");
        var inn=inns.get(Math.floorMod(p.id().hashCode(),inns.size()));
        Profile moved=new Profile(p.id(),p.name(),destination,p.occupation(),p.combatClass(),p.ambition(),
            inn.x(),inn.z(),0,0,p.personality(),p.birthFloor(),epoch);
        return new State(moved,state.needs(),state.memories());
    }
}
