package com.cpriest.shiftright.mixin.compat;

import com.cpriest.shiftright.compat.refinedstorage.RsGridInsertAdapter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Refined Storage adapter hook (see {@link RsGridInsertAdapter}). Wraps the
 * {@code ItemHandlerHelper.insertItem} call inside RS2's item-handler insertion so
 * grid-to-player extraction fills the player inventory two-pass in policy order.
 * The same class serves machine-side insertions too — the adapter applies only to
 * the player-inventory wrapper and everything else takes the original call.
 *
 * <p>{@code @Pseudo}: dormant when Refined Storage is absent. {@code require = 0}:
 * if an RS update reshapes the method, the wrap silently doesn't apply — fail safe,
 * never crash. Signature verified via {@code javap} against refined-storage 2.0.9.
 */
@Pseudo
@Mixin(targets = "com.refinedmods.refinedstorage.neoforge.storage.ItemHandlerInsertableStorage", remap = false)
public abstract class RsItemHandlerInsertableStorageMixin {

    @WrapOperation(
            method = "insert(Lcom/refinedmods/refinedstorage/common/support/resource/ItemResource;JLcom/refinedmods/refinedstorage/api/core/Action;Lnet/neoforged/neoforge/items/IItemHandler;)J",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/items/ItemHandlerHelper;insertItem(Lnet/neoforged/neoforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"),
            require = 0)
    private ItemStack shiftright$policyOrderedPlayerInsert(IItemHandler handler, ItemStack stack, boolean simulate,
                                                           Operation<ItemStack> original) {
        ItemStack reordered = RsGridInsertAdapter.tryInsert(handler, stack, simulate);
        return reordered != null ? reordered : original.call(handler, stack, simulate);
    }
}
