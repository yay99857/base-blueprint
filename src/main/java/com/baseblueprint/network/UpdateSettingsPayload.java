package com.baseblueprint.network;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the settings chosen in the blueprint menu, for the blueprint in the given hand. */
public record UpdateSettingsPayload(boolean mainHand, BlueprintSettings settings) {
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(mainHand);
        settings.write(buf);
    }

    public static UpdateSettingsPayload read(FriendlyByteBuf buf) {
        return new UpdateSettingsPayload(buf.readBoolean(), BlueprintSettings.read(buf));
    }

    public static void handle(UpdateSettingsPayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            InteractionHand hand = payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(BaseBlueprint.BLUEPRINT.get())) {
                ModComponents.setSettings(stack, payload.settings().clamped());
            }
        });
        ctx.setPacketHandled(true);
    }
}
