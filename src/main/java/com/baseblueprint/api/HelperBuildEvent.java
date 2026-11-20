package com.baseblueprint.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;

/**
 * Fired on the NeoForge event bus when a player presses "Build with a helper" in the blueprint menu. Another mod
 * (Bubie) can hand the blueprint to one of its builders: it calls {@link #accept} with the message to show the player.
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
