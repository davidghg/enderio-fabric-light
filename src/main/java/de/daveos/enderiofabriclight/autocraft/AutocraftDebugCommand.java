package de.daveos.enderiofabriclight.autocraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.daveos.enderiofabriclight.block.PanelBlock;
import de.daveos.enderiofabriclight.inventory.ConduitInventorySource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Temporary test command for autocrafting, replaced by the terminal UI later:
 * {@code /eflcraft <panel pos> <item> [amount]} prints the plan for that panel's network without
 * crafting anything; appending {@code go} crafts it (needs a crafting panel on the network, and
 * respects its order limit).
 */
public final class AutocraftDebugCommand {
    /** Keeps chat readable; longer lists are cut off with a count. */
    private static final int MAX_LINES = 12;

    private AutocraftDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("eflcraft")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("item", ItemArgument.item(context))
                    .executes(ctx -> run(ctx, 1))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 9999))
                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "amount")))
                        .then(Commands.literal("go")
                            .executes(ctx -> craft(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))));
    }

    private static int craft(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        Item target = ItemArgument.getItem(ctx, "item").item().value();
        if (!(level.getBlockState(pos).getBlock() instanceof PanelBlock)) {
            source.sendFailure(Component.literal("No terminal or panel at " + pos.toShortString()));
            return 0;
        }
        ConduitInventorySource network = new ConduitInventorySource();
        network.update(level, pos);

        ServerPlayer player = source.getPlayer();
        Autocrafter.Result result = Autocrafter.craft(level, network, target, amount, overflow -> {
            // Storage full: hand the rest to the player, or drop it at the panel.
            if (player == null || !player.getInventory().add(overflow)) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, overflow);
            }
        });
        ChatFormatting color = result.outcome() == Autocrafter.Outcome.CRAFTED ? ChatFormatting.GREEN : ChatFormatting.RED;
        source.sendSuccess(() -> Component.literal(result.outcome() + ": " + amount + "× ").append(name(target))
            .append(" (limit " + result.limit() + ")").withStyle(color), false);
        return result.outcome() == Autocrafter.Outcome.CRAFTED ? 1 : 0;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        Item target = ItemArgument.getItem(ctx, "item").item().value();

        if (!(level.getBlockState(pos).getBlock() instanceof PanelBlock)) {
            source.sendFailure(Component.literal("No terminal or panel at " + pos.toShortString()));
            return 0;
        }
        ConduitInventorySource network = new ConduitInventorySource();
        network.update(level, pos);

        long start = System.nanoTime();
        CraftingPlan plan = CraftingPlanner.plan(RecipeIndex.get(source.getServer()),
            CraftingPlanner.plainStock(network.getInventories()), target, amount);
        long micros = (System.nanoTime() - start) / 1000;

        ChatFormatting color = plan.canCraft() ? ChatFormatting.GREEN : ChatFormatting.RED;
        source.sendSuccess(() -> Component.literal("Plan: " + amount + "× ").append(name(target))
            .append(" → " + plan.status() + " (" + micros + " µs)").withStyle(color), false);

        if (!plan.steps().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Steps:").withStyle(ChatFormatting.GOLD), false);
            int shown = 0;
            for (CraftingPlan.Step step : plan.steps()) {
                if (shown++ == MAX_LINES) {
                    int rest = plan.steps().size() - MAX_LINES;
                    source.sendSuccess(() -> Component.literal("  … " + rest + " more"), false);
                    break;
                }
                MutableComponent line = Component.literal("  " + step.times() + "× craft → " + step.outputCount() + " ")
                    .append(name(step.recipe().result().getItem()))
                    .append(Component.literal("  [" + step.recipe().id().identifier() + "]").withStyle(ChatFormatting.DARK_GRAY));
                source.sendSuccess(() -> line, false);
            }
        }
        list(source, "From storage:", plan.consumed(), ChatFormatting.AQUA);
        list(source, "Missing:", plan.missing(), ChatFormatting.RED);
        list(source, "Left over:", plan.leftovers(), ChatFormatting.GRAY);
        return plan.canCraft() ? 1 : 0;
    }

    private static void list(CommandSourceStack source, String title, Map<Item, Long> items, ChatFormatting color) {
        if (items.isEmpty()) return;
        source.sendSuccess(() -> Component.literal(title).withStyle(color), false);
        int shown = 0;
        for (Map.Entry<Item, Long> entry : items.entrySet()) {
            if (shown++ == MAX_LINES) {
                int rest = items.size() - MAX_LINES;
                source.sendSuccess(() -> Component.literal("  … " + rest + " more"), false);
                return;
            }
            source.sendSuccess(() -> Component.literal("  " + entry.getValue() + "× ").append(name(entry.getKey())), false);
        }
    }

    private static Component name(Item item) {
        return new ItemStack(item).getHoverName();
    }
}
