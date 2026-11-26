package com.baseblueprint;

import com.baseblueprint.api.HelperBuildEvent;
import com.baseblueprint.client.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) BaseBuilder.undo(player);
        } else if (level.isClientSide) {
            ClientHooks.openScreen(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();

        if (player.isShiftKeyDown()) {
            Block block = level.getBlockState(ctx.getClickedPos()).getBlock();
            if (!level.isClientSide) {
                if (BaseBuilder.isValidMaterial(block)) {
                    boolean walls = BaseBuilder.settings(stack).pick() == BlueprintSettings.Pick.WALLS;
                    if (walls) ModComponents.setWallMaterial(stack, block);
                    else ModComponents.setMaterial(stack, block);
                    player.displayClientMessage(Component.translatable(walls ? "message.baseblueprint.wall_material_set"
                                    : "message.baseblueprint.material_set", block.getName())
                            .withStyle(ChatFormatting.AQUA), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.baseblueprint.invalid_material", block.getName())
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            BlockPos origin = BaseBuilder.target(level, ctx.getClickedPos(), ctx.getClickedFace());
            // "Built by: Bubie" in the menu: the same click, on the same preview, hands the job to a helper
            if (BaseBuilder.settings(stack).helper() && HelperBuildEvent.available()
                    && player instanceof ServerPlayer serverPlayer) {
                HelperBuildEvent.ask(serverPlayer, stack, origin);
            } else {
                BaseBuilder.build(player, stack, origin);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Paper repairs it in an anvil. */
    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.PAPER);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BlueprintSettings s = BaseBuilder.settings(stack);
        int left = stack.getMaxDamage() - stack.getDamageValue();
        tooltip.add(Component.translatable("tooltip.baseblueprint.uses", left, stack.getMaxDamage())
                .withStyle(left <= 2 ? ChatFormatting.RED : ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.baseblueprint.summary",
                Component.translatable("shape.baseblueprint." + s.shape().getSerializedName()), s.width(), s.length(),
                Component.translatable("floor.baseblueprint." + s.floor().getSerializedName()), s.wallHeight())
                .withStyle(ChatFormatting.GRAY));
        if (s.effectiveStoreys() > 1) {
            tooltip.add(Component.translatable("tooltip.baseblueprint.storeys", s.effectiveStoreys(), s.wallHeight())
                    .withStyle(ChatFormatting.GRAY));
        }
        Block material = ModComponents.getMaterial(stack);
        tooltip.add(material != null
                ? Component.translatable("tooltip.baseblueprint.material", material.getName()).withStyle(ChatFormatting.AQUA)
                : Component.translatable("tooltip.baseblueprint.no_material").withStyle(ChatFormatting.YELLOW));
        Block walls = ModComponents.getWallMaterial(stack);
        if (walls != null) {
            tooltip.add(Component.translatable("tooltip.baseblueprint.wall_material", walls.getName()).withStyle(ChatFormatting.AQUA));
        }
        if (s.door() || s.effectiveRoof() != BlueprintSettings.Roof.NONE || s.foundation()) {
            tooltip.add(Component.translatable("tooltip.baseblueprint.extras",
                    Component.translatable(s.door() ? "options.on" : "options.off"),
                    Component.translatable("roof.baseblueprint." + s.effectiveRoof().getSerializedName()),
                    Component.translatable(s.foundation() ? "options.on" : "options.off")).withStyle(ChatFormatting.GRAY));
        }
        for (String line : new String[]{"build", "menu", "pick", "offhand", "undo"}) {
            tooltip.add(Component.translatable("tooltip.baseblueprint.help." + line).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
