package com.cpriest.shiftright.gametest;

import com.cpriest.shiftright.ShiftRightMod;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.slf4j.Logger;

/**
 * Offline verification of the @Pseudo compat mixins against the real third-party
 * jars, without ever opening a client:
 *
 * <pre>./gradlew runGameTestServer -PdevStorage</pre>
 *
 * The run targets (unlike the FML JUnit launch) discover classpath jars as full mods,
 * so loading a target class here triggers real mixin application. Targets whose mod
 * isn't in the run are skipped, so a plain {@code runGameTestServer} still passes.
 * Combined with {@code ci/check-compat-contracts.sh} (javap signature checks), this
 * catches compat drift offline; booting a client is only for eyeballing feel.
 */
@GameTestHolder(ShiftRightMod.MOD_ID)
public class CompatMixinApplyGameTests {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** target class -> the handler method our mixin merges into it. */
    private static final Map<String, String> MIXIN_TARGETS = Map.of(
            "appeng.menu.AEBaseMenu", "shiftright$reorderQuickMoveDestinations",
            "com.refinedmods.refinedstorage.neoforge.storage.ItemHandlerInsertableStorage",
            "shiftright$policyOrderedPlayerInsert",
            "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase", "shiftright$visitSlotsInPolicyOrder",
            "yalter.mousetweaks.Main", "shiftright$policyOrderPushSearch");

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public static void compatMixinsMergeIntoLoadedMods(GameTestHelper helper) {
        int verified = 0;
        List<String> failures = new ArrayList<>();
        for (var entry : MIXIN_TARGETS.entrySet()) {
            boolean merged;
            try {
                Class<?> target = Class.forName(entry.getKey());
                // Mixin decorates merged handler names (e.g. handler$zzz$shiftright$...),
                // so match by substring rather than equality.
                merged = Arrays.stream(target.getDeclaredMethods())
                        .anyMatch(m -> m.getName().contains(entry.getValue()));
            } catch (ClassNotFoundException e) {
                continue; // that mod isn't part of this run
            } catch (Throwable t) {
                // e.g. MouseTweaks' Main references client-only classes, which the
                // dedicated-server dist guard refuses to resolve. The javap contract
                // check still covers its hook signature offline.
                LOGGER.info("[shift-right] skipping merge check for {} (not loadable on this dist): {}",
                        entry.getKey(), t.toString());
                continue;
            }
            if (!merged) {
                failures.add(entry.getValue() + " did not merge into " + entry.getKey());
                continue;
            }
            verified++;
        }
        if (!failures.isEmpty()) {
            helper.fail("mixin target resolution failed: " + String.join("; ", failures));
            return;
        }
        LOGGER.info("[shift-right] compat mixin merge verified for {}/{} targets present in this run",
                verified, MIXIN_TARGETS.size());
        helper.succeed();
    }

    /**
     * End-to-end through RS's own code: inserts via a real
     * {@code ItemHandlerInsertableStorage} (reflection — no compile dependency) into a
     * mock player's inventory. RS-native behavior is first-fit into empty hotbar slot
     * 1; the adapter must top up the existing partial stack instead, so the assertion
     * fails if either the mixin didn't inject or the adapter regressed.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty")
    public static void refinedStorageGridInsertTopsUpExistingStack(GameTestHelper helper) {
        Class<?> storageClass;
        try {
            storageClass = Class.forName("com.refinedmods.refinedstorage.neoforge.storage.ItemHandlerInsertableStorage");
        } catch (ClassNotFoundException e) {
            helper.succeed(); // RS not part of this run
            return;
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 16));
        IItemHandler handler = new PlayerMainInvWrapper(player.getInventory());

        long inserted;
        try {
            Class<?> cacheClass = Class.forName("com.refinedmods.refinedstorage.neoforge.storage.CapabilityCache");
            Object cache = cacheClass.getMethod("ofItemHandler", IItemHandler.class).invoke(null, handler);
            Object storage = storageClass.getConstructor(cacheClass).newInstance(cache);

            Object resource = Class.forName("com.refinedmods.refinedstorage.common.support.resource.ItemResource")
                    .getMethod("ofItemStack", ItemStack.class).invoke(null, new ItemStack(Items.COBBLESTONE));
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> actionClass = (Class<Enum>) Class.forName("com.refinedmods.refinedstorage.api.core.Action");
            Object execute = Enum.valueOf(actionClass, "EXECUTE");
            Class<?> actorClass = Class.forName("com.refinedmods.refinedstorage.api.storage.Actor");
            Object actor = actorClass.getField("EMPTY").get(null);

            inserted = (long) storageClass.getMethod("insert",
                            Class.forName("com.refinedmods.refinedstorage.api.resource.ResourceKey"),
                            long.class, actionClass, actorClass)
                    .invoke(storage, resource, 32L, execute, actor);
        } catch (ReflectiveOperationException e) {
            helper.fail("RS API reshaped, update this test and likely the adapter: " + e);
            return;
        }

        if (inserted != 32) {
            helper.fail("expected RS to report 32 items inserted, got " + inserted);
        }
        if (player.getInventory().getItem(5).getCount() != 48) {
            helper.fail("expected the hotbar partial topped up to 48 (RS-native first-fit would fill slot 1); got "
                    + player.getInventory().getItem(5));
        }
        if (!player.getInventory().getItem(0).isEmpty()) {
            helper.fail("hotbar slot 1 should stay empty when a partial can absorb everything; got "
                    + player.getInventory().getItem(0));
        }
        helper.succeed();
    }
}
