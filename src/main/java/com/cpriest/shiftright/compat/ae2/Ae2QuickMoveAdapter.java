package com.cpriest.shiftright.compat.ae2;

import com.cpriest.shiftright.QuickMoveReorder;
import com.cpriest.shiftright.ShiftRightConfig;
import com.cpriest.shiftright.debug.HookTelemetry;
import com.cpriest.shiftright.policy.QuickMoveSlot;
import com.cpriest.shiftright.policy.SlotOrders;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * AE2 adapter logic (PLAN §5). AE2's shift-click path (SHIFT_CLICK and MOVE_REGION)
 * funnels through {@code AEBaseMenu#getQuickMoveDestinationSlots}, which returns an
 * ordered list of vanilla {@link Slot}s; AE2's own extraction/merge then walks that
 * list. Reordering the returned list is the single choke point — AE2's powered
 * extraction and merge logic is reused untouched.
 *
 * <p>The returned slots are plain vanilla {@code Slot}s over the player inventory, so
 * classification uses the same generic container-slot logic as the core mixin and
 * needs no AE2 classes at all (which also removes most version fragility).
 *
 * <p>Slots holding a matching, non-full stack are moved ahead of everything else
 * (each group in policy order). The slot shift-click path is two-pass (top-up, then
 * empties) and unaffected by this split, but the terminal-grid shift-click
 * ({@code MEStorageMenu#moveOneStackToPlayer}) walks the list single-pass first-fit —
 * without partials-first it opens a fresh policy-first slot instead of topping up,
 * unlike vanilla.
 *
 * <p>Fully fail-safe: any surprise (config off, unexpected list contents, policy
 * mismatch, exception) returns AE2's original list unchanged.
 */
public final class Ae2QuickMoveAdapter {

    private Ae2QuickMoveAdapter() {
    }

    public static List<Slot> reorder(List<Slot> original, ItemStack stackToMove) {
        try {
            if (original == null || original.size() < 2 || !ShiftRightConfig.ae2AdapterEnabled()) {
                return original;
            }
            List<QuickMoveSlot> candidates = new ArrayList<>(original.size());
            for (Slot slot : original) {
                candidates.add(QuickMoveReorder.classify(slot, slot));
            }
            List<QuickMoveSlot> ordered = SlotOrders.computeOrdered(candidates, ShiftRightConfig.policy(),
                    ShiftRightConfig.policyContext());
            if (ordered == null) {
                return original;
            }
            HookTelemetry.AE2_ADAPTER.record();
            List<Slot> topUpTargets = new ArrayList<>();
            List<Slot> rest = new ArrayList<>(ordered.size());
            for (QuickMoveSlot slot : ordered) {
                (canTopUp((Slot) slot.handle(), stackToMove) ? topUpTargets : rest).add((Slot) slot.handle());
            }
            topUpTargets.addAll(rest);
            return topUpTargets;
        } catch (Throwable t) {
            QuickMoveReorder.logOnce(t);
            return original;
        }
    }

    private static boolean canTopUp(Slot slot, ItemStack stackToMove) {
        ItemStack destination = slot.getItem();
        return !destination.isEmpty()
                && destination.isStackable()
                && ItemStack.isSameItemSameComponents(destination, stackToMove)
                && destination.getCount() < slot.getMaxStackSize(destination);
    }
}
