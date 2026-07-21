package com.cpriest.shiftright.policy;

/**
 * A destination-slot candidate for a quick-move, abstracted away from any particular
 * inventory implementation so ordering policies stay pure and adapter-agnostic.
 *
 * @param role     where this slot lives in the player's inventory (or OTHER for
 *                 non-player slots such as block inventory slots caught in the range)
 * @param subIndex position within the role: hotbar 0..8 left-to-right, main inventory
 *                 0..26 row-major top-left to bottom-right. Meaningless for OTHER.
 * @param handle   the adapter's native reference for this slot (e.g. an absolute menu
 *                 slot index, or a vanilla {@code Slot} object). Policies never look
 *                 inside it; adapters get it back after ordering.
 */
public record QuickMoveSlot(Role role, int subIndex, Object handle) {

    public enum Role {
        HOTBAR,
        MAIN,
        /** Not part of the player's hotbar/main inventory; always ordered last, original order kept. */
        OTHER
    }
}
