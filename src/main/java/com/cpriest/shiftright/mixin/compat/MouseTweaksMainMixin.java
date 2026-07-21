package com.cpriest.shiftright.mixin.compat;

import com.cpriest.shiftright.compat.mousetweaks.MouseTweaksScrollAdapter;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * MouseTweaks adapter hook. Its wheel tweak simulates pickup clicks on the client and
 * picks destinations via {@code Main#findPushSlots}, scanning the screen slot list
 * front-to-back — main inventory before hotbar. Handing it a policy-reordered copy of
 * the list (see {@link MouseTweaksScrollAdapter}) makes scrolled-out items land
 * hotbar-first, consistent with quick-move. Registered in the mixin config's
 * {@code client} set — MouseTweaks is a client-only mod.
 *
 * <p>{@code @Pseudo}: dormant when MouseTweaks is absent. {@code require = 0}: if a
 * MouseTweaks update renames {@code findPushSlots}, the injection silently doesn't
 * apply and its native ordering is used — fail safe, never crash.
 */
@Pseudo
@Mixin(targets = "yalter.mousetweaks.Main", remap = false)
public abstract class MouseTweaksMainMixin {

    @ModifyVariable(
            method = "findPushSlots(Ljava/util/List;Lnet/minecraft/world/inventory/Slot;IZ)Ljava/util/List;",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0)
    private static List<Slot> shiftright$policyOrderPushSearch(List<Slot> slots) {
        return MouseTweaksScrollAdapter.reorderPlayerSlots(slots);
    }
}
