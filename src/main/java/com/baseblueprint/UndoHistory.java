package com.baseblueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's last base, kept with the world (data/baseblueprint_undo.dat) so undo still works after leaving
 * the world or restarting the server.
 */
public final class UndoHistory extends SavedData {
    private static final String NAME = "baseblueprint_undo";

    /** One placed block. */
    public record Placed(BlockPos pos, Block block) {
    }

    public record LastBuild(ResourceKey<Level> dimension, List<Placed> blocks) {
    }

    private final Map<UUID, LastBuild> last = new HashMap<>();

    public static UndoHistory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(UndoHistory::load, UndoHistory::new, NAME);
    }

    @Nullable
    public LastBuild get(UUID player) {
        return last.get(player);
    }

    public void put(UUID player, LastBuild build) {
        last.put(player, build);
        setDirty();
    }

    public void remove(UUID player) {
        if (last.remove(player) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        last.forEach((uuid, build) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", uuid);
            entry.putString("Dimension", build.dimension().location().toString());
            // A palette keeps the file small: most bases use one or two blocks.
            Map<Block, Integer> palette = new LinkedHashMap<>();
            long[] positions = new long[build.blocks().size()];
            int[] indices = new int[build.blocks().size()];
            for (int i = 0; i < positions.length; i++) {
                Placed placed = build.blocks().get(i);
                positions[i] = placed.pos().asLong();
                indices[i] = palette.computeIfAbsent(placed.block(), b -> palette.size());
            }
            ListTag names = new ListTag();
            palette.keySet().forEach(b -> names.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(b).toString())));
            entry.put("Palette", names);
            entry.putLongArray("Positions", positions);
            entry.putIntArray("Blocks", indices);
            players.add(entry);
        });
        tag.put("Players", players);
        return tag;
    }

    public static UndoHistory load(CompoundTag tag) {
        UndoHistory history = new UndoHistory();
        for (Tag t : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            ResourceLocation dim = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dim == null || !entry.hasUUID("Player")) continue;
            List<Block> palette = new ArrayList<>();
            for (Tag name : entry.getList("Palette", Tag.TAG_STRING)) {
                ResourceLocation id = ResourceLocation.tryParse(name.getAsString());
                palette.add(id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null));
            }
            long[] positions = entry.getLongArray("Positions");
            int[] indices = entry.getIntArray("Blocks");
            List<Placed> blocks = new ArrayList<>(positions.length);
            for (int i = 0; i < positions.length && i < indices.length; i++) {
                Block block = indices[i] < palette.size() ? palette.get(indices[i]) : null;
                if (block != null) blocks.add(new Placed(BlockPos.of(positions[i]), block));
            }
            history.last.put(entry.getUUID("Player"),
                    new LastBuild(ResourceKey.create(Registries.DIMENSION, dim), blocks));
        }
        return history;
    }
}
