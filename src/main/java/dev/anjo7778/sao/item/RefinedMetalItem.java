package dev.anjo7778.sao.item;

import dev.anjo7778.sao.simulation.MaterialRules;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/** Real material item; stat information describes the metal, not active player modifiers. */
public final class RefinedMetalItem extends Item {
    private final MaterialRules.Material metal;
    public RefinedMetalItem(MaterialRules.Material metal,Properties properties){super(properties);this.metal=metal;}
    public MaterialRules.Material metal(){return metal;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);
        var p=metal.properties;
        tooltip.add(Component.translatable(metal.origin==MaterialRules.Origin.REFINED_ALLOY?"tooltip.sao.refined_metal":"tooltip.sao.pure_metal").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sao.metal_edge",percent(p.edge())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sao.metal_weight",percent(p.mass())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sao.metal_durability",percent(p.durability())).withStyle(ChatFormatting.GRAY));
    }
    private static int percent(double n){return (int)Math.round(n*100);}
}
