package com.baseblueprint.gametest;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BaseBuilder;
import com.baseblueprint.BaseShape;
import com.baseblueprint.BlueprintConfig;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import com.baseblueprint.UndoHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Run with ./gradlew runGameTestServer (exits non-zero when a test fails). */
@GameTestHolder(BaseBlueprint.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintGameTests {
    private static final String EMPTY = "empty";

    private static ItemStack blueprint(BlueprintSettings settings) {
        ItemStack stack = new ItemStack(BaseBlueprint.BLUEPRINT.get());
        stack.set(ModComponents.SETTINGS.get(), settings);
        stack.set(ModComponents.MATERIAL.get(), Blocks.COBBLESTONE);
        return stack;
    }

    private static Player survivor(GameTestHelper helper, int cobble) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        if (cobble > 0) player.getInventory().add(new ItemStack(Items.COBBLESTONE, cobble));
        return player;
    }

    /** Pixel-circle counts: 5×5 circle drops the 4 corners, 7×7 drops 3 per corner. */
    @GameTest(template = EMPTY)
    public static void circleShapeMatchesPixelCircle(GameTestHelper helper) {
        BlueprintSettings c5 = new BlueprintSettings(BlueprintSettings.Shape.CIRCLE, 5, 5, BlueprintSettings.Floor.FILLED, 0);
        BlueprintSettings c7 = new BlueprintSettings(BlueprintSettings.Shape.CIRCLE, 7, 7, BlueprintSettings.Floor.FILLED, 0);
        BlueprintSettings sq = new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 6, 4, BlueprintSettings.Floor.OUTLINE, 2);
        helper.assertTrue(BaseShape.of(c5).blockCount(c5) == 21, "5x5 circle should have 21 cells, got " + BaseShape.of(c5).blockCount(c5));
        helper.assertTrue(BaseShape.of(c7).blockCount(c7) == 37, "7x7 circle should have 37 cells, got " + BaseShape.of(c7).blockCount(c7));
        // 6×4 square ring = 16 cells, plus 2 wall layers of the same ring
        helper.assertTrue(BaseShape.of(sq).blockCount(sq) == 48, "6x4 outline with 2 walls should be 48, got " + BaseShape.of(sq).blockCount(sq));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void buildsAndTakesBlocksFromInventory(GameTestHelper helper) {
        Player player = survivor(helper, 64);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BlueprintSettings s = new BlueprintSettings(BlueprintSettings.Shape.CIRCLE, 5, 5, BlueprintSettings.Floor.FILLED, 0);
        int placed = BaseBuilder.build(player, blueprint(s), origin);
        helper.assertTrue(placed == 21, "expected 21 blocks placed, got " + placed);
        helper.assertTrue(BaseBuilder.countItem(player, Items.COBBLESTONE) == 43, "expected 43 cobblestone left");
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 1, 4));
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(6, 1, 5));
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(6, 1, 6)); // corner of the circle
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void refusesWithoutEnoughBlocks(GameTestHelper helper) {
        Player player = survivor(helper, 10);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BlueprintSettings s = new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 5, 5, BlueprintSettings.Floor.FILLED, 0);
        int placed = BaseBuilder.build(player, blueprint(s), origin);
        helper.assertTrue(placed == 0, "nothing should be built with 10 of 25 blocks");
        helper.assertTrue(BaseBuilder.countItem(player, Items.COBBLESTONE) == 10, "inventory must be untouched");
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(4, 1, 4));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void refusesWhenSpotsAreOccupied(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        Player player = survivor(helper, 64);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BlueprintSettings s = new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0);
        int placed = BaseBuilder.build(player, blueprint(s), origin);
        helper.assertTrue(placed == 0, "an occupied spot must stop the whole base, got " + placed);
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(3, 1, 3));
        helper.assertTrue(BaseBuilder.countItem(player, Items.COBBLESTONE) == 64, "inventory must be untouched");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void replaceablePlantsDoNotBlock(GameTestHelper helper) {
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.SHORT_GRASS);
        Player player = survivor(helper, 64);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BlueprintSettings s = new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0);
        int placed = BaseBuilder.build(player, blueprint(s), origin);
        helper.assertTrue(placed == 9, "short grass is built over, got " + placed);
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(5, 1, 4));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void partialBuildSkipsAndChargesOnlyPlaced(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        Player player = survivor(helper, 64);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BlueprintSettings s = new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0);
        BlueprintConfig.ALLOW_PARTIAL.set(true);
        int placed;
        try {
            placed = BaseBuilder.build(player, blueprint(s), origin);
        } finally {
            BlueprintConfig.ALLOW_PARTIAL.set(false);
        }
        helper.assertTrue(placed == 8, "expected 8 placed around the stone, got " + placed);
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 1, 4));
        helper.assertTrue(BaseBuilder.countItem(player, Items.COBBLESTONE) == 56, "only placed blocks are charged");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void wallsGoOnTopOfTheFloorEdge(GameTestHelper helper) {
        Player player = survivor(helper, 64);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BlueprintSettings s = new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 2);
        int placed = BaseBuilder.build(player, blueprint(s), origin);
        helper.assertTrue(placed == 9 + 8 * 2, "expected 25, got " + placed);
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(3, 3, 3));
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(4, 2, 4)); // inside stays open
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(3, 4, 3));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void undoReturnsTheBlocks(GameTestHelper helper) {
        Player player = survivor(helper, 64);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BlueprintSettings s = new BlueprintSettings(BlueprintSettings.Shape.CIRCLE, 5, 5, BlueprintSettings.Floor.OUTLINE, 1);
        int placed = BaseBuilder.build(player, blueprint(s), origin);
        helper.assertTrue(placed > 0, "should build");
        BaseBuilder.undo(player);
        helper.assertTrue(BaseBuilder.countItem(player, Items.COBBLESTONE) == 64, "undo should return every block");
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(4, 1, 2));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void offhandBlockWinsAndCreativeIsFree(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS));
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BlueprintSettings s = new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0);
        int placed = BaseBuilder.build(player, blueprint(s), origin);
        helper.assertTrue(placed == 9, "expected 9, got " + placed);
        helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 1, 4));
        helper.assertTrue(player.getOffhandItem().getCount() == 1, "creative should not consume");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rejectsBadMaterials(GameTestHelper helper) {
        helper.assertTrue(BaseBuilder.isValidMaterial(Blocks.COBBLESTONE), "cobblestone is fine");
        helper.assertTrue(BaseBuilder.isValidMaterial(Blocks.OAK_SLAB), "slabs are fine");
        helper.assertFalse(BaseBuilder.isValidMaterial(Blocks.CHEST), "chests have block entities");
        helper.assertFalse(BaseBuilder.isValidMaterial(Blocks.TORCH), "torches have no collision");
        helper.assertFalse(BaseBuilder.isValidMaterial(Blocks.WHEAT), "crops are not placeable blocks");
        helper.assertFalse(BaseBuilder.isValidMaterial(Blocks.WATER), "fluids are not materials");
        helper.succeed();
    }

    private static BlueprintSettings with(BlueprintSettings.Shape shape, int w, int l, BlueprintSettings.Floor floor, int walls,
                                          boolean door, BlueprintSettings.Roof roof, boolean foundation) {
        return new BlueprintSettings(shape, w, l, floor, walls, door, roof, foundation, BlueprintSettings.Pick.FLOOR);
    }

    /** Octagon and hexagon footprints: counts and the corners they cut. */
    @GameTest(template = EMPTY)
    public static void octagonAndHexagonShapes(GameTestHelper helper) {
        BaseShape oct = BaseShape.of(BlueprintSettings.Shape.OCTAGON, 9, 9);
        BaseShape hex = BaseShape.of(BlueprintSettings.Shape.HEXAGON, 9, 7);
        BlueprintSettings o = new BlueprintSettings(BlueprintSettings.Shape.OCTAGON, 9, 9, BlueprintSettings.Floor.FILLED, 0);
        BlueprintSettings h = new BlueprintSettings(BlueprintSettings.Shape.HEXAGON, 9, 7, BlueprintSettings.Floor.FILLED, 0);
        helper.assertValueEqual(oct.blockCount(o), 69, "9x9 octagon cells");
        helper.assertValueEqual(hex.blockCount(h), 47, "9x7 hexagon cells");
        helper.assertFalse(oct.filled(0, 0), "octagon corner is cut");
        helper.assertTrue(oct.filled(4, 0) && oct.filled(0, 4), "octagon sides are full");
        helper.assertTrue(hex.filled(0, 3), "hexagon point on the left");
        helper.assertFalse(hex.filled(0, 0), "hexagon slanted side cuts the corner");
        helper.assertTrue(hex.filled(4, 0), "hexagon flat top");
        helper.succeed();
    }

    /** Floor in one block, walls in another, each taken from the inventory. */
    @GameTest(template = EMPTY)
    public static void separateWallMaterial(GameTestHelper helper) {
        Player player = survivor(helper, 25);
        player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 16));
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        ItemStack stack = blueprint(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 5, 5, BlueprintSettings.Floor.FILLED, 1));
        stack.set(ModComponents.WALL_MATERIAL.get(), Blocks.OAK_PLANKS);
        int placed = BaseBuilder.build(player, stack, origin);
        helper.assertValueEqual(placed, 25 + 16, "blocks placed");
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 1, 4));
        helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(2, 2, 2));
        helper.assertValueEqual(BaseBuilder.countItem(player, Items.COBBLESTONE), 0, "cobblestone left");
        helper.assertValueEqual(BaseBuilder.countItem(player, Items.OAK_PLANKS), 0, "planks left");
        helper.succeed();
    }

    /** The door is a 1x2 opening in the middle of the wall facing the player. */
    @GameTest(template = EMPTY)
    public static void doorFacesThePlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos south = helper.absolutePos(new BlockPos(4, 1, 12));
        player.moveTo(south.getX() + 0.5, south.getY(), south.getZ() + 0.5);
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        BaseBuilder.build(player, blueprint(with(BlueprintSettings.Shape.SQUARE, 5, 5, BlueprintSettings.Floor.FILLED, 3,
                true, BlueprintSettings.Roof.NONE, false)), origin);
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(4, 2, 6));
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(4, 3, 6));
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 4, 6)); // lintel
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 2, 2)); // north wall stays closed
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(3, 2, 6));
        helper.succeed();
    }

    /** A flat roof covers the whole footprint one layer above the walls. */
    @GameTest(template = EMPTY)
    public static void flatRoofCoversTheBase(GameTestHelper helper) {
        BlueprintSettings s = with(BlueprintSettings.Shape.SQUARE, 5, 5, BlueprintSettings.Floor.FILLED, 2,
                false, BlueprintSettings.Roof.FLAT, false);
        helper.assertValueEqual(BaseShape.of(s).blockCount(s), 25 + 16 * 2 + 25, "blocks for floor, walls and roof");
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BaseBuilder.build(player, blueprint(s), helper.absolutePos(new BlockPos(4, 1, 4)));
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 4, 4));
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(4, 3, 4)); // the room inside stays empty
        helper.succeed();
    }

    /** A dome is a closed half-ellipsoid shell: every footprint cell has roof above it, and it is hollow. */
    @GameTest(template = EMPTY)
    public static void domeIsAClosedShell(GameTestHelper helper) {
        BlueprintSettings s = with(BlueprintSettings.Shape.CIRCLE, 7, 7, BlueprintSettings.Floor.NONE, 1,
                false, BlueprintSettings.Roof.DOME, false);
        BaseShape shape = BaseShape.of(s);
        java.util.Set<Long> covered = new java.util.HashSet<>();
        for (BaseShape.Placement p : shape.placements(s, BlockPos.ZERO, null)) {
            if (p.part() == BaseShape.Part.ROOF) covered.add(BlockPos.asLong(p.pos().getX(), 0, p.pos().getZ()));
        }
        int x0 = BaseShape.startX(s, BlockPos.ZERO), z0 = BaseShape.startZ(s, BlockPos.ZERO);
        for (int z = 0; z < 7; z++) {
            for (int x = 0; x < 7; x++) {
                if (shape.filled(x, z)) {
                    helper.assertTrue(covered.contains(BlockPos.asLong(x0 + x, 0, z0 + z)), "no roof above cell " + x + "," + z);
                }
            }
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        // the test box is 5 blocks tall (barriers above), so the base starts at its bottom layer
        int placed = BaseBuilder.build(player, blueprint(s), helper.absolutePos(new BlockPos(4, 0, 4)));
        helper.assertTrue(placed > 0, "dome not built");
        // walls at y = 0, dome layers from y = 1; a 7-wide dome is 4 layers tall
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 4, 4));
        helper.assertBlockNotPresent(Blocks.COBBLESTONE, new BlockPos(4, 2, 4)); // hollow
        helper.succeed();
    }

    /** On a ledge the foundation fills under the floor down to the ground. */
    @GameTest(template = EMPTY)
    public static void foundationFillsTheGap(GameTestHelper helper) {
        Player player = survivor(helper, 64);
        BlueprintSettings s = with(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0,
                false, BlueprintSettings.Roof.NONE, true);
        int placed = BaseBuilder.build(player, blueprint(s), helper.absolutePos(new BlockPos(4, 3, 4)));
        // the test box floor is y = 0 (open below it is the test platform), so three layers go under the floor
        helper.assertValueEqual(placed, 9 + 9 * 3, "floor plus three foundation layers");
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(3, 0, 3));
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(5, 2, 5));
        helper.succeed();
    }

    /** With an empty inventory the blocks come from a chest next to the player. */
    @GameTest(template = EMPTY)
    public static void takesBlocksFromANearbyChest(GameTestHelper helper) {
        Player player = survivor(helper, 0);
        BlockPos chestPos = new BlockPos(1, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(chestPos), null);
        chest.insertItem(0, new ItemStack(Items.COBBLESTONE, 20), false);
        BlockPos near = helper.absolutePos(new BlockPos(1, 1, 3));
        player.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        int placed = BaseBuilder.build(player, blueprint(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3,
                BlueprintSettings.Floor.FILLED, 0)), helper.absolutePos(new BlockPos(5, 1, 5)));
        helper.assertValueEqual(placed, 9, "blocks placed from the chest");
        helper.assertValueEqual(chest.getStackInSlot(0).getCount(), 11, "cobblestone left in the chest");
        helper.succeed();
    }

    /** The undo history survives a save and reload of the world data. */
    @GameTest(template = EMPTY)
    public static void undoHistoryIsSaved(GameTestHelper helper) {
        Player player = survivor(helper, 64);
        BaseBuilder.build(player, blueprint(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3,
                BlueprintSettings.Floor.FILLED, 1)), helper.absolutePos(new BlockPos(4, 1, 4)));
        var registries = helper.getLevel().registryAccess();
        var tag = UndoHistory.get(helper.getLevel().getServer()).save(new net.minecraft.nbt.CompoundTag(), registries);
        UndoHistory reloaded = UndoHistory.load(tag, registries);
        UndoHistory.LastBuild last = reloaded.get(player.getUUID());
        helper.assertTrue(last != null, "no history after reload");
        helper.assertValueEqual(last.blocks().size(), 9 + 8, "blocks remembered");
        helper.assertValueEqual(last.blocks().get(0).block(), Blocks.COBBLESTONE, "block remembered");
        helper.succeed();
    }

    /** A blueprint builds 8 bases and then breaks; creative does not wear it. */
    @GameTest(template = EMPTY)
    public static void blueprintWearsOutAfterEightBases(GameTestHelper helper) {
        Player player = survivor(helper, 64);
        ItemStack stack = blueprint(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0));
        helper.assertValueEqual(stack.getMaxDamage(), 8, "uses");
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        for (int i = 1; i <= 8; i++) {
            helper.assertValueEqual(BaseBuilder.build(player, stack, origin), 9, "blocks placed on build " + i);
            if (i < 8) {
                helper.assertValueEqual(stack.getDamageValue(), i, "uses spent after build " + i);
                BaseBuilder.undo(player);
            }
        }
        helper.assertTrue(stack.isEmpty(), "blueprint did not break after 8 bases");

        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack free = blueprint(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0));
        BaseBuilder.build(creative, free, helper.absolutePos(new BlockPos(4, 2, 4)));
        helper.assertValueEqual(free.getDamageValue(), 0, "creative wore the blueprint");
        helper.succeed();
    }
}
