package dev.anjo7778.sao.world;

import com.mojang.brigadier.arguments.LongArgumentType;
import dev.anjo7778.sao.simulation.FloorCatalog;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SaoCommands {
    private SaoCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sao")
            .then(Commands.literal("progress").executes(ctx -> {
                long unlocked = SaoWorldData.get(ctx.getSource().getServer()).highestUnlocked();
                ctx.getSource().sendSuccess(() -> Component.literal("SAO: highest unlocked floor " + unlocked), false);
                return 1;
            }))
            .then(Commands.literal("travel")
                .then(Commands.argument("number", LongArgumentType.longArg(1)).executes(ctx ->
                    FloorTravel.request(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "number")))))
            .then(Commands.literal("floor")
                .then(Commands.argument("number", LongArgumentType.longArg(1)).executes(ctx -> {
                    long n = LongArgumentType.getLong(ctx, "number");
                    var world = ctx.getSource().getServer().overworld();
                    var floor = new FloorCatalog(world.getSeed()).describe(n);
                    boolean unlocked = SaoWorldData.get(ctx.getSource().getServer()).isUnlocked(n);
                    ctx.getSource().sendSuccess(() -> Component.literal("Floor " + n + " | " + floor.theme()
                        + " | radius " + floor.radiusBlocks() + " | " + (unlocked ? "unlocked" : "locked")
                        + " (catalog description)"), false);
                    return 1;
                }))));
    }
}
