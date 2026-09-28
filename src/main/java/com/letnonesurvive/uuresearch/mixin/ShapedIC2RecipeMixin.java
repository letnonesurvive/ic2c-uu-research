package com.letnonesurvive.uuresearch.mixin;

import com.letnonesurvive.uuresearch.research.CraftLock;
import com.letnonesurvive.uuresearch.research.CraftLockNotifier;
import ic2.core.platform.recipes.crafting.ShapedIC2Recipe;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes unresearched UU-Matter recipes not match anywhere recipes are matched. IC2's jar keeps this method's
 * readable name in production (the obfuscated m_5818_ is only a bridge to it), hence remap = false.
 */
@Mixin(value = ShapedIC2Recipe.class, remap = false)
public abstract class ShapedIC2RecipeMixin {

    @Inject(method = "matches(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z",
            at = @At("RETURN"), cancellable = true)
    private void uuresearch$requireResearch(CraftingContainer container, Level level, CallbackInfoReturnable<Boolean> cir) {
        // Only a recipe whose pattern actually matched is checked, keeping the knowledge lookup off the hot path
        if (cir.getReturnValueZ() && CraftLock.shouldBlock((CraftingRecipe) (Object) this, level)) {
            cir.setReturnValue(false);
            CraftLockNotifier.onBlocked(container, level);
        }
    }
}
