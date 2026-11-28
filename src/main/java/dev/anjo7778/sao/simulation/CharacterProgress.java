package dev.anjo7778.sao.simulation;

import java.util.*;

/** Shared player/NPC RPG progression. No gameplay cap and no vanilla XP mutation.
 * Event producers must authenticate real actions and supply monotonically increasing
 * per-character, per-source sequences; only authenticated server event adapters may award XP.
 */
public final class CharacterProgress {
    public enum Source { COMBAT, QUEST, DISCOVERY, CRAFT, GATHER, COOK, HEAL, TRADE, FISH, TRAIN }
    public enum Attribute { STRENGTH, VITALITY, AGILITY, DEXTERITY, INTELLIGENCE, ENDURANCE, LUCK }
    public enum Mastery {
        SWORD, LONGSWORD, GREATSWORD, CURVED_SWORD, KATANA, RAPIER, SABRE, DAGGER,
        DUAL_SWORDS, DUAL_DAGGERS, SWORD_DAGGER, TWIN_KATANA, DUAL_AXES, DUAL_SICKLES,
        AXE, GREATAXE, MACE, CLUB, WARHAMMER, GREAT_HAMMER, FLAIL, SPEAR, PIKE, HALBERD, GLAIVE,
        LANCE, TRIDENT, SCYTHE, SICKLE, BOW, CROSSBOW, THROWN, UNARMED, GAUNTLET, CLAW,
        WHIP, CHAIN, CHAKRAM, SHIELD, SWORD_SHIELD, STAFF, WAND, SPELLBOOK, ORB,
        BLACKSMITHING, ARMORSMITHING, METALLURGY, MINING, HERBALISM, GATHERING, COOKING,
        BREWING, ALCHEMY, TAILORING, LEATHERWORK, JEWELCRAFT, ENCHANTING, COMMERCE, FISHING,
        EXPLORATION, HEALING
    }
    public record State(long xp,Map<Attribute,Long> attributes,Map<Mastery,Long> masteryXp,Map<Source,Long> watermarks){
        public State{
            if(xp<0)throw new IllegalArgumentException("Negative XP");
            attributes=validated(Attribute.class,attributes);
            masteryXp=validated(Mastery.class,masteryXp);
            watermarks=validated(Source.class,watermarks);
            long points=pointsFor(xp);
            for(long value:attributes.values()){
                if(value>points)throw new IllegalArgumentException("Excess allocated attribute points");
                points-=value;
            }
        }
        public long level(){return levelFor(xp,50);}
        public long unspent(){return pointsFor(xp)-attributes.values().stream().mapToLong(Long::longValue).sum();}
        public long attribute(Attribute a){return 10+attributes.getOrDefault(a,0L);}
        public long mastery(Mastery m){return levelFor(masteryXp.getOrDefault(m,0L),25);}
        public long masteryExperience(Mastery m){return masteryXp.getOrDefault(m,0L);}
        /** Bounded normalized attribute contribution, not yet connected to damage or movement. */
        public double attributeBonus(Attribute a){double p=attributes.getOrDefault(a,0L);return p/(p+100.0);}
        public long xpIntoLevel(){return xp-threshold(level(),50);}
        public long xpForNextLevel(){return 100*level();}
    }
    public static final State EMPTY=new State(0,Map.of(),Map.of(),Map.of());
    private CharacterProgress(){}
    private static <E extends Enum<E>> Map<E,Long> validated(Class<E> type,Map<E,Long> source){
        var result=new EnumMap<E,Long>(type);
        source.forEach((k,v)->{if(k==null||v==null||v<=0)throw new IllegalArgumentException("Invalid progression entry");result.put(k,v);});
        return Collections.unmodifiableMap(result);
    }
    /** Total threshold: factor * (level - 1) * level. Division avoids overflow during search. */
    private static long levelFor(long xp,long factor){
        if(xp<0)throw new IllegalArgumentException("Negative XP");
        long lo=1,hi=1_000_000_001L; // strict search bound for all nonnegative signed-long XP
        while(lo+1<hi){
            long mid=(lo+hi)>>>1;
            if(mid-1<=xp/factor/mid)lo=mid;else hi=mid;
        }
        return lo;
    }
    private static long threshold(long level,long factor){
        if(level<1)throw new IllegalArgumentException("Invalid level");
        return Math.multiplyExact(Math.multiplyExact(level-1,level),factor);
    }
    public static long levelForXp(long xp){return levelFor(xp,50);}
    public static long pointsFor(long xp){return 3*(levelForXp(xp)-1);}
    /** Replay-safe, all-or-nothing event application. A zero character-XP training action may award mastery. */
    public static State award(State before,Source source,long sequence,long xp,Mastery mastery,long proficiency){
        Objects.requireNonNull(before);Objects.requireNonNull(source);
        if(sequence<=0||xp<0||xp>1_000_000||proficiency<0||proficiency>1_000_000
            ||mastery==null&&proficiency!=0||xp==0&&proficiency==0)throw new IllegalArgumentException("Invalid XP event");
        if(sequence<=before.watermarks().getOrDefault(source,0L))return before;
        long total=Math.addExact(before.xp(),xp);
        var tracks=new EnumMap<Mastery,Long>(Mastery.class);tracks.putAll(before.masteryXp());
        if(proficiency>0)tracks.put(mastery,Math.addExact(before.masteryExperience(mastery),proficiency));
        var marks=new EnumMap<Source,Long>(Source.class);marks.putAll(before.watermarks());marks.put(source,sequence);
        return new State(total,before.attributes(),tracks,marks);
    }
    public static State allocate(State before,Attribute attribute,long points){
        if(attribute==null||points<=0||points>before.unspent())return before;
        var next=new EnumMap<Attribute,Long>(Attribute.class);next.putAll(before.attributes());
        next.merge(attribute,points,Math::addExact);
        return new State(before.xp(),next,before.masteryXp(),before.watermarks());
    }
}
