package com.baseblueprint;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (world/serverconfig/baseblueprint-server.toml), synced to clients so the menu shows the same limits. */
public final class BlueprintConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_SIZE;
    public static final ModConfigSpec.IntValue MAX_WALL_HEIGHT;
    public static final ModConfigSpec.BooleanValue ALLOW_UNDO;
    public static final ModConfigSpec.BooleanValue ALLOW_PARTIAL;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        MAX_SIZE = b.comment("Largest width or length a base can have, in blocks.")
                .defineInRange("maxSize", 64, 3, 256);
        MAX_WALL_HEIGHT = b.comment("Tallest wall the blueprint can build, in blocks.")
                .defineInRange("maxWallHeight", 16, 0, 64);
        ALLOW_UNDO = b.comment("Sneak + right-click in the air removes your last base and gives the blocks back.")
                .define("allowUndo", true);
        ALLOW_PARTIAL = b.comment("Build anyway when some spots are occupied (leaving holes there). Off: the base is only built when every spot is free.")
                .define("allowPartial", false);
        SPEC = b.build();
    }

    public static int maxSize() {
        return SPEC.isLoaded() ? MAX_SIZE.get() : 64;
    }

    public static int maxWallHeight() {
        return SPEC.isLoaded() ? MAX_WALL_HEIGHT.get() : 16;
    }

    public static boolean allowUndo() {
        return !SPEC.isLoaded() || ALLOW_UNDO.get();
    }

    public static boolean allowPartial() {
        return SPEC.isLoaded() && ALLOW_PARTIAL.get();
    }

    private BlueprintConfig() {
    }
}
