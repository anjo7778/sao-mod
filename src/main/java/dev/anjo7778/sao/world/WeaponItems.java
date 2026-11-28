package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.WeaponRules;
import dev.anjo7778.sao.simulation.WeaponRules.Family;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import java.util.*;

/** Explicit extensibility via sao:weapons/<family> item tags.
 * Ambiguous multi-family declarations are refused rather than guessed from item names.
 */
public final class WeaponItems {
    private static final Map<Family,TagKey<Item>> TAGS=new EnumMap<>(Family.class);
    static{for(var f:Family.values())TAGS.put(f,TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("sao","weapons/"+f.name().toLowerCase(Locale.ROOT))));}
    private WeaponItems(){}
    public static Family classify(ItemStack stack){
        if(stack.isEmpty())return null;
        Family match=null;
        for(var entry:TAGS.entrySet())if(stack.is(entry.getValue())){
            if(match!=null)return null;match=entry.getKey();
        }
        if(match!=null)return match;
        // Only known vanilla items get implicit families. Unknown external weapons require tags.
        if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft"))return null;
        var item=stack.getItem();
        if(item instanceof SwordItem)return Family.SWORD;
        if(item instanceof AxeItem)return Family.AXE;
        if(item instanceof BowItem)return Family.BOW;
        if(item instanceof CrossbowItem)return Family.CROSSBOW;
        if(item instanceof TridentItem)return Family.TRIDENT;
        if(item instanceof ShieldItem)return Family.SHIELD;
        if(item instanceof MaceItem)return Family.GREAT_HAMMER;
        return null;
    }
    public static WeaponRules.Loadout resolve(ItemStack main,ItemStack off){
        return WeaponRules.resolve(classify(main),main.isEmpty(),classify(off),off.isEmpty());
    }
}
