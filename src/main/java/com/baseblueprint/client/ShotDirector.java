package com.baseblueprint.client;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BaseBuilder;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** DEV ONLY (not shipped): with -Dbaseblueprint.shots=true, builds sample bases and takes screenshots for the page. */
@EventBusSubscriber(modid = BaseBlueprint.MOD_ID, value = Dist.CLIENT)
public final class ShotDirector {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final boolean ON = Boolean.getBoolean("baseblueprint.shots");
    private static int CX = 3000, CZ = 3000;

    private record Step(int delay, Runnable action) {
    }

    private static final List<Step> steps = new ArrayList<>();
    private static int index = -1, timer = 0, g;

    private static void server(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void cmd(ServerPlayer p, String c) {
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4).withSuppressedOutput(), c);
    }

    private static void look(ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - (y + p.getEyeHeight()), dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
        p.setDeltaMovement(0, 0, 0);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "bb_" + name + ".png", mc.getMainRenderTarget(), c -> LOG.info("shot {}", name));
    }

    private static ItemStack stack(BlueprintSettings s, Block floor, Block walls) {
        ItemStack st = new ItemStack(BaseBlueprint.BLUEPRINT.get());
        st.set(ModComponents.SETTINGS.get(), s);
        st.set(ModComponents.MATERIAL.get(), floor);
        if (walls != null) st.set(ModComponents.WALL_MATERIAL.get(), walls);
        return st;
    }

    private record Scene(int sx, int sz, ItemStack stack) {
    }

    private static List<Scene> scenes() {
        return List.of(
                new Scene(-18, -18, stack(new BlueprintSettings(BlueprintSettings.Shape.CIRCLE, 15, 15, BlueprintSettings.Floor.FILLED, 3,
                        true, BlueprintSettings.Roof.DOME, false, BlueprintSettings.Pick.FLOOR), Blocks.SPRUCE_PLANKS, Blocks.STONE_BRICKS)),
                new Scene(18, -18, stack(new BlueprintSettings(BlueprintSettings.Shape.OCTAGON, 13, 13, BlueprintSettings.Floor.FILLED, 4,
                        true, BlueprintSettings.Roof.FLAT, false, BlueprintSettings.Pick.FLOOR), Blocks.POLISHED_ANDESITE, Blocks.OAK_PLANKS)),
                new Scene(-18, 18, stack(new BlueprintSettings(BlueprintSettings.Shape.HEXAGON, 15, 11, BlueprintSettings.Floor.FILLED, 3,
                        true, BlueprintSettings.Roof.NONE, false, BlueprintSettings.Pick.FLOOR), Blocks.SMOOTH_STONE, Blocks.COBBLESTONE)),
                new Scene(18, 18, stack(new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 9, 9, BlueprintSettings.Floor.FILLED, 3,
                        true, BlueprintSettings.Roof.FLAT, true, BlueprintSettings.Pick.FLOOR), Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS)));
    }

    private static void flatten(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CX, CZ);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                boolean pit = x >= 10 && x <= 30 && z >= 19 && z <= 36;
                int top = pit ? g - 4 : g - 1;
                for (int y = g - 8; y <= g + 40; y++) {
                    m.set(CX + x, y, CZ + z);
                    Block b = y > top ? Blocks.AIR : y == top ? Blocks.GRASS_BLOCK : y > top - 4 ? Blocks.DIRT : Blocks.STONE;
                    level.setBlock(m, b.defaultBlockState(), 2);
                }
            }
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(CX - 70, g - 10, CZ - 70, CX + 70, g + 50, CZ + 70),
                e -> !(e instanceof Player))) e.discard();
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (!ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (index < 0) {
            build();
            index = 0;
            timer = 100;
        }
        if (index >= steps.size()) return;
        if (--timer > 0) return;
        steps.get(index).action().run();
        index++;
        if (index < steps.size()) timer = steps.get(index).delay();
    }

    private static void add(int wait, Runnable r) {
        steps.add(new Step(wait, r));
    }

    private static void build() {
        Minecraft mc = Minecraft.getInstance();
        add(1, () -> server(p -> {
            for (String c : List.of("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "time set 5000", "weather clear", "gamemode creative",
                    "attribute @s minecraft:player.block_interaction_range base set 40"))
                cmd(p, c);
            var found = p.serverLevel().findClosestBiome3d(b -> b.is(net.minecraft.world.level.biome.Biomes.PLAINS),
                    new BlockPos(3000, 64, 3000), 6400, 32, 64);
            if (found != null) {
                CX = found.getFirst().getX();
                CZ = found.getFirst().getZ();
            }
            LOG.info("plot at {} {}", CX, CZ);
            cmd(p, "forceload add " + (CX - 64) + " " + (CZ - 64) + " " + (CX + 64) + " " + (CZ + 64));
            p.teleportTo(p.serverLevel(), CX, 200, CZ, 0, 0);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        }));
        add(200, () -> server(ShotDirector::flatten));
        List<Scene> scenes = scenes();
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            int n = i;
            add(60, () -> server(p -> {
                p.setItemInHand(InteractionHand.MAIN_HAND, s.stack().copy());
                double x = CX + s.sx() + 0.5, z = CZ + s.sz() + 13.5;
                look(p, x, g + 9, z, CX + s.sx() + 0.5, g, CZ + s.sz() + 0.5);
            }));
            if (n == 0) {
                add(60, () -> shot("preview"));
                add(10, () -> mc.setScreen(new BlueprintScreen(InteractionHand.MAIN_HAND)));
                add(20, () -> shot("menu"));
                add(5, () -> mc.setScreen(null));
            }
            add(20, () -> server(p -> BaseBuilder.build(p, p.getMainHandItem(), new BlockPos(CX + s.sx(), g, CZ + s.sz()))));
        }
        add(40, () -> {
            mc.options.hideGui = true;
            server(p -> p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        });
        // close-ups: camera south-east of each base, looking at its centre
        String[] names = {"dome", "octagon", "hexagon", "foundation"};
        for (int i = 0; i < scenes.size(); i++) {
            Scene s = scenes.get(i);
            String name = names[i];
            double ox = i == 3 ? 13 : 7, oy = i == 3 ? 6 : 6, oz = i == 3 ? 15 : 16;
            add(20, () -> server(p -> look(p, CX + s.sx() + ox, g + oy, CZ + s.sz() + oz, CX + s.sx() + 0.5, g + 1, CZ + s.sz() + 0.5)));
            add(60, () -> shot(name));
        }
        // inside the dome, looking at the door from the back wall
        add(1, () -> server(p -> cmd(p, "effect give @s minecraft:night_vision infinite 0 true")));
        add(20, () -> server(p -> look(p, CX - 18 + 0.5, g + 1, CZ - 18 - 4.5, CX - 18 + 0.5, g + 2.5, CZ - 18 + 7)));
        add(60, () -> shot("inside"));
        add(20, () -> server(p -> look(p, CX + 0.5, g + 26, CZ + 50, CX + 0.5, g, CZ + 2)));
        add(80, () -> shot("overview"));
        add(40, () -> LOG.info("shots done"));
        add(20, () -> Minecraft.getInstance().stop());
    }

    private ShotDirector() {
    }
}
