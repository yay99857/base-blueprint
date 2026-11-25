package com.baseblueprint.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The menu's 3D view of the base: the blocks are drawn into a texture of their own (a "picture in picture") and
 * that texture is put on the screen, as the game does for entities in inventories.
 */
public final class BasePreview {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();

    private BasePreview() {
    }

    /** One frame of the view: the blocks, the camera around their centre, and where on the screen. */
    public record State(List<BlockPos> positions, List<BlockModelRenderState> models, float cx, float cy, float cz,
                        float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
            implements PictureInPictureRenderState {
    }

    /** Resolves the block models once per change of the base (not per frame). */
    public static List<BlockModelRenderState> models(List<BlockState> states) {
        var resolver = Minecraft.getInstance().getBlockModelResolver();
        List<BlockModelRenderState> out = new ArrayList<>(states.size());
        for (BlockState state : states) {
            BlockModelRenderState model = new BlockModelRenderState();
            resolver.update(model, state, CONTEXT);
            out.add(model);
        }
        return out;
    }

    public static State state(List<BlockPos> positions, List<BlockModelRenderState> models, float cx, float cy, float cz,
                              float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
                              @Nullable ScreenRectangle scissor) {
        return new State(positions, models, cx, cy, cz, yaw, pitch, x0, y0, x1, y1, scale, scissor,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor));
    }

    public static final class Renderer extends PictureInPictureRenderer<State> {
        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
            pose.scale(1.0F, -1.0F, -1.0F); // y up and z towards the viewer, as the menu had it before
            pose.rotateDegrees(Axis.XP, state.pitch());
            pose.rotateDegrees(Axis.YP, state.yaw());
            pose.translate(-state.cx(), -state.cy(), -state.cz());
            for (int i = 0; i < state.positions().size(); i++) {
                BlockPos b = state.positions().get(i);
                pose.pushPose();
                pose.translate(b.getX(), b.getY(), b.getZ());
                state.models().get(i).submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected String getTextureLabel() {
            return "base blueprint preview";
        }
    }
}
