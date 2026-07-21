package com.cpriest.shiftright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpriest.shiftright.policy.MainFirstPolicy;
import com.cpriest.shiftright.policy.SlotOrderPolicy;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Menu-level tests that run through FML's JUnit launch (ModDevGradle {@code unitTest}),
 * so Minecraft registries are bootstrapped and this mod's mixins are APPLIED. A real
 * {@link ChestMenu} over a real player {@link Inventory} is exercised via
 * {@code quickMoveStack} — the same server-side path a shift-click takes.
 *
 * <p>Key fact these tests rely on: vanilla ChestMenu quick-moves into the player
 * inventory with {@code moveItemStackTo(stack, 27, 63, reverse=true)}, i.e. vanilla
 * fills the hotbar right-to-left (slot 9 first). Our default policy fills hotbar
 * left-to-right (slot 1 first). Assertions on hotbar slot 0 vs 8 therefore prove the
 * mixin is active, not just that items moved.
 */
class QuickMoveIntegrationTest {

    // ChestMenu slot layout: 0..26 chest, 27..53 player main (container 9..35),
    // 54..62 hotbar (container 0..8).
    private static final int FIRST_PLAYER_MENU_SLOT = 27;

    private final Inventory playerInv = new Inventory(null);
    private final SimpleContainer chest = new SimpleContainer(27);
    private final ChestMenu menu = ChestMenu.threeRows(1, playerInv, chest);

    @AfterEach
    void resetOverrides() {
        ShiftRightConfig.clearTestOverrides();
    }

    private int totalCount(net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) {
                total += chest.getItem(i).getCount();
            }
        }
        for (int i = 0; i < playerInv.getContainerSize(); i++) {
            if (playerInv.getItem(i).is(item)) {
                total += playerInv.getItem(i).getCount();
            }
        }
        return total;
    }

    @Test
    void chestQuickMoveLandsInLeftmostHotbarSlot() {
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));

        menu.quickMoveStack(null, 0);

        assertEquals(64, playerInv.getItem(0).getCount(),
                "expected the full stack in hotbar slot 0 (vanilla reverse order would use slot 8)");
        assertTrue(playerInv.getItem(0).is(Items.COBBLESTONE));
        assertTrue(playerInv.getItem(8).isEmpty(), "vanilla's first-choice slot must be empty");
        assertTrue(chest.getItem(0).isEmpty(), "source slot must be emptied");
    }

    @Test
    void topUpPassPrefersHotbarPartialOverMainPartial() {
        playerInv.setItem(20, new ItemStack(Items.COBBLESTONE, 16)); // main inventory partial
        playerInv.setItem(5, new ItemStack(Items.COBBLESTONE, 16));  // hotbar partial
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));

        menu.quickMoveStack(null, 0);

        assertEquals(48, playerInv.getItem(5).getCount(), "hotbar partial should be topped up first");
        assertEquals(16, playerInv.getItem(20).getCount(), "main partial should be untouched");
    }

    @Test
    void nonStackablesFillHotbarLeftToRightSkippingOccupied() {
        playerInv.setItem(0, new ItemStack(Items.DIRT, 1));
        for (int i = 0; i < 3; i++) {
            chest.setItem(i, new ItemStack(Items.IRON_SWORD));
            menu.quickMoveStack(null, i);
        }

        for (int hotbar : new int[]{1, 2, 3}) {
            assertTrue(playerInv.getItem(hotbar).is(Items.IRON_SWORD),
                    "expected a sword in hotbar slot " + hotbar);
        }
        assertTrue(playerInv.getItem(4).isEmpty());
    }

    @Test
    void quickMoveIntoChestKeepsVanillaOrder() {
        playerInv.setItem(0, new ItemStack(Items.OAK_PLANKS, 64)); // hotbar 0 = menu slot 54
        chest.setItem(0, new ItemStack(Items.STONE, 64));          // force planks past slot 0

        menu.quickMoveStack(null, 54);

        assertEquals(64, chest.getItem(1).getCount(),
                "ranges without player slots must keep vanilla forward fill (first empty chest slot)");
        assertTrue(chest.getItem(1).is(Items.OAK_PLANKS));
    }

    @Test
    void noItemsLostOrDuplicatedAcrossManyQuickMoves() {
        for (int i = 0; i < 27; i++) {
            chest.setItem(i, new ItemStack(Items.COBBLESTONE, 1 + (i * 7) % 64));
        }
        int before = totalCount(Items.COBBLESTONE);

        for (int i = 0; i < 27; i++) {
            menu.quickMoveStack(null, i);
        }

        assertEquals(before, totalCount(Items.COBBLESTONE), "total item count must be conserved");
    }

    @Test
    void overflowStaysInChestWhenPlayerInventoryFills() {
        for (int i = 0; i < playerInv.items.size(); i++) {
            playerInv.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        playerInv.setItem(0, new ItemStack(Items.COBBLESTONE, 32)); // one partial, room for 32
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        int before = totalCount(Items.COBBLESTONE);

        menu.quickMoveStack(null, 0);

        assertEquals(64, playerInv.getItem(0).getCount(), "partial hotbar stack should be topped up");
        assertEquals(32, chest.getItem(0).getCount(), "overflow must remain in the chest");
        assertEquals(before, totalCount(Items.COBBLESTONE));
    }

    @Test
    void mainFirstPolicyOverrideFillsMainInventoryFirst() {
        ShiftRightConfig.setTestOverrides(MainFirstPolicy.INSTANCE, SlotOrderPolicy.Context.DEFAULT);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));

        menu.quickMoveStack(null, 0);

        assertEquals(64, playerInv.getItem(9).getCount(),
                "MAIN_FIRST should land in the first main-inventory slot (container slot 9)");
        assertTrue(playerInv.getItem(0).isEmpty());
    }

    @Test
    void inventoryGetFreeSlotHonorsMainFirstOverride() {
        ShiftRightConfig.setTestOverrides(MainFirstPolicy.INSTANCE, SlotOrderPolicy.Context.DEFAULT);
        Inventory inv = new Inventory(null);

        assertEquals(9, inv.getFreeSlot(),
                "MAIN_FIRST should scan main inventory (slot 9) before the hotbar");
    }

    @Test
    void inventoryGetFreeSlotDefaultMatchesVanillaHotbarFirst() {
        Inventory inv = new Inventory(null);
        inv.setItem(0, new ItemStack(Items.DIRT, 1));

        assertEquals(1, inv.getFreeSlot());
    }
}
