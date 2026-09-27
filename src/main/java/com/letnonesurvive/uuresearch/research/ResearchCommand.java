package com.letnonesurvive.uuresearch.research;

import com.letnonesurvive.uuresearch.UUResearch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * /uuresearch learn|forget &lt;item&gt;, learnall, reset, list. Requires permission level 2.
 */
public final class ResearchCommand {

    private ResearchCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal(UUResearch.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("learn")
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> learn(ctx, item(ctx)))))
                .then(Commands.literal("forget")
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> forget(ctx, item(ctx)))))
                .then(Commands.literal("learnall").executes(ResearchCommand::learnAll))
                .then(Commands.literal("reset").executes(ResearchCommand::reset))
                .then(Commands.literal("list").executes(ResearchCommand::list)));
    }

    private static Item item(CommandContext<CommandSourceStack> ctx) {
        return ItemArgument.getItem(ctx, "item").getItem();
    }

    private static int learn(CommandContext<CommandSourceStack> ctx, Item item) {
        MinecraftServer server = ctx.getSource().getServer();
        if (!UURecipeIndex.hasUURecipe(server.getRecipeManager(), item)) {
            ctx.getSource().sendFailure(Component.translatable("commands.uuresearch.no_recipe", item.getDescription()));
            return 0;
        }
        boolean changed = ResearchKnowledge.get(server).learn(ForgeRegistries.ITEMS.getKey(item));
        ctx.getSource().sendSuccess(Component.translatable(
                changed ? "commands.uuresearch.learn.success" : "commands.uuresearch.learn.already",
                item.getDescription()), true);
        return changed ? 1 : 0;
    }

    private static int forget(CommandContext<CommandSourceStack> ctx, Item item) {
        boolean changed = ResearchKnowledge.get(ctx.getSource().getServer()).forget(ForgeRegistries.ITEMS.getKey(item));
        ctx.getSource().sendSuccess(Component.translatable(
                changed ? "commands.uuresearch.forget.success" : "commands.uuresearch.forget.not_learned",
                item.getDescription()), true);
        return changed ? 1 : 0;
    }

    private static int learnAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        Set<ResourceLocation> all = UURecipeIndex.outputIds(server.getRecipeManager());
        ResearchKnowledge.get(server).learnAll(all);
        ctx.getSource().sendSuccess(Component.translatable("commands.uuresearch.learnall.success", all.size()), true);
        return all.size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ResearchKnowledge.get(ctx.getSource().getServer()).reset();
        ctx.getSource().sendSuccess(Component.translatable("commands.uuresearch.reset.success"), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        Set<ResourceLocation> learned = ResearchKnowledge.get(server).learned();
        int total = UURecipeIndex.outputIds(server.getRecipeManager()).size();
        String names = learned.stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));
        ctx.getSource().sendSuccess(Component.translatable("commands.uuresearch.list", learned.size(), total, names), false);
        return learned.size();
    }
}
