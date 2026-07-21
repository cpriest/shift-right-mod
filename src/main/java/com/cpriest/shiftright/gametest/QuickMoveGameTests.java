package com.cpriest.shiftright.gametest;

import com.cpriest.shiftright.ShiftRightMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless in-world tests (run via {@code ./gradlew runGameTestServer}; the server
 * exits non-zero on failure, so CI catches regressions without ever opening a client).
 *
 * <p>Unlike the JUnit menu tests, these go through {@code AbstractContainerMenu#clicked}
 * with {@link ClickType#QUICK_MOVE} against a real chest block entity in a real
 * {@code ServerLevel} — the exact entry point a player's shift-click packet hits on the
 * server.
 */
@GameTestHolder(ShiftRightMod.MOD_ID)
public class QuickMoveGameTests {

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public static void chestQuickMoveFillsHotbarFirst(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), chest);
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);

        ItemStack hotbar0 = player.getInventory().getItem(0);
        if (!hotbar0.is(Items.COBBLESTONE) || hotbar0.getCount() != 64) {
            helper.fail("expected 64 cobblestone in hotbar slot 0 (vanilla would use slot 8), found: " + hotbar0);
        }
        if (!player.getInventory().getItem(8).isEmpty()) {
            helper.fail("hotbar slot 8 (vanilla's first choice) should be empty");
        }
        if (!chest.getItem(0).isEmpty()) {
            helper.fail("chest slot 0 should have been emptied");
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public static void quickMoveIntoChestIsUntouchedAndConserved(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.STONE, 64));

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), chest);
        // Hotbar container slot 0 is menu slot 54 in a three-row chest menu.
        menu.clicked(54, 0, ClickType.QUICK_MOVE, player);

        if (!chest.getItem(1).is(Items.OAK_PLANKS) || chest.getItem(1).getCount() != 64) {
            helper.fail("expected planks in first empty chest slot (vanilla forward order), found: " + chest.getItem(1));
        }
        if (!player.getInventory().getItem(0).isEmpty()) {
            helper.fail("player hotbar slot should have been emptied");
        }
        helper.succeed();
    }
}
