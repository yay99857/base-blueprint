package com.baseblueprint;

import com.baseblueprint.api.HelperBuildEvent;
import net.minecraft.core.BlockPos;
import com.baseblueprint.client.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Right-click a block: build the base there. Right-click the air (or press the menu key): choose shape and size.
 * Sneak + right-click a block: use that block as the material. Sneak + right-click the air: undo the last base.
 */
public class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) BaseBuilder.undo(player);
        } else if (level.isClientSide()) {
            ClientHooks.openScreen(hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();

        if (player.isShiftKeyDown()) {
            Block block = level.getBlockState(ctx.getClickedPos()).getBlock();
            if (!level.isClientSide()) {
                if (BaseBuilder.isValidMaterial(block)) {
                    boolean walls = BaseBuilder.settings(stack).pick() == BlueprintSettings.Pick.WALLS;
                    stack.set(walls ? ModComponents.WALL_MATERIAL.get() : ModComponents.MATERIAL.get(), block);
                    player.sendOverlayMessage(Component.translatable(walls ? "message.baseblueprint.wall_material_set"
                                    : "message.baseblueprint.material_set", block.getName())
                            .withStyle(ChatFormatting.AQUA));
                } else {
                    player.sendOverlayMessage(Component.translatable("message.baseblueprint.invalid_material", block.getName())
                            .withStyle(ChatFormatting.RED));
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            BlockPos origin = BaseBuilder.target(level, ctx.getClickedPos(), ctx.getClickedFace());
            // "Built by: Bubie" in the menu: the same click, on the same preview, hands the job to a helper
            if (BaseBuilder.settings(stack).helper() && HelperBuildEvent.available()
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                HelperBuildEvent.ask(serverPlayer, stack, origin);
            } else {
                BaseBuilder.build(player, stack, origin);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        BlueprintSettings s = BaseBuilder.settings(stack);
        int left = stack.getMaxDamage() - stack.getDamageValue();
        tooltip.accept(Component.translatable("tooltip.baseblueprint.uses", left, stack.getMaxDamage())
                .withStyle(left <= 2 ? ChatFormatting.RED : ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.baseblueprint.summary",
                Component.translatable("shape.baseblueprint." + s.shape().getSerializedName()), s.width(), s.length(),
                Component.translatable("floor.baseblueprint." + s.floor().getSerializedName()), s.wallHeight())
                .withStyle(ChatFormatting.GRAY));
        if (s.effectiveStoreys() > 1) {
            tooltip.accept(Component.translatable("tooltip.baseblueprint.storeys", s.effectiveStoreys(), s.wallHeight())
                    .withStyle(ChatFormatting.GRAY));
        }
        Block material = stack.get(ModComponents.MATERIAL.get());
        tooltip.accept(material != null
                ? Component.translatable("tooltip.baseblueprint.material", material.getName()).withStyle(ChatFormatting.AQUA)
                : Component.translatable("tooltip.baseblueprint.no_material").withStyle(ChatFormatting.YELLOW));
        Block walls = stack.get(ModComponents.WALL_MATERIAL.get());
        if (walls != null) {
            tooltip.accept(Component.translatable("tooltip.baseblueprint.wall_material", walls.getName()).withStyle(ChatFormatting.AQUA));
        }
        if (s.door() || s.effectiveRoof() != BlueprintSettings.Roof.NONE || s.foundation()) {
            tooltip.accept(Component.translatable("tooltip.baseblueprint.extras",
                    Component.translatable(s.door() ? "options.on" : "options.off"),
                    Component.translatable("roof.baseblueprint." + s.effectiveRoof().getSerializedName()),
                    Component.translatable(s.foundation() ? "options.on" : "options.off")).withStyle(ChatFormatting.GRAY));
        }
        for (String line : new String[]{"build", "menu", "pick", "offhand", "undo"}) {
            tooltip.accept(Component.translatable("tooltip.baseblueprint.help." + line).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
