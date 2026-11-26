package com.baseblueprint;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

/** NBT storage on the blueprint stack (Forge 1.20.1 equivalent of data components). */
public final class ModComponents {
    private static final String TAG_SETTINGS = "Settings";
    private static final String TAG_MATERIAL = "Material";
    private static final String TAG_WALL_MATERIAL = "WallMaterial";

    private ModComponents() {
    }

    public static BlueprintSettings getSettings(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SETTINGS)) return BlueprintSettings.DEFAULT;
        return BlueprintSettings.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_SETTINGS))
                .result().orElse(BlueprintSettings.DEFAULT);
    }

    public static void setSettings(ItemStack stack, BlueprintSettings settings) {
        BlueprintSettings.CODEC.encodeStart(NbtOps.INSTANCE, settings).result()
                .ifPresent(encoded -> stack.getOrCreateTag().put(TAG_SETTINGS, encoded));
    }

    @Nullable
    public static Block getMaterial(ItemStack stack) {
        return getBlock(stack, TAG_MATERIAL);
    }

    public static void setMaterial(ItemStack stack, @Nullable Block block) {
        setBlock(stack, TAG_MATERIAL, block);
    }

    @Nullable
    public static Block getWallMaterial(ItemStack stack) {
        return getBlock(stack, TAG_WALL_MATERIAL);
    }

    public static void setWallMaterial(ItemStack stack, @Nullable Block block) {
        setBlock(stack, TAG_WALL_MATERIAL, block);
    }

    @Nullable
    private static Block getBlock(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
        return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
    }

    private static void setBlock(ItemStack stack, String key, @Nullable Block block) {
        if (block == null) {
            CompoundTag tag = stack.getTag();
            if (tag != null) tag.remove(key);
            return;
        }
        stack.getOrCreateTag().putString(key, BuiltInRegistries.BLOCK.getKey(block).toString());
    }
}
