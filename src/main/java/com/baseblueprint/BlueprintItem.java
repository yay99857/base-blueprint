package com.baseblueprint;

import com.baseblueprint.client.ClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
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
                    stack.set(ModComponents.MATERIAL.get(), block);
                    player.displayClientMessage(Component.translatable("message.baseblueprint.material_set", block.getName())
                            .withStyle(ChatFormatting.AQUA), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.baseblueprint.invalid_material", block.getName())
                            .withStyle(ChatFormatting.RED), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            BaseBuilder.build(player, stack, BaseBuilder.target(level, ctx.getClickedPos(), ctx.getClickedFace()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlueprintSettings s = BaseBuilder.settings(stack);
        tooltip.add(Component.translatable("tooltip.baseblueprint.summary",
                Component.translatable("shape.baseblueprint." + s.shape().getSerializedName()), s.width(), s.length(),
                Component.translatable("floor.baseblueprint." + s.floor().getSerializedName()), s.wallHeight())
                .withStyle(ChatFormatting.GRAY));
        Block material = stack.get(ModComponents.MATERIAL.get());
        tooltip.add(material != null
                ? Component.translatable("tooltip.baseblueprint.material", material.getName()).withStyle(ChatFormatting.AQUA)
                : Component.translatable("tooltip.baseblueprint.no_material").withStyle(ChatFormatting.YELLOW));
        for (String line : new String[]{"build", "menu", "pick", "offhand", "undo"}) {
            tooltip.add(Component.translatable("tooltip.baseblueprint.help." + line).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
