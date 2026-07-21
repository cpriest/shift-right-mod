package com.cpriest.shiftright.policy;

import java.util.List;

/**
 * Decides the order in which quick-moved items should try destination slots.
 *
 * <p>Implementations must be pure and side-effect free: they decide order only and
 * never move items. They must return a list containing exactly the same elements as
 * {@code candidates} (callers validate this and fall back to native behavior on any
 * mismatch — never crash, never lose items).
 */
public interface SlotOrderPolicy {

    List<QuickMoveSlot> order(List<QuickMoveSlot> candidates, Context ctx);

    /**
     * @param reverse when true, the ordered player-slot section (HOTBAR/MAIN) is
     *                reversed. OTHER slots always stay last in their original order.
     */
    record Context(boolean reverse) {
        public static final Context DEFAULT = new Context(false);
    }
}
