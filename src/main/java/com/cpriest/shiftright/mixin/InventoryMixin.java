package com.cpriest.shiftright.mixin;

import com.cpriest.shiftright.QuickMoveReorder;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla add-path hook (PLAN §4): many custom-{@code quickMoveStack} mods delegate
 * the "find a home" step to {@code player.getInventory().add()} / {@code addItem()},
 * which resolve through {@code getFreeSlot()} / {@code getSlotWithRemainingSpace()}.
 * Reordering those scans influences that long tail without per-mod code.
 *
 * <p>These also fire in non-shift contexts (ground pickup, dispensers); that is
 * documented and the {@code enableVanillaAddPathMixin} config toggle exists for it.
 * With the default HOTBAR_FIRST/FORWARD policy the scan order equals vanilla's
 * (0..35), so behavior only actually changes under MAIN_FIRST or REVERSE.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow @Final public NonNullList<ItemStack> items;
    @Shadow @Final public NonNullList<ItemStack> offhand;
    @Shadow public int selected;

    @Shadow public abstract ItemStack getItem(int index);

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void shiftright$orderedGetFreeSlot(CallbackInfoReturnable<Integer> cir) {
        int[] order = QuickMoveReorder.inventoryScanOrder(this.items.size());
        if (order == null) {
            return;
        }
        for (int slot : order) {
            if (this.items.get(slot).isEmpty()) {
                cir.setReturnValue(slot);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void shiftright$orderedGetSlotWithRemainingSpace(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        int[] order = QuickMoveReorder.inventoryScanOrder(this.items.size());
        if (order == null) {
            return;
        }
        // Keep vanilla's fast paths: the currently selected hotbar slot and the
        // offhand keep priority for topping up (matches player expectation while
        // holding a partial stack).
        if (shiftright$hasRemainingSpaceForItem(this.getItem(this.selected), stack)) {
            cir.setReturnValue(this.selected);
            return;
        }
        if (shiftright$hasRemainingSpaceForItem(this.offhand.get(0), stack)) {
            cir.setReturnValue(40); // offhand container slot, same literal vanilla returns
            return;
        }
        for (int slot : order) {
            if (shiftright$hasRemainingSpaceForItem(this.items.get(slot), stack)) {
                cir.setReturnValue(slot);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    /**
     * Mirror of vanilla {@code Inventory#hasRemainingSpaceForItem} (private, so not
     * shadowable): destination must be a matching, stackable, non-full stack.
     */
    @Unique
    private boolean shiftright$hasRemainingSpaceForItem(ItemStack destination, ItemStack origin) {
        // getMaxStackSize() is a Container interface default, not declared on
        // Inventory, so it can't be @Shadow'd — go through the interface instead.
        int limit = Math.min(destination.getMaxStackSize(),
                ((net.minecraft.world.Container) (Object) this).getMaxStackSize());
        return !destination.isEmpty()
                && ItemStack.isSameItemSameComponents(destination, origin)
                && destination.isStackable()
                && destination.getCount() < limit;
    }
}
