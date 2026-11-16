package com.baseblueprint.client;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BaseBuilder;
import com.baseblueprint.BaseShape;
import com.baseblueprint.BlueprintConfig;
import com.baseblueprint.BlueprintSettings;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

/** In-world outline preview, the hint under the crosshair and the menu key. */
@EventBusSubscriber(modid = BaseBlueprint.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.baseblueprint.menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.baseblueprint");

    /** What the blueprint would build where the player is looking. Refreshed every few ticks. */
    private record Preview(BlueprintSettings settings, BaseShape shape, BlockPos origin, @Nullable Block block,
                           int need, int have, List<BlockPos> blocked, boolean creative) {
        boolean enough() {
            return creative || have >= need;
        }

        /** Would a click build? Needs the material, enough of it and (unless partial builds are on) a clear area. */
        boolean buildable() {
            return block != null && enough() && (blocked.isEmpty() || BlueprintConfig.allowPartial());
        }
    }

    /** Red boxes drawn at most for this many occupied spots, so a base dropped into a forest stays cheap to render. */
    private static final int MAX_BLOCKED_BOXES = 400;

    @Nullable
    private static Preview preview;
    private static int tick;

    private ClientEvents() {
    }

    /** Mod-bus event; NeoForge routes it to the mod bus automatically. */
    @SubscribeEvent
    static void keys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    @Nullable
    private static InteractionHand heldHand(Player player) {
        if (player.getMainHandItem().is(BaseBlueprint.BLUEPRINT.get())) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(BaseBlueprint.BLUEPRINT.get())) return InteractionHand.OFF_HAND;
        return null;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            preview = null;
            return;
        }
        InteractionHand hand = heldHand(player);
        while (OPEN_MENU.consumeClick()) {
            if (hand != null && mc.screen == null) ClientHooks.openScreen(hand);
        }
        if (hand == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            preview = null;
            return;
        }
        if (++tick % 4 != 0 && preview != null) return;

        ItemStack stack = player.getItemInHand(hand);
        BlueprintSettings settings = BaseBuilder.settings(stack).clamped();
        BlockPos origin = BaseBuilder.target(mc.level, hit.getBlockPos(), hit.getDirection());
        Block block = BaseBuilder.material(player, stack);
        BaseShape shape = preview != null && preview.settings().equals(settings) ? preview.shape() : BaseShape.of(settings);
        int need = 0, have = 0;
        List<BlockPos> blocked = List.of();
        if (block != null) {
            BaseBuilder.Plan plan = BaseBuilder.plan(mc.level, player, settings, origin, block);
            need = plan.toPlace().size();
            blocked = plan.blockedPositions();
            have = BaseBuilder.countItem(player, block.asItem());
        }
        preview = new Preview(settings, shape, origin, block, need, have, blocked, player.isCreative());
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Preview p = preview;
        if (p == null) return;
        BlueprintSettings s = p.settings();
        int layers = BaseShape.wallStart(s) + s.wallHeight();
        if (layers == 0) return;

        float r, g, b;
        if (p.block() == null) {
            r = 1.0f; g = 0.85f; b = 0.3f;
        } else if (p.buildable()) {
            r = 0.35f; g = 0.95f; b = 0.45f;
        } else {
            r = 1.0f; g = 0.35f; b = 0.3f;
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        int x0 = BaseShape.startX(s, p.origin()), z0 = BaseShape.startZ(s, p.origin()), y0 = p.origin().getY();
        BaseShape shape = p.shape();
        for (int z = 0; z < shape.length(); z++) {
            for (int x = 0; x < shape.width(); x++) {
                if (!shape.edge(x, z)) continue;
                double e = 0.004;
                LevelRenderer.renderLineBox(pose, lines, x0 + x + e, y0 + e, z0 + z + e,
                        x0 + x + 1 - e, y0 + layers - e, z0 + z + 1 - e, r, g, b, 0.9f);
            }
        }
        // occupied spots: clear these (or move) before the base can be built
        int shown = 0;
        for (BlockPos pos : p.blocked()) {
            if (shown++ >= MAX_BLOCKED_BOXES) break;
            LevelRenderer.renderLineBox(pose, lines, pos.getX() + 0.06, pos.getY() + 0.06, pos.getZ() + 0.06,
                    pos.getX() + 0.94, pos.getY() + 0.94, pos.getZ() + 0.94, 1f, 0.15f, 0.15f, 1f);
            LevelRenderer.renderLineBox(pose, lines, pos.getX() - 0.002, pos.getY() - 0.002, pos.getZ() - 0.002,
                    pos.getX() + 1.002, pos.getY() + 1.002, pos.getZ() + 1.002, 1f, 0.15f, 0.15f, 1f);
        }
        // the block you are aiming at, so the centre is obvious
        int cx = p.origin().getX(), cz = p.origin().getZ();
        LevelRenderer.renderLineBox(pose, lines, cx + 0.3, y0 + 0.01, cz + 0.3, cx + 0.7, y0 + 0.4, cz + 0.7, 1f, 1f, 1f, 1f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Preview p = preview;
        if (p == null || mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        BlueprintSettings s = p.settings();
        Component summary = Component.translatable("tooltip.baseblueprint.summary",
                Component.translatable("shape.baseblueprint." + s.shape().getSerializedName()), s.width(), s.length(),
                Component.translatable("floor.baseblueprint." + s.floor().getSerializedName()), s.wallHeight());
        Component status;
        if (p.block() == null) {
            status = Component.translatable("hud.baseblueprint.no_material").withStyle(ChatFormatting.YELLOW);
        } else if (p.creative()) {
            status = Component.translatable("hud.baseblueprint.creative", p.need(), p.block().getName())
                    .withStyle(ChatFormatting.GREEN);
        } else {
            status = Component.translatable("hud.baseblueprint.count", p.block().getName(), p.have(), p.need())
                    .withStyle(p.enough() ? ChatFormatting.GREEN : ChatFormatting.RED);
        }
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
        g.drawCenteredString(mc.font, summary, cx, y, 0xFFE0E0E0);
        g.drawCenteredString(mc.font, status, cx, y + 11, 0xFFFFFFFF);
        if (!p.blocked().isEmpty() && p.block() != null) {
            boolean partial = BlueprintConfig.allowPartial();
            g.drawCenteredString(mc.font, Component.translatable(partial ? "hud.baseblueprint.blocked_partial"
                    : "hud.baseblueprint.blocked", p.blocked().size())
                    .withStyle(partial ? ChatFormatting.GRAY : ChatFormatting.RED), cx, y + 22, 0xFFFFFFFF);
        }
    }
}
