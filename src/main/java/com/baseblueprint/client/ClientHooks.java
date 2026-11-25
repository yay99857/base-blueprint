package com.baseblueprint.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Client-only entry points called from common code (only ever reached on the client). */
public final class ClientHooks {
    public static void openScreen(InteractionHand hand) {
        Minecraft.getInstance().gui.setScreen(new BlueprintScreen(hand));
    }

    private ClientHooks() {
    }
}
