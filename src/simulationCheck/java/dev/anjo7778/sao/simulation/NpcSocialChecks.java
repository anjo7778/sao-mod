package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.AdventurerMind.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

public final class NpcSocialChecks {
    private static int count;
    private static void check(boolean b,String message){count++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        int friendly=0,disagreements=0;
        for(long floor=1;floor<=100;floor++){
            var roster=NpcRoster.generate(42,floor);
            for(int i=0;i<roster.size()-1;i++){
                var a=roster.get(i);var b=roster.get(i+1);
                var e=NpcSocial.offer(a,b,4);
                if(e.isEmpty())continue;
                var m=e.get();var reverse=NpcSocial.offer(b,a,4).orElseThrow();
                check(m.first().eventId().equals(m.second().eventId()),"Shared event ID");
                check(m.first().equals(reverse.second())&&m.second().equals(reverse.first()),"Reciprocal outcome");
                check(m.first().actor().equals(b.profile().id())&&m.second().actor().equals(a.profile().id()),"Actor IDs");
                var ra=a.remember(m.first());var rb=b.remember(m.second());
                check(NpcSocial.offer(ra,rb,4).isEmpty(),"Daily duplicate");
                check(NpcSocial.offer(ra,rb,3).isEmpty(),"Rewound day duplicate");
                check(NpcSocial.offer(ra,rb,5).isPresent(),"Next day available");
                check(ra.relationship(b.profile().id()).equals(rb.relationship(a.profile().id())),"Mutual relationship");
                if(m.friendly()){friendly++;check(ra.relationship(b.profile().id()).friendship()==3,"Friendly increment");}
                else {disagreements++;check(ra.relationship(b.profile().id()).trust()==47,"Disagreement trust");}
            }
        }
        check(friendly>0&&disagreements>0,"Both outcomes exercised");
        var roster=NpcRoster.generate(42,1);
        State a=roster.stream().filter(s->s.profile().personality().sociability()>=50).findFirst().orElseThrow();
        UUID id=a.profile().id();
        for(int i=0;i<roster.size()&&NpcSocial.dailyCount(a,9)<NpcSocial.DAILY_LIMIT;i++){
            var e=NpcSocial.offer(a,roster.get(i),9);
            if(e.isPresent())a=a.remember(e.get().first());
        }
        check(NpcSocial.dailyCount(a,9)==NpcSocial.DAILY_LIMIT,"Daily interaction budget");
        for(State b:roster)check(NpcSocial.offer(a,b,9).isEmpty(),"Budget enforced for every other actor");
        check(NpcSocial.offer(a,NpcRoster.generate(42,2).getFirst(),10).isEmpty(),"Cross-floor forbidden");
        check(NpcSocial.offer(a,a,10).isEmpty(),"Self conversation forbidden");
        check(a.profile().id().equals(id),"Identity retained");
        System.out.printf("PASS: %,d social checks; %,d friendly exchanges and %,d disagreements%n",count,friendly,disagreements);
    }
}
