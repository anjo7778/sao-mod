package dev.anjo7778.sao.mixin;

import dev.anjo7778.sao.world.SwordCombat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The SAO action clock replaces attack charge only for an already-authorized native hit.
 * Ordinary vanilla attacks, cooldown indicators, and other mods remain unchanged. */
@Mixin(Player.class)
public abstract class SwordAttackChargeMixin {
    @Inject(method="getAttackStrengthScale(F)F",at=@At("HEAD"),cancellable=true)
    private void sao$authoritativeCharge(float partial,CallbackInfoReturnable<Float> cir){
        if(SwordCombat.authorizedPlayerHit((Player)(Object)this))cir.setReturnValue(1f);
    }
}
