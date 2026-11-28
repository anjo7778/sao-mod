package dev.anjo7778.sao.network;

import dev.anjo7778.sao.simulation.CharacterProgress.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;

/** Only server-owned character state is synchronized. No client-authoritative XP or attributes. */
public final class ProfileNetwork {
    private ProfileNetwork(){}
    private static final Map<ServerPlayer,Long> LAST_ALLOCATION=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> LAST_READ=new WeakHashMap<>();
    private static volatile Snapshot clientSnapshot;
    private static long actionSequence;
    public static Snapshot clientSnapshot(){return clientSnapshot;}
    public static void clearClient(){clientSnapshot=null;}
    public static void request(){PacketDistributor.sendToServer(new Request(0,-1));}
    public static void allocate(Attribute a){PacketDistributor.sendToServer(new Request(++actionSequence,a.ordinal()));}
    public record Request(long sequence,int attribute) implements CustomPacketPayload{
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("sao","profile_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of(
            (b,p)->{b.writeLong(p.sequence());b.writeVarInt(p.attribute());},
            b->new Request(b.readLong(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(UUID owner,boolean available,String name,long floor,long level,long xp,long into,long required,long unspent,
                           List<Long> attributes,List<Long> masteries,List<Long> masteryXp) implements CustomPacketPayload{
        public Snapshot{
            attributes=List.copyOf(attributes);masteries=List.copyOf(masteries);masteryXp=List.copyOf(masteryXp);
            if(owner==null||name==null||name.length()>80||attributes.size()!=Attribute.values().length
                ||masteries.size()!=Mastery.values().length||masteryXp.size()!=Mastery.values().length)throw new IllegalArgumentException("Invalid profile snapshot");
        }
        public long attribute(Attribute a){return attributes.get(a.ordinal());}
        public long mastery(Mastery m){return masteries.get(m.ordinal());}
        public long experience(Mastery m){return masteryXp.get(m.ordinal());}
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("sao","profile_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of(
            (b,p)->{
                b.writeUUID(p.owner());b.writeBoolean(p.available());b.writeUtf(p.name(),80);
                b.writeVarLong(p.floor());b.writeVarLong(p.level());b.writeVarLong(p.xp());b.writeVarLong(p.into());
                b.writeVarLong(p.required());b.writeVarLong(p.unspent());
                for(long n:p.attributes())b.writeVarLong(n);
                for(long n:p.masteries())b.writeVarLong(n);
                for(long n:p.masteryXp())b.writeVarLong(n);
            },
            b->new Snapshot(b.readUUID(),b.readBoolean(),b.readUtf(80),b.readVarLong(),b.readVarLong(),b.readVarLong(),
                b.readVarLong(),b.readVarLong(),b.readVarLong(),read(b,Attribute.values().length),
                read(b,Mastery.values().length),read(b,Mastery.values().length)));
        private static List<Long> read(RegistryFriendlyByteBuf b,int count){
            var out=new ArrayList<Long>(count);for(int i=0;i<count;i++)out.add(b.readVarLong());return out;
        }
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToServer(Request.TYPE,Request.CODEC,ProfileNetwork::handleRequest);
        // This handler deliberately references no Minecraft client classes, so dedicated servers can load it.
        registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(payload,context)->context.enqueueWork(()->clientSnapshot=payload));
    }
    private static void handleRequest(Request request,IPayloadContext context){
        context.enqueueWork(()->{
            if(!(context.player() instanceof ServerPlayer p))return;
            var level=p.serverLevel();var server=level.getServer();
            long floor=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ());
            boolean available=CityRules.hasRegionalSettlements(level)&&SaoWorldData.get(server).isUnlocked(floor);
            if(request.attribute()<-1||request.attribute()>=Attribute.values().length)return;
            if(request.attribute()==-1){
                long now=server.overworld().getGameTime();
                long last=LAST_READ.getOrDefault(p,-100L);
                if(now>=last&&now-last<5)return;
                LAST_READ.put(p,now);
            }
            var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID());
            var data=available?CharacterProgressData.get(server):null;
            if(data!=null&&request.attribute()>=0&&request.sequence()>LAST_ALLOCATION.getOrDefault(p,0L)){
                LAST_ALLOCATION.put(p,request.sequence());
                data.allocate(owner,Attribute.values()[request.attribute()],1);
            }
            var s=data==null?dev.anjo7778.sao.simulation.CharacterProgress.EMPTY:data.state(owner);
            String name=p.getName().getString();
            if(name.length()>80)name=name.substring(0,80);
            PacketDistributor.sendToPlayer(p,new Snapshot(p.getUUID(),available,name,available?floor:0,s.level(),s.xp(),s.xpIntoLevel(),s.xpForNextLevel(),s.unspent(),
                Arrays.stream(Attribute.values()).map(s::attribute).toList(),
                Arrays.stream(Mastery.values()).map(s::mastery).toList(),
                Arrays.stream(Mastery.values()).map(s::masteryExperience).toList()));
        });
    }
}
