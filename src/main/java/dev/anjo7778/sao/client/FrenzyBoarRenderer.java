package dev.anjo7778.sao.client;

import dev.anjo7778.sao.entity.FrenzyBoar;
import dev.anjo7778.sao.entity.SaoEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="sao",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class FrenzyBoarRenderer extends MobRenderer<FrenzyBoar,FrenzyBoarModel> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("sao","textures/entity/frenzy_boar.png");
    public FrenzyBoarRenderer(EntityRendererProvider.Context context){super(context,new FrenzyBoarModel(context.bakeLayer(FrenzyBoarModel.LAYER)),.65f);}
    @Override public ResourceLocation getTextureLocation(FrenzyBoar boar){return TEXTURE;}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(FrenzyBoarModel.LAYER,FrenzyBoarModel::layer);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(SaoEntities.FRENZY_BOAR.get(),FrenzyBoarRenderer::new);}
}
