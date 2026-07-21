package com.cpriest.shiftright;

import com.cpriest.shiftright.policy.HotbarFirstPolicy;
import com.cpriest.shiftright.policy.MainFirstPolicy;
import com.cpriest.shiftright.policy.SlotOrderPolicy;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side config. SERVER-type configs are synced to remote clients on login, so
 * client-side quick-move prediction stays consistent with the server's authoritative
 * result.
 *
 * <p>All accessors are safe to call before the config is loaded (e.g. very early in
 * startup) and return the defaults in that case.
 */
public final class ShiftRightConfig {

    public enum FillOrder {
        HOTBAR_FIRST,
        MAIN_FIRST
    }

    public enum Direction {
        FORWARD,
        REVERSE
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.EnumValue<FillOrder> FILL_ORDER = BUILDER
            .comment("Which player inventory section quick-moved items fill first.")
            .defineEnum("fillOrder", FillOrder.HOTBAR_FIRST);

    private static final ModConfigSpec.EnumValue<Direction> DIRECTION = BUILDER
            .comment("FORWARD fills hotbar 1->9 and main inventory top-left->bottom-right; REVERSE flips the whole order.")
            .defineEnum("direction", Direction.FORWARD);

    private static final ModConfigSpec.BooleanValue ENABLE_AE2_ADAPTER = BUILDER
            .comment("Reorder quick-move destinations for AE2 terminals (ME/Crafting/Pattern Access/Wireless/Portable).")
            .define("enableAe2Adapter", true);

    private static final ModConfigSpec.BooleanValue ENABLE_MOUSE_TWEAKS_ADAPTER = BUILDER
            .comment("Reorder MouseTweaks' wheel-scroll destination search so scrolled-out items land in the player",
                    "inventory in policy order (client-side; which stack DONATES into a container stays governed by",
                    "MouseTweaks' own wheelSearchOrder config).")
            .define("enableMouseTweaksAdapter", true);

    private static final ModConfigSpec.BooleanValue ENABLE_SOPHISTICATED_ADAPTER = BUILDER
            .comment("Reorder Sophisticated Storage/Backpacks quick-moves into the player inventory (their menus",
                    "reimplement vanilla's merge, bypassing the core hook).")
            .define("enableSophisticatedAdapter", true);

    private static final ModConfigSpec.BooleanValue ENABLE_REFINED_STORAGE_ADAPTER = BUILDER
            .comment("Reserved: reorder quick-move destinations for Refined Storage grids (see README for current status).")
            .define("enableRefinedStorageAdapter", true);

    private static final ModConfigSpec.BooleanValue ENABLE_VANILLA_ADD_PATH_MIXIN = BUILDER
            .comment("Reorder Inventory.getFreeSlot/getSlotWithRemainingSpace scans (the vanilla 'give' path many mods",
                    "delegate to). Also affects non-shift-click item entry such as ground pickup and dispensers.")
            .define("enableVanillaAddPathMixin", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ShiftRightConfig() {
    }

    // Test-only overrides: unit/game tests run without a loaded config file, so this
    // is the seam they use to exercise non-default policies. Never set in production.
    private static volatile SlotOrderPolicy testPolicyOverride;
    private static volatile SlotOrderPolicy.Context testContextOverride;

    public static void setTestOverrides(SlotOrderPolicy policy, SlotOrderPolicy.Context ctx) {
        testPolicyOverride = policy;
        testContextOverride = ctx;
    }

    public static void clearTestOverrides() {
        testPolicyOverride = null;
        testContextOverride = null;
    }

    public static SlotOrderPolicy policy() {
        SlotOrderPolicy override = testPolicyOverride;
        if (override != null) {
            return override;
        }
        return fillOrder() == FillOrder.MAIN_FIRST ? MainFirstPolicy.INSTANCE : HotbarFirstPolicy.INSTANCE;
    }

    public static SlotOrderPolicy.Context policyContext() {
        SlotOrderPolicy.Context override = testContextOverride;
        if (override != null) {
            return override;
        }
        return direction() == Direction.REVERSE ? new SlotOrderPolicy.Context(true) : SlotOrderPolicy.Context.DEFAULT;
    }

    public static FillOrder fillOrder() {
        return SPEC.isLoaded() ? FILL_ORDER.get() : FillOrder.HOTBAR_FIRST;
    }

    public static Direction direction() {
        return SPEC.isLoaded() ? DIRECTION.get() : Direction.FORWARD;
    }

    public static boolean ae2AdapterEnabled() {
        return SPEC.isLoaded() ? ENABLE_AE2_ADAPTER.get() : true;
    }

    public static boolean mouseTweaksAdapterEnabled() {
        return SPEC.isLoaded() ? ENABLE_MOUSE_TWEAKS_ADAPTER.get() : true;
    }

    public static boolean sophisticatedAdapterEnabled() {
        return SPEC.isLoaded() ? ENABLE_SOPHISTICATED_ADAPTER.get() : true;
    }

    public static boolean refinedStorageAdapterEnabled() {
        return SPEC.isLoaded() ? ENABLE_REFINED_STORAGE_ADAPTER.get() : true;
    }

    public static boolean vanillaAddPathEnabled() {
        return SPEC.isLoaded() ? ENABLE_VANILLA_ADD_PATH_MIXIN.get() : true;
    }
}
