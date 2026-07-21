package com.cpriest.shiftright.mixin;

import com.cpriest.shiftright.QuickMoveReorder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Core hook (PLAN §3): {@code moveItemStackTo} runs two passes (top-up matching
 * stacks, then fill empties) iterating {@code slots[start..end)} — only the iteration
 * order is wrong. We wrap the {@code slots.get(i)} lookups of both passes so each
 * iteration visits the policy-ordered slot instead, while vanilla keeps doing all
 * merge math ({@code mayPlace}, max stack size, return-value semantics) itself.
 *
 * <p>{@code this.slots} is never reordered — a slot's list position is its
 * {@code slotNumber}, referenced by click packets; reordering the list desyncs.
 *
 * <p>Ranges that contain no player-inventory slots (e.g. moving INTO a chest) are left
 * completely untouched, including the {@code reverseDirection} flag's semantics.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @WrapOperation(
            method = "moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;"))
    private Object shiftright$visitSlotsInPolicyOrder(NonNullList<?> slots, int index, Operation<Object> original,
                                                      @Local(argsOnly = true, ordinal = 0) int startIndex,
                                                      @Local(argsOnly = true, ordinal = 1) int endIndex,
                                                      @Local(argsOnly = true) boolean reverseDirection) {
        int mapped = QuickMoveReorder.mapVisit((AbstractContainerMenu) (Object) this, index, startIndex, endIndex,
                reverseDirection);
        return original.call(slots, mapped);
    }
}
