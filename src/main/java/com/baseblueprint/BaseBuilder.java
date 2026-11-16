package com.baseblueprint;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server side: works out what a blueprint would place, takes the blocks from the inventory and builds. */
public final class BaseBuilder {
    /** Last base each player built, for undo. Memory only. */
    private static final Map<UUID, LastBuild> LAST = new ConcurrentHashMap<>();

    private record LastBuild(ResourceKey<Level> dimension, List<BlockPos> positions, Block block) {
    }

    /** Positions that will get a block, and the ones where something solid is in the way. */
    public record Plan(List<BlockPos> toPlace, List<BlockPos> blockedPositions) {
        public int blocked() {
            return blockedPositions.size();
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

    /** The block in the off hand wins; otherwise the block picked with sneak + right-click. */
    @Nullable
    public static Block material(Player player, ItemStack blueprint) {
        if (player.getOffhandItem().getItem() instanceof BlockItem item && isValidMaterial(item.getBlock())) {
            return item.getBlock();
        }
        Block picked = blueprint.get(ModComponents.MATERIAL.get());
        return isValidMaterial(picked) ? picked : null;
    }

    public static BlueprintSettings settings(ItemStack blueprint) {
        return blueprint.getOrDefault(ModComponents.SETTINGS.get(), BlueprintSettings.DEFAULT);
    }

    public static Plan plan(Level level, @Nullable Player player, BlueprintSettings settings, BlockPos origin, Block block) {
        BlockState state = block.defaultBlockState();
        List<BlockPos> toPlace = new ArrayList<>();
        List<BlockPos> blocked = new ArrayList<>();
        for (BlockPos pos : BaseShape.of(settings).positions(settings, origin)) {
            if (!level.isInWorldBounds(pos) || (player != null && !level.mayInteract(player, pos))) {
                blocked.add(pos);
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (current.is(block)) continue; // already built
            if (!current.canBeReplaced() || !state.canSurvive(level, pos)) {
                blocked.add(pos);
                continue;
            }
            toPlace.add(pos);
        }
        return new Plan(toPlace, blocked);
    }

    public static int countItem(Player player, Item item) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

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
    }

    /** Builds the base. Returns the number of blocks placed (0 when nothing was built). */
    public static int build(Player player, ItemStack blueprint, BlockPos origin) {
        Level level = player.level();
        if (!player.mayBuild()) return 0;
        Block block = material(player, blueprint);
        if (block == null) {
            player.displayClientMessage(Component.translatable("message.baseblueprint.no_material")
                    .withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }
        BlueprintSettings settings = settings(blueprint).clamped();
        Plan plan = plan(level, player, settings, origin, block);
        // A base with holes is not a base: refuse until the spots are cleared (unless the server allows it).
        if (plan.blocked() > 0 && !BlueprintConfig.allowPartial()) {
            player.displayClientMessage(Component.translatable("message.baseblueprint.blocked", plan.blocked())
                    .withStyle(ChatFormatting.RED), true);
            return 0;
        }
        int need = plan.toPlace().size();
        if (need == 0) {
            player.displayClientMessage(Component.translatable("message.baseblueprint.nothing_to_place")
                    .withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }

        boolean free = player.isCreative();
        Item item = block.asItem();
        if (!free) {
            int have = countItem(player, item);
            if (have < need) {
                player.displayClientMessage(Component.translatable("message.baseblueprint.not_enough",
                        need - have, block.getName(), have, need).withStyle(ChatFormatting.RED), true);
                return 0;
            }
        }

        BlockState state = block.defaultBlockState();
        List<BlockPos> placed = new ArrayList<>(need);
        for (BlockPos pos : plan.toPlace()) {
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            if (!level.setBlock(pos, state, Block.UPDATE_ALL)) continue;
            // Lets claim and protection mods refuse single blocks; refused ones are put back and not charged.
            if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
                snapshot.restore(Block.UPDATE_ALL);
                continue;
            }
            placed.add(pos);
        }
        if (placed.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.baseblueprint.nothing_to_place")
                    .withStyle(ChatFormatting.YELLOW), true);
            return 0;
        }
        if (!free) consume(player, item, placed.size());
        liftEntities(level, placed);

        SoundType sound = state.getSoundType(level, origin, player);
        level.playSound(null, origin, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        LAST.put(player.getUUID(), new LastBuild(level.dimension(), placed, block));

        Component msg = plan.blocked() > 0
                ? Component.translatable("message.baseblueprint.built_blocked", placed.size(), block.getName(), plan.blocked())
                : Component.translatable("message.baseblueprint.built", placed.size(), block.getName());
        player.displayClientMessage(msg.copy().withStyle(ChatFormatting.GREEN), true);
        return placed.size();
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
        if (!BlueprintConfig.allowUndo()) return;
        Level level = player.level();
        LastBuild last = LAST.get(player.getUUID());
        if (last == null || last.dimension() != level.dimension()) {
            player.displayClientMessage(Component.translatable("message.baseblueprint.nothing_to_undo")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        LAST.remove(player.getUUID());
        int removed = 0;
        // Walls first so nothing is left floating while the floor goes.
        for (int i = last.positions().size() - 1; i >= 0; i--) {
            BlockPos pos = last.positions().get(i);
            if (level.getBlockState(pos).is(last.block()) && level.mayInteract(player, pos)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                removed++;
            }
        }
        if (!player.isCreative()) {
            Item item = last.block().asItem();
            int left = removed;
            while (left > 0) {
                int n = Math.min(left, item.getDefaultMaxStackSize());
                player.getInventory().placeItemBackInInventory(new ItemStack(item, n));
                left -= n;
            }
        }
        player.displayClientMessage(Component.translatable("message.baseblueprint.undone", removed, last.block().getName())
                .withStyle(ChatFormatting.AQUA), true);
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
