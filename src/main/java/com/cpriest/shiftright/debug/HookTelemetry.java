package com.cpriest.shiftright.debug;

import java.util.function.LongSupplier;

/**
 * Dev-facing telemetry for the mod's three reorder hooks, fed by the hot paths and
 * read by the debug overlay (enabled with {@code -Dshiftright.debugOverlay=true};
 * the Gradle dev client sets it). Minecraft-free so it stays pure-JUnit-testable.
 *
 * <p>A single shift-click drives the core hook once per slot visit inside
 * {@code moveItemStackTo}, so records within {@link #DEBOUNCE_NANOS} of the previous
 * one collapse into the same counted call; the last-fired timestamp always updates.
 *
 * <p>Hooks record on the server thread and the overlay reads on the render thread;
 * the fields are volatile and dev-client testing is singleplayer (integrated server,
 * same JVM). On a dedicated server the overlay simply shows nothing — recording
 * itself is two volatile writes, cheap enough to leave unconditional.
 */
public enum HookTelemetry {
    CORE_QUICK_MOVE("quickMove reorder"),
    ADD_PATH("add-path scan"),
    AE2_ADAPTER("AE2 dest reorder"),
    MOUSE_TWEAKS("MouseTweaks dest reorder"),
    SOPHISTICATED("Sophisticated merge");

    static final long DEBOUNCE_NANOS = 100_000_000L;

    /** Test seam — tests inject a manual clock, production uses nanoTime. */
    static LongSupplier clock = System::nanoTime;

    private final String label;
    private volatile long lastNanos = Long.MIN_VALUE;
    private volatile int calls;

    HookTelemetry(String label) {
        this.label = label;
    }

    public void record() {
        long now = clock.getAsLong();
        if (lastNanos == Long.MIN_VALUE || now - lastNanos > DEBOUNCE_NANOS) {
            calls++;
        }
        lastNanos = now;
    }

    public String label() {
        return label;
    }

    public int calls() {
        return calls;
    }

    /** Nanos since the last {@link #record()}, or -1 if this hook has never fired. */
    public long nanosSinceLast() {
        long last = lastNanos;
        return last == Long.MIN_VALUE ? -1 : clock.getAsLong() - last;
    }

    /** {@code "never"}, {@code "874ms"}, {@code "3.2s"}, {@code "4m 07s"}. */
    public static String humanize(long nanos) {
        if (nanos < 0) {
            return "never";
        }
        long ms = nanos / 1_000_000L;
        if (ms < 1_000) {
            return ms + "ms";
        }
        if (ms < 60_000) {
            long tenths = ms / 100;
            return (tenths / 10) + "." + (tenths % 10) + "s";
        }
        long seconds = ms / 1_000;
        return (seconds / 60) + "m " + String.format("%02ds", seconds % 60);
    }

    static void resetAll() {
        for (HookTelemetry hook : values()) {
            hook.lastNanos = Long.MIN_VALUE;
            hook.calls = 0;
        }
    }
}
