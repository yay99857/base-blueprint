package com.baseblueprint;

import com.baseblueprint.network.UpdateSettingsPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;

@Mod(BaseBlueprint.MOD_ID)
public class BaseBlueprint {
    public static final String MOD_ID = "baseblueprint";

    /** Bases one blueprint builds before it wears out (repair with paper in an anvil). */
    public static final int USES = 8;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<BlueprintItem> BLUEPRINT =
            ITEMS.register("base_blueprint", () -> new BlueprintItem(new Item.Properties().durability(USES)));

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(id("main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);

    public BaseBlueprint() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, BlueprintConfig.SPEC);
        ITEMS.register(modBus);

        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(BLUEPRINT.get());
            }
        });

        CHANNEL.registerMessage(0, UpdateSettingsPayload.class, UpdateSettingsPayload::write,
                UpdateSettingsPayload::read, UpdateSettingsPayload::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
