package com.cpriest.shiftright.mixin.compat;

import com.cpriest.shiftright.compat.ae2.Ae2QuickMoveAdapter;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * AE2 adapter hook (PLAN §5), shared by all AE2 terminals (ME, Crafting, Pattern
 * Access, Wireless, Portable). Server-side logic; the class exists on both sides.
 *
 * <p>{@code @Pseudo}: if AE2 is absent the target class never loads and this mixin
 * stays dormant. {@code require = 0}: if the pinned AE2 version renamed or re-signed
 * {@code getQuickMoveDestinationSlots}, the injection silently doesn't apply and AE2's
 * native ordering is used — fail safe, never crash.
 */
@Pseudo
@Mixin(targets = "appeng.menu.AEBaseMenu", remap = false)
public abstract class AEBaseMenuMixin {

    @ModifyReturnValue(
            method = "getQuickMoveDestinationSlots(Lnet/minecraft/world/item/ItemStack;Z)Ljava/util/List;",
            at = @At("RETURN"),
            require = 0)
    private List<Slot> shiftright$reorderQuickMoveDestinations(List<Slot> original) {
        return Ae2QuickMoveAdapter.reorder(original);
    }
}
