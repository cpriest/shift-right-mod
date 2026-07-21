package com.cpriest.shiftright.mixin.compat;

import com.cpriest.shiftright.compat.sophisticated.SophisticatedMergeAdapter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Sophisticated Storage/Backpacks adapter hook (see {@link SophisticatedMergeAdapter}).
 * Wraps every {@code getSlot(i)} lookup inside their vanilla-merge reimplementation so
 * each iteration visits the policy-ordered slot — their merge math, memory-slot pass,
 * and overflow logic all run untouched. Both mods' menus extend this one base class.
 *
 * <p>{@code @Pseudo}: dormant when Sophisticated Core is absent. {@code require = 0}:
 * if an update reshapes {@code mergeItemStack}, the wrap silently doesn't apply and
 * their native ordering is used — fail safe, never crash. Signature verified via
 * {@code javap} against sophisticated-core 1.21.1-1.4.77.2173.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase", remap = false)
public abstract class StorageContainerMenuBaseMixin {

    @WrapOperation(
            method = "mergeItemStack(Lnet/minecraft/world/item/ItemStack;IIZZZ)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE",
                    target = "Lnet/p3pp3rf1y/sophisticatedcore/common/gui/StorageContainerMenuBase;getSlot(I)Lnet/minecraft/world/inventory/Slot;"),
            require = 0)
    private Slot shiftright$visitSlotsInPolicyOrder(@Coerce AbstractContainerMenu menu, int index, Operation<Slot> original,
                                                    @Local(argsOnly = true, ordinal = 0) int startIndex,
                                                    @Local(argsOnly = true, ordinal = 1) int endIndex,
                                                    @Local(argsOnly = true, ordinal = 0) boolean reverseDirection) {
        int mapped = SophisticatedMergeAdapter.mapVisit(menu, index, startIndex, endIndex, reverseDirection);
        return original.call(menu, mapped);
    }
}
