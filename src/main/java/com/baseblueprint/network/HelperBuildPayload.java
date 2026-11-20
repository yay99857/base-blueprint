package com.baseblueprint.network;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BaseBuilder;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import com.baseblueprint.api.HelperBuildEvent;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Client → server: "Build with a helper" in the blueprint menu. Carries the menu's settings (saved first, as closing
 * the menu does) and the block the player was looking at; the base goes there, or where the player stands.
 */
public record HelperBuildPayload(boolean mainHand, BlueprintSettings settings, Optional<BlockPos> clicked, Direction face)
        implements CustomPacketPayload {
    public static final Type<HelperBuildPayload> TYPE = new Type<>(BaseBlueprint.id("helper_build"));
    public static final StreamCodec<ByteBuf, HelperBuildPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HelperBuildPayload::mainHand,
            BlueprintSettings.STREAM_CODEC, HelperBuildPayload::settings,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), HelperBuildPayload::clicked,
            Direction.STREAM_CODEC, HelperBuildPayload::face,
            HelperBuildPayload::new);

    /** How far the looked-at block may be (the menu is opened right-clicking the air, so this is generous). */
    private static final double MAX_DISTANCE = 48;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelperBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            InteractionHand hand = payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(BaseBlueprint.BLUEPRINT.get())) return;
            stack.set(ModComponents.SETTINGS.get(), payload.settings().clamped());
            BlockPos origin = payload.clicked()
                    .filter(p -> p.distToCenterSqr(player.position()) <= MAX_DISTANCE * MAX_DISTANCE)
                    .map(p -> BaseBuilder.target(player.level(), p, payload.face()))
                    .orElse(player.blockPosition());
            HelperBuildEvent event = NeoForge.EVENT_BUS.post(new HelperBuildEvent(player, stack, origin));
            Component message = event.isAccepted() ? event.getMessage()
                    : Component.translatable("message.baseblueprint.no_helper").withStyle(ChatFormatting.YELLOW);
            player.displayClientMessage(message, true);
        });
    }
}
