package com.cpriest.shiftright.compat.sophisticated;

import com.cpriest.shiftright.QuickMoveReorder;
import com.cpriest.shiftright.ShiftRightConfig;
import com.cpriest.shiftright.debug.HookTelemetry;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Sophisticated Storage/Backpacks adapter logic. Their shared menu base
 * ({@code sophisticatedcore ... StorageContainerMenuBase}) reimplements vanilla's
 * merge as its own {@code mergeItemStack} — iterating {@code getSlot(i)} over
 * {@code [startIndex, endIndex)} with {@code reverseDirection=true} for player-bound
 * moves — so the core {@code moveItemStackTo} hook never sees it. The mixin wraps
 * those {@code getSlot} lookups and remaps the visit order exactly like the core hook:
 * ranges without player slots (their storage inventory, upgrade slots, memory pass)
 * come back unchanged from {@link QuickMoveReorder#mapVisit}.
 */
public final class SophisticatedMergeAdapter {

    private SophisticatedMergeAdapter() {
    }

    public static int mapVisit(AbstractContainerMenu menu, int index, int startIndex, int endIndex,
                               boolean reverseDirection) {
        if (!ShiftRightConfig.sophisticatedAdapterEnabled()) {
            return index;
        }
        return QuickMoveReorder.mapVisit(menu, index, startIndex, endIndex, reverseDirection,
                HookTelemetry.SOPHISTICATED);
    }
}
