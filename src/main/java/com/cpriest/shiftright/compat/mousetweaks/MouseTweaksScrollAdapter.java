package com.cpriest.shiftright.compat.mousetweaks;

import com.cpriest.shiftright.QuickMoveReorder;
import com.cpriest.shiftright.ShiftRightConfig;
import com.cpriest.shiftright.debug.HookTelemetry;
import com.cpriest.shiftright.policy.QuickMoveSlot;
import com.cpriest.shiftright.policy.SlotOrders;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.Slot;

/**
 * MouseTweaks adapter (client-side). Its wheel tweak moves items by simulating
 * pickup clicks entirely on the client, choosing destinations itself by scanning the
 * screen's slot list front-to-back ({@code Main#findPushSlots}) — player slots appear
 * there main-inventory-first, so scrolled-out items land in the main inventory before
 * the hotbar, the opposite of this mod's quick-move order. The mixin hands that search
 * a reordered <em>copy</em> of the list: player hotbar/main slots re-sequenced into
 * policy order at the positions they occupied, everything else (container slots,
 * armor/offhand) untouched. Slot objects are never cloned or mutated, so the simulated
 * clicks (which use slot ids) stay valid.
 *
 * <p>Deliberately not applied to {@code findPullSlot}: that picks which player stack
 * *donates* into a hovered container stack, and this mod's scope is where items land
 * when entering the player inventory, not which stack leaves it (MouseTweaks' own
 * {@code wheelSearchOrder} config governs that).
 *
 * <p>Fully fail-safe: any surprise returns the original list unchanged.
 */
public final class MouseTweaksScrollAdapter {

    private MouseTweaksScrollAdapter() {
    }

    public static List<Slot> reorderPlayerSlots(List<Slot> original) {
        try {
            if (original == null || original.size() < 2 || !ShiftRightConfig.mouseTweaksAdapterEnabled()) {
                return original;
            }
            List<Integer> playerPositions = new ArrayList<>();
            List<QuickMoveSlot> candidates = new ArrayList<>();
            for (int i = 0; i < original.size(); i++) {
                Slot slot = original.get(i);
                QuickMoveSlot candidate = QuickMoveReorder.classify(slot, slot);
                if (candidate.role() != QuickMoveSlot.Role.OTHER) {
                    playerPositions.add(i);
                    candidates.add(candidate);
                }
            }
            if (candidates.size() < 2) {
                return original;
            }
            List<QuickMoveSlot> ordered = SlotOrders.computeOrdered(candidates, ShiftRightConfig.policy(),
                    ShiftRightConfig.policyContext());
            if (ordered == null) {
                return original;
            }
            List<Slot> result = new ArrayList<>(original);
            for (int i = 0; i < playerPositions.size(); i++) {
                result.set(playerPositions.get(i), (Slot) ordered.get(i).handle());
            }
            HookTelemetry.MOUSE_TWEAKS.record();
            return result;
        } catch (Throwable t) {
            QuickMoveReorder.logOnce(t);
            return original;
        }
    }
}
