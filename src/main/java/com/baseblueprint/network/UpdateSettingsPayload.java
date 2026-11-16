package com.baseblueprint.network;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: the settings chosen in the blueprint menu, for the blueprint in the given hand. */
public record UpdateSettingsPayload(boolean mainHand, BlueprintSettings settings) implements CustomPacketPayload {
    public static final Type<UpdateSettingsPayload> TYPE = new Type<>(BaseBlueprint.id("update_settings"));
    public static final StreamCodec<ByteBuf, UpdateSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, UpdateSettingsPayload::mainHand,
            BlueprintSettings.STREAM_CODEC, UpdateSettingsPayload::settings,
            UpdateSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            InteractionHand hand = payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = context.player().getItemInHand(hand);
            if (stack.is(BaseBlueprint.BLUEPRINT.get())) {
                stack.set(ModComponents.SETTINGS.get(), payload.settings().clamped());
            }
        });
    }
}
