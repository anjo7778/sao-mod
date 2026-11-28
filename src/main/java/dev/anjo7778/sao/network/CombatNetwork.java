package dev.anjo7778.sao.network;

import dev.anjo7778.sao.simulation.SwordActions;
import dev.anjo7778.sao.world.SwordCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

/** Client sends only an action intent and target/direction; the server owns timing, damage and stamina. */
public final class CombatNetwork {
    private static volatile Snapshot client;
    private CombatNetwork(){}
    public static Snapshot snapshot(){return client;}
    public static void clear(){client=null;}
    public static void intent(int action,int direction){PacketDistributor.sendToServer(new Intent(action,direction));}
    public record Intent(int action,int direction) implements CustomPacketPayload{
        public static final Type<Intent> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("sao","combat_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Intent> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.action());b.writeVarInt(p.direction());},b->new Intent(b.readVarInt(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(UUID owner,boolean available,int stamina,int phase,int move,int combo,int skillCooldown,boolean skillUnlocked,int pose,int selectedSkill,int landedHits) implements CustomPacketPayload{
        public Snapshot{
            if(owner==null||stamina<0||stamina>SwordActions.MAX_STAMINA||phase<0||phase>=SwordActions.Phase.values().length
                ||move<0||move>=SwordActions.Move.values().length||combo<0||combo>2||skillCooldown<0||skillCooldown>SwordActions.MAX_SKILL_COOLDOWN
                ||SwordActions.Skill.fromIndex(selectedSkill)==null||landedHits<0||landedHits>SwordActions.Move.values()[move].hits)
                throw new IllegalArgumentException("Invalid combat snapshot");
        }
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("sao","combat_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
            (b,p)->{b.writeUUID(p.owner());b.writeBoolean(p.available());b.writeVarInt(p.stamina());b.writeVarInt(p.phase());
                b.writeVarInt(p.move());b.writeVarInt(p.combo());b.writeVarInt(p.skillCooldown());b.writeBoolean(p.skillUnlocked());b.writeInt(p.pose());b.writeVarInt(p.selectedSkill());b.writeVarInt(p.landedHits());},
            b->new Snapshot(b.readUUID(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readInt(),b.readVarInt(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void send(ServerPlayer p,boolean available,int stamina,SwordActions.Phase phase,SwordActions.Move move,
            int combo,int skillCooldown,boolean skillUnlocked,int pose,int selectedSkill,int landedHits){
        PacketDistributor.sendToPlayer(p,new Snapshot(p.getUUID(),available,stamina,phase.ordinal(),move.ordinal(),combo,skillCooldown,skillUnlocked,pose,selectedSkill,landedHits));
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("1");
        r.playToServer(Intent.TYPE,Intent.CODEC,(payload,context)->context.enqueueWork(()->{
            boolean valid=switch(payload.action()){
                case 0,3,4->payload.direction()==0;
                case 1->payload.direction()>=0&&payload.direction()<=3;
                default->false;
            };
            if(context.player() instanceof ServerPlayer p&&valid)SwordCombat.intent(p,payload.action(),payload.direction());
        }));
        r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(payload,context)->context.enqueueWork(()->client=payload));
    }
}
