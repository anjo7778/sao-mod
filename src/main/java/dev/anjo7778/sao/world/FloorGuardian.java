package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.FloorLayout;
import dev.anjo7778.sao.simulation.CityPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.UUID;

/** Stage-one real encounter. Later boss design can replace the Ravager fixture without replacing progression. */
public final class FloorGuardian {
    private static final String TAG="sao_guardian_floor";
    private static final String HOME="sao_guardian_home";
    private FloorGuardian() {}

    public static int challenge(ServerPlayer p) {
        var level=p.serverLevel();var server=level.getServer();
        if(!FloorTravel.isAincrad(server)||server.getLevel(FloorTravel.UPPER)==null) return reply(p,"A fresh Aincrad world with upper floors is required.");
        long floor=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ());
        var data=SaoWorldData.get(server);
        if(floor!=data.highestUnlocked()||floor>=FloorLayout.MAX_FLOOR) return reply(p,"Challenge the guardian on your highest unlocked floor.");
        if(p.isSpectator()||!p.isAlive()||level.getDifficulty()==Difficulty.PEACEFUL) return reply(p,"Guardians require a living player and non-Peaceful difficulty.");
        var c=FloorLayout.center(floor);
        long distance=Math.max(Math.abs((long)p.getBlockX()-c.x()),Math.abs((long)p.getBlockZ()-c.z()));
        boolean city=CityRules.hasCities(level);
        if(city&&(CityRules.isSafeZone(level,p.getBlockX(),p.getBlockZ())||distance>CityPlan.half(floor)+192))
            return reply(p,"Challenge outside the capital, within 192 blocks of its outer wall.");
        if(!city&&distance>96)return reply(p,"Return to the central courtyard (within 96 blocks of the floor center).");
        UUID active=data.activeGuardian();
        if(active!=null) return reply(p,"A guardian is already active. Defeat it or use /sao abandon to reset the encounter.");
        Ravager boss=EntityType.RAVAGER.create(level);
        if(boss==null) return reply(p,"Guardian creation failed.");
        int[][] offsets={{16,0},{-16,0},{0,16},{0,-16},{16,16},{-16,-16}};
        BlockPos home=null;
        for(int[] d:offsets){
            BlockPos candidate=FloorTravel.safeArrival(level,p.getBlockX()+d[0],p.getBlockZ()+d[1]);
            if(candidate==null||candidate.distSqr(p.blockPosition())<64||CityRules.isSafeZone(level,candidate.getX(),candidate.getZ())) continue;
            boss.moveTo(candidate.getX()+.5,candidate.getY(),candidate.getZ()+.5,0,0);
            if(level.noCollision(boss)){home=candidate;break;}
        }
        if(home==null) return reply(p,"No clear guardian arena nearby; move to open ground and try again.");
        boss.setCustomName(Component.literal("Floor "+floor+" Guardian"));
        boss.setCustomNameVisible(true);
        boss.setPersistenceRequired();
        double hp=Math.min(500,100+30*Math.log(floor));
        var max=boss.getAttribute(Attributes.MAX_HEALTH);
        if(max!=null) max.setBaseValue(hp);
        boss.setHealth(boss.getMaxHealth());
        var attack=boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if(attack!=null) attack.setBaseValue(Math.min(36,12+2*Math.log(floor)));
        boss.getPersistentData().putLong(TAG,floor);
        boss.getPersistentData().putLong(HOME,home.asLong());
        // Reserve UUID before spawn; roll back if a mod or engine rejects it.
        if(!data.beginGuardian(floor,boss.getUUID())) return reply(p,"Encounter already active.");
        if(!level.addFreshEntity(boss)){data.clearGuardian(boss.getUUID());return reply(p,"Guardian spawn rejected.");}
        p.sendSystemMessage(Component.literal("Floor "+floor+" Guardian challenge started. Defeat the guardian to unlock the next floor."));
        return 1;
    }

    public static int abandon(ServerPlayer p) {
        var level=p.serverLevel();
        if(!FloorTravel.isAincrad(level.getServer())) return reply(p,"Not an Aincrad world.");
        var data=SaoWorldData.get(level.getServer());
        long floor=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ());
        if(floor!=data.highestUnlocked()) return reply(p,"Return to the active frontier floor first.");
        UUID id=data.activeGuardian();
        if(id==null) return reply(p,"No active guardian.");
        if(level.getEntity(id) instanceof Ravager boss) boss.discard();
        data.clearGuardian(id);
        p.sendSystemMessage(Component.literal("Guardian challenge abandoned. No floor was unlocked."));
        return 1;
    }

    public static void death(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof Ravager boss)||!(boss.level() instanceof ServerLevel level))return;
        long floor=boss.getPersistentData().getLong(TAG);
        if(floor<1||!FloorTravel.isAincrad(level.getServer()))return;
        var data=SaoWorldData.get(level.getServer());
        UUID id=boss.getUUID();
        if(!id.equals(data.activeGuardian())) return;
        if(event.getSource().getEntity() instanceof ServerPlayer p && !p.isSpectator()
            && p.serverLevel()==level && FloorTravel.currentFloor(level,boss.getBlockX(),boss.getBlockZ())==floor
            && FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ())==floor
            && data.completeGuardian(floor,id)) {
            level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Floor "+floor+" cleared! Floor "+(floor+1)+" is now unlocked."),false);
        } else data.clearGuardian(id);
    }

    /** A stale challenge entity may be loaded after abandoning an unloaded encounter. */
    public static void tick(EntityTickEvent.Post event) {
        if(!(event.getEntity() instanceof Ravager boss)||!(boss.level() instanceof ServerLevel level)||boss.tickCount%20!=0||!boss.isAlive())return;
        long floor=boss.getPersistentData().getLong(TAG);
        if(floor<1||!FloorTravel.isAincrad(level.getServer()))return;
        var data=SaoWorldData.get(level.getServer());
        if(!boss.getUUID().equals(data.activeGuardian())||floor!=data.highestUnlocked()){boss.discard();return;}
        BlockPos home=BlockPos.of(boss.getPersistentData().getLong(HOME));
        if(CityRules.isSafeZone(level,boss.getBlockX(),boss.getBlockZ())||boss.blockPosition().distSqr(home)>96*96 || boss.getY()<level.getMinBuildHeight()+4
            || FloorTravel.currentFloor(level,boss.getBlockX(),boss.getBlockZ())!=floor){
            // Cancel instead of teleporting a boss into a potentially changed/blocked arena.
            data.clearGuardian(boss.getUUID());boss.discard();
        }
    }
    private static int reply(ServerPlayer p,String message){p.sendSystemMessage(Component.literal(message));return 0;}
}
