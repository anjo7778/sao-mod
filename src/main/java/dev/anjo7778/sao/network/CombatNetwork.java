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

/** Client sends only a defensive intent; all timing, hit and stamina decisions stay on the server. */
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
    public record Snapshot(UUID owner,boolean available,int stamina,int phase) implements CustomPacketPayload{
        public Snapshot{
            if(owner==null||stamina<0||stamina>SwordActions.MAX_STAMINA||phase<0||phase>=SwordActions.Phase.values().length)
                throw new IllegalArgumentException("Invalid combat snapshot");
        }
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("sao","combat_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
            (b,p)->{b.writeUUID(p.owner());b.writeBoolean(p.available());b.writeVarInt(p.stamina());b.writeVarInt(p.phase());},
            b->new Snapshot(b.readUUID(),b.readBoolean(),b.readVarInt(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void send(ServerPlayer p,boolean available,int stamina,SwordActions.Phase phase){
        PacketDistributor.sendToPlayer(p,new Snapshot(p.getUUID(),available,stamina,phase.ordinal()));
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("1");
        r.playToServer(Intent.TYPE,Intent.CODEC,(payload,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer p&&payload.action()>=0&&payload.action()<=1
                &&payload.direction()>=0&&payload.direction()<=3)SwordCombat.intent(p,payload.action(),payload.direction());
        }));
        r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(payload,context)->context.enqueueWork(()->client=payload));
    }
}
