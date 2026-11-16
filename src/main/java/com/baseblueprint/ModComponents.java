package com.baseblueprint;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BaseBlueprint.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintSettings>> SETTINGS =
            COMPONENTS.registerComponentType("settings", b -> b
                    .persistent(BlueprintSettings.CODEC)
                    .networkSynchronized(BlueprintSettings.STREAM_CODEC));

    /** The block picked with sneak + right-click. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Block>> MATERIAL =
            COMPONENTS.registerComponentType("material", b -> b
                    .persistent(BuiltInRegistries.BLOCK.byNameCodec())
                    .networkSynchronized(ByteBufCodecs.registry(Registries.BLOCK)));

    private ModComponents() {
    }
}
