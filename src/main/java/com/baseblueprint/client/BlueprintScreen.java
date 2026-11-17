package com.baseblueprint.client;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BaseBuilder;
import com.baseblueprint.BaseShape;
import com.baseblueprint.BlueprintConfig;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import com.baseblueprint.network.UpdateSettingsPayload;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Shape, size, floor and wall menu, with a pixel preview of the footprint like a pixel circle generator. */
public class BlueprintScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 340;
    private static final int GRID = 128;
    private static final int ROW = 22;
    private static final int MIN_STOREY_HEIGHT = 2, DEFAULT_STOREY_HEIGHT = 4;

    private final InteractionHand hand;
    private BlueprintSettings settings;
    private boolean locked;
    private BaseShape shape;
    private int left, top;
    /** The 3D view of the preview box: the base turned and tilted, dragged with the mouse, zoomed with the wheel. */
    private boolean view3d = true;
    private float yaw = 35, pitch = 30, zoom = 1;
    private List<BlockPos> blockPos = List.of();
    private List<BlockState> blockStates = List.of();
    private int minX, minY, minZ, maxX, maxY, maxZ;

    public BlueprintScreen(InteractionHand hand) {
        super(Component.translatable("screen.baseblueprint.title"));
        this.hand = hand;
        this.settings = BaseBuilder.settings(heldStack()).clamped();
        this.locked = settings.width() == settings.length();
        this.shape = BaseShape.of(settings);
        rebuild3d();
    }

    /** Every block of the base with its material, for the 3D view (door on the south side, towards the viewer). */
    private void rebuild3d() {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        BaseBuilder.Materials m = player == null ? new BaseBuilder.Materials(null, null) : BaseBuilder.materials(player, heldStack());
        BlockState floor = (m.floor() == null ? Blocks.STONE : m.floor()).defaultBlockState();
        BlockState walls = m.walls() == null ? floor : m.walls().defaultBlockState();
        List<BlockPos> pos = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
        for (BaseShape.Placement p : shape.placements(settings, BlockPos.ZERO, Direction.SOUTH)) {
            if (pos.size() >= MAX_3D_BLOCKS) break;
            BlockPos b = p.pos();
            pos.add(b);
            states.add(p.part().usesWallMaterial() ? walls : floor);
            minX = Math.min(minX, b.getX()); maxX = Math.max(maxX, b.getX());
            minY = Math.min(minY, b.getY()); maxY = Math.max(maxY, b.getY());
            minZ = Math.min(minZ, b.getZ()); maxZ = Math.max(maxZ, b.getZ());
        }
        blockPos = pos;
        blockStates = states;
    }

    private static final int MAX_3D_BLOCKS = 12000;

    private ItemStack heldStack() {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        int x = left + GRID + 22;
        int y = top + 26;
        int colW = PANEL_W - GRID - 32;

        addRenderableWidget(CycleButton.<BlueprintSettings.Shape>builder(s ->
                        Component.translatable("shape.baseblueprint." + s.getSerializedName()))
                .withValues(BlueprintSettings.Shape.values())
                .withInitialValue(settings.shape())
                .create(x, y, colW, 20, Component.translatable("screen.baseblueprint.shape"),
                        (b, v) -> update(settings.withShape(v))));
        y += ROW;
        stepper(x, y, colW, "screen.baseblueprint.width", () -> settings.width(), v -> {
            int w = Mth.clamp(v, 1, BlueprintConfig.maxSize());
            update(locked ? settings.withWidth(w).withLength(w) : settings.withWidth(w));
        });
        y += ROW;
        stepper(x, y, colW, "screen.baseblueprint.length", () -> settings.length(), v -> {
            int l = Mth.clamp(v, 1, BlueprintConfig.maxSize());
            update(locked ? settings.withWidth(l).withLength(l) : settings.withLength(l));
        });
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(locked)
                .create(x, y, colW, 20, Component.translatable("screen.baseblueprint.lock"), (b, v) -> {
                    locked = v;
                    if (v) update(settings.withLength(settings.width()));
                }));
        y += ROW;
        addRenderableWidget(CycleButton.<BlueprintSettings.Floor>builder(f ->
                        Component.translatable("floor.baseblueprint." + f.getSerializedName()))
                .withValues(BlueprintSettings.Floor.values())
                .withInitialValue(settings.floor())
                .create(x, y, colW, 20, Component.translatable("screen.baseblueprint.floor"),
                        (b, v) -> update(settings.withFloor(v))));
        y += ROW;
        stepper(x, y, colW, "screen.baseblueprint.walls", () -> settings.wallHeight(),
                v -> update(settings.withWallHeight(Mth.clamp(v, settings.storeys() > 1 ? MIN_STOREY_HEIGHT : 0,
                        BlueprintConfig.maxWallHeight()))));
        y += ROW;
        stepper(x, y, colW, "screen.baseblueprint.storeys", () -> settings.storeys(),
                v -> {
                    BlueprintSettings next = settings.withStoreys(Mth.clamp(v, 1, BlueprintSettings.MAX_STOREYS));
                    // a storey you cannot stand in is no storey: raise low walls to a usable height
                    if (next.storeys() > 1 && next.wallHeight() < MIN_STOREY_HEIGHT) next = next.withWallHeight(DEFAULT_STOREY_HEIGHT);
                    update(next);
                });
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(settings.door())
                .create(x, y, colW, 20, Component.translatable("screen.baseblueprint.door"),
                        (b, v) -> update(settings.withDoor(v))));
        y += ROW;
        addRenderableWidget(CycleButton.<BlueprintSettings.Roof>builder(r ->
                        Component.translatable("roof.baseblueprint." + r.getSerializedName()))
                .withValues(BlueprintSettings.Roof.values())
                .withInitialValue(settings.roof())
                .create(x, y, colW, 20, Component.translatable("screen.baseblueprint.roof"),
                        (b, v) -> update(settings.withRoof(v))));
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(settings.foundation())
                .create(x, y, colW, 20, Component.translatable("screen.baseblueprint.foundation"),
                        (b, v) -> update(settings.withFoundation(v))));
        y += ROW;
        addRenderableWidget(CycleButton.<BlueprintSettings.Pick>builder(k ->
                        Component.translatable("pick.baseblueprint." + k.getSerializedName()))
                .withValues(BlueprintSettings.Pick.values())
                .withInitialValue(settings.pick())
                .create(x, y, colW, 20, Component.translatable("screen.baseblueprint.pick"),
                        (b, v) -> update(settings.withPick(v))));

        addRenderableWidget(Button.builder(Component.literal(view3d ? "2D" : "3D"), b -> {
            view3d = !view3d;
            b.setMessage(Component.literal(view3d ? "2D" : "3D"));
        }).bounds(left + 10 + GRID - 22, top + 24 + GRID - 16, 22, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_W / 2 - 50, top + PANEL_H - 28, 100, 20).build());
    }

    /** A "label  [-] value [+]" row; shift changes the value by 5. */
    private void stepper(int x, int y, int colW, String label, IntSupplier get, IntConsumer set) {
        int bx = x + colW - 62;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> set.accept(get.getAsInt() - step()))
                .bounds(bx, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> set.accept(get.getAsInt() + step()))
                .bounds(bx + 42, y, 20, 20).build());
        addRenderableOnly((g, mx, my, pt) -> {
            g.drawString(font, Component.translatable(label), x, y + 6, 0xFFE0E0E0, false);
            String value = String.valueOf(get.getAsInt());
            g.drawString(font, value, bx + 31 - font.width(value) / 2, y + 6, 0xFFFFFFFF, true);
        });
    }

    private static int step() {
        return hasShiftDown() ? 5 : 1;
    }

    private void update(BlueprintSettings next) {
        settings = next.clamped();
        shape = BaseShape.of(settings);
        rebuild3d();
    }

    private boolean inPreview(double mx, double my) {
        return mx >= left + 10 && mx < left + 10 + GRID && my >= top + 24 && my < top + 24 + GRID;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        return inPreview(mx, my);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (view3d && inPreview(mx, my)) {
            yaw += (float) dx * 2.5f;
            pitch = Mth.clamp(pitch + (float) dy * 2.5f, -10, 89);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (view3d && inPreview(mx, my)) {
            zoom = Mth.clamp(zoom * (sy > 0 ? 1.15f : 1 / 1.15f), 0.4f, 6f);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private void render3d(GuiGraphics g, int gx, int gy) {
        if (blockPos.isEmpty() || minecraft == null) return;
        float cx = (minX + maxX + 1) / 2f, cy = (minY + maxY + 1) / 2f, cz = (minZ + maxZ + 1) / 2f;
        float size = (float) Math.sqrt(Math.pow(maxX - minX + 1, 2) + Math.pow(maxY - minY + 1, 2) + Math.pow(maxZ - minZ + 1, 2));
        float scale = GRID / size * zoom;
        var blocks = minecraft.getBlockRenderer();
        var buffers = g.bufferSource();
        g.enableScissor(gx, gy, gx + GRID, gy + GRID);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(gx + GRID / 2f, gy + GRID / 2f, 400);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-cx, -cy, -cz);
        Lighting.setupFor3DItems();
        for (int i = 0; i < blockPos.size(); i++) {
            BlockPos b = blockPos.get(i);
            pose.pushPose();
            pose.translate(b.getX(), b.getY(), b.getZ());
            blocks.renderSingleBlock(blockStates.get(i), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, null);
            pose.popPose();
        }
        g.flush();
        pose.popPose();
        g.disableScissor();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, 0xFF0B1E33);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0153A5E);
        g.fill(left + 10, top + 24, left + 10 + GRID, top + 24 + GRID, 0xFF0E2A47);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + PANEL_W / 2, top + 9, 0xFFFFFFFF);
        if (view3d && settings.wallHeight() > 0) {
            render3d(g, left + 10, top + 24);
        } else {
            renderGrid(g, left + 10, top + 24);
        }

        Player player = minecraft == null ? null : minecraft.player;
        int need = shape.blockCount(settings);
        int y = top + 26 + ROW * 11 + 4;
        g.drawString(font, Component.translatable("screen.baseblueprint.size", settings.width(), settings.length()),
                left + 10, top + 24 + GRID + 6, 0xFFB8D4F0, false);
        g.drawString(font, Component.translatable("screen.baseblueprint.blocks", need), left + 10, top + 24 + GRID + 17,
                0xFFFFFFFF, false);
        if (settings.roof() == BlueprintSettings.Roof.DOME && settings.shape() != BlueprintSettings.Shape.CIRCLE) {
            g.drawString(font, Component.translatable("screen.baseblueprint.dome_circle_only"), left + 10, top + 24 + GRID + 28,
                    0xFFFFD866, false);
        }
        BaseBuilder.Materials materials = player == null ? new BaseBuilder.Materials(null, null)
                : BaseBuilder.materials(player, heldStack());
        if (materials.floor() == null) {
            g.drawString(font, Component.translatable("screen.baseblueprint.no_material"), left + 10, y, 0xFFFFD866, false);
            return;
        }
        drawMaterial(g, player, "screen.baseblueprint.material_floor", materials.floor(), left + 10, y);
        drawMaterial(g, player, "screen.baseblueprint.material_walls", materials.walls(), left + 10, y + 11);
    }

    private void drawMaterial(GuiGraphics g, Player player, String key, Block block, int x, int y) {
        boolean creative = player.isCreative();
        int have = BaseBuilder.countItem(player, block.asItem());
        Component line = creative ? Component.translatable(key + "_free", block.getName())
                : Component.translatable(key, block.getName(), have);
        g.drawString(font, line, x, y, 0xFFE0E0E0, false);
    }

    private void renderGrid(GuiGraphics g, int gx, int gy) {
        int w = settings.width(), l = settings.length();
        float scale = (float) GRID / Math.max(w, l);
        // Whole-pixel cells when they fit, so the preview stays crisp.
        if (scale >= 1) scale = (float) Math.floor(scale);
        float ox = gx + (GRID - w * scale) / 2f;
        float oz = gy + (GRID - l * scale) / 2f;
        boolean walls = settings.wallHeight() > 0;
        int cx = w / 2, cz = l / 2;
        // door cells: wall cells the shape leaves open on the south side (the preview's "towards you")
        java.util.Set<Long> wallCells = new java.util.HashSet<>();
        if (walls && settings.door()) {
            for (BaseShape.Placement p : shape.placements(settings.withRoof(BlueprintSettings.Roof.NONE)
                    .withFloor(BlueprintSettings.Floor.NONE).withWallHeight(1), net.minecraft.core.BlockPos.ZERO,
                    net.minecraft.core.Direction.SOUTH)) {
                wallCells.add(net.minecraft.core.BlockPos.asLong(p.pos().getX() - BaseShape.startX(settings, net.minecraft.core.BlockPos.ZERO),
                        0, p.pos().getZ() - BaseShape.startZ(settings, net.minecraft.core.BlockPos.ZERO)));
            }
        }

        g.pose().pushPose();
        g.pose().translate(ox, oz, 0);
        g.pose().scale(scale, scale, 1);
        for (int z = 0; z < l; z++) {
            for (int x = 0; x < w; x++) {
                int color;
                boolean edge = shape.edge(x, z);
                if (x == cx && z == cz) {
                    color = 0xFF5BE37A; // where you click
                } else if (walls && edge && settings.door() && !wallCells.contains(net.minecraft.core.BlockPos.asLong(x, 0, z))) {
                    color = 0xFF66CCFF; // doorway
                } else if (walls && edge) {
                    color = 0xFFE59A3C;
                } else if (settings.floor() == BlueprintSettings.Floor.FILLED && shape.filled(x, z)
                        || settings.floor() == BlueprintSettings.Floor.OUTLINE && edge) {
                    color = 0xFFD9E6F2;
                } else {
                    color = ((x + z) & 1) == 0 ? 0xFF16385A : 0xFF13324F;
                }
                g.fill(x, z, x + 1, z + 1, color);
            }
        }
        g.pose().popPose();
    }

    @Override
    public void onClose() {
        ItemStack stack = heldStack();
        if (stack.is(BaseBlueprint.BLUEPRINT.get())) {
            stack.set(ModComponents.SETTINGS.get(), settings); // instant local preview; the server confirms
            PacketDistributor.sendToServer(new UpdateSettingsPayload(hand == InteractionHand.MAIN_HAND, settings));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
