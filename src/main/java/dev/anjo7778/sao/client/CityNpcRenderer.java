package dev.anjo7778.sao.client;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.entity.SaoEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Uses local vanilla player skins; no account lookup or network skin download. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CityNpcRenderer extends HumanoidMobRenderer<CityNpc,PlayerModel<CityNpc>> {
    public CityNpcRenderer(EntityRendererProvider.Context c){super(c,new PlayerModel<>(c.bakeLayer(ModelLayers.PLAYER),false),.5f);}
    @Override public ResourceLocation getTextureLocation(CityNpc npc){
        var texture=DefaultPlayerSkin.get(npc.getUUID()).texture();
        return ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),texture.getPath().replace("/slim/","/wide/"));
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){
        e.registerEntityRenderer(SaoEntities.CITY_NPC.get(),CityNpcRenderer::new);
    }
}
