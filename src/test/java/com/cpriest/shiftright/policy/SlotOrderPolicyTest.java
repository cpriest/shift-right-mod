package com.cpriest.shiftright.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpriest.shiftright.policy.QuickMoveSlot.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SlotOrderPolicyTest {

    private static QuickMoveSlot hotbar(int sub) {
        return new QuickMoveSlot(Role.HOTBAR, sub, "H" + sub);
    }

    private static QuickMoveSlot main(int sub) {
        return new QuickMoveSlot(Role.MAIN, sub, "M" + sub);
    }

    private static QuickMoveSlot other(String id) {
        return new QuickMoveSlot(Role.OTHER, 0, id);
    }

    private static List<Object> handles(List<QuickMoveSlot> slots) {
        return slots.stream().map(QuickMoveSlot::handle).toList();
    }

    /** A shuffled full player-inventory range, like a chest menu's [27..63) region. */
    private static List<QuickMoveSlot> shuffledPlayerRange(long seed) {
        List<QuickMoveSlot> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            slots.add(hotbar(i));
        }
        for (int i = 0; i < 27; i++) {
            slots.add(main(i));
        }
        Collections.shuffle(slots, new Random(seed));
        return slots;
    }

    @Test
    void hotbarFirstOrdersHotbarThenMainRowMajor() {
        List<QuickMoveSlot> ordered = HotbarFirstPolicy.INSTANCE
                .order(shuffledPlayerRange(42), SlotOrderPolicy.Context.DEFAULT);

        List<Object> expected = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            expected.add("H" + i);
        }
        for (int i = 0; i < 27; i++) {
            expected.add("M" + i);
        }
        assertEquals(expected, handles(ordered));
    }

    @Test
    void mainFirstOrdersMainThenHotbar() {
        List<QuickMoveSlot> ordered = MainFirstPolicy.INSTANCE
                .order(shuffledPlayerRange(7), SlotOrderPolicy.Context.DEFAULT);

        List<Object> expected = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            expected.add("M" + i);
        }
        for (int i = 0; i < 9; i++) {
            expected.add("H" + i);
        }
        assertEquals(expected, handles(ordered));
    }

    @Test
    void reverseFlipsPlayerSectionOnly() {
        List<QuickMoveSlot> candidates = new ArrayList<>(List.of(
                main(3), hotbar(1), other("X"), hotbar(0), main(0), other("Y")));

        List<QuickMoveSlot> ordered = HotbarFirstPolicy.INSTANCE
                .order(candidates, new SlotOrderPolicy.Context(true));

        assertEquals(List.of("M3", "M0", "H1", "H0", "X", "Y"), handles(ordered));
    }

    @Test
    void otherSlotsAlwaysLastInOriginalOrder() {
        List<QuickMoveSlot> candidates = new ArrayList<>(List.of(
                other("A"), main(5), other("B"), hotbar(4), other("C")));

        List<QuickMoveSlot> ordered = HotbarFirstPolicy.INSTANCE
                .order(candidates, SlotOrderPolicy.Context.DEFAULT);

        assertEquals(List.of("H4", "M5", "A", "B", "C"), handles(ordered));
    }

    @Test
    void policiesArePureAndDoNotMutateInput() {
        List<QuickMoveSlot> candidates = shuffledPlayerRange(13);
        List<QuickMoveSlot> snapshot = List.copyOf(candidates);

        HotbarFirstPolicy.INSTANCE.order(candidates, SlotOrderPolicy.Context.DEFAULT);
        MainFirstPolicy.INSTANCE.order(candidates, new SlotOrderPolicy.Context(true));

        assertEquals(snapshot, candidates);
    }

    @Test
    void duplicateSubIndicesAreKeptStably() {
        QuickMoveSlot first = main(2);
        QuickMoveSlot second = main(2);
        List<QuickMoveSlot> ordered = HotbarFirstPolicy.INSTANCE
                .order(List.of(first, second), SlotOrderPolicy.Context.DEFAULT);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0) == first && ordered.get(1) == second);
    }

    @Test
    void sameElementsDetectsDuplicatesAndOmissions() {
        QuickMoveSlot a = hotbar(0);
        QuickMoveSlot b = main(0);
        assertTrue(SlotOrders.sameElements(List.of(a, b), List.of(b, a)));
        assertFalse(SlotOrders.sameElements(List.of(a, b), List.of(a, a)));
        assertFalse(SlotOrders.sameElements(List.of(a, b), List.of(a)));
        assertFalse(SlotOrders.sameElements(List.of(a, b), List.of(a, b, b)));
    }

    @Test
    void computeIndexPermutationForChestStylePlayerRange() {
        // Chest menu player region: absolute indices 27..62; main = 27..53, hotbar = 54..62.
        List<QuickMoveSlot> candidates = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            candidates.add(new QuickMoveSlot(Role.MAIN, i, 27 + i));
        }
        for (int i = 0; i < 9; i++) {
            candidates.add(new QuickMoveSlot(Role.HOTBAR, i, 54 + i));
        }

        int[] perm = SlotOrders.computeIndexPermutation(candidates, HotbarFirstPolicy.INSTANCE,
                SlotOrderPolicy.Context.DEFAULT);

        assertEquals(36, perm.length);
        // Hotbar 54..62 first, then main 27..53.
        for (int i = 0; i < 9; i++) {
            assertEquals(54 + i, perm[i]);
        }
        for (int i = 0; i < 27; i++) {
            assertEquals(27 + i, perm[9 + i]);
        }
    }

    @Test
    void computeIndexPermutationReturnsNullWithoutPlayerSlots() {
        List<QuickMoveSlot> candidates = List.of(
                new QuickMoveSlot(Role.OTHER, 0, 0),
                new QuickMoveSlot(Role.OTHER, 0, 1));
        assertNull(SlotOrders.computeIndexPermutation(candidates, HotbarFirstPolicy.INSTANCE,
                SlotOrderPolicy.Context.DEFAULT));
    }

    @Test
    void computeIndexPermutationRejectsBrokenPolicy() {
        SlotOrderPolicy broken = (candidates, ctx) -> {
            List<QuickMoveSlot> result = new ArrayList<>(candidates);
            result.set(0, result.get(1)); // duplicate an element, drop another
            return result;
        };
        List<QuickMoveSlot> candidates = List.of(
                new QuickMoveSlot(Role.HOTBAR, 0, 10),
                new QuickMoveSlot(Role.HOTBAR, 1, 11));
        assertNull(SlotOrders.computeIndexPermutation(candidates, broken, SlotOrderPolicy.Context.DEFAULT));
    }

    @Test
    void computeIndexPermutationIsAPermutationForEveryConfigCombination() {
        List<QuickMoveSlot> candidates = shuffledPlayerRangeWithIndices();
        for (SlotOrderPolicy policy : List.of(HotbarFirstPolicy.INSTANCE, MainFirstPolicy.INSTANCE)) {
            for (boolean reverse : new boolean[]{false, true}) {
                int[] perm = SlotOrders.computeIndexPermutation(candidates, policy,
                        new SlotOrderPolicy.Context(reverse));
                boolean[] seen = new boolean[100];
                for (int index : perm) {
                    assertFalse(seen[index], "duplicate index " + index);
                    seen[index] = true;
                }
                assertEquals(candidates.size(), perm.length);
            }
        }
    }

    private static List<QuickMoveSlot> shuffledPlayerRangeWithIndices() {
        List<QuickMoveSlot> slots = new ArrayList<>();
        int abs = 20;
        for (int i = 0; i < 9; i++) {
            slots.add(new QuickMoveSlot(Role.HOTBAR, i, abs++));
        }
        for (int i = 0; i < 27; i++) {
            slots.add(new QuickMoveSlot(Role.MAIN, i, abs++));
        }
        slots.add(new QuickMoveSlot(Role.OTHER, 0, abs++));
        Collections.shuffle(slots, new Random(99));
        return slots;
    }
}
