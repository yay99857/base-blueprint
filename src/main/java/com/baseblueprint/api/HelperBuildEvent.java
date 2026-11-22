package com.baseblueprint.api;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import javax.annotation.Nullable;

/**
 * Fired on the NeoForge event bus when a player right-clicks a block with a blueprint set to "Built by: Bubie" in its
 * menu. Another mod (Bubie) can hand the blueprint to one of its builders: it calls {@link #accept} with the message
 * to show the player.
 * When nobody accepts, the player is told no helper is nearby. The blueprint stack is the player's own: copy it,
 * don't change it.
 */
public class HelperBuildEvent extends Event {
    private final ServerPlayer player;
    private final ItemStack blueprint;
    private final BlockPos origin;
    @Nullable
    private Component message;

    public HelperBuildEvent(ServerPlayer player, ItemStack blueprint, BlockPos origin) {
        this.player = player;
        this.blueprint = blueprint;
        this.origin = origin;
    }

    /** Whether a mod that provides helpers (Bubie) is installed; without one a blueprint always builds at once. */
    public static boolean available() {
        return ModList.get().isLoaded("bubie");
    }

    /** Offers the job to the helpers and tells the player who took it, or that none is near. True if one took it. */
    public static boolean ask(ServerPlayer player, ItemStack blueprint, BlockPos origin) {
        HelperBuildEvent event = NeoForge.EVENT_BUS.post(new HelperBuildEvent(player, blueprint, origin));
        player.displayClientMessage(event.isAccepted() ? event.getMessage()
                : Component.translatable("message.baseblueprint.no_helper").withStyle(ChatFormatting.YELLOW), true);
        return event.isAccepted();
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ItemStack getBlueprint() {
        return blueprint;
    }

    /** Where the base goes, as if the player had right-clicked there with the blueprint. */
    public BlockPos getOrigin() {
        return origin;
    }

    public boolean isAccepted() {
        return message != null;
    }

    /** A helper took the job; the message is shown to the player above the hotbar. */
    public void accept(Component message) {
        this.message = message;
    }

    @Nullable
    public Component getMessage() {
        return message;
    }
}
