package dev.anjo7778.sao.simulation;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.anjo7778.sao.simulation.AdventurerMind.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

/** Reciprocal, bounded social encounters. No remote model and no invented world events. */
public final class NpcSocial {
    public static final int DAILY_LIMIT=3;
    public enum Topic { TRAINING, EQUIPMENT, CITY_LIFE, AMBITION }
    public record Encounter(Memory first,Memory second,Topic topic,boolean friendly,String lineFirst,String lineSecond){}
    private NpcSocial(){}

    public static boolean socialMemory(Memory m){
        return m.type()==EventType.CONVERSED || m.type()==EventType.DISAGREED;
    }
    public static long dailyCount(State s,long day){
        return s.memories().stream().filter(m->socialMemory(m)&&m.day()==day).count();
    }
    public static boolean canTalk(State a,State b,long day){
        if(a==null||b==null||day<0||a.profile().id().equals(b.profile().id())
            ||a.profile().originFloor()!=b.profile().originFloor())return false;
        if(dailyCount(a,day)>=DAILY_LIMIT||dailyCount(b,day)>=DAILY_LIMIT)return false;
        if(a.memories().stream().anyMatch(m->socialMemory(m)&&m.actor().equals(b.profile().id())&&m.day()>=day)
            ||b.memories().stream().anyMatch(m->socialMemory(m)&&m.actor().equals(a.profile().id())&&m.day()>=day))return false;
        return a.profile().personality().sociability()+b.profile().personality().sociability()>=45
            &&a.relationship(b.profile().id()).trust()>=25 && b.relationship(a.profile().id()).trust()>=25;
    }
    public static Optional<Encounter> offer(State a,State b,long day){
        if(!canTalk(a,b,day))return Optional.empty();
        var p=a.profile();var q=b.profile();
        UUID low=p.id().compareTo(q.id())<=0?p.id():q.id();
        UUID high=p.id().equals(low)?q.id():p.id();
        UUID id=UUID.nameUUIDFromBytes(("sao-social-v1:"+low+":"+high+":"+day).getBytes(StandardCharsets.UTF_8));
        var r=a.relationship(q.id());var s=b.relationship(p.id());
        int rapport=(r.trust()+s.trust())/2;
        int difference=Math.abs(p.personality().ambition()-q.personality().ambition());
        boolean friendly=!(difference>30&&rapport<70&&Math.floorMod(id.hashCode(),5)==0);
        var type=friendly?EventType.CONVERSED:EventType.DISAGREED;
        Topic topic;
        if(p.occupation()==Occupation.ADVENTURER&&q.occupation()==Occupation.ADVENTURER)
            topic=Math.floorMod(id.hashCode(),2)==0?Topic.TRAINING:Topic.AMBITION;
        else if(p.occupation()==Occupation.BLACKSMITH||q.occupation()==Occupation.BLACKSMITH
            ||p.occupation()==Occupation.MERCHANT||q.occupation()==Occupation.MERCHANT)topic=Topic.EQUIPMENT;
        else topic=Topic.CITY_LIFE;
        String line=switch(topic){
            case TRAINING -> "We should practise before attempting a difficult expedition.";
            case EQUIPMENT -> "Good equipment and enough supplies make a difference.";
            case CITY_LIFE -> "It's good to have somewhere safe to return to.";
            case AMBITION -> aspiration(p.ambition());
        };
        String answer=friendly?switch(topic){
            case TRAINING -> "Agreed. There is always something more to learn.";
            case EQUIPMENT -> "I would rather prepare properly than take unnecessary risks.";
            case CITY_LIFE -> "Yes. I hope this city continues to prosper.";
            case AMBITION -> aspiration(q.ambition());
        }:"I see things differently. We can talk another time.";
        return Optional.of(new Encounter(new Memory(id,q.id(),day,type),new Memory(id,p.id(),day,type),topic,friendly,line,answer));
    }
    public static String aspiration(Ambition a){
        return switch(a){
            case STABLE_LIFE -> "I'd like to make a stable life here.";
            case MASTERY -> "I want to become truly skilled at what I do.";
            case WEALTH -> "I hope to build up my savings and equipment.";
            case DISCOVERY -> "I want to discover places nobody has visited.";
            case FRIENDSHIP -> "I'd like to find companions I can trust.";
            case LEADERSHIP -> "Someday I'd like to lead a group of my own.";
        };
    }
}
