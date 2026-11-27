package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.AdventurerMind.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

public final class NpcRosterChecks {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        Set<UUID> all=new HashSet<>();
        long start=System.nanoTime();int total=0;
        for(long floor=1;floor<=1000;floor++){
            var a=NpcRoster.generate(42,floor);var b=NpcRoster.generate(42,floor);
            check(a.equals(b),"Non-deterministic roster");
            check(a.size()==(floor==1?148:60),"Unexpected population");
            check(a.stream().filter(s->s.profile().occupation()==Occupation.ADVENTURER).count()==(floor==1?48:24),"Adventurer count");
            var roles=EnumSet.noneOf(Occupation.class);
            for(var s:a){
                var p=s.profile();roles.add(p.occupation());check(all.add(p.id()),"Identity collision");
                check(p.originFloor()==floor,"Wrong origin");
                check(CityPlan.inside(floor,p.homeX(),p.homeZ()),"Home outside capital");
                check(s.choose()!=null,"No decision");total++;
            }
            check(roles.size()==8,"Missing occupation");
        }
        var s=NpcRoster.generate(42,1).getFirst();
        var actor=new UUID(1,2);var mem=new Memory(new UUID(2,3),actor,2,EventType.RESCUED);
        var r=s.remember(mem);
        check(r.relationship(actor).trust()==75,"Memory relationship");
        check(r.remember(mem)==r,"Duplicate memory");
        check(r.withNeeds(new Needs(10,100,100,100,0)).choose()==Action.REST,"Needs decision");
        check(s.memories().isEmpty(),"Immutable snapshot");
        check(!s.profile().id().equals(NpcRoster.generate(43,1).getFirst().profile().id()),"World-specific identity");
        System.out.printf("PASS: %,d generated NPC records across 1,000 sequential capitals, %.1f ms%n",total,(System.nanoTime()-start)/1e6);
    }
}
