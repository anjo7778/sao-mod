package dev.anjo7778.sao.entity;

import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.*;

import static dev.anjo7778.sao.simulation.NpcDailyPlan.*;

/**
 * Temporary visible representation of a durable roster profile. The roster is authoritative.
 * No item drops, permanent death, inventory economy or adventurer combat in this initial slice.
 */
public final class CityNpc extends PathfinderMob {
    private long floor;
    private Profile profile;
    private Plan plan;
    private List<Point> route=List.of();
    private int waypoint,failedAttempts,awayTicks;
    private long nextPath,lastGreeting=-1000,conversationUntil;
    private UUID conversationPeer;
    public CityNpc(EntityType<? extends CityNpc> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes()
        .add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.27).add(Attributes.FOLLOW_RANGE,40);}
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,8));
        goalSelector.addGoal(6,new RandomLookAroundGoal(this));
    }
    public void bind(State state){
        if(profile!=null)throw new IllegalStateException("Already bound");
        profile=state.profile();floor=profile.originFloor();
        setUUID(profile.id());
        setCustomName(Component.literal(profile.name()));
        setCustomNameVisible(false);
        setPersistenceRequired();
        setInvulnerable(true);
        setCanPickUpLoot(false);
        setHealth(getMaxHealth()*Math.max(1,state.needs().health())/100f);
        plan=level() instanceof ServerLevel serverLevel?AdventureSimulationData.plan(serverLevel,state):NpcDailyPlan.at(state,level().getDayTime());
        updateDisplayItem();
    }
    public long originFloor(){return floor;}
    public boolean atScheduledDestination(){
        if(plan==null)return false;
        var c=FloorLayout.center(floor);
        return distanceToSqr(c.x()+plan.destination().x()+.5,CityPlan.GROUND+1,c.z()+plan.destination().z()+.5)<=16;
    }
    public String activity(){return plan==null?"unassigned":plan.activity().name();}
    public boolean canConverse(){
        return profile!=null&&isAlive()&&plan!=null&&
            (plan.activity()==Activity.SOCIALIZE||plan.activity()==Activity.PREPARE)
            &&level().getGameTime()>=conversationUntil;
    }
    public UUID conversationPartner(){return level().getGameTime()<conversationUntil?conversationPeer:null;}
    public void beginConversation(CityNpc other){
        if(other==null||other==this||other.originFloor()!=floor||!canConverse())return;
        conversationPeer=other.getUUID();conversationUntil=level().getGameTime()+65;
        getNavigation().stop();getLookControl().setLookAt(other,30,30);
    }
    private void endConversation(){
        conversationPeer=null;conversationUntil=0;
    }
    public void updateDisplayItem(){
        if(profile==null)return;
        Item item=null;
        if(plan.activity()==Activity.TRAIN){
            item=switch(profile.combatClass()){
                case ARCHER -> Items.BOW;
                case MAGE,HEALER -> Items.BOOK;
                case KNIGHT -> Items.IRON_SWORD;
                default -> Items.WOODEN_SWORD;
            };
        }else if(plan.activity()==Activity.WORK){
            item=switch(profile.occupation()){
                case BLACKSMITH -> Items.IRON_PICKAXE;
                case GUILD_CLERK,QUEST_CLERK -> Items.BOOK;
                default -> null;
            };
        }
        setItemSlot(EquipmentSlot.MAINHAND,item==null?ItemStack.EMPTY:new ItemStack(item));
        setDropChance(EquipmentSlot.MAINHAND,0);
    }
    @Override public void tick(){
        super.tick();
        if(!(level() instanceof ServerLevel level)||!isAlive())return;
        if(profile==null||!CityRules.isCity(level,getBlockX(),getBlockZ())||FloorTravel.currentFloor(level,getBlockX(),getBlockZ())!=floor){
            discard();return;
        }
        if(tickCount%20==0){
            boolean near=level.players().stream().anyMatch(p->!p.isSpectator()&&p.distanceToSqr(this)<=100*100);
            awayTicks=near?0:awayTicks+20;
            if(awayTicks>=100){discard();return;}
        }
        if(tickCount%100==Math.floorMod(profile.id().hashCode(),100)){
            var data=NpcPopulationData.get(level.getServer());
            State state=data.state(floor,getUUID());
            if(state==null){discard();return;}
            Plan next=AdventureSimulationData.plan(level,state);
            if(!next.equals(plan)){
                endConversation();plan=next;route=List.of();waypoint=0;getNavigation().stop();failedAttempts=0;updateDisplayItem();
            }
            var c=FloorLayout.center(floor);
            if(plan.activity()==Activity.REST&&distanceToSqr(c.x()+plan.destination().x()+.5,CityPlan.GROUND+1,c.z()+plan.destination().z()+.5)<16){
                int hp=state.needs().health();
                if(hp<100){
                    var n=state.needs();int nextHp=Math.min(100,hp+2);
                    data.updateNeeds(floor,getUUID(),new AdventurerMind.Needs(nextHp,n.supplies(),n.equipment(),n.partyReadiness(),n.danger()));
                    setHealth(getMaxHealth()*nextHp/100f);
                }
            }
            if(plan.activity()==Activity.TRAIN&&getNavigation().isDone()&&getRandom().nextInt(3)==0&&!getMainHandItem().isEmpty())
                swing(InteractionHand.MAIN_HAND);
        }
        if(conversationPeer!=null){
            if(level.getGameTime()<conversationUntil&&level.getEntity(conversationPeer) instanceof CityNpc peer
                &&peer.isAlive()&&distanceToSqr(peer)<=49&&hasLineOfSight(peer)
                &&getUUID().equals(peer.conversationPartner())){
                getNavigation().stop();
                getLookControl().setLookAt(peer,30,30);
                return;
            }
            endConversation();
        }
        if(level.getGameTime()<nextPath)return;
        nextPath=level.getGameTime()+20;
        var center=FloorLayout.center(floor);
        Point here=new Point(getBlockX()-center.x(),getBlockZ()-center.z());
        if(route.isEmpty()||waypoint>=route.size()){
            double d=Math.hypot(here.x()-plan.destination().x(),here.z()-plan.destination().z());
            if(d<=2){
                if(!getNavigation().isDone()||plan.activity()==Activity.REST)return;
                // Small local strolls at the current workplace/plaza, not fake long-distance movement.
                int x=plan.destination().x()+getRandom().nextInt(9)-4,z=plan.destination().z()+getRandom().nextInt(9)-4;
                Point dest=new Point(x,z);
                if(canWalk(level,center,dest)){
                    getNavigation().moveTo(center.x()+x+.5,CityPlan.GROUND+1,center.z()+z+.5,.8);
                    nextPath+=100;
                }
                return;
            }
            route=NpcDailyPlan.route(floor,here,plan.destination());waypoint=0;failedAttempts=0;
        }
        if(waypoint>=route.size())return;
        Point p=route.get(waypoint);
        if(Math.hypot(here.x()-p.x(),here.z()-p.z())<2){waypoint++;failedAttempts=0;return;}
        if(!getNavigation().isDone())return;
        if(!canWalk(level,center,p)||!getNavigation().moveTo(center.x()+p.x()+.5,CityPlan.GROUND+1,center.z()+p.z()+.5,.9)){
            if(++failedAttempts>=3){route=List.of();waypoint=0;nextPath+=120;}
        }else failedAttempts=0;
    }
    private boolean canWalk(ServerLevel level,FloorLayout.Center c,Point p){
        if(!CityPlan.inside(floor,p.x(),p.z()))return false;
        var pos=new BlockPos(c.x()+p.x(),CityPlan.GROUND+1,c.z()+p.z());
        return level.hasChunkAt(pos)&&level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
            &&level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
            &&!level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty();
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){
        if(!player.getItemInHand(hand).isEmpty())return InteractionResult.PASS;
        if(level().isClientSide)return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer p)||profile==null||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(level().getGameTime()-lastGreeting<20)return InteractionResult.CONSUME;
        lastGreeting=level().getGameTime();
        var data=NpcPopulationData.get(p.serverLevel().getServer());var state=data.state(floor,getUUID());
        if(state==null)return InteractionResult.PASS;
        p.sendSystemMessage(Component.literal(profile.name()+": "+NpcDialogue.greetingText(state,p.getUUID(),plan.activity())));
        data.remember(floor,getUUID(),NpcDialogue.greeting(getUUID(),p.getUUID(),Math.max(0,level().getDayTime()/24000)));
        getLookControl().setLookAt(p,30,30);
        return InteractionResult.CONSUME;
    }
}
