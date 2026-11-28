package dev.anjo7778.sao.item;

import dev.anjo7778.sao.simulation.MaterialRules.*;
import dev.anjo7778.sao.simulation.WeaponRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
import java.util.List;

/** First native one-handed sword. Native damage, durability and repair until the shared action resolver is installed. */
public final class NativeSteelSword extends SwordItem {
    public static final EquipmentMaterial EQUIPMENT=new EquipmentMaterial(Material.STEEL,dev.anjo7778.sao.simulation.MaterialRules.Rarity.COMMON,CraftQuality.STANDARD);
    private static final Tier TIER=new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL,
        (int)Math.round(250*EQUIPMENT.effective().durability()),6f,
        (float)(6*EQUIPMENT.effective().edge()-4),14,()->Ingredient.of(SaoItems.STEEL_INGOT.get()));
    public NativeSteelSword(){
        super(TIER,new Item.Properties().attributes(SwordItem.createAttributes(TIER,3,-2.4f)));
    }
    public WeaponRules.Family family(){return WeaponRules.Family.SWORD;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);
        tooltip.add(Component.translatable("tooltip.sao.steel_sword").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sao.weapon_quality_standard").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sao.weapon_grip_one").withStyle(ChatFormatting.GRAY));
    }
}
