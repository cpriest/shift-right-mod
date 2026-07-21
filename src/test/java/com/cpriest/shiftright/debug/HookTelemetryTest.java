package com.cpriest.shiftright.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure JUnit (no Minecraft classes): debounced call counting and elapsed-time
 * formatting for the dev overlay.
 */
class HookTelemetryTest {

    private long now;

    @BeforeEach
    void injectClock() {
        now = 0;
        HookTelemetry.clock = () -> now;
        HookTelemetry.resetAll();
    }

    @AfterEach
    void restoreClock() {
        HookTelemetry.clock = System::nanoTime;
        HookTelemetry.resetAll();
    }

    @Test
    void neverFiredReportsNegativeElapsedAndZeroCalls() {
        assertEquals(-1, HookTelemetry.CORE_QUICK_MOVE.nanosSinceLast());
        assertEquals(0, HookTelemetry.CORE_QUICK_MOVE.calls());
    }

    @Test
    void recordCountsOnceAndTracksElapsed() {
        HookTelemetry.CORE_QUICK_MOVE.record();
        now += ms(250);
        assertEquals(ms(250), HookTelemetry.CORE_QUICK_MOVE.nanosSinceLast());
        assertEquals(1, HookTelemetry.CORE_QUICK_MOVE.calls());
    }

    @Test
    void burstWithinDebounceWindowCountsAsOneCall() {
        // The core hook fires once per slot visit inside moveItemStackTo — a single
        // shift-click produces a burst of records within a few ms.
        for (int i = 0; i < 40; i++) {
            HookTelemetry.CORE_QUICK_MOVE.record();
            now += ms(1);
        }
        assertEquals(1, HookTelemetry.CORE_QUICK_MOVE.calls());
        assertEquals(ms(1), HookTelemetry.CORE_QUICK_MOVE.nanosSinceLast());
    }

    @Test
    void recordsBeyondDebounceWindowCountSeparately() {
        HookTelemetry.CORE_QUICK_MOVE.record();
        now += ms(150);
        HookTelemetry.CORE_QUICK_MOVE.record();
        assertEquals(2, HookTelemetry.CORE_QUICK_MOVE.calls());
    }

    @Test
    void hooksTrackIndependently() {
        HookTelemetry.AE2_ADAPTER.record();
        assertEquals(1, HookTelemetry.AE2_ADAPTER.calls());
        assertEquals(0, HookTelemetry.ADD_PATH.calls());
        assertEquals(-1, HookTelemetry.ADD_PATH.nanosSinceLast());
    }

    @Test
    void humanizeFormatsAcrossMagnitudes() {
        assertEquals("never", HookTelemetry.humanize(-1));
        assertEquals("0ms", HookTelemetry.humanize(0));
        assertEquals("874ms", HookTelemetry.humanize(ms(874)));
        assertEquals("1.2s", HookTelemetry.humanize(ms(1_250)));
        assertEquals("59.9s", HookTelemetry.humanize(ms(59_999)));
        assertEquals("1m 00s", HookTelemetry.humanize(ms(60_000)));
        assertEquals("4m 07s", HookTelemetry.humanize(ms(247_000)));
    }

    private static long ms(long millis) {
        return millis * 1_000_000L;
    }
}
