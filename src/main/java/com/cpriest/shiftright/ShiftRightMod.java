package com.cpriest.shiftright;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(ShiftRightMod.MOD_ID)
public final class ShiftRightMod {

    public static final String MOD_ID = "shiftright";

    public ShiftRightMod(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ShiftRightConfig.SPEC);
    }
}
