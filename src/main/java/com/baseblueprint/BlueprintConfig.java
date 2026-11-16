package com.baseblueprint;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (world/serverconfig/baseblueprint-server.toml), synced to clients so the menu shows the same limits. */
public final class BlueprintConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_SIZE;
    public static final ModConfigSpec.IntValue MAX_WALL_HEIGHT;
    public static final ModConfigSpec.BooleanValue ALLOW_UNDO;
    public static final ModConfigSpec.BooleanValue ALLOW_PARTIAL;
    public static final ModConfigSpec.IntValue CONTAINER_RADIUS;
    public static final ModConfigSpec.IntValue MAX_FOUNDATION;

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
        CONTAINER_RADIUS = b.comment("Blocks also come from containers within this many blocks of the player (chests, barrels,",
                        "drawers...) and from containers carried in the inventory (backpacks). 0 = inventory only.")
                .defineInRange("containerRadius", 8, 0, 32);
        MAX_FOUNDATION = b.comment("Deepest foundation the blueprint fills under the floor on uneven ground, in blocks.")
                .defineInRange("maxFoundation", 8, 0, 64);
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

    public static int containerRadius() {
        return SPEC.isLoaded() ? CONTAINER_RADIUS.get() : 8;
    }

    public static int maxFoundation() {
        return SPEC.isLoaded() ? MAX_FOUNDATION.get() : 8;
    }

    private BlueprintConfig() {
    }
}
