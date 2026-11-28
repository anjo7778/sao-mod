package dev.anjo7778.sao.item;

import dev.anjo7778.sao.simulation.MaterialRules.*;
import dev.anjo7778.sao.simulation.WeaponRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
import java.util.List;
import java.util.UUID;

/** First native one-handed sword. Native attributes, durability and repair until the action resolver. */
public final class NativeSteelSword extends SwordItem {
    private static final String FORGE="sao_forge";
    public static final EquipmentMaterial EQUIPMENT=new EquipmentMaterial(Material.STEEL,dev.anjo7778.sao.simulation.MaterialRules.Rarity.COMMON,CraftQuality.STANDARD);
    private static Tier tier(CraftQuality quality){
        var p=new EquipmentMaterial(Material.STEEL,EQUIPMENT.rarity(),quality).effective();
        return new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL,(int)Math.round(250*p.durability()),6f,
            (float)(6*p.edge()-4),14,()->Ingredient.of(SaoItems.STEEL_INGOT.get()));
    }
    private static final Tier TIER=tier(CraftQuality.STANDARD);
    public NativeSteelSword(){
        super(TIER,new Item.Properties().attributes(SwordItem.createAttributes(TIER,3,-2.4f)));
    }
    public WeaponRules.Family family(){return WeaponRules.Family.SWORD;}
    public static CraftQuality quality(ItemStack stack){
        if(!(stack.getItem() instanceof NativeSteelSword))return CraftQuality.STANDARD;
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        try{return CraftQuality.valueOf(tag.getCompound(FORGE).getString("quality"));}
        catch(IllegalArgumentException ex){return CraftQuality.STANDARD;}
    }
    public static EquipmentMaterial equipment(ItemStack stack){
        return new EquipmentMaterial(Material.STEEL,EQUIPMENT.rarity(),quality(stack));
    }
    public static ItemStack forged(CraftQuality quality,UUID maker,String name){
        if(maker==null||name==null||name.isBlank()||name.length()>80||quality==null)throw new IllegalArgumentException("Invalid forge result");
        var stack=new ItemStack(SaoItems.STEEL_SWORD.get());var materialTier=tier(quality);
        var record=new CompoundTag();record.putString("quality",quality.name());record.putUUID("maker",maker);record.putString("name",name);
        var tag=new CompoundTag();tag.put(FORGE,record);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        stack.set(DataComponents.MAX_DAMAGE,materialTier.getUses());
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS,SwordItem.createAttributes(materialTier,3,-2.4f));
        return stack;
    }
    /** NPC maintenance uses the vanilla-style one-quarter durability per steel ingot.
     * No new sword is constructed: every component except damage is preserved. */
    public static final int NPC_REPAIR_PRICE=16;
    public static boolean needsNpcRepair(ItemStack stack){
        return stack.getItem() instanceof NativeSteelSword&&stack.getCount()==1
            &&stack.getMaxDamage()>0&&stack.getDamageValue()>0&&stack.getDamageValue()<stack.getMaxDamage()
            &&(long)stack.getDamageValue()*2>=stack.getMaxDamage();
    }
    public static ItemStack npcRepaired(ItemStack stack){
        if(!needsNpcRepair(stack))return ItemStack.EMPTY;
        var next=stack.copy();
        next.setDamageValue(Math.max(0,stack.getDamageValue()-Math.max(1,stack.getMaxDamage()/4)));
        return next;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);
        tooltip.add(Component.translatable("tooltip.sao.steel_sword").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sao.weapon_quality",Component.translatable("quality.sao."+quality(stack).name().toLowerCase(java.util.Locale.ROOT))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sao.weapon_grip_one").withStyle(ChatFormatting.GRAY));
        var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound(FORGE);
        if(data.hasUUID("maker")&&!data.getString("name").isBlank())
            tooltip.add(Component.translatable("tooltip.sao.forged_by",data.getString("name")).withStyle(ChatFormatting.GRAY));
    }
}
