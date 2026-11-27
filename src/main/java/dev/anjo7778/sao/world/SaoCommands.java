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
            .then(Commands.literal("population").executes(ctx->{
                var p=ctx.getSource().getPlayerOrException();
                if(!CityRules.isCity(p.serverLevel(),p.getBlockX(),p.getBlockZ())){
                    ctx.getSource().sendFailure(Component.literal("Enter a generated capital first."));return 0;
                }
                long floor=FloorTravel.currentFloor(p.serverLevel(),p.getBlockX(),p.getBlockZ());
                var data=NpcPopulationData.get(p.serverLevel().getServer());
                var roster=data.roster(floor);
                long adventurers=roster.stream().filter(n->n.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER).count();
                ctx.getSource().sendSuccess(()->Component.literal("Capital "+floor+": "+roster.size()+" saved NPC profiles ("+adventurers+" adventurers; up to 32 nearby projected characters)"),false);
                return 1;
            }))
            .then(Commands.literal("challenge").executes(ctx -> FloorGuardian.challenge(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("abandon").executes(ctx -> FloorGuardian.abandon(ctx.getSource().getPlayerOrException())))
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
