package com.baseblueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
    /** Kept as one tag in the layout the old SavedData used. */
    private static final com.mojang.serialization.Codec<UndoHistory> CODEC =
            CompoundTag.CODEC.xmap(UndoHistory::load, h -> h.save(new CompoundTag()));
    private static final net.minecraft.world.level.saveddata.SavedDataType<UndoHistory> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    Identifier.fromNamespaceAndPath(BaseBlueprint.MOD_ID, "undo"), UndoHistory::new, CODEC);

    /** One placed block. */
    public record Placed(BlockPos pos, Block block) {
    }

    public record LastBuild(ResourceKey<Level> dimension, List<Placed> blocks) {
    }

    private final Map<UUID, LastBuild> last = new HashMap<>();

    public static UndoHistory get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
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

    public CompoundTag save(CompoundTag tag) {
        ListTag players = new ListTag();
        last.forEach((uuid, build) -> {
            CompoundTag entry = new CompoundTag();
            entry.store("Player", net.minecraft.core.UUIDUtil.CODEC, uuid);
            entry.putString("Dimension", build.dimension().identifier().toString());
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
        for (Tag t : tag.getListOrEmpty("Players")) {
            CompoundTag entry = (CompoundTag) t;
            Identifier dim = Identifier.tryParse(entry.getStringOr("Dimension", ""));
            UUID player = entry.read("Player", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
            if (dim == null || player == null) continue;
            List<Block> palette = new ArrayList<>();
            for (Tag name : entry.getListOrEmpty("Palette")) {
                Identifier id = Identifier.tryParse(name.asString().orElse(""));
                palette.add(id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null));
            }
            long[] positions = entry.getLongArray("Positions").orElse(new long[0]);
            int[] indices = entry.getIntArray("Blocks").orElse(new int[0]);
            List<Placed> blocks = new ArrayList<>(positions.length);
            for (int i = 0; i < positions.length && i < indices.length; i++) {
                Block block = indices[i] < palette.size() ? palette.get(indices[i]) : null;
                if (block != null) blocks.add(new Placed(BlockPos.of(positions[i]), block));
            }
            history.last.put(player,
                    new LastBuild(ResourceKey.create(Registries.DIMENSION, dim), blocks));
        }
        return history;
    }
}
