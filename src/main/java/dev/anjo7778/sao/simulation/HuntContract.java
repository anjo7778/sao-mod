package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

/** A single named quarry contract associated with one actual scouting survey. */
public record HuntContract(long floor,long surveySince,UUID quarry,Status status){
    public enum Status { ACTIVE, KILLED, EXPIRED }
    public static final int WINDOW_EPOCHS=3, REWARD=10;
    public HuntContract{
        if(!FloorLayout.supported(floor)||surveySince<0||quarry==null||status==null)throw new IllegalArgumentException("Invalid hunt");
    }
    public boolean matches(long floor,ScoutingTrip.Trip trip){
        return this.floor==floor&&trip!=null&&trip.phase()==ScoutingTrip.Phase.SURVEY&&surveySince==trip.since();
    }
    public boolean active(){return status==Status.ACTIVE;}
    public HuntContract close(Status result){
        if(!active()||result==Status.ACTIVE)throw new IllegalArgumentException("Invalid hunt closure");
        return new HuntContract(floor,surveySince,quarry,result);
    }
    public static boolean melee(Profile p){
        return p.occupation()==Occupation.ADVENTURER&&(p.combatClass()==CombatClass.SWORDSMAN||p.combatClass()==CombatClass.KNIGHT||p.combatClass()==CombatClass.ASSASSIN);
    }
    public static boolean supported(Profile p){return p.occupation()==Occupation.ADVENTURER&&HuntStyle.of(p.combatClass())!=null;}
    public static boolean ready(State s){
        return supported(s.profile())&&s.needs().health()>=70&&s.needs().equipment()>=40&&s.needs().supplies()>=25;
    }
}
