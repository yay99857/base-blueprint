package com.baseblueprint;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server side: works out what a blueprint would place, takes the blocks from the player (inventory, containers
 * carried in it such as backpacks, and containers nearby) and builds.
 */
public final class BaseBuilder {
    /** One block to place. */
    public record Placement(BlockPos pos, Block block, BaseShape.Part part) {
    }

    /** Positions that will get a block, and the ones where something solid is in the way. */
    public record Plan(List<Placement> toPlace, List<BlockPos> blockedPositions) {
        public int blocked() {
            return blockedPositions.size();
        }

        /** How many of each block the plan needs. */
        public Map<Block, Integer> needs() {
            Map<Block, Integer> needs = new LinkedHashMap<>();
            for (Placement p : toPlace) needs.merge(p.block(), 1, Integer::sum);
            return needs;
        }
    }

    /** The two materials of a base: floor (and foundation), walls (and roof). */
    public record Materials(@Nullable Block floor, @Nullable Block walls) {
        @Nullable
        public Block of(BaseShape.Part part) {
            return part.usesWallMaterial() ? walls : floor;
        }
    }

    private BaseBuilder() {
    }

    /** Where a click puts the base: in the clicked block when it is replaceable (grass, snow), else beside it. */
    public static BlockPos target(Level level, BlockPos clicked, Direction face) {
        return level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
    }

    /** Blocks the blueprint can build with: plain, solid blocks you can hold (no chests, plants or torches). */
    public static boolean isValidMaterial(@Nullable Block block) {
        if (block == null || block == Blocks.AIR || block instanceof EntityBlock) return false;
        if (!(block.asItem() instanceof BlockItem item) || item.getBlock() != block) return false;
        BlockState state = block.defaultBlockState();
        return !state.canBeReplaced()
                && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    /**
     * The materials to build with. A block in the off hand is used for everything; otherwise the floor block picked
     * with sneak + right-click, and the wall block picked the same way (walls fall back to the floor block).
     */
    public static Materials materials(Player player, ItemStack blueprint) {
        if (player.getOffhandItem().getItem() instanceof BlockItem item && isValidMaterial(item.getBlock())) {
            return new Materials(item.getBlock(), item.getBlock());
        }
        Block floor = blueprint.get(ModComponents.MATERIAL.get());
        Block walls = blueprint.get(ModComponents.WALL_MATERIAL.get());
        floor = isValidMaterial(floor) ? floor : null;
        walls = isValidMaterial(walls) ? walls : floor;
        return new Materials(floor, walls);
    }

    /** The floor material (for callers that only need one block). */
    @Nullable
    public static Block material(Player player, ItemStack blueprint) {
        return materials(player, blueprint).floor();
    }

    public static BlueprintSettings settings(ItemStack blueprint) {
        return blueprint.getOrDefault(ModComponents.SETTINGS.get(), BlueprintSettings.DEFAULT);
    }

    /** The side the door goes on: the one facing the player. */
    public static Direction doorSide(BlueprintSettings settings, BlockPos origin, @Nullable Player player) {
        return player == null ? Direction.SOUTH : BaseShape.sideFacing(settings, origin, player.getX(), player.getZ());
    }

    public static Plan plan(Level level, @Nullable Player player, BlueprintSettings settings, BlockPos origin, Block block) {
        return plan(level, player, settings, origin, new Materials(block, block));
    }

    /** What would be placed (parts without a material are skipped) and what is in the way. */
    public static Plan plan(Level level, @Nullable Player player, BlueprintSettings settings, BlockPos origin, Materials materials) {
        return plan(level, player, settings, origin, materials, doorSide(settings, origin, player));
    }

    /**
     * The same with the door on a given side: for a helper that keeps building after the player walked away, and for
     * showing on the client what it still has to place.
     */
    public static Plan plan(Level level, @Nullable Player player, BlueprintSettings settings, BlockPos origin, Materials materials,
                            Direction door) {
        List<Placement> wanted = new ArrayList<>();
        List<BaseShape.Placement> shape = BaseShape.of(settings).placements(settings, origin, door);
        // Foundation first, bottom-up, so every column is supported before the floor goes on top.
        if (settings.foundation() && settings.floor() != BlueprintSettings.Floor.NONE && materials.floor() != null) {
            int max = BlueprintConfig.maxFoundation();
            for (BaseShape.Placement p : shape) {
                if (p.part() != BaseShape.Part.FLOOR) continue;
                List<BlockPos> column = new ArrayList<>();
                BlockPos below = p.pos().below();
                while (column.size() < max && level.isInWorldBounds(below) && level.getBlockState(below).canBeReplaced()) {
                    column.add(below);
                    below = below.below();
                }
                for (int i = column.size() - 1; i >= 0; i--) {
                    wanted.add(new Placement(column.get(i), materials.floor(), BaseShape.Part.FOUNDATION));
                }
            }
        }
        for (BaseShape.Placement p : shape) {
            Block block = materials.of(p.part());
            if (block != null) wanted.add(new Placement(p.pos(), block, p.part()));
        }

        List<Placement> toPlace = new ArrayList<>();
        List<BlockPos> blocked = new ArrayList<>();
        for (Placement p : wanted) {
            BlockPos pos = p.pos();
            if (!level.isInWorldBounds(pos) || (player != null && !level.mayInteract(player, pos))) {
                blocked.add(pos);
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (current.is(p.block())) continue; // already built
            if (!current.canBeReplaced() || !p.block().defaultBlockState().canSurvive(level, pos)) {
                blocked.add(pos);
                continue;
            }
            toPlace.add(p);
        }
        return new Plan(toPlace, blocked);
    }

    // ---- where the blocks come from ----

    /** Item handlers of containers carried in the inventory (backpacks and the like). */
    private static List<ResourceHandler<ItemResource>> carriedContainers(Player player, Item item) {
        List<ResourceHandler<ItemResource>> out = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || stack.is(item)) continue;
            // through the player's slot, so what is taken out is written back into the carried item
            ResourceHandler<ItemResource> handler = stack.getCapability(Capabilities.Item.ITEM, ItemAccess.forPlayerSlot(player, i));
            if (handler != null) out.add(handler);
        }
        return out;
    }

    /** Item handlers of containers within {@link BlueprintConfig#containerRadius()} blocks the player may open. */
    private static List<ResourceHandler<ItemResource>> nearbyContainers(Player player) {
        int radius = BlueprintConfig.containerRadius();
        List<ResourceHandler<ItemResource>> out = new ArrayList<>();
        if (radius <= 0 || !(player.level() instanceof ServerLevel level)) return out;
        BlockPos center = player.blockPosition();
        int minCx = (center.getX() - radius) >> 4, maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4, maxCz = (center.getZ() + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockPos pos : List.copyOf(chunk.getBlockEntities().keySet())) {
                    if (!pos.closerThan(center, radius + 0.5) || !level.mayInteract(player, pos)) continue;
                    ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, null);
                    if (handler != null) out.add(handler);
                }
            }
        }
        return out;
    }

    private static int count(ResourceHandler<ItemResource> handler, Item item) {
        int total = 0;
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getResource(i).is(item)) total += handler.getAmountAsInt(i);
        }
        return total;
    }

    /** Blocks of {@code item} in the inventory alone (all the client can see, for the preview). */
    public static int countItem(Player player, Item item) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /** Blocks of {@code item} available to build with: inventory, carried containers and nearby containers. */
    public static int countAvailable(Player player, Item item) {
        int total = countItem(player, item);
        for (ResourceHandler<ItemResource> handler : carriedContainers(player, item)) total += count(handler, item);
        for (ResourceHandler<ItemResource> handler : nearbyContainers(player)) total += count(handler, item);
        return total;
    }

    /** Takes {@code amount} of {@code item}: inventory first, then carried containers, then nearby ones. */
    private static void consume(Player player, Item item, int amount) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(amount, stack.getCount());
                stack.shrink(take);
                amount -= take;
            }
        }
        inv.setChanged();
        if (amount <= 0) return;
        List<ResourceHandler<ItemResource>> others = new ArrayList<>(carriedContainers(player, item));
        others.addAll(nearbyContainers(player));
        for (ResourceHandler<ItemResource> handler : others) {
            for (int i = 0; i < handler.size() && amount > 0; i++) {
                ItemResource resource = handler.getResource(i);
                if (!resource.is(item)) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    amount -= handler.extract(i, resource, amount, tx);
                    tx.commit();
                }
            }
            if (amount <= 0) break;
        }
    }

    // ---- building ----

    /** Builds the base. Returns the number of blocks placed (0 when nothing was built). */
    public static int build(Player player, ItemStack blueprint, BlockPos origin) {
        Level level = player.level();
        if (!player.mayBuild()) return 0;
        BlueprintSettings settings = settings(blueprint).clamped();
        Materials materials = materials(player, blueprint);
        if (materials.floor() == null) {
            player.sendOverlayMessage(Component.translatable("message.baseblueprint.no_material")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        Plan plan = plan(level, player, settings, origin, materials);
        // A base with holes is not a base: refuse until the spots are cleared (unless the server allows it).
        if (plan.blocked() > 0 && !BlueprintConfig.allowPartial()) {
            player.sendOverlayMessage(Component.translatable("message.baseblueprint.blocked", plan.blocked())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (plan.toPlace().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.baseblueprint.nothing_to_place")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        boolean free = player.isCreative();
        if (!free) {
            for (Map.Entry<Block, Integer> need : plan.needs().entrySet()) {
                int have = countAvailable(player, need.getKey().asItem());
                if (have < need.getValue()) {
                    player.sendOverlayMessage(Component.translatable("message.baseblueprint.not_enough",
                            need.getValue() - have, need.getKey().getName(), have, need.getValue())
                            .withStyle(ChatFormatting.RED));
                    return 0;
                }
            }
        }

        List<UndoHistory.Placed> placed = new ArrayList<>(plan.toPlace().size());
        Map<Block, Integer> used = new LinkedHashMap<>();
        for (Placement p : plan.toPlace()) {
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, p.pos());
            if (!level.setBlock(p.pos(), p.block().defaultBlockState(), Block.UPDATE_ALL)) continue;
            // Lets claim and protection mods refuse single blocks; refused ones are put back and not charged.
            if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
                snapshot.restore(Block.UPDATE_ALL);
                continue;
            }
            placed.add(new UndoHistory.Placed(p.pos(), p.block()));
            used.merge(p.block(), 1, Integer::sum);
        }
        if (placed.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.baseblueprint.nothing_to_place")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!free) used.forEach((block, n) -> consume(player, block.asItem(), n));
        List<BlockPos> positions = new ArrayList<>(placed.size());
        for (UndoHistory.Placed p : placed) positions.add(p.pos());
        liftEntities(level, positions);

        SoundType sound = placed.get(0).block().defaultBlockState().getSoundType(level, origin, player);
        level.playSound(null, origin, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        if (level.getServer() != null) {
            UndoHistory.get(level.getServer()).put(player.getUUID(), new UndoHistory.LastBuild(level.dimension(), placed));
        }

        Block main = used.keySet().iterator().next();
        Component msg = plan.blocked() > 0
                ? Component.translatable("message.baseblueprint.built_blocked", placed.size(), main.getName(), plan.blocked())
                : Component.translatable("message.baseblueprint.built", placed.size(), main.getName());
        player.sendOverlayMessage(msg.copy().withStyle(ChatFormatting.GREEN));
        wear(player, blueprint);
        return placed.size();
    }

    /** Each base built uses one of the blueprint's uses ({@link BaseBlueprint#USES}); free in creative. */
    static void wear(Player player, ItemStack blueprint) {
        if (player.isCreative() || !blueprint.isDamageableItem()
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel server)) return;
        if (player.getMainHandItem() == blueprint) {
            blueprint.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        } else if (player.getOffhandItem() == blueprint) {
            blueprint.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        } else {
            blueprint.hurtAndBreak(1, server, player, item -> { });
        }
    }

    /** Anything standing where a block appeared is moved on top of it instead of suffocating. */
    private static void liftEntities(Level level, List<BlockPos> placed) {
        Set<BlockPos> set = new HashSet<>(placed);
        AABB area = null;
        for (BlockPos pos : placed) {
            AABB box = new AABB(pos);
            area = area == null ? box : area.minmax(box);
        }
        for (Entity entity : level.getEntities((Entity) null, area.inflate(1))) {
            AABB bb = entity.getBoundingBox();
            int top = Integer.MIN_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(
                    BlockPos.containing(bb.minX, bb.minY, bb.minZ), BlockPos.containing(bb.maxX - 1e-4, bb.maxY - 1e-4, bb.maxZ - 1e-4))) {
                if (set.contains(pos)) {
                    // climb the whole column (a wall under the feet means standing on top of it)
                    int y = pos.getY();
                    while (set.contains(new BlockPos(pos.getX(), y + 1, pos.getZ()))) y++;
                    top = Math.max(top, y);
                }
            }
            if (top != Integer.MIN_VALUE) {
                entity.teleportTo(entity.getX(), top + 1, entity.getZ());
            }
        }
    }

    /** Removes the player's last base, if its blocks are still there, and returns them. */
    public static void undo(Player player) {
        Level level = player.level();
        if (!BlueprintConfig.allowUndo() || level.getServer() == null) return;
        UndoHistory history = UndoHistory.get(level.getServer());
        UndoHistory.LastBuild last = history.get(player.getUUID());
        if (last == null || last.blocks().isEmpty() || last.dimension() != level.dimension()) {
            player.sendOverlayMessage(Component.translatable("message.baseblueprint.nothing_to_undo")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        history.remove(player.getUUID());
        Map<Block, Integer> removed = new LinkedHashMap<>();
        // Roof and walls first, foundation last, so nothing is left floating while the base goes.
        for (int i = last.blocks().size() - 1; i >= 0; i--) {
            UndoHistory.Placed p = last.blocks().get(i);
            if (level.getBlockState(p.pos()).is(p.block()) && level.mayInteract(player, p.pos())) {
                level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                removed.merge(p.block(), 1, Integer::sum);
            }
        }
        if (!player.isCreative()) {
            removed.forEach((block, n) -> {
                Item item = block.asItem();
                int left = n;
                while (left > 0) {
                    int stack = Math.min(left, item.getDefaultMaxStackSize());
                    player.getInventory().placeItemBackInInventory(new ItemStack(item, stack), net.minecraft.util.Prediction.SERVER_ONLY);
                    left -= stack;
                }
            });
        }
        int total = removed.values().stream().mapToInt(Integer::intValue).sum();
        Block main = removed.isEmpty() ? last.blocks().get(0).block() : removed.keySet().iterator().next();
        player.sendOverlayMessage(Component.translatable("message.baseblueprint.undone", total, main.getName())
                .withStyle(ChatFormatting.AQUA));
    }
}
