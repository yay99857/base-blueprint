package com.baseblueprint.gametest;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BaseBuilder;
import com.baseblueprint.BaseShape;
import com.baseblueprint.BlueprintConfig;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
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
}
