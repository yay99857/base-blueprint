package com.baseblueprint.gametest;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BaseBuilder;
import com.baseblueprint.BaseShape;
import com.baseblueprint.BlueprintConfig;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import com.baseblueprint.UndoHistory;
import com.baseblueprint.api.HelperBuildEvent;
import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Run with ./gradlew runGameTestServer (exits non-zero when a test fails). */
@GameTestHolder(BaseBlueprint.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintGameTests {
    private static final String EMPTY = "empty";

    private static <T> void assertEq(GameTestHelper helper, T actual, T expected, String message) {
        helper.assertTrue(Objects.equals(actual, expected), message + ": expected " + expected + ", got " + actual);
    }

    private static ItemStack blueprint(BlueprintSettings settings) {
        ItemStack stack = new ItemStack(BaseBlueprint.BLUEPRINT.get());
        ModComponents.setSettings(stack, settings);
        ModComponents.setMaterial(stack, Blocks.COBBLESTONE);
        return stack;
    }

    private static Player survivor(GameTestHelper helper, int cobble) {
        Player player = helper.makeMockSurvivalPlayer();
        if (cobble > 0) player.getInventory().add(new ItemStack(Items.COBBLESTONE, cobble));
        return player;
    }

    /** "Built by: Bubie" is kept on the blueprint, and a blueprint from before 1.4 is still built by the player. */
    @GameTest(template = EMPTY)
    public static void builtByAHelperIsSavedAndOldBlueprintsAreNot(GameTestHelper helper) {
        BlueprintSettings chosen = BlueprintSettings.DEFAULT.withHelper(true);
        JsonElement json = BlueprintSettings.CODEC.encodeStart(JsonOps.INSTANCE, chosen)
                .getOrThrow(false, msg -> { throw new RuntimeException(msg); });
        helper.assertTrue(BlueprintSettings.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(false, msg -> { throw new RuntimeException(msg); }).equals(chosen), "the choice is saved");
        json.getAsJsonObject().remove("helper");
        helper.assertTrue(!BlueprintSettings.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(false, msg -> { throw new RuntimeException(msg); }).helper(), "a 1.3 blueprint is built by the player");
        helper.assertTrue(chosen.withWidth(9).clamped().helper(), "changing the size keeps the choice");
        helper.succeed();
    }

    /** A click with "Built by: Bubie" offers the job to the helpers: one takes it, or the player is told none is near. */
    @GameTest(template = EMPTY)
    public static void aHelperTakesTheJobOrNobodyDoes(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(
                helper.getLevel(), new GameProfile(UUID.randomUUID(), "BlueprintTester"));
        ItemStack stack = blueprint(BlueprintSettings.DEFAULT.withHelper(true));
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.assertTrue(!HelperBuildEvent.ask(player, stack, origin), "nobody takes it");
        Consumer<HelperBuildEvent> taker = event -> {
            if (event.getOrigin().equals(origin)) event.accept(Component.literal("on it"));
        };
        MinecraftForge.EVENT_BUS.addListener(taker);
        try {
            helper.assertTrue(HelperBuildEvent.ask(player, stack, origin), "a helper takes it");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(taker);
        }
        helper.assertTrue(helper.getLevel().getBlockState(origin).isAir(), "nothing is placed at once");
        helper.succeed();
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
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.GRASS);
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
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.OAK_PLANKS));
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
        assertEq(helper, oct.blockCount(o), 69, "9x9 octagon cells");
        assertEq(helper, hex.blockCount(h), 47, "9x7 hexagon cells");
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
        ModComponents.setWallMaterial(stack, Blocks.OAK_PLANKS);
        int placed = BaseBuilder.build(player, stack, origin);
        assertEq(helper, placed, 25 + 16, "blocks placed");
        helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 1, 4));
        helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(2, 2, 2));
        assertEq(helper, BaseBuilder.countItem(player, Items.COBBLESTONE), 0, "cobblestone left");
        assertEq(helper, BaseBuilder.countItem(player, Items.OAK_PLANKS), 0, "planks left");
        helper.succeed();
    }

    /** The door is a 1x2 opening in the middle of the wall facing the player. */
    @GameTest(template = EMPTY)
    public static void doorFacesThePlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
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
        assertEq(helper, BaseShape.of(s).blockCount(s), 25 + 16 * 2 + 25, "blocks for floor, walls and roof");
        Player player = helper.makeMockPlayer();
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
        Set<Long> covered = new HashSet<>();
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
        Player player = helper.makeMockPlayer();
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
        assertEq(helper, placed, 9 + 9 * 3, "floor plus three foundation layers");
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
        var chest = helper.getBlockEntity(chestPos).getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(IllegalStateException::new);
        chest.insertItem(0, new ItemStack(Items.COBBLESTONE, 20), false);
        BlockPos near = helper.absolutePos(new BlockPos(1, 1, 3));
        player.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        int placed = BaseBuilder.build(player, blueprint(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3,
                BlueprintSettings.Floor.FILLED, 0)), helper.absolutePos(new BlockPos(5, 1, 5)));
        assertEq(helper, placed, 9, "blocks placed from the chest");
        assertEq(helper, chest.getStackInSlot(0).getCount(), 11, "cobblestone left in the chest");
        helper.succeed();
    }

    /** The undo history survives a save and reload of the world data. */
    @GameTest(template = EMPTY)
    public static void undoHistoryIsSaved(GameTestHelper helper) {
        Player player = survivor(helper, 64);
        BaseBuilder.build(player, blueprint(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3,
                BlueprintSettings.Floor.FILLED, 1)), helper.absolutePos(new BlockPos(4, 1, 4)));
        var tag = UndoHistory.get(helper.getLevel().getServer()).save(new CompoundTag());
        UndoHistory reloaded = UndoHistory.load(tag);
        UndoHistory.LastBuild last = reloaded.get(player.getUUID());
        helper.assertTrue(last != null, "no history after reload");
        assertEq(helper, last.blocks().size(), 9 + 8, "blocks remembered");
        assertEq(helper, last.blocks().get(0).block(), Blocks.COBBLESTONE, "block remembered");
        helper.succeed();
    }

    /** A blueprint builds 8 bases and then breaks; creative does not wear it. */
    @GameTest(template = EMPTY)
    public static void blueprintWearsOutAfterEightBases(GameTestHelper helper) {
        Player player = survivor(helper, 64);
        ItemStack stack = blueprint(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0));
        assertEq(helper, stack.getMaxDamage(), 8, "uses");
        BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
        for (int i = 1; i <= 8; i++) {
            assertEq(helper, BaseBuilder.build(player, stack, origin), 9, "blocks placed on build " + i);
            if (i < 8) {
                assertEq(helper, stack.getDamageValue(), i, "uses spent after build " + i);
                BaseBuilder.undo(player);
            }
        }
        helper.assertTrue(stack.isEmpty(), "blueprint did not break after 8 bases");

        Player creative = helper.makeMockPlayer();
        ItemStack free = blueprint(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0));
        BaseBuilder.build(creative, free, helper.absolutePos(new BlockPos(4, 2, 4)));
        assertEq(helper, free.getDamageValue(), 0, "creative wore the blueprint");
        helper.succeed();
    }

    /** Storeys: walls of the chosen height, a floor between storeys with a 1x1 hatch, the roof on top. */
    @GameTest(template = EMPTY)
    public static void storeysStackFloorsAndWalls(GameTestHelper helper) {
        BlueprintSettings s = new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 5, 5, BlueprintSettings.Floor.FILLED, 2,
                true, BlueprintSettings.Roof.FLAT, false, BlueprintSettings.Pick.FLOOR, 3);
        BaseShape shape = BaseShape.of(s);
        // ground floor 25, walls 3 x 16 x 2 (minus the 1x2 door), two upper floors of 25 minus the hatch, roof 25
        assertEq(helper, shape.blockCount(s), 25 + 3 * 32 - 2 + 2 * 24 + 25, "blocks for three storeys");
        Map<BlockPos, BaseShape.Part> at = new HashMap<>();
        for (BaseShape.Placement p : shape.placements(s, BlockPos.ZERO, Direction.SOUTH)) at.put(p.pos(), p.part());
        int x0 = BaseShape.startX(s, BlockPos.ZERO), z0 = BaseShape.startZ(s, BlockPos.ZERO);
        // upper floors at y = 3 and 6, roof at 9; the hatch is on the north side, opposite the door
        for (int y : new int[]{3, 6}) {
            assertEq(helper, at.get(new BlockPos(x0 + 2, y, z0 + 2)), BaseShape.Part.FLOOR, "floor of the storey at " + y);
            assertEq(helper, at.get(new BlockPos(x0, y, z0 + 2)), BaseShape.Part.WALL, "wall through the floor at " + y);
            helper.assertTrue(!at.containsKey(new BlockPos(x0 + 2, y, z0 + 1)), "no hatch at " + y);
        }
        helper.assertTrue(!at.containsKey(new BlockPos(x0 + 2, 4, z0 + 2)), "storey not hollow");
        assertEq(helper, at.get(new BlockPos(x0 + 2, 9, z0 + 2)), BaseShape.Part.ROOF, "roof on top");
        // the door only on the ground storey
        helper.assertTrue(!at.containsKey(new BlockPos(x0 + 2, 1, z0 + 4)), "no door on the ground storey");
        assertEq(helper, at.get(new BlockPos(x0 + 2, 4, z0 + 4)), BaseShape.Part.WALL, "door on an upper storey");
        helper.succeed();
    }
}
