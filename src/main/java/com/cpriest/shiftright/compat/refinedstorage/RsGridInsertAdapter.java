package com.cpriest.shiftright.compat.refinedstorage;

import com.cpriest.shiftright.QuickMoveReorder;
import com.cpriest.shiftright.ShiftRightConfig;
import com.cpriest.shiftright.debug.HookTelemetry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

/**
 * Refined Storage adapter logic. RS2's grid extraction inserts into the player
 * inventory through {@code ItemHandlerHelper.insertItem} — a single-pass first-fit
 * walk of the handler's slots with no top-up pass, so an empty hotbar slot beats an
 * existing partial stack. This replaces that call (via the
 * {@code ItemHandlerInsertableStorage} mixin) with the vanilla-style two passes —
 * top up matching stacks, then fill empties — both in policy order.
 *
 * <p>Applies only when the destination is the player inventory
 * ({@link PlayerMainInvWrapper}, wrapper slot i == container slot i); any other
 * handler (machines, exporters, the cursor handler) returns {@code null} so the
 * caller keeps RS's native path. On any mid-insert surprise the remainder tracked so
 * far is returned — items already placed stay placed, nothing is duplicated or lost.
 */
public final class RsGridInsertAdapter {

    private RsGridInsertAdapter() {
    }

    /**
     * Policy-ordered two-pass insert, or {@code null} when this adapter does not
     * apply and the caller must use RS's native insertion.
     */
    public static ItemStack tryInsert(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (!(handler instanceof PlayerMainInvWrapper) || stack.isEmpty()
                || !ShiftRightConfig.refinedStorageAdapterEnabled()) {
            return null;
        }
        return insertPolicyOrdered(handler, stack, simulate);
    }

    // Package-visible for tests, which drive it through an equivalent player-less
    // wrapper (PlayerMainInvWrapper's pop-time animation needs a live player).
    static ItemStack insertPolicyOrdered(IItemHandler handler, ItemStack stack, boolean simulate) {
        int[] order = QuickMoveReorder.policyScanOrder(handler.getSlots());
        if (order == null) {
            return null;
        }
        HookTelemetry.REFINED_STORAGE.record();
        ItemStack remaining = stack;
        try {
            // Same two-pass shape (and simulate semantics) as NeoForge's own
            // ItemHandlerHelper.insertItemStacked, just in policy order.
            for (int slot : order) {
                if (remaining.isEmpty()) {
                    return remaining;
                }
                ItemStack existing = handler.getStackInSlot(slot);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, remaining)) {
                    remaining = handler.insertItem(slot, remaining, simulate);
                }
            }
            for (int slot : order) {
                if (remaining.isEmpty()) {
                    return remaining;
                }
                if (handler.getStackInSlot(slot).isEmpty()) {
                    remaining = handler.insertItem(slot, remaining, simulate);
                }
            }
        } catch (Throwable t) {
            QuickMoveReorder.logOnce(t);
        }
        return remaining;
    }
}
