package com.baseblueprint;

import com.baseblueprint.network.UpdateSettingsPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BaseBlueprint.MOD_ID)
public class BaseBlueprint {
    public static final String MOD_ID = "baseblueprint";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<BlueprintItem> BLUEPRINT =
            ITEMS.register("base_blueprint", () -> new BlueprintItem(new Item.Properties().stacksTo(1)));

    public BaseBlueprint(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, BlueprintConfig.SPEC);
        ModComponents.COMPONENTS.register(modBus);
        ITEMS.register(modBus);

        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(BLUEPRINT.get());
            }
        });
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToServer(UpdateSettingsPayload.TYPE, UpdateSettingsPayload.STREAM_CODEC, UpdateSettingsPayload::handle));

    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

}
