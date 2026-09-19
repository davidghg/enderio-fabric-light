package de.daveos.enderiofabriclight.item;

import de.daveos.enderiofabriclight.block.CacheBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Locale;
import java.util.function.Consumer;

/** Cache item: shows what the cache holds and how much fits. */
public class CacheBlockItem extends BlockItem {
    private final CacheBlock cache;

    public CacheBlockItem(CacheBlock block, Properties properties) {
        super(block, properties);
        this.cache = block;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        CacheContents contents = stack.get(ModComponents.CACHE_CONTENTS);
        if (contents != null) {
            tooltip.accept(Component.translatable("tooltip.enderio-fabric-light.cache.contents",
                format(contents.count()), new ItemStack(contents.item()).getHoverName()).withStyle(ChatFormatting.GRAY));
            if (contents.locked()) {
                tooltip.accept(Component.translatable("tooltip.enderio-fabric-light.cache.locked").withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.accept(Component.translatable("tooltip.enderio-fabric-light.cache.capacity",
            format(cache.capacity())).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String format(long n) {
        return String.format(Locale.GERMANY, "%,d", n);
    }
}
