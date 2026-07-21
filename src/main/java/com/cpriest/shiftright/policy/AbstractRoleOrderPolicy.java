package com.cpriest.shiftright.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shared implementation for role-ranked policies: player slots are stably sorted by
 * (role rank, subIndex); OTHER slots are always appended last in their original order
 * and are never affected by {@link SlotOrderPolicy.Context#reverse()}.
 */
abstract class AbstractRoleOrderPolicy implements SlotOrderPolicy {

    /** Lower rank fills first. Only called for HOTBAR and MAIN. */
    protected abstract int rank(QuickMoveSlot.Role role);

    @Override
    public final List<QuickMoveSlot> order(List<QuickMoveSlot> candidates, Context ctx) {
        List<QuickMoveSlot> player = new ArrayList<>(candidates.size());
        List<QuickMoveSlot> other = new ArrayList<>();
        for (QuickMoveSlot slot : candidates) {
            (slot.role() == QuickMoveSlot.Role.OTHER ? other : player).add(slot);
        }
        player.sort(Comparator
                .comparingInt((QuickMoveSlot s) -> rank(s.role()))
                .thenComparingInt(QuickMoveSlot::subIndex));
        if (ctx.reverse()) {
            Collections.reverse(player);
        }
        player.addAll(other);
        return player;
    }
}
