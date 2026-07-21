package com.cpriest.shiftright.policy;

/** Default policy: hotbar 1&rarr;9, then main inventory top-left&rarr;bottom-right. */
public final class HotbarFirstPolicy extends AbstractRoleOrderPolicy {

    public static final HotbarFirstPolicy INSTANCE = new HotbarFirstPolicy();

    private HotbarFirstPolicy() {
    }

    @Override
    protected int rank(QuickMoveSlot.Role role) {
        return role == QuickMoveSlot.Role.HOTBAR ? 0 : 1;
    }
}
