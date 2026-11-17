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
import net.minecraft.core.Direction;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-world outline preview, the hint under the crosshair and the menu key. */
@EventBusSubscriber(modid = BaseBlueprint.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.baseblueprint.menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.baseblueprint");

    /** What the blueprint would build where the player is looking. Refreshed every few ticks. */
    private record Preview(BlueprintSettings settings, BaseShape shape, BlockPos origin, @Nullable Block block,
                           Map<Block, int[]> counts, List<BlockPos> blocked, List<BaseShape.Placement> placements,
                           Direction doorSide, boolean creative) {
        /** Enough of every block in the inventory alone (the client cannot see containers). */
        boolean enough() {
            if (creative) return true;
            for (int[] c : counts.values()) if (c[1] < c[0]) return false;
            return true;
        }

        /** Would a click build? Needs the material, enough of it and (unless partial builds are on) a clear area. */
        boolean buildable() {
            return block != null && enough() && (blocked.isEmpty() || BlueprintConfig.allowPartial());
        }

        /** Short of blocks in the inventory, but nearby containers and backpacks may cover it on the server. */
        boolean maybeFromContainers() {
            return block != null && !enough() && BlueprintConfig.containerRadius() > 0
                    && (blocked.isEmpty() || BlueprintConfig.allowPartial());
        }
    }

    /** Red boxes drawn at most for this many occupied spots, so a base dropped into a forest stays cheap to render. */
    private static final int MAX_BLOCKED_BOXES = 400;
    /** Dome blocks outlined at most, for the same reason. */
    private static final int MAX_ROOF_BOXES = 3000;

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
        BaseBuilder.Materials materials = BaseBuilder.materials(player, stack);
        Block block = materials.floor();
        BaseShape shape = preview != null && preview.settings().equals(settings) ? preview.shape() : BaseShape.of(settings);
        Direction doorSide = BaseBuilder.doorSide(settings, origin, player);
        Map<Block, int[]> counts = new LinkedHashMap<>();
        List<BlockPos> blocked = List.of();
        if (block != null) {
            BaseBuilder.Plan plan = BaseBuilder.plan(mc.level, player, settings, origin, materials);
            plan.needs().forEach((b, n) -> counts.put(b, new int[]{n, BaseBuilder.countItem(player, b.asItem())}));
            blocked = plan.blockedPositions();
        }
        preview = new Preview(settings, shape, origin, block, counts, blocked,
                shape.placements(settings, origin, doorSide), doorSide, player.isCreative());
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Preview p = preview;
        if (p == null) return;
        BlueprintSettings s = p.settings();
        int storeys = s.effectiveStoreys();
        int layers = BaseShape.wallStart(s) + storeys * s.wallHeight() + (storeys - 1);
        if (layers == 0) return;

        float r, g, b;
        if (p.block() == null) {
            r = 1.0f; g = 0.85f; b = 0.3f;
        } else if (p.buildable()) {
            r = 0.35f; g = 0.95f; b = 0.45f;
        } else if (p.maybeFromContainers()) {
            r = 1.0f; g = 0.75f; b = 0.25f;
        } else {
            r = 1.0f; g = 0.35f; b = 0.3f;
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        // Our own buffer, flushed right here: Sodium/Iris wrap the shared one and may never draw lines put in it.
        MultiBufferSource.BufferSource buffers = OUTLINE_BUFFERS;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        int x0 = BaseShape.startX(s, p.origin()), z0 = BaseShape.startZ(s, p.origin()), y0 = p.origin().getY();
        BaseShape shape = p.shape();
        double e = 0.004;
        int wallTop = y0 + layers;
        // The ring of the floor and walls as columns, then each roof block (a flat roof or the dome shell).
        java.util.Set<Long> columns = new java.util.HashSet<>();
        int roofBoxes = 0;
        for (BaseShape.Placement placement : p.placements()) {
            BlockPos pos = placement.pos();
            boolean slab = storeys > 1 && placement.part() != BaseShape.Part.ROOF && shape.edge(pos.getX() - x0, pos.getZ() - z0)
                    && (pos.getY() - y0 - BaseShape.wallStart(s)) % (s.wallHeight() + 1) == s.wallHeight();
            if (slab && roofBoxes++ < MAX_ROOF_BOXES) {
                // the floor between two storeys, as a ring on the wall
                LevelRenderer.renderLineBox(pose, lines, pos.getX() + e, pos.getY() + e, pos.getZ() + e,
                        pos.getX() + 1 - e, pos.getY() + 1 - e, pos.getZ() + 1 - e, r, g, b, 0.5f);
            }
            if (placement.part() == BaseShape.Part.ROOF) {
                if (s.effectiveRoof() == BlueprintSettings.Roof.FLAT && !shape.edge(pos.getX() - x0, pos.getZ() - z0)) continue;
                if (roofBoxes++ >= MAX_ROOF_BOXES) continue;
                LevelRenderer.renderLineBox(pose, lines, pos.getX() + e, pos.getY() + e, pos.getZ() + e,
                        pos.getX() + 1 - e, pos.getY() + 1 - e, pos.getZ() + 1 - e, r, g, b, 0.6f);
            } else if (shape.edge(pos.getX() - x0, pos.getZ() - z0) && columns.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()))) {
                LevelRenderer.renderLineBox(pose, lines, pos.getX() + e, y0 + e, pos.getZ() + e,
                        pos.getX() + 1 - e, wallTop - e, pos.getZ() + 1 - e, r, g, b, 0.9f);
            }
        }
        // the doorway, in light blue
        if (s.door() && s.wallHeight() > 0) {
            java.util.Set<BlockPos> walls = new java.util.HashSet<>();
            for (BaseShape.Placement placement : p.placements()) {
                if (placement.part() == BaseShape.Part.WALL) walls.add(placement.pos());
            }
            int wy = y0 + BaseShape.wallStart(s);
            for (int z = 0; z < shape.length(); z++) {
                for (int x = 0; x < shape.width(); x++) {
                    BlockPos wall = new BlockPos(x0 + x, wy, z0 + z);
                    if (!shape.edge(x, z) || walls.contains(wall)) continue;
                    LevelRenderer.renderLineBox(pose, lines, wall.getX() + 0.1, wy + 0.02, wall.getZ() + 0.1,
                            wall.getX() + 0.9, wy + Math.min(2, s.wallHeight()) - 0.02, wall.getZ() + 0.9, 0.4f, 0.8f, 1f, 1f);
                }
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

    private static final MultiBufferSource.BufferSource OUTLINE_BUFFERS =
            MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(1 << 18));

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
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
        g.drawCenteredString(mc.font, summary, cx, y, 0xFFE0E0E0);
        if (p.block() == null) {
            y += 11;
            g.drawCenteredString(mc.font, Component.translatable("hud.baseblueprint.no_material").withStyle(ChatFormatting.YELLOW),
                    cx, y, 0xFFFFFFFF);
        } else {
            // one line per block: floor, and walls when they differ
            for (Map.Entry<Block, int[]> entry : p.counts().entrySet()) {
                y += 11;
                int need = entry.getValue()[0], have = entry.getValue()[1];
                Component line = p.creative()
                        ? Component.translatable("hud.baseblueprint.creative", need, entry.getKey().getName()).withStyle(ChatFormatting.GREEN)
                        : Component.translatable("hud.baseblueprint.count", entry.getKey().getName(), have, need)
                        .withStyle(have >= need ? ChatFormatting.GREEN
                                : BlueprintConfig.containerRadius() > 0 ? ChatFormatting.GOLD : ChatFormatting.RED);
                g.drawCenteredString(mc.font, line, cx, y, 0xFFFFFFFF);
            }
            if (p.maybeFromContainers()) {
                y += 11;
                g.drawCenteredString(mc.font, Component.translatable("hud.baseblueprint.containers", BlueprintConfig.containerRadius())
                        .withStyle(ChatFormatting.GRAY), cx, y, 0xFFFFFFFF);
            }
        }
        y -= 11; // the blocked line below goes right after the last one
        if (!p.blocked().isEmpty() && p.block() != null) {
            boolean partial = BlueprintConfig.allowPartial();
            g.drawCenteredString(mc.font, Component.translatable(partial ? "hud.baseblueprint.blocked_partial"
                    : "hud.baseblueprint.blocked", p.blocked().size())
                    .withStyle(partial ? ChatFormatting.GRAY : ChatFormatting.RED), cx, y + 22, 0xFFFFFFFF);
        }
    }
}
