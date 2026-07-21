package com.cpriest.shiftright.policy;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure helpers shared by all adapters. Every entry point validates the policy's output
 * and returns a "no reorder" result on anything suspicious — adapters then fall back to
 * native behavior. Never crash, never lose items.
 */
public final class SlotOrders {

    private SlotOrders() {
    }

    /** True if at least one candidate is a player HOTBAR/MAIN slot (i.e. reordering is meaningful). */
    public static boolean hasPlayerSlots(List<QuickMoveSlot> candidates) {
        for (QuickMoveSlot slot : candidates) {
            if (slot.role() != QuickMoveSlot.Role.OTHER) {
                return true;
            }
        }
        return false;
    }

    /**
     * Orders {@code candidates} (whose handles must be unique {@link Integer} absolute
     * indices) and returns the resulting index permutation, or {@code null} if the
     * candidates contain no player slots or the policy output is not an exact
     * permutation of the input.
     */
    public static int[] computeIndexPermutation(List<QuickMoveSlot> candidates, SlotOrderPolicy policy,
                                                SlotOrderPolicy.Context ctx) {
        if (!hasPlayerSlots(candidates)) {
            return null;
        }
        List<QuickMoveSlot> ordered = policy.order(candidates, ctx);
        if (!sameElements(candidates, ordered)) {
            return null;
        }
        int[] permutation = new int[ordered.size()];
        for (int i = 0; i < permutation.length; i++) {
            if (!(ordered.get(i).handle() instanceof Integer index)) {
                return null;
            }
            permutation[i] = index;
        }
        return permutation;
    }

    /**
     * Orders {@code candidates} and returns the ordered list, or {@code null} if the
     * candidates contain no player slots or the policy output is not an exact
     * permutation of the input.
     */
    public static List<QuickMoveSlot> computeOrdered(List<QuickMoveSlot> candidates, SlotOrderPolicy policy,
                                                     SlotOrderPolicy.Context ctx) {
        if (!hasPlayerSlots(candidates)) {
            return null;
        }
        List<QuickMoveSlot> ordered = policy.order(candidates, ctx);
        return sameElements(candidates, ordered) ? ordered : null;
    }

    /** True iff {@code ordered} contains exactly the elements of {@code candidates}, each once. */
    static boolean sameElements(List<QuickMoveSlot> candidates, List<QuickMoveSlot> ordered) {
        if (ordered == null || ordered.size() != candidates.size()) {
            return false;
        }
        Map<QuickMoveSlot, Integer> counts = new IdentityHashMap<>();
        for (QuickMoveSlot slot : candidates) {
            counts.merge(slot, 1, Integer::sum);
        }
        for (QuickMoveSlot slot : ordered) {
            Integer remaining = counts.get(slot);
            if (remaining == null || remaining == 0) {
                return false;
            }
            counts.put(slot, remaining - 1);
        }
        return true;
    }
}
