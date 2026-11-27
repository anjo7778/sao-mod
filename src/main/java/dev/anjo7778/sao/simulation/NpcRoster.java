package dev.anjo7778.sao.simulation;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.anjo7778.sao.simulation.AdventurerMind.*;

/** Immutable, stable identities and bounded state for one visited capital; no entity or chunk allocation. */
public final class NpcRoster {
    public enum Occupation { RESIDENT, INNKEEPER, MERCHANT, GUILD_CLERK, BLACKSMITH, HEALER, QUEST_CLERK, ADVENTURER }
    public enum CombatClass { NONE, SWORDSMAN, KNIGHT, ARCHER, ASSASSIN, MAGE, HEALER }
    public enum Ambition { STABLE_LIFE, MASTERY, WEALTH, DISCOVERY, FRIENDSHIP, LEADERSHIP }
    public record Profile(UUID id,String name,long originFloor,Occupation occupation,CombatClass combatClass,
                          Ambition ambition,int homeX,int homeZ,int workX,int workZ,Personality personality) {
        public Profile {
            Objects.requireNonNull(id);Objects.requireNonNull(name);Objects.requireNonNull(occupation);
            Objects.requireNonNull(combatClass);Objects.requireNonNull(ambition);Objects.requireNonNull(personality);
            if(!FloorLayout.supported(originFloor)||name.isBlank()||name.length()>80)throw new IllegalArgumentException("Invalid identity");
            if(!CityPlan.inside(originFloor,homeX,homeZ)||!CityPlan.inside(originFloor,workX,workZ))throw new IllegalArgumentException("Invalid capital destination");
            if((occupation==Occupation.ADVENTURER)!=(combatClass!=CombatClass.NONE))throw new IllegalArgumentException("Invalid class");
        }
    }
    public record State(Profile profile,Needs needs,List<Memory> memories) {
        public State {
            Objects.requireNonNull(profile);Objects.requireNonNull(needs);
            memories=List.copyOf(memories);
            if(memories.size()>128||memories.stream().map(Memory::eventId).distinct().count()!=memories.size())
                throw new IllegalArgumentException("Invalid bounded memories");
        }
        private AdventurerMind mind(){
            var mind=new AdventurerMind(profile.personality());memories.forEach(mind::remember);return mind;
        }
        public Action choose(){return mind().choose(needs);}
        public Relationship relationship(UUID actor){return mind().relationship(actor);}
        public State remember(Memory event){
            var m=mind();
            return m.remember(event)?new State(profile,needs,m.memories()):this;
        }
        public State withNeeds(Needs n){return new State(profile,n,memories);}
    }
    private static final String[] FIRST={"Alden","Aria","Bram","Celia","Darian","Elena","Finn","Galen","Hana","Iris","Joren","Kael","Lena","Mira","Nolan","Orin","Perrin","Quinn","Raven","Sera","Talia","Vera","Wren","Yuna"};
    private static final String[] LAST={"Ashford","Brightwood","Crowe","Dawnwell","Everhart","Fairwind","Graybrook","Hawthorn","Ironvale","Larkspur","Moonfall","Northwind","Oakheart","Rivers","Silvermere","Stonebridge","Thorne","Vale","Westbrook","Winter"};
    private NpcRoster(){}

    public static List<State> generate(long seed,long floor){
        if(!FloorLayout.supported(floor))throw new IllegalArgumentException("Invalid floor");
        var rnd=new SplittableRandom(seed ^ (floor*0x9E3779B97F4A7C15L));
        var plots=new ArrayList<CityPlan.Plot>();var inns=new ArrayList<CityPlan.Plot>();
        int limit=CityPlan.half(floor)/CityPlan.LOT;
        for(int x=-limit;x<=limit;x++)for(int z=-limit;z<=limit;z++){
            var p=CityPlan.plot(floor,x,z);
            if(p!=null){plots.add(p);if(p.role()==CityPlan.Role.INN)inns.add(p);}
        }
        if(inns.isEmpty())throw new IllegalStateException("Capital has no inn");
        List<State> roster=new ArrayList<>();
        int slot=0;
        for(var p:plots){
            var occupation=switch(p.role()){
                case HOUSE->Occupation.RESIDENT;
                case INN->Occupation.INNKEEPER;
                case MARKET->Occupation.MERCHANT;
                case GUILD->Occupation.GUILD_CLERK;
                case FORGE->Occupation.BLACKSMITH;
                case CLINIC->Occupation.HEALER;
                case QUEST_HALL->Occupation.QUEST_CLERK;
            };
            roster.add(create(seed,floor,slot++,rnd,occupation,CombatClass.NONE,p.x(),p.z(),p.x(),p.z()));
        }
        int count=floor==1?48:24;
        for(int i=0;i<count;i++){
            var inn=inns.get(i%inns.size());
            var cls=CombatClass.values()[1+rnd.nextInt(CombatClass.values().length-1)];
            roster.add(create(seed,floor,slot++,rnd,Occupation.ADVENTURER,cls,inn.x(),inn.z(),0,0));
        }
        return List.copyOf(roster);
    }
    private static State create(long seed,long floor,int slot,SplittableRandom rnd,Occupation role,CombatClass cls,int hx,int hz,int wx,int wz){
        UUID id=UUID.nameUUIDFromBytes(("sao-population-v1:"+seed+":"+floor+":"+slot).getBytes(StandardCharsets.UTF_8));
        String name=FIRST[rnd.nextInt(FIRST.length)]+" "+LAST[rnd.nextInt(LAST.length)];
        var personality=new Personality(trait(rnd),trait(rnd),trait(rnd),trait(rnd));
        var ambitions=Ambition.values();
        var ambition=role==Occupation.ADVENTURER?ambitions[1+rnd.nextInt(ambitions.length-1)]:ambitions[rnd.nextInt(ambitions.length)];
        var p=new Profile(id,name,floor,role,cls,ambition,hx,hz,wx,wz,personality);
        return new State(p,new Needs(100,65+rnd.nextInt(36),80+rnd.nextInt(21),0,0),List.of());
    }
    private static int trait(SplittableRandom r){return 15+r.nextInt(71);}
}
