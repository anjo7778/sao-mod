package dev.anjo7778.sao.simulation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static dev.anjo7778.sao.simulation.AdventurerMind.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

public final class NpcDialogue {
    private NpcDialogue(){}
    public static Memory greeting(UUID npc,UUID player,long day){
        UUID id=UUID.nameUUIDFromBytes(("sao-greeting:"+npc+":"+player+":"+day).getBytes(StandardCharsets.UTF_8));
        return new Memory(id,player,day,EventType.GREETED);
    }
    public static String greetingText(State s,UUID player,NpcDailyPlan.Activity activity){
        var p=s.profile();var relation=s.relationship(player);
        String opening=relation.trust()<35?"I'd prefer to keep my distance. ":relation.friendship()>=15?"Good to see you again, friend. ":
            s.memories().stream().anyMatch(m->m.actor().equals(player)&&m.type()==EventType.GREETED)?"We have met before. ":"Hello, traveller. ";
        String detail=switch(activity){
            case REST -> "I'm taking some time to recover at the inn or at home.";
            case TRAIN -> "I'm practising for the next expedition.";
            case PREPARE -> "I'm getting ready for the day's adventure.";
            case SOCIALIZE -> "I'm enjoying some time in the capital.";
            case WORK -> switch(p.occupation()){
                case BLACKSMITH -> "The forge is my workplace.";
                case MERCHANT -> "I work in the market district.";
                case INNKEEPER -> "I look after the inn.";
                case HEALER -> "I work at the clinic.";
                case GUILD_CLERK -> "I help at the guild hall.";
                case QUEST_CLERK -> "I work at the quest hall.";
                default -> "I'm attending to my daily routine.";
            };
        };
        boolean rescued=s.memories().stream().anyMatch(m->m.actor().equals(player)&&m.type()==EventType.RESCUED);
        if(rescued&&relation.trust()>=65)opening="I still remember when you helped me. ";
        return opening+detail+(activity==NpcDailyPlan.Activity.SOCIALIZE?" "+NpcSocial.aspiration(p.ambition()):"");
    }
}
