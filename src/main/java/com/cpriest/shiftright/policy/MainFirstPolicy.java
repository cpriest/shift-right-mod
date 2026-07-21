package com.cpriest.shiftright.policy;

/** Alternate policy: main inventory top-left&rarr;bottom-right, then hotbar 1&rarr;9. */
public final class MainFirstPolicy extends AbstractRoleOrderPolicy {

    public static final MainFirstPolicy INSTANCE = new MainFirstPolicy();

    private MainFirstPolicy() {
    }

    @Override
    protected int rank(QuickMoveSlot.Role role) {
        return role == QuickMoveSlot.Role.MAIN ? 0 : 1;
    }
}
