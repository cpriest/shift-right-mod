package com.cpriest.shiftright;

import com.cpriest.shiftright.debug.HookTelemetry;
import com.cpriest.shiftright.policy.QuickMoveSlot;
import com.cpriest.shiftright.policy.SlotOrders;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;

/**
 * Bridges vanilla menu/inventory structures to the pure ordering policy. All entry
 * points are fully defensive: any unexpected state or exception logs once and yields
 * native (unmodified) behavior.
 */
public final class QuickMoveReorder {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean();

    // Vanilla player inventory layout: container slots 0..8 hotbar, 9..35 main,
    // 36..39 armor, 40 offhand.
    private static final int HOTBAR_SIZE = 9;
    private static final int MAIN_END = 36;

    private QuickMoveReorder() {
    }

    /**
     * Maps one loop visit of {@code AbstractContainerMenu#moveItemStackTo} onto the
     * policy-ordered slot for that iteration. Called (via mixin) in place of the
     * vanilla {@code slots.get(i)} lookups, so vanilla's merge logic still runs — only
     * the visit order changes. Returns {@code index} unchanged whenever reordering is
     * not applicable (range contains no player slots, index outside the range, any
     * internal failure), which preserves exact vanilla behavior including the
     * {@code reverseDirection} flag.
     */
    public static int mapVisit(AbstractContainerMenu menu, int index, int startIndex, int endIndex,
                               boolean reverseDirection, HookTelemetry hook) {
        try {
            if (index < startIndex || index >= endIndex || endIndex - startIndex < 2
                    || endIndex > menu.slots.size()) {
                return index;
            }
            int[] permutation = computePermutation(menu, startIndex, endIndex);
            if (permutation == null) {
                return index;
            }
            hook.record();
            int position = reverseDirection ? (endIndex - 1 - index) : (index - startIndex);
            return permutation[position];
        } catch (Throwable t) {
            logOnce(t);
            return index;
        }
    }

    /**
     * Policy-ordered absolute slot indices for {@code [startIndex, endIndex)}, or
     * {@code null} when the range should be left in vanilla order.
     */
    private static int[] computePermutation(AbstractContainerMenu menu, int startIndex, int endIndex) {
        List<QuickMoveSlot> candidates = new ArrayList<>(endIndex - startIndex);
        for (int i = startIndex; i < endIndex; i++) {
            candidates.add(classify(menu.slots.get(i), i));
        }
        return SlotOrders.computeIndexPermutation(candidates, ShiftRightConfig.policy(),
                ShiftRightConfig.policyContext());
    }

    /**
     * Scan order for {@code Inventory#getFreeSlot()} / {@code getSlotWithRemainingSpace}
     * over the 36 main+hotbar container slots, or {@code null} to use vanilla order
     * (mixin disabled by config, or any internal failure).
     */
    public static int[] inventoryScanOrder(int size) {
        if (!ShiftRightConfig.vanillaAddPathEnabled()) {
            return null;
        }
        int[] order = policyScanOrder(size);
        if (order != null) {
            HookTelemetry.ADD_PATH.record();
        }
        return order;
    }

    /**
     * Policy scan order over {@code size} player container slots (0..8 hotbar,
     * 9..35 main), independent of any config toggle, or {@code null} on failure.
     */
    public static int[] policyScanOrder(int size) {
        try {
            List<QuickMoveSlot> candidates = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                candidates.add(classifyContainerSlot(i, i));
            }
            return SlotOrders.computeIndexPermutation(candidates, ShiftRightConfig.policy(),
                    ShiftRightConfig.policyContext());
        } catch (Throwable t) {
            logOnce(t);
            return null;
        }
    }

    /**
     * Classifies any vanilla {@link Slot} by whether it sits over the player inventory,
     * independent of the menu it belongs to. Works for vanilla menus, third-party block
     * menus, and slots handed out by mod menus (e.g. AE2 terminal destination lists).
     */
    public static QuickMoveSlot classify(Slot slot, Object handle) {
        if (slot != null && slot.container instanceof Inventory) {
            return classifyContainerSlot(slot.getContainerSlot(), handle);
        }
        return new QuickMoveSlot(QuickMoveSlot.Role.OTHER, 0, handle);
    }

    private static QuickMoveSlot classifyContainerSlot(int containerSlot, Object handle) {
        if (containerSlot >= 0 && containerSlot < HOTBAR_SIZE) {
            return new QuickMoveSlot(QuickMoveSlot.Role.HOTBAR, containerSlot, handle);
        }
        if (containerSlot >= HOTBAR_SIZE && containerSlot < MAIN_END) {
            return new QuickMoveSlot(QuickMoveSlot.Role.MAIN, containerSlot - HOTBAR_SIZE, handle);
        }
        // Armor, offhand, or anything exotic: never reorder ahead of hotbar/main.
        return new QuickMoveSlot(QuickMoveSlot.Role.OTHER, 0, handle);
    }

    public static void logOnce(Throwable t) {
        if (LOGGED_FAILURE.compareAndSet(false, true)) {
            LOGGER.error("[shift-right] Reordering failed; falling back to native behavior for the rest of this session.", t);
        }
    }
}
