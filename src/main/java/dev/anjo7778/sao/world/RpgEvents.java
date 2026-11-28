package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.CharacterProgress.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Real server events only. No arbitrary player-reported XP, simulated kills or historical awards. */
public final class RpgEvents {
    private static final String KILL_CREDIT="sao_rpg_kill_credited";
    private RpgEvents(){}
    private static CharacterProgressData.Owner player(ServerPlayer p){return new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID());}
    private static CharacterProgressData.Owner npc(Profile p){return new CharacterProgressData.Owner(CharacterProgressData.Kind.NPC,p.id());}
    private static boolean eligible(ServerPlayer p){
        return !p.isCreative()&&!p.isSpectator()&&CityRules.hasRegionalSettlements(p.serverLevel())
            &&SaoWorldData.get(p.serverLevel().getServer()).isUnlocked(FloorTravel.currentFloor(p.serverLevel(),p.getBlockX(),p.getBlockZ()));
    }
    private static long combatXp(long floor,long level){
        long reference=1+Math.min(1_000_000L,Math.max(0,floor-1)*5);
        // Conservative temporary floor band; fixed monster levels will replace it.
        long gap=Math.max(0,level-reference-4);
        return Math.max(1,20/(1+gap/5));
    }
    public static void playerKill(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof Mob victim)||!HuntService.allowedType(victim.getType())
            ||!(victim.level() instanceof ServerLevel level)||!(event.getSource().getEntity() instanceof ServerPlayer p)
            ||p.serverLevel()!=level||!eligible(p)||victim.getPersistentData().getBoolean(KILL_CREDIT)
            ||CityRules.isSafeZone(level,victim.getBlockX(),victim.getBlockZ()))return;
        long floor=FloorTravel.currentFloor(level,victim.getBlockX(),victim.getBlockZ());
        if(floor!=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ()))return;
        Mastery mastery=null;
        if(event.getSource().getDirectEntity()==p){
            var weapon=p.getMainHandItem();
            var family=WeaponItems.classify(weapon);
            // A vanilla main-hand strike earns only that actual weapon's mastery.
            // An equipped pair is not proof of a dual-wield skill or offhand strike.
            if(weapon.isEmpty())mastery=Mastery.UNARMED;
            else if(family!=null)mastery=family.mastery;
        }
        var data=CharacterProgressData.get(level.getServer());var owner=player(p);
        long xp=combatXp(floor,data.state(owner).level());
        if(data.awardNext(owner,Source.COMBAT,xp,mastery,mastery==null?0:xp)){
            victim.getPersistentData().putBoolean(KILL_CREDIT,true);
        }
    }
    public static void discover(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||p.tickCount%40!=0||!p.isAlive()||!eligible(p))return;
        var level=p.serverLevel();int x=p.getBlockX(),z=p.getBlockZ();long floor=FloorTravel.currentFloor(level,x,z);
        int site=0;
        if(!CityRules.isCity(level,x,z)){
            var c=FloorLayout.center(floor);var regional=RegionalSettlements.at(floor,x-c.x(),z-c.z());
            if(regional==null)return;site=1+regional.kind().ordinal();
        }
        CharacterProgressData.get(level.getServer()).discover(player(p),floor,site);
    }
    public static void crafted(PlayerEvent.ItemCraftedEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!eligible(p)||event.getCrafting().isEmpty())return;
        var key=BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem());
        if(!key.getNamespace().equals("minecraft"))return;
        String name=key.getPath();
        if(!(name.startsWith("iron_")||name.startsWith("golden_")||name.startsWith("diamond_")))return;
        Mastery mastery;
        if(name.endsWith("_helmet")||name.endsWith("_chestplate")||name.endsWith("_leggings")||name.endsWith("_boots"))mastery=Mastery.ARMORSMITHING;
        else if(name.endsWith("_sword")||name.endsWith("_axe")||name.endsWith("_pickaxe")||name.endsWith("_shovel")||name.endsWith("_hoe"))mastery=Mastery.BLACKSMITHING;
        else return;
        var server=p.serverLevel().getServer();
        CharacterProgressData.get(server).craft(player(p),Math.max(0,server.overworld().getGameTime()/24000),mastery);
    }
    public static void npcHunt(ServerLevel level,Profile p,Mob victim,Entity direct){
        var data=CharacterProgressData.get(level.getServer());var owner=npc(p);
        // Prototype equipped weapons are known; do not grant dual-wield or other advanced mastery.
        Mastery m=direct instanceof Arrow?Mastery.BOW:Mastery.SWORD;
        int count=NpcPopulationData.get(level.getServer()).wallet(p).huntKills();
        long xp=combatXp(p.currentFloor(),data.state(owner).level());
        if(data.award(owner,Source.COMBAT,count,xp,m,xp)){
            data.awardNext(owner,Source.QUEST,10,null,0);allocateNpc(level.getServer(),data,p);
        }
    }
    public static void npcScouting(MinecraftServer server,Profile p){
        var data=CharacterProgressData.get(server);
        if(data.awardNext(npc(p),Source.QUEST,15,Mastery.EXPLORATION,8))allocateNpc(server,data,p);
    }
    public static void npcDiscovery(MinecraftServer server,Profile p,RegionalSettlements.Kind kind){
        var data=CharacterProgressData.get(server);
        if(data.discover(npc(p),p.currentFloor(),1+kind.ordinal()))allocateNpc(server,data,p);
    }
    public static void npcService(ServerLevel level,Profile p,int count){
        var data=CharacterProgressData.get(level.getServer());
        Mastery m=p.occupation()==Occupation.BLACKSMITH?Mastery.BLACKSMITHING:Mastery.COMMERCE;
        if(data.award(npc(p),Source.TRADE,count,4,m,6))allocateNpc(level.getServer(),data,p);
    }
    private static void allocateNpc(MinecraftServer server,CharacterProgressData data,Profile p){
        var owner=npc(p);long points=data.state(owner).unspent();if(points==0)return;
        Attribute primary,secondary;
        if(p.occupation()==Occupation.ADVENTURER){
            primary=switch(p.combatClass()){
                case KNIGHT->Attribute.VITALITY;case ARCHER,ASSASSIN->Attribute.DEXTERITY;
                case MAGE,HEALER->Attribute.INTELLIGENCE;default->Attribute.STRENGTH;
            };
            secondary=switch(p.combatClass()){
                case ARCHER,ASSASSIN->Attribute.AGILITY;case KNIGHT->Attribute.STRENGTH;
                default->Attribute.ENDURANCE;
            };
        }else{
            primary=p.occupation()==Occupation.BLACKSMITH?Attribute.STRENGTH:Attribute.INTELLIGENCE;
            secondary=p.occupation()==Occupation.MERCHANT?Attribute.LUCK:Attribute.ENDURANCE;
        }
        long first=points-points/3;
        data.allocate(owner,primary,first);
        if(points>first)data.allocate(owner,secondary,points-first);
        var level=p.currentFloor()==1?server.overworld():server.getLevel(FloorTravel.UPPER);
        if(level!=null&&level.getEntity(p.id()) instanceof CityNpc npc&&npc.boundAdventurer())
            npc.reviewMenu(new NpcMenuReview(NpcMenuReview.Kind.ATTRIBUTES,primary.name()+" +"+first+(points>first?"|"+secondary.name()+" +"+(points-first):"")));
    }
}
