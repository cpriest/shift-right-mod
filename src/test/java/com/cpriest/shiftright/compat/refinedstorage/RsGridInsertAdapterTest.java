package com.cpriest.shiftright.compat.refinedstorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.junit.jupiter.api.Test;

/**
 * FML JUnit. RS2's grid extraction inserts into the player inventory through
 * {@code ItemHandlerHelper.insertItem} — single-pass first-fit over wrapper slots
 * (hotbar 1..9 then main), with no top-up pass. The adapter replaces that with a
 * two-pass, policy-ordered insert, but only for the player-inventory wrapper.
 * Wrapper slot i == container slot i: 0..8 hotbar, 9..35 main.
 */
class RsGridInsertAdapterTest {

    private final Inventory playerInv = new Inventory(null);
    // Same 36-slot view as PlayerMainInvWrapper (slot i == container slot i) but
    // without its pop-time animation, which needs a live player.
    private final RangedWrapper wrapper = new RangedWrapper(new InvWrapper(playerInv), 0, 36);

    @Test
    void topsUpPartialStacksInPolicyOrderBeforeEmpties() {
        playerInv.setItem(20, new ItemStack(Items.COBBLESTONE, 16)); // main partial
        playerInv.setItem(5, new ItemStack(Items.COBBLESTONE, 16));  // hotbar partial

        ItemStack remainder = RsGridInsertAdapter.insertPolicyOrdered(wrapper, new ItemStack(Items.COBBLESTONE, 80), false);

        assertTrue(remainder.isEmpty(), "everything must fit");
        assertEquals(64, playerInv.getItem(5).getCount(), "hotbar partial topped up first");
        assertEquals(48, playerInv.getItem(20).getCount(), "main partial takes the rest");
        assertTrue(playerInv.getItem(0).isEmpty(), "no empty slot should be opened");
    }

    @Test
    void withoutPartialsFillsFirstHotbarSlot() {
        ItemStack remainder = RsGridInsertAdapter.insertPolicyOrdered(wrapper, new ItemStack(Items.COBBLESTONE, 32), false);

        assertTrue(remainder.isEmpty());
        assertEquals(32, playerInv.getItem(0).getCount(), "policy-first slot is hotbar slot 1");
    }

    @Test
    void fullAndForeignStacksAreSkipped() {
        playerInv.setItem(3, new ItemStack(Items.COBBLESTONE, 64));
        playerInv.setItem(4, new ItemStack(Items.DIRT, 16));

        RsGridInsertAdapter.insertPolicyOrdered(wrapper, new ItemStack(Items.COBBLESTONE, 32), false);

        assertEquals(32, playerInv.getItem(0).getCount());
        assertEquals(64, playerInv.getItem(3).getCount());
        assertEquals(16, playerInv.getItem(4).getCount());
    }

    @Test
    void simulateReportsFitWithoutMutating() {
        playerInv.setItem(5, new ItemStack(Items.COBBLESTONE, 60));

        ItemStack remainder = RsGridInsertAdapter.insertPolicyOrdered(wrapper, new ItemStack(Items.COBBLESTONE, 10), true);

        assertTrue(remainder.isEmpty(), "4 into the partial + 6 into an empty slot");
        assertEquals(60, playerInv.getItem(5).getCount(), "simulate must not mutate");
        assertTrue(playerInv.getItem(0).isEmpty());
    }

    @Test
    void nonPlayerHandlersAreNotApplicable() {
        assertNull(RsGridInsertAdapter.tryInsert(new ItemStackHandler(27), new ItemStack(Items.COBBLESTONE, 32), false),
                "machine/exporter handlers must keep RS native behavior");
    }
}
