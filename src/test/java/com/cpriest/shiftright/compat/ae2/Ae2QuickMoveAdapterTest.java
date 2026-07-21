package com.cpriest.shiftright.compat.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpriest.shiftright.ShiftRightConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * FML JUnit (registries bootstrapped). AE2's terminal-grid shift-click
 * ({@code MEStorageMenu#moveOneStackToPlayer}) walks the destination list
 * single-pass first-fit, so the adapter must emit matching partial stacks before
 * everything else or terminal pulls skip existing stacks and open a fresh hotbar
 * slot. The two-pass slot path ({@code quickMoveToOtherSlots}) filters by
 * has-item/empty itself, so partials-first ordering is safe for both call sites.
 */
class Ae2QuickMoveAdapterTest {

    // ChestMenu slot layout: 27..53 player main (container 9..35), 54..62 hotbar
    // (container 0..8). AE2 hands the adapter just the player-side slots.
    private final Inventory playerInv = new Inventory(null);
    private final ChestMenu menu = ChestMenu.threeRows(1, playerInv, new SimpleContainer(27));
    private final List<Slot> playerSlots = new ArrayList<>(menu.slots.subList(27, 63));

    @AfterEach
    void resetOverrides() {
        ShiftRightConfig.clearTestOverrides();
    }

    @Test
    void matchingPartialStacksComeFirstInPolicyOrder() {
        playerInv.setItem(20, new ItemStack(Items.COBBLESTONE, 16)); // main partial
        playerInv.setItem(5, new ItemStack(Items.COBBLESTONE, 16));  // hotbar partial

        List<Slot> ordered = Ae2QuickMoveAdapter.reorder(playerSlots, new ItemStack(Items.COBBLESTONE, 32));

        assertEquals(5, ordered.get(0).getContainerSlot(), "hotbar partial must be the first destination");
        assertEquals(20, ordered.get(1).getContainerSlot(), "main partial must follow");
        assertEquals(0, ordered.get(2).getContainerSlot(), "then the remaining slots in policy order");
    }

    @Test
    void fullAndForeignStacksDoNotJumpTheQueue() {
        playerInv.setItem(3, new ItemStack(Items.COBBLESTONE, 64)); // full: no room, not a top-up target
        playerInv.setItem(4, new ItemStack(Items.DIRT, 16));        // different item

        List<Slot> ordered = Ae2QuickMoveAdapter.reorder(playerSlots, new ItemStack(Items.COBBLESTONE, 32));

        assertEquals(0, ordered.get(0).getContainerSlot(),
                "no partial matches: plain policy order starting at hotbar slot 1");
        assertEquals(1, ordered.get(1).getContainerSlot());
    }

    @Test
    void nonStackableMovesUsePurePolicyOrder() {
        playerInv.setItem(5, new ItemStack(Items.IRON_SWORD));

        List<Slot> ordered = Ae2QuickMoveAdapter.reorder(playerSlots, new ItemStack(Items.IRON_SWORD));

        assertEquals(0, ordered.get(0).getContainerSlot());
    }

    @Test
    void reorderPreservesSlotIdentityAndCount() {
        playerInv.setItem(5, new ItemStack(Items.COBBLESTONE, 16));

        List<Slot> ordered = Ae2QuickMoveAdapter.reorder(playerSlots, new ItemStack(Items.COBBLESTONE, 32));

        assertEquals(playerSlots.size(), ordered.size());
        assertTrue(playerSlots.containsAll(ordered), "reorder must not create or clone slots");
    }

    @Test
    void degenerateInputsPassThrough() {
        assertSame(null, Ae2QuickMoveAdapter.reorder(null, ItemStack.EMPTY));
        List<Slot> single = List.of(playerSlots.get(0));
        assertSame(single, Ae2QuickMoveAdapter.reorder(single, new ItemStack(Items.COBBLESTONE)));
    }
}
