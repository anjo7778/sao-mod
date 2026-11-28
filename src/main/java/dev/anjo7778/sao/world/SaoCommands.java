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
            .then(Commands.literal("profile").executes(ctx->{
                var player=ctx.getSource().getPlayerOrException();
                if(!CityRules.hasRegionalSettlements(player.serverLevel())){ctx.getSource().sendFailure(Component.literal("Aincrad regional world required."));return 0;}
                var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,player.getUUID());
                var state=CharacterProgressData.get(player.serverLevel().getServer()).state(owner);
                ctx.getSource().sendSuccess(()->Component.literal("RPG level "+state.level()+" | XP "+state.xpIntoLevel()+"/"+state.xpForNextLevel()+" | "+state.unspent()+" unspent attributes (foundation; XP sources pending)"),false);
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
            .then(Commands.literal("adventurers")
                .then(Commands.argument("floor",LongArgumentType.longArg(1)).executes(ctx->{
                    var server=ctx.getSource().getServer();
                    if(!CityRules.hasCities(server.overworld())){
                        ctx.getSource().sendFailure(Component.literal("Aincrad city world required."));return 0;
                    }
                    long floor=LongArgumentType.getLong(ctx,"floor");
                    var roster=NpcPopulationData.get(server).roster(floor).stream()
                        .filter(s->s.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER).toList();
                    if(roster.isEmpty()){ctx.getSource().sendFailure(Component.literal("No discovered adventurers on this floor."));return 0;}
                    var data=AdventureSimulationData.get(server);
                    long offscreen=0,practice=0,completed=0;int active=0;
                    for(var s:roster){
                        var l=data.ledger(s);
                        if(l!=null){offscreen+=l.abstractUpdates();practice+=l.practice();completed+=l.trip().completed();if(l.trip().active())active++;}
                    }
                    final long total=offscreen,points=practice,trips=completed;final int travelling=active;
                    ctx.getSource().sendSuccess(()->Component.literal("Floor "+floor+": "+roster.size()+" adventurers, "+total+" abstract activity updates, "+points+" practice points; "+travelling+" scouting, "+trips+" completed scouting trips."),false);
                    roster.stream().sorted(java.util.Comparator.comparingInt((dev.anjo7778.sao.simulation.NpcRoster.State s)->{
                        var l=data.ledger(s);return l==null?0:l.practice();
                    }).reversed()).limit(5).forEach(s->{
                        var l=data.ledger(s);
                        ctx.getSource().sendSuccess(()->Component.literal(s.profile().name()+" | "+s.profile().combatClass()+" | born floor "+s.profile().birthFloor()+" | practice rank "+(l==null?1:l.practiceRank())+
                            " | fatigue "+(l==null?0:l.fatigue())+" | trip "+(l==null?"IDLE":l.trip().phase().name())+" | last "+(l==null?"IDLE":l.last().name())+" | goal "+(l==null?"none":dev.anjo7778.sao.simulation.AdventurerGoals.description(l.goal()))),false);
                    });
                    return 1;
                })))
            .then(Commands.literal("recon")
                .then(Commands.argument("floor",LongArgumentType.longArg(1)).executes(ctx->{
                    var server=ctx.getSource().getServer();
                    long floor=LongArgumentType.getLong(ctx,"floor");
                    if(!CityRules.hasCities(server.overworld())||!SaoWorldData.get(server).isUnlocked(floor)){
                        ctx.getSource().sendFailure(Component.literal("Discovered, unlocked Aincrad capital required."));return 0;
                    }
                    var population=NpcPopulationData.get(server);
                    var roster=population.roster(floor);
                    if(roster.isEmpty()){ctx.getSource().sendFailure(Component.literal("No reports from this capital yet."));return 0;}
                    var sim=AdventureSimulationData.get(server);
                    for(var gate:dev.anjo7778.sao.simulation.ScoutingTrip.Gate.values()){
                        int surveyed=0,observed=0,reports=0;
                        int bit=dev.anjo7778.sao.simulation.SurveyJournal.bit(gate);
                        for(var s:roster){
                            var l=sim.ledger(s);
                            if(l==null)continue;
                            var journal=l.journal();
                            if(journal.surveyed(gate))surveyed++;
                            if(journal.observed(gate))observed++;
                            if((journal.reported()&bit)!=0)reports++;
                        }
                        final int own=surveyed,physical=observed,heard=reports;
                        ctx.getSource().sendSuccess(()->Component.literal(gate.name()+" gate | "+own+
                            " personal surveys ("+physical+" physically observed), "+heard+" other adventurers informed"),false);
                    }
                    return 1;
                })))
            .then(Commands.literal("economy").then(Commands.argument("floor",LongArgumentType.longArg(1)).executes(ctx->{
                var server=ctx.getSource().getServer();long floor=LongArgumentType.getLong(ctx,"floor");
                if(!SaoWorldData.get(server).isUnlocked(floor)){ctx.getSource().sendFailure(Component.literal("Unlocked floor required."));return 0;}
                var pop=NpcPopulationData.get(server);
                long purchases=0,observed=0,funds=0,sold=0,till=0,earned=0,hunts=0,paidHunts=0;
                var local=new java.util.ArrayList<>(pop.roster(floor));
                for(var kind:dev.anjo7778.sao.simulation.RegionalSettlements.Kind.values())local.addAll(pop.regionalRoster(floor,kind));
                for(var state:local){
                    var profile=state.profile();
                    if(profile.occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER){
                        var w=pop.wallet(profile);purchases+=w.orders();observed+=w.observedOrders();funds+=w.col();earned+=w.earned();hunts+=w.huntKills();paidHunts+=w.paidHunts();
                    }else if(dev.anjo7778.sao.simulation.AdventurerMarket.forRole(profile.occupation())!=null){
                        var shop=pop.shop(profile);sold+=shop.sold();till+=shop.till();
                    }
                }
                final long a=purchases,b=observed,c=funds,d=sold,e=till,earnedCol=earned,kills=hunts,paidKills=paidHunts;
                var treasury=pop.treasury(floor);
                ctx.getSource().sendSuccess(()->Component.literal("Floor "+floor+" | "+a+" buyer purchases ("+b+" observed) | "+c+" Col in adventurer wallets | "+d+" local shop sales | "+e+" Col in local tills | "+earnedCol+" earned by current adventurers | "+kills+" real hunt kills ("+paidKills+" funded) | guild "+treasury.col()+" Col, wholesale stock "+treasury.reserve(dev.anjo7778.sao.simulation.AdventurerMarket.Service.SUPPLIES)+"/"+treasury.reserve(dev.anjo7778.sao.simulation.AdventurerMarket.Service.REPAIR)+" (supply/repair units)"),false);
                return 1;
            })))
            .then(Commands.literal("journeys")
                .then(Commands.argument("floor",LongArgumentType.longArg(1)).executes(ctx->{
                    var server=ctx.getSource().getServer();long floor=LongArgumentType.getLong(ctx,"floor");
                    if(!SaoWorldData.get(server).isUnlocked(floor)){ctx.getSource().sendFailure(Component.literal("Unlocked floor required."));return 0;}
                    var sim=AdventureSimulationData.get(server);
                    var roster=NpcPopulationData.get(server).roster(floor);
                    int active=0,completed=0;
                    for(var s:roster){
                        var j=sim.journey(s);
                        completed+=j.completed();
                        if(!j.active())continue;
                        active++;
                        ctx.getSource().sendSuccess(()->Component.literal(s.profile().name()+" | "+j.phase()+" | "+j.target()+" | "+j.purpose()+" (abstract travel legs)"),false);
                    }
                    final int a=active,c=completed;
                    ctx.getSource().sendSuccess(()->Component.literal("Floor "+floor+" | "+a+" active regional journeys | "+c+" completed round trips by current adventurers"),false);
                    return 1;
                })))
            .then(Commands.literal("settlements").executes(ctx->{
                var p=ctx.getSource().getPlayerOrException();
                var level=p.serverLevel();
                if(!CityRules.hasRegionalSettlements(level)){ctx.getSource().sendFailure(Component.literal("Regional Aincrad world required."));return 0;}
                long floor=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ());
                var center=dev.anjo7778.sao.simulation.FloorLayout.center(floor);
                for(var s:dev.anjo7778.sao.simulation.RegionalSettlements.sites(floor)){
                    int x=center.x()+s.x(),z=center.z()+s.z();
                    var roster=NpcPopulationData.get(level.getServer()).regionalRoster(floor,s.kind());
                    ctx.getSource().sendSuccess(()->Component.literal(s.name()+" | Floor "+floor+" | x="+x+" z="+z+" | "+s.kind().name()+" | "+(roster.isEmpty()?"unvisited":roster.size()+" saved residents")),false);
                }
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
