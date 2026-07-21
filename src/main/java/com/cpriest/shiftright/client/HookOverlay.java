package com.cpriest.shiftright.client;

import com.cpriest.shiftright.ShiftRightMod;
import com.cpriest.shiftright.debug.HookTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Dev overlay: one line per hook showing time since it last fired and a debounced
 * call count, to verify the hooks engage against whichever mods are loaded. Gated
 * behind {@code -Dshiftright.debugOverlay=true} (the Gradle dev client sets it), so
 * released jars never render it. Hooks record on the integrated server in the same
 * JVM, so this is meaningful in singleplayer — exactly the dev-client case.
 */
@EventBusSubscriber(modid = ShiftRightMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class HookOverlay {

    private static final boolean ENABLED = Boolean.getBoolean("shiftright.debugOverlay");
    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 4;
    private static final int COLOR_NEVER = 0xFF9E9E9E;
    private static final int COLOR_RECENT = 0xFF7CFC8F;
    private static final int COLOR_STALE = 0xFFFFFFFF;
    private static final long RECENT_NANOS = 2_000_000_000L;

    private HookOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int y = MARGIN;
        for (HookTelemetry hook : HookTelemetry.values()) {
            long since = hook.nanosSinceLast();
            String text = since < 0
                    ? hook.label() + ": never"
                    : hook.label() + ": " + HookTelemetry.humanize(since) + " ago (x" + hook.calls() + ")";
            int color = since < 0 ? COLOR_NEVER : since < RECENT_NANOS ? COLOR_RECENT : COLOR_STALE;
            graphics.drawString(font, text, MARGIN, y, color, true);
            y += LINE_HEIGHT;
        }
    }
}
