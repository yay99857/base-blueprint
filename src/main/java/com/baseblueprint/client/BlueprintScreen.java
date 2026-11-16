package com.baseblueprint.client;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BaseBuilder;
import com.baseblueprint.BaseShape;
import com.baseblueprint.BlueprintConfig;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import com.baseblueprint.network.UpdateSettingsPayload;
import net.minecraft.client.gui.GuiGraphics;
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

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Shape, size, floor and wall menu, with a pixel preview of the footprint like a pixel circle generator. */
public class BlueprintScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PANEL_H = 214;
    private static final int GRID = 128;
    private static final int ROW = 22;

    private final InteractionHand hand;
    private BlueprintSettings settings;
    private boolean locked;
    private BaseShape shape;
    private int left, top;

    public BlueprintScreen(InteractionHand hand) {
        super(Component.translatable("screen.baseblueprint.title"));
        this.hand = hand;
        this.settings = BaseBuilder.settings(heldStack()).clamped();
        this.locked = settings.width() == settings.length();
        this.shape = BaseShape.of(settings);
    }

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
                v -> update(settings.withWallHeight(Mth.clamp(v, 0, BlueprintConfig.maxWallHeight()))));

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
        renderGrid(g, left + 10, top + 24);

        Player player = minecraft == null ? null : minecraft.player;
        int need = shape.blockCount(settings);
        int y = top + 24 + GRID + 6;
        g.drawString(font, Component.translatable("screen.baseblueprint.size", settings.width(), settings.length()),
                left + 10, y, 0xFFB8D4F0, false);
        g.drawString(font, Component.translatable("screen.baseblueprint.blocks", need), left + 10, y + 11, 0xFFFFFFFF, false);
        Block block = player == null ? null : BaseBuilder.material(player, heldStack());
        Component materialLine;
        if (block == null) {
            materialLine = Component.translatable("screen.baseblueprint.no_material");
            g.drawString(font, materialLine, left + GRID + 22, y + 11, 0xFFFFD866, false);
        } else {
            boolean creative = player.isCreative();
            int have = BaseBuilder.countItem(player, block.asItem());
            materialLine = creative
                    ? Component.translatable("screen.baseblueprint.material_creative", block.getName())
                    : Component.translatable("screen.baseblueprint.material", block.getName(), have, need);
            g.drawString(font, materialLine, left + GRID + 22, y + 11, creative || have >= need ? 0xFF7CF08C : 0xFFFF7A6E, false);
        }
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

        g.pose().pushPose();
        g.pose().translate(ox, oz, 0);
        g.pose().scale(scale, scale, 1);
        for (int z = 0; z < l; z++) {
            for (int x = 0; x < w; x++) {
                int color;
                boolean edge = shape.edge(x, z);
                if (x == cx && z == cz) {
                    color = 0xFF5BE37A; // where you click
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
