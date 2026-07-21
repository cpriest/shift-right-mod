package com.cpriest.shiftright.compat.mousetweaks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpriest.shiftright.ShiftRightConfig;
import com.cpriest.shiftright.policy.MainFirstPolicy;
import com.cpriest.shiftright.policy.SlotOrderPolicy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Runs through FML's JUnit launch like {@link com.cpriest.shiftright.QuickMoveIntegrationTest},
 * so real menus/slots are available. The adapter reorders a *copy* of a screen's slot
 * list so MouseTweaks' destination search visits player slots in policy order; these
 * tests pin the copy semantics (identity preserved, non-player positions fixed, source
 * list untouched) that keep the simulated clicks valid.
 */
class MouseTweaksScrollAdapterTest {

    // ChestMenu slot layout: 0..26 chest, 27..53 player main (container 9..35),
    // 54..62 hotbar (container 0..8).
    private static final int CHEST_SLOTS = 27;
    private static final int HOTBAR_SIZE = 9;

    private final Inventory playerInv = new Inventory(null);
    private final ChestMenu menu = ChestMenu.threeRows(1, playerInv, new SimpleContainer(CHEST_SLOTS));

    @AfterEach
    void resetOverrides() {
        ShiftRightConfig.clearTestOverrides();
    }

    @Test
    void chestSlotsKeepTheirExactPositions() {
        List<Slot> reordered = MouseTweaksScrollAdapter.reorderPlayerSlots(menu.slots);

        for (int i = 0; i < CHEST_SLOTS; i++) {
            assertSame(menu.slots.get(i), reordered.get(i), "non-player slot moved at index " + i);
        }
    }

    @Test
    void playerSlotsBecomeHotbarFirstInTheirPositions() {
        List<Slot> reordered = MouseTweaksScrollAdapter.reorderPlayerSlots(menu.slots);

        // Player positions 27..35 must now hold hotbar container slots 1..9, then
        // 36..62 the main inventory (container 9..35) in reading order.
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            assertEquals(i, reordered.get(CHEST_SLOTS + i).getContainerSlot(),
                    "expected hotbar slot " + (i + 1) + " at player position " + i);
        }
        for (int i = 0; i < 27; i++) {
            assertEquals(HOTBAR_SIZE + i, reordered.get(CHEST_SLOTS + HOTBAR_SIZE + i).getContainerSlot());
        }
    }

    @Test
    void reorderPreservesSlotIdentityAndCount() {
        List<Slot> reordered = MouseTweaksScrollAdapter.reorderPlayerSlots(menu.slots);

        assertEquals(menu.slots.size(), reordered.size());
        // Slot has no equals override, so containsAll is an identity check.
        assertTrue(menu.slots.containsAll(reordered), "reorder must not create or clone slots");
    }

    @Test
    void sourceListIsNeverMutated() {
        List<Slot> before = new ArrayList<>(menu.slots);

        MouseTweaksScrollAdapter.reorderPlayerSlots(menu.slots);

        for (int i = 0; i < before.size(); i++) {
            assertSame(before.get(i), menu.slots.get(i), "menu slot list mutated at index " + i);
        }
    }

    @Test
    void mainFirstPolicyPutsMainInventoryFirst() {
        ShiftRightConfig.setTestOverrides(MainFirstPolicy.INSTANCE, SlotOrderPolicy.Context.DEFAULT);

        List<Slot> reordered = MouseTweaksScrollAdapter.reorderPlayerSlots(menu.slots);

        assertEquals(HOTBAR_SIZE, reordered.get(CHEST_SLOTS).getContainerSlot(),
                "MAIN_FIRST should put main container slot 9 at the first player position");
        assertEquals(0, reordered.get(CHEST_SLOTS + 27).getContainerSlot(),
                "hotbar should follow the main inventory under MAIN_FIRST");
    }

    @Test
    void tinyListsPassThroughUntouched()  {
        List<Slot> single = List.of(menu.slots.get(0));
        assertSame(single, MouseTweaksScrollAdapter.reorderPlayerSlots(single));
        assertSame(null, MouseTweaksScrollAdapter.reorderPlayerSlots(null));
    }
}
