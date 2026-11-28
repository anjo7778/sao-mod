package dev.anjo7778.sao.world;
import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@EventBusSubscriber(modid="sao")
public final class TemplatePersistenceProbe {
    @SubscribeEvent public static void start(ServerStartedEvent event){
        String phase=System.getProperty("sao.templatePersistence","off");
        if(phase.equals("off"))return;
        var server=event.getServer();
        require(server.isDedicatedServer()&&server.getWorldData().getLevelName().equals("mts-template-test"),"Wrong test world");
        require(CityRules.hasCities(server.overworld())&&server.getLevel(FloorTravel.UPPER)!=null,"Missing city generator");
        require(phase.equals("write")||phase.equals("read"),"Invalid phase");
        boolean reading=phase.equals("read");
        String first=inspect(server.overworld(),1,reading),second=inspect(server.getLevel(FloorTravel.UPPER),2,reading);
        BlockPos marker=new BlockPos(12,CityPlan.GROUND,42);
        var level=server.overworld();level.getChunk(marker.getX()>>4,marker.getZ()>>4);
        if(reading)require(level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK),"Saved edit overwritten");
        else require(level.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),3),"Marker placement failed");
        LogUtils.getLogger().info("SAO_TEMPLATE_RESTART_{} floor1={} floor2={} marker=true",reading?"READ":"WRITE",first,second);
        server.halt(false);
    }
    private static String inspect(ServerLevel level,long floor,boolean reading){
        require(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen&&gen.generatorSettings().is(floor==1?FloorTravel.FIRST_CITY_TEMPLATES_V2:FloorTravel.UPPER_CITY_TEMPLATES_V2),"Wrong template version");
        var map=new EnumMap<CityArchitecture.Style,CityPlan.Plot>(CityArchitecture.Style.class);
        int limit=CityPlan.half(floor)/CityPlan.LOT;
        for(int x=-limit;x<=limit;x++)for(int z=-limit;z<=limit;z++){
            var plot=CityPlan.plot(floor,x,z);
            if(plot!=null)map.merge(CityArchitecture.style(plot),plot,(a,b)->a.x()*a.x()+a.z()*a.z()<=b.x()*b.x()+b.z()*b.z()?a:b);
        }
        require(map.size()>=7,"Missing building roles");
        var center=FloorLayout.center(floor);var palette=new TemplateCapitalPalette(FloorCatalog.themeFor(floor));
        var digest=digest();int count=0;
        for(var plot:map.values()){
            var b=BuildingTemplates.forPlot(plot);int x0=center.x()+plot.x(),z0=center.z()+plot.z();
            int hw=b.halfWidth(),hd=b.halfDepth();
            var chunks=new ArrayList<int[]>();
            for(int x=(x0-hw)>>4;x<=(x0+hw)>>4;x++)for(int z=(z0-hd)>>4;z<=(z0+hd)>>4;z++)chunks.add(new int[]{x,z});
            if(reading)Collections.reverse(chunks);
            for(int[] c:chunks)level.getChunk(c[0],c[1]);
            for(int y=0;y<b.height();y++)for(int z=-hd;z<=hd;z++)for(int x=-hw;x<=hw;x++){
                char symbol=b.at(x,y,z);if(y==0&&symbol=='.')continue;
                var pos=new BlockPos(x0+x,CityPlan.GROUND+y,z0+z);
                var state=level.getBlockState(pos);var expected=palette.state(symbol,plot);
                require(state.equals(expected),"State mismatch floor "+floor+" "+b.style()+" "+pos+" "+state+" expected "+expected);
                digest.update((b.style()+":"+x+","+y+","+z+":"+state+"\n").getBytes(StandardCharsets.UTF_8));count++;
            }
        }
        return "styles="+map.size()+",cells="+count+",sha256="+HexFormat.of().formatHex(digest.digest());
    }
    private static MessageDigest digest(){
        try{return MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
