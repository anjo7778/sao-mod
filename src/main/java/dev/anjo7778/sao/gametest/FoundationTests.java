package dev.anjo7778.sao.gametest;

import dev.anjo7778.sao.simulation.FloorProgress;
import dev.anjo7778.sao.simulation.FloorLayout;
import dev.anjo7778.sao.world.SaoWorldData;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sao")
@PrefixGameTestTemplate(false)
public final class FoundationTests {
    @GameTest(template = "empty")
    public static void progressionRoundTrip(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var data = new SaoWorldData();
        helper.assertTrue(data.isUnlocked(1) && !data.isUnlocked(2), "New world progression");
        helper.assertFalse(data.recordVictory(2, FloorProgress.Victor.PLAYER), "Locked floor victory");
        helper.assertTrue(data.recordVictory(1, FloorProgress.Victor.NPC_PARTY), "NPC unlock");
        helper.assertTrue(data.isDirty(), "Persistence must be marked dirty");
        var saved = data.save(new CompoundTag(), registries);
        var restored = SaoWorldData.load(saved, registries);
        helper.assertTrue(restored.isUnlocked(2) && !restored.isUnlocked(3), "NBT round trip");
        helper.assertFalse(restored.recordVictory(1, FloorProgress.Victor.PLAYER), "Replay victory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void floorShape(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "floor_one"));
        var settings = access.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
        var random = RandomState.create(settings, access.lookupOrThrow(Registries.NOISE), 42L);
        var density = random.router().initialDensityWithoutJaggedness();
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(0, 0, 0)) > 0, "Solid center");
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(0, -60, 0)) < 0, "Void below");
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(0, 200, 0)) < 0, "Open sky");
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(4100, 0, 0)) < 0, "Void past edge");
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(3000, 0, 3000)) < 0, "Circular corner");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void upperFloorShape(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "upper_floors"));
        var settings = access.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
        var density = RandomState.create(settings, access.lookupOrThrow(Registries.NOISE), 42L).router().initialDensityWithoutJaggedness();
        for (long floor : new long[] {2, 3, 1000, 10000, FloorLayout.MAX_FLOOR}) {
            var c = FloorLayout.center(floor);
            helper.assertTrue(FloorLayout.floorAt(c.x(), c.z()) == floor, "Address round trip");
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(c.x(), 0, c.z())) > 0, "Solid upper floor " + floor);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(c.x(), -60, c.z())) < 0, "Void below");
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(c.x(), 290, c.z())) < 0, "Open sky");
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(c.x()+6000, 0, c.z())) < 0, "Gap outside floor");
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void castleArchitecture(GameTestHelper helper) {
        var access=helper.getLevel().registryAccess();
        for(String name:new String[]{"floor_one_castle","upper_floors_castle"}) {
            var key=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao",name));
            var settings=access.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
            var density=RandomState.create(settings,access.lookupOrThrow(Registries.NOISE),42L).router().finalDensity();
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(384,180,384))>0,"Solid support pillar: "+name);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(384,280,192))>0,"Solid roof rib: "+name);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(0,280,0))<0,"Clear spawn above: "+name);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(576,280,576))<0,"Open daylight court: "+name);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(384,-60,384))<0,"Void preserved: "+name);
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void guardianIdentity(GameTestHelper helper){
        var data=new SaoWorldData();
        var a=new java.util.UUID(1,2);var b=new java.util.UUID(1,3);
        helper.assertTrue(data.beginGuardian(1,a),"Reserve real guardian identity");
        helper.assertFalse(data.beginGuardian(1,b),"Duplicate encounter rejected");
        helper.assertFalse(data.completeGuardian(1,b),"Unrelated mob cannot unlock");
        var restored=SaoWorldData.load(data.save(new CompoundTag(),helper.getLevel().registryAccess()),helper.getLevel().registryAccess());
        helper.assertTrue(a.equals(restored.activeGuardian()),"Active guardian identity saved");
        helper.assertFalse(restored.completeGuardian(2,a),"Wrong floor rejected");
        helper.assertTrue(restored.completeGuardian(1,a),"Guardian victory unlocks exactly one");
        helper.assertFalse(restored.completeGuardian(1,a),"Replay rejected");
        helper.assertTrue(restored.highestUnlocked()==2 && restored.activeGuardian()==null,"Encounter cleared");
        var legacy=new CompoundTag();legacy.putInt("schema",1);legacy.putLong("highest_unlocked",7);legacy.putBoolean("npc_unlocks",true);
        helper.assertTrue(SaoWorldData.load(legacy,helper.getLevel().registryAccess()).highestUnlocked()==7,"Legacy progression preserved");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void capitalTerrain(GameTestHelper helper){
        var access=helper.getLevel().registryAccess();
        for(String name:new String[]{"floor_one_city","upper_floors_city","floor_one_city_detail","upper_floors_city_detail","floor_one_city_templates","upper_floors_city_templates","floor_one_city_templates_v2","upper_floors_city_templates_v2"}){
            var key=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao",name));
            var settings=access.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
            var density=RandomState.create(settings,access.lookupOrThrow(Registries.NOISE),42L).router().finalDensity();
            for(int x:new int[]{0,64,-64,128}){
                helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(x,95,0))>0,"Solid city foundation");
                helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(x,97,0))<0,"Flat city clearance");
                helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(x,280,0))<0,"No overhead roof over city");
            }
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(576,280,384))>0,"Castle outside capital preserved");
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void npcPopulationRoundTrip(GameTestHelper helper){
        var data=new dev.anjo7778.sao.world.NpcPopulationData();
        helper.assertTrue(data.discover(1,42),"First discovered capital");
        helper.assertFalse(data.discover(1,43),"No regeneration of existing identities");
        helper.assertTrue(data.discoveredCapitals()==1&&data.roster(2).isEmpty(),"No future capital allocation");
        var npc=data.roster(1).getFirst();var id=npc.profile().id();
        var actor=new java.util.UUID(1,4);
        var memory=new dev.anjo7778.sao.simulation.AdventurerMind.Memory(new java.util.UUID(4,5),actor,3,dev.anjo7778.sao.simulation.AdventurerMind.EventType.RESCUED);
        helper.assertTrue(data.remember(1,id,memory),"Memory stored");
        helper.assertFalse(data.remember(1,id,memory),"Duplicate refused");
        helper.assertTrue(data.updateNeeds(1,id,new dev.anjo7778.sao.simulation.AdventurerMind.Needs(10,100,100,100,0)),"Needs updated");
        var registries=helper.getLevel().registryAccess();
        var loaded=dev.anjo7778.sao.world.NpcPopulationData.load(data.save(new CompoundTag(),registries),registries);
        helper.assertTrue(loaded.roster(1).equals(data.roster(1)),"Full roster persisted");
        helper.assertTrue(loaded.roster(1).getFirst().relationship(actor).trust()==75,"Social memory survived");
        helper.assertTrue(loaded.roster(1).getFirst().choose()==dev.anjo7778.sao.simulation.AdventurerMind.Action.REST,"Needs survived");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void cityNpcEntity(GameTestHelper helper){
        var level=helper.getLevel();
        var state=dev.anjo7778.sao.simulation.NpcRoster.generate(42,1).getFirst();
        var npc=dev.anjo7778.sao.entity.SaoEntities.CITY_NPC.get().create(level);
        helper.assertTrue(npc!=null,"Entity type registered");
        npc.bind(state);
        helper.assertTrue(npc.getUUID().equals(state.profile().id()),"Entity uses profile identity");
        helper.assertTrue(npc.getCustomName().getString().equals(state.profile().name()),"Entity has persistent profile name");
        helper.assertTrue(npc.getMaxHealth()==20,"Attributes registered");
        helper.assertTrue(!npc.getType().canSerialize(),"Projection does not persist separately");
        npc.discard();
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void reciprocalSocialMemory(GameTestHelper helper){
        var data=new dev.anjo7778.sao.world.NpcPopulationData();
        data.discover(1,42);data.discover(2,42);
        var roster=data.roster(1);
        java.util.UUID first=null,second=null;
        dev.anjo7778.sao.simulation.NpcSocial.Encounter encounter=null;
        outer:for(var a:roster)for(var b:roster){
            var e=data.converse(1,a.profile().id(),b.profile().id(),5);
            if(e.isPresent()){first=a.profile().id();second=b.profile().id();encounter=e.get();break outer;}
        }
        helper.assertTrue(encounter!=null,"A social pair accepted");
        helper.assertTrue(data.state(1,first).memories().contains(encounter.first()),"First memory");
        helper.assertTrue(data.state(1,second).memories().contains(encounter.second()),"Second memory");
        helper.assertTrue(data.converse(1,first,second,5).isEmpty(),"Duplicate rejected");
        helper.assertTrue(data.converse(1,first,data.roster(2).getFirst().profile().id(),5).isEmpty(),"Cross-floor rejected");
        var registries=helper.getLevel().registryAccess();
        var restored=dev.anjo7778.sao.world.NpcPopulationData.load(data.save(new CompoundTag(),registries),registries);
        helper.assertTrue(restored.roster(1).equals(data.roster(1)),"Both perspectives persist");
        helper.assertTrue(restored.converse(1,second,first,5).isEmpty(),"Reload prevents replay");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void individualNightRoutines(GameTestHelper helper){
        var data=new dev.anjo7778.sao.world.NpcPopulationData();
        data.discover(1,42);
        int awake=0,rest=0,dayRest=0;
        for(var s:data.roster(1)){
            if(dev.anjo7778.sao.simulation.NpcDailyPlan.at(s,18000).activity()==dev.anjo7778.sao.simulation.NpcDailyPlan.Activity.REST)rest++;
            else awake++;
            if(dev.anjo7778.sao.simulation.NpcDailyPlan.at(s,6000).activity()==dev.anjo7778.sao.simulation.NpcDailyPlan.Activity.REST)dayRest++;
        }
        helper.assertTrue(awake>0&&rest>0&&dayRest>0,"Not a universal bedtime");
        var r=helper.getLevel().registryAccess();
        var loaded=dev.anjo7778.sao.world.NpcPopulationData.load(data.save(new CompoundTag(),r),r);
        for(var s:data.roster(1)){
            var other=loaded.state(1,s.profile().id());
            helper.assertTrue(dev.anjo7778.sao.simulation.NpcDailyPlan.at(s,18000).equals(dev.anjo7778.sao.simulation.NpcDailyPlan.at(other,18000)),"Routine preserved after serialization");
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void adventureLedgerRoundTrip(GameTestHelper helper){
        var data=new dev.anjo7778.sao.world.AdventureSimulationData();
        var id=new java.util.UUID(8,9);
        var mode=dev.anjo7778.sao.simulation.AdventureProgress.Mode.PRACTICE;
        helper.assertFalse(data.update(1,id,20,mode,true),"Discovery initializes without retroactive reward");
        helper.assertTrue(data.update(1,id,21,mode,true),"Abstract update");
        helper.assertFalse(data.update(1,id,21,mode,true),"Duplicate epoch rejected");
        var ledger=data.ledger(1,id);
        helper.assertTrue(ledger.practice()==1&&ledger.abstractUpdates()==1&&ledger.fatigue()==12,"Practice and fatigue recorded");
        var r=helper.getLevel().registryAccess();
        var restored=dev.anjo7778.sao.world.AdventureSimulationData.load(data.save(new CompoundTag(),r),r);
        helper.assertTrue(ledger.equals(restored.ledger(1,id)),"Progress persists");
        helper.assertFalse(restored.update(1,id,21,mode,true),"Saved epoch prevents replay");
        helper.assertTrue(restored.update(1,id,10000,mode,false),"Single bounded later update");
        helper.assertTrue(restored.ledger(1,id).practice()==2&&restored.ledger(1,id).abstractUpdates()==1,"No backlog reward");
        helper.assertTrue(restored.ledger(2,id)==null,"No unrelated floor allocation");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void legacyAdventureLedger(GameTestHelper helper){
        var id=new java.util.UUID(12,13);
        var data=new dev.anjo7778.sao.world.AdventureSimulationData();
        data.update(1,id,1,dev.anjo7778.sao.simulation.AdventureProgress.Mode.PRACTICE,true);
        data.update(1,id,2,dev.anjo7778.sao.simulation.AdventureProgress.Mode.PRACTICE,true);
        var r=helper.getLevel().registryAccess();
        var n=data.save(new CompoundTag(),r);
        n.putInt("schema",1);
        n.getList("capitals",10).getCompound(0).getList("npcs",10).getCompound(0).remove("trip");
        var loaded=dev.anjo7778.sao.world.AdventureSimulationData.load(n,r);
        var l=loaded.ledger(1,id);
        helper.assertTrue(l.practice()==1&&l.abstractUpdates()==1&&!l.trip().active(),"Old ledger migrates without losing progress");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void surveyKnowledgePersistence(GameTestHelper helper){
        var pop=new dev.anjo7778.sao.world.NpcPopulationData();
        pop.discover(1,42);pop.discover(2,42);
        var roster=pop.roster(1).stream().filter(s->s.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER).toList();
        var first=roster.get(0);var second=roster.stream().filter(s->dev.anjo7778.sao.simulation.NpcSocial.offer(first,s,7).map(e->e.friendly()).orElse(false)).findFirst().orElseThrow();
        var a=first.profile().id();var b=second.profile().id();
        var data=new dev.anjo7778.sao.world.AdventureSimulationData();
        var idle=dev.anjo7778.sao.simulation.AdventureProgress.Mode.IDLE;
        data.update(1,a,10,idle,true);data.update(1,b,10,idle,true);
        var r=helper.getLevel().registryAccess();var tag=data.save(new CompoundTag(),r);
        var list=tag.getList("capitals",10).getCompound(0).getList("npcs",10);
        for(int i=0;i<list.size();i++){
            var entry=list.getCompound(i);
            entry.putIntArray("journal",entry.getUUID("id").equals(a)?new int[]{1,1,0}:new int[]{2,0,0});
        }
        var loaded=dev.anjo7778.sao.world.AdventureSimulationData.load(tag,r);
        helper.assertTrue(loaded.converse(pop,1,a,b,7).isPresent(),"Accepted conversation");
        var ja=loaded.ledger(1,a).journal();var jb=loaded.ledger(1,b).journal();
        helper.assertTrue(ja.surveyed()==1&&ja.observed()==1&&ja.reported()==2,"First firsthand and peer report");
        helper.assertTrue(jb.surveyed()==2&&jb.observed()==0&&jb.reported()==1,"Peer observation not promoted");
        helper.assertTrue(loaded.converse(pop,1,a,b,7).isEmpty(),"No repeat contact");
        helper.assertTrue(loaded.converse(pop,1,a,pop.roster(2).getFirst().profile().id(),7).isEmpty(),"No cross-floor exchange");
        var restored=dev.anjo7778.sao.world.AdventureSimulationData.load(loaded.save(new CompoundTag(),r),r);
        helper.assertTrue(ja.equals(restored.ledger(1,a).journal())&&jb.equals(restored.ledger(1,b).journal()),"Journals persist");
        var schema2=loaded.save(new CompoundTag(),r);schema2.putInt("schema",2);
        var legacy=dev.anjo7778.sao.world.AdventureSimulationData.load(schema2,r);
        helper.assertTrue(legacy.ledger(1,a).journal().equals(dev.anjo7778.sao.simulation.SurveyJournal.EMPTY),"Old save does not invent directional knowledge");
        helper.assertTrue(legacy.ledger(1,a).trip().equals(loaded.ledger(1,a).trip()),"Existing trips retained");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void personalGoalPersistence(GameTestHelper helper){
        var pop=new dev.anjo7778.sao.world.NpcPopulationData();pop.discover(1,42);
        var roster=pop.roster(1).stream().filter(s->s.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER).toList();
        var a=roster.getFirst();var b=roster.stream().filter(s->dev.anjo7778.sao.simulation.NpcSocial.offer(a,s,8).map(e->e.friendly()).orElse(false)).findFirst().orElseThrow();
        var data=new dev.anjo7778.sao.world.AdventureSimulationData();
        var idle=dev.anjo7778.sao.simulation.AdventureProgress.Mode.IDLE;
        data.update(1,a.profile().id(),10,idle,true);data.update(1,b.profile().id(),10,idle,true);
        var r=helper.getLevel().registryAccess();var tag=data.save(new CompoundTag(),r);
        var list=tag.getList("capitals",10).getCompound(0).getList("npcs",10);
        for(int i=0;i<list.size();i++){
            var g=list.getCompound(i).getCompound("goal");g.putString("kind","CONNECT");g.putInt("target",3);g.putLong("deadline",110);
        }
        var loaded=dev.anjo7778.sao.world.AdventureSimulationData.load(tag,r);
        helper.assertTrue(loaded.converse(pop,1,a.profile().id(),b.profile().id(),8,11).isPresent(),"Real accepted friendly contact");
        helper.assertTrue(loaded.ledger(1,a.profile().id()).goal().progress()==1&&loaded.ledger(1,b.profile().id()).goal().progress()==1,"Reciprocal goal credit");
        helper.assertTrue(loaded.converse(pop,1,a.profile().id(),b.profile().id(),8,11).isEmpty(),"Repeated contact refused");
        helper.assertTrue(loaded.ledger(1,a.profile().id()).goal().progress()==1,"No duplicate credit");
        var restored=dev.anjo7778.sao.world.AdventureSimulationData.load(loaded.save(new CompoundTag(),r),r);
        helper.assertTrue(restored.ledger(1,a.profile().id()).equals(loaded.ledger(1,a.profile().id())),"Goal state preserved");
        var prior=loaded.save(new CompoundTag(),r);prior.putInt("schema",3);
        var migrated=dev.anjo7778.sao.world.AdventureSimulationData.load(prior,r);
        helper.assertTrue(!migrated.ledger(1,a.profile().id()).goal().active(),"Old ledger has no invented goal");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void migrationPersistence(GameTestHelper helper){
        var pop=new dev.anjo7778.sao.world.NpcPopulationData();
        pop.discover(1,42);pop.discover(2,42);
        var old=pop.roster(1).stream().filter(s->s.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER).findFirst().orElseThrow();
        var id=old.profile().id();var data=new dev.anjo7778.sao.world.AdventureSimulationData();
        data.update(1,id,1,dev.anjo7778.sao.simulation.AdventureProgress.Mode.IDLE,true);
        data.update(1,id,2,dev.anjo7778.sao.simulation.AdventureProgress.Mode.PRACTICE,true);
        var r=helper.getLevel().registryAccess();var tag=data.save(new CompoundTag(),r);
        var entry=tag.getList("capitals",10).getCompound(0).getList("npcs",10).getCompound(0);
        entry.putIntArray("journal",new int[]{1,1,0});
        data=dev.anjo7778.sao.world.AdventureSimulationData.load(tag,r);
        helper.assertFalse(pop.relocate(1,2,id,25,1),"Locked destination refused");
        helper.assertTrue(pop.relocate(1,2,id,25,2),"Eligible transfer fixture");
        var moved=pop.state(2,id);
        helper.assertTrue(pop.state(1,id)==null&&moved.profile().birthFloor()==1&&moved.profile().currentFloor()==2,"One identity, new residence");
        helper.assertTrue(moved.needs().equals(old.needs())&&moved.memories().equals(old.memories()),"State preserved");
        helper.assertTrue(data.ledger(moved).practice()==1&&data.ledger(moved).journal().surveyed()==0
            &&data.ledger(moved).journalFor(1).observed()==1,"Lifetime credit and scoped journals");
        var restored=dev.anjo7778.sao.world.NpcPopulationData.load(pop.save(new CompoundTag(),r),r);
        var ledgers=dev.anjo7778.sao.world.AdventureSimulationData.load(data.save(new CompoundTag(),r),r);
        helper.assertTrue(restored.state(2,id).equals(moved)&&restored.state(1,id)==null,"Migration saved once");
        helper.assertTrue(ledgers.ledger(restored.state(2,id)).equals(data.ledger(moved)),"Ledger retained at birth key");
        helper.assertFalse(restored.relocate(1,2,id,26,2),"No duplicate migration");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void regionalPopulationPersistence(GameTestHelper helper){
        var pop=new dev.anjo7778.sao.world.NpcPopulationData();
        var market=dev.anjo7778.sao.simulation.RegionalSettlements.Kind.MARKET_TOWN;
        var village=dev.anjo7778.sao.simulation.RegionalSettlements.Kind.CRAFTS_VILLAGE;
        helper.assertTrue(pop.discoverRegional(1,market,42),"Discover market");
        helper.assertFalse(pop.discoverRegional(1,market,42),"No duplicate discovery");
        helper.assertTrue(pop.roster(1).isEmpty()&&pop.discoveredCapitals()==0,"Village does not discover capital");
        pop.discoverRegional(1,village,42);pop.discover(1,42);
        var residents=pop.regionalRoster(1,market);
        helper.assertTrue(residents.size()==8&&pop.regionalRoster(1,village).size()==4&&pop.roster(1).size()==148,"Separate populations");
        var a=residents.getFirst();
        helper.assertTrue(pop.state(1,a.profile().id()).equals(a),"Identity lookup");
        helper.assertTrue(pop.converse(1,a.profile().id(),pop.regionalRoster(1,village).getFirst().profile().id(),1).isEmpty(),"Cross-site exchange refused");
        helper.assertTrue(pop.converse(1,a.profile().id(),pop.roster(1).getFirst().profile().id(),1).isEmpty(),"Cross-capital exchange refused");
        var n=a.needs();
        helper.assertTrue(pop.updateNeeds(1,a.profile().id(),new dev.anjo7778.sao.simulation.AdventurerMind.Needs(90,n.supplies(),n.equipment(),n.partyReadiness(),n.danger())),"Regional needs update");
        boolean talked=false;
        for(var first:residents)for(var second:residents)if(!talked&&pop.converse(1,first.profile().id(),second.profile().id(),2).isPresent())talked=true;
        helper.assertTrue(talked,"Regional reciprocal memory");
        var regs=helper.getLevel().registryAccess();var tag=pop.save(new CompoundTag(),regs);
        var restored=dev.anjo7778.sao.world.NpcPopulationData.load(tag,regs);
        helper.assertTrue(restored.regionalRoster(1,market).equals(pop.regionalRoster(1,market))&&restored.roster(1).equals(pop.roster(1)),"Regional and capital persistence");
        tag.putInt("schema",2);tag.remove("regions");
        var legacy=dev.anjo7778.sao.world.NpcPopulationData.load(tag,regs);
        helper.assertTrue(legacy.regionalRoster(1,market).isEmpty()&&legacy.roster(1).equals(pop.roster(1)),"Legacy population keeps capital without invented residents");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void regionalJourneyPersistence(GameTestHelper helper){
        var pop=new dev.anjo7778.sao.world.NpcPopulationData();pop.discover(1,42);
        var s=pop.roster(1).stream().filter(a->a.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER).findFirst().orElseThrow();
        var id=s.profile().id();var regs=helper.getLevel().registryAccess();
        var original=new dev.anjo7778.sao.world.AdventureSimulationData();
        original.update(1,id,1,dev.anjo7778.sao.simulation.AdventureProgress.Mode.IDLE,true);
        var tag=original.save(new CompoundTag(),regs);
        var group=new CompoundTag();group.putLong("floor",1);
        var entry=new CompoundTag();entry.putUUID("id",id);entry.putString("phase","VISITING");entry.putString("target","CRAFTS_VILLAGE");entry.putString("purpose","EQUIPMENT");
        entry.putLong("since",2);entry.putLong("nextStart",0);entry.putInt("visited",1);
        var list=new net.minecraft.nbt.ListTag();list.add(entry);group.put("npcs",list);
        tag.getList("regionalJourneys",10).add(group);
        var data=dev.anjo7778.sao.world.AdventureSimulationData.load(tag,regs);
        var j=data.journey(s);
        helper.assertTrue(j.visiting()&&j.completed()==1,"Saved journey and prior visit");
        helper.assertTrue(data.localSite(s.profile()).kind()==dev.anjo7778.sao.simulation.RegionalSettlements.Kind.CRAFTS_VILLAGE,"Visitor location");
        var restored=dev.anjo7778.sao.world.AdventureSimulationData.load(data.save(new CompoundTag(),regs),regs);
        helper.assertTrue(restored.journey(s).equals(j)&&restored.ledger(s).equals(data.ledger(s)),"Journey and lifetime ledger round trip");
        tag.putInt("schema",5);tag.remove("regionalJourneys");
        var legacy=dev.anjo7778.sao.world.AdventureSimulationData.load(tag,regs);
        helper.assertTrue(!legacy.journey(s).active()&&legacy.journey(s).completed()==0,"Legacy save has no invented visit");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void marketPersistence(GameTestHelper helper){
        var pop=new dev.anjo7778.sao.world.NpcPopulationData();pop.discover(1,42);pop.discover(2,42);
        var roster=pop.roster(1);
        var buyer=roster.stream().filter(s->s.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER
            &&!dev.anjo7778.sao.simulation.NpcRoutine.resting(s.profile(),6000)).findFirst().orElseThrow();
        var seller=roster.stream().filter(s->s.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.MERCHANT
            &&dev.anjo7778.sao.simulation.NpcDailyPlan.at(s,6000).activity()==dev.anjo7778.sao.simulation.NpcDailyPlan.Activity.WORK).findFirst().orElseThrow();
        var b=buyer.profile();var v=seller.profile();var n=buyer.needs();
        var needs=new dev.anjo7778.sao.simulation.AdventurerMind.Needs(n.health(),10,20,n.partyReadiness(),n.danger());
        pop.updateNeeds(1,b.id(),needs);
        var dest=dev.anjo7778.sao.simulation.NpcDailyPlan.at(seller,6000).destination();
        helper.assertFalse(pop.purchase(1,b.id(),v.id(),null,new dev.anjo7778.sao.simulation.NpcDailyPlan.Point(0,0),6000,1,false),"Wrong location refused");
        helper.assertFalse(pop.purchase(1,b.id(),v.id(),null,dest,23000,1,false),"Closed shop refused");
        helper.assertTrue(pop.purchase(1,b.id(),v.id(),null,dest,6000,2,false),"Supply sale");
        helper.assertTrue(pop.wallet(b).col()==88&&pop.shop(v).remaining()==23&&pop.shop(v).till()==12,"Conserved money and stock");
        helper.assertTrue(pop.state(1,b.id()).needs().supplies()==34&&pop.state(1,b.id()).needs().equipment()==20,"Supplies only");
        helper.assertFalse(pop.purchase(1,b.id(),v.id(),null,dest,6000,2,false),"Duplicate epoch refused");
        var kind=dev.anjo7778.sao.simulation.RegionalSettlements.Kind.CRAFTS_VILLAGE;pop.discoverRegional(1,kind,42);
        var smith=pop.regionalRoster(1,kind).stream().filter(s->s.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.BLACKSMITH).findFirst().orElseThrow();
        long t=0;while(t<24000&&(dev.anjo7778.sao.simulation.NpcRoutine.resting(b,t)||dev.anjo7778.sao.simulation.NpcDailyPlan.at(smith,t).activity()!=dev.anjo7778.sao.simulation.NpcDailyPlan.Activity.WORK))t+=100;
        if(t<24000){
            var sd=dev.anjo7778.sao.simulation.NpcDailyPlan.at(smith,t).destination();
            helper.assertFalse(pop.purchase(1,b.id(),smith.profile().id(),null,sd,t,3,false),"Wrong site refused");
            helper.assertTrue(pop.purchase(1,b.id(),smith.profile().id(),kind,sd,t,3,true),"Regional repair sale");
            helper.assertTrue(pop.wallet(b).col()==70&&pop.wallet(b).observedOrders()==1&&pop.shop(smith.profile()).till()==18&&pop.state(1,b.id()).needs().equipment()==45,"Repair and receipts");
        }
        var regs=helper.getLevel().registryAccess();var tag=pop.save(new CompoundTag(),regs);
        var restored=dev.anjo7778.sao.world.NpcPopulationData.load(tag,regs);
        helper.assertTrue(restored.wallet(b).equals(pop.wallet(b))&&restored.shop(v).equals(pop.shop(v))&&restored.shop(smith.profile()).equals(pop.shop(smith.profile())),"Saved balances and stock");
        helper.assertTrue(restored.state(1,b.id()).equals(pop.state(1,b.id())),"Saved needs");
        helper.assertFalse(restored.purchase(1,b.id(),v.id(),null,dest,6000,2,false),"No replay after reload");
        var w=dev.anjo7778.sao.simulation.AdventurerMarket.NEW_WALLET;
        var market=dev.anjo7778.sao.simulation.AdventurerMarket.Service.SUPPLIES;
        var full=new dev.anjo7778.sao.simulation.AdventurerMarket.Shop(market,market.initialStock());
        helper.assertTrue(dev.anjo7778.sao.simulation.AdventurerMarket.buy(needs,w,full,1,false)==null,"Stock cannot be negative");
        var poor=new dev.anjo7778.sao.simulation.AdventurerMarket.Wallet(8,0,8,0);
        helper.assertTrue(dev.anjo7778.sao.simulation.AdventurerMarket.buy(needs,poor,new dev.anjo7778.sao.simulation.AdventurerMarket.Shop(market,0),9,false)==null,"No credit or free supplies");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void guildEconomyPersistence(GameTestHelper helper){
        var pop=new dev.anjo7778.sao.world.NpcPopulationData();pop.discover(1,42);
        var buyer=pop.roster(1).stream().filter(s->s.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER
            &&!dev.anjo7778.sao.simulation.NpcRoutine.resting(s.profile(),6000)).findFirst().orElseThrow();
        var seller=pop.roster(1).stream().filter(s->s.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.MERCHANT
            &&dev.anjo7778.sao.simulation.NpcDailyPlan.at(s,6000).activity()==dev.anjo7778.sao.simulation.NpcDailyPlan.Activity.WORK).findFirst().orElseThrow();
        var p=buyer.profile();var v=seller.profile();var reg=helper.getLevel().registryAccess();
        helper.assertTrue(pop.settleScouting(1,p.id(),7,8)==10,"A newly completed scout earns a commission, not seven historical ones");
        helper.assertTrue(pop.wallet(p).col()==110&&pop.wallet(p).claimedThrough()==8&&pop.treasury(1).col()==1190,"Treasury funded reward");
        helper.assertTrue(pop.settleScouting(1,p.id(),7,8)==0&&pop.settleScouting(1,p.id(),8,10)==0,"Replay and skipped completions refused");
        helper.assertTrue(pop.state(1,p.id()).needs().equipment()==buyer.needs().equipment()-4,"Only one expedition wear");
        // Seed a consistent exhausted shop fixture: its initial stock was sold, and receipts are available.
        var tag=pop.save(new CompoundTag(),reg);
        var list=tag.getList("capitals",10).getCompound(0).getList("npcs",10);
        for(int i=0;i<list.size();i++)if(list.getCompound(i).getUUID("id").equals(v.id()))list.getCompound(i).putInt("sold",24);
        pop=dev.anjo7778.sao.world.NpcPopulationData.load(tag,reg);
        var n=pop.state(1,p.id()).needs();
        pop.updateNeeds(1,p.id(),new dev.anjo7778.sao.simulation.AdventurerMind.Needs(n.health(),10,n.equipment(),n.partyReadiness(),n.danger()));
        var dest=dev.anjo7778.sao.simulation.NpcDailyPlan.at(seller,6000).destination();
        long before=(long)pop.wallet(p).col()+pop.shop(v).till()+pop.treasury(1).col();
        helper.assertTrue(pop.purchase(1,p.id(),v.id(),null,dest,6000,12,false),"Paid wholesale replenishment and retail sale");
        helper.assertTrue(pop.shop(v).remaining()==5&&pop.shop(v).restocked()==6&&pop.shop(v).sold()==25,"Six reserve units, one consumed");
        helper.assertTrue(pop.treasury(1).supplyReleased()==6&&pop.treasury(1).col()==1238,"Wholesale fee paid to treasury");
        helper.assertTrue(before==(long)pop.wallet(p).col()+pop.shop(v).till()+pop.treasury(1).col(),"Conservation across three balances");
        var restored=dev.anjo7778.sao.world.NpcPopulationData.load(pop.save(new CompoundTag(),reg),reg);
        helper.assertTrue(restored.wallet(p).equals(pop.wallet(p))&&restored.shop(v).equals(pop.shop(v))&&restored.treasury(1).equals(pop.treasury(1)),"Exact economy persistence");
        helper.assertTrue(restored.settleScouting(1,p.id(),7,8)==0,"Saved claim prevents replay");
        helper.assertFalse(restored.purchase(1,p.id(),v.id(),null,dest,6000,12,false),"Saved sale prevents replay");
        var empty=new dev.anjo7778.sao.simulation.AdventurerMarket.Treasury(0,0,120);
        var w=dev.anjo7778.sao.simulation.AdventurerMarket.NEW_WALLET;
        var unpaid=dev.anjo7778.sao.simulation.AdventurerMarket.commission(w,empty,0,1);
        helper.assertTrue(unpaid.paid()==0&&unpaid.wallet().claimedThrough()==1&&unpaid.wallet().col()==100,"No minted Col when empty");
        helper.assertTrue(dev.anjo7778.sao.simulation.AdventurerMarket.commission(unpaid.wallet(),dev.anjo7778.sao.simulation.AdventurerMarket.NEW_TREASURY,0,1)==null,"Unfunded historical completion cannot be replayed");
        var exhausted=new dev.anjo7778.sao.simulation.AdventurerMarket.Treasury(256,0,0);
        var shop=new dev.anjo7778.sao.simulation.AdventurerMarket.Shop(dev.anjo7778.sao.simulation.AdventurerMarket.Service.SUPPLIES,24);
        helper.assertTrue(dev.anjo7778.sao.simulation.AdventurerMarket.restock(shop,exhausted)==null,"No imaginary goods when reserve empty");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void huntContractPersistence(GameTestHelper helper){
        var pop=new dev.anjo7778.sao.world.NpcPopulationData();pop.discover(1,42);
        var buyer=pop.roster(1).stream().filter(dev.anjo7778.sao.simulation.HuntContract::ready).findFirst().orElseThrow();
        var p=buyer.profile();var quarry=new java.util.UUID(51,52);var wrong=new java.util.UUID(53,54);
        helper.assertTrue(pop.openHunt(1,p.id(),5,quarry),"One named quarry");
        helper.assertFalse(pop.openHunt(1,p.id(),5,wrong),"No second contract for same survey");
        helper.assertFalse(pop.finishHunt(1,p.id(),5,wrong),"Wrong monster cannot pay");
        helper.assertFalse(pop.finishHunt(2,p.id(),5,quarry),"Wrong floor cannot pay");
        helper.assertTrue(pop.finishHunt(1,p.id(),5,quarry),"Exact quarry settlement fixture");
        helper.assertTrue(pop.wallet(p).huntKills()==1&&pop.wallet(p).paidHunts()==1&&pop.wallet(p).col()==110&&pop.treasury(1).col()==1190,"Conserved bounty");
        helper.assertTrue(pop.state(1,p.id()).needs().equipment()==buyer.needs().equipment()-2,"Combat wear");
        helper.assertFalse(pop.finishHunt(1,p.id(),5,quarry),"No duplicate death payout");
        helper.assertTrue(pop.openHunt(1,p.id(),9,wrong)&&pop.expireHunt(1,p.id(),9),"New survey may expire");
        helper.assertFalse(pop.finishHunt(1,p.id(),9,wrong),"Expired quarry cannot pay");
        pop.recordHuntHealth(1,p.id(),30);helper.assertTrue(pop.state(1,p.id()).needs().health()==30,"Durable injury");
        var regs=helper.getLevel().registryAccess();var restored=dev.anjo7778.sao.world.NpcPopulationData.load(pop.save(new CompoundTag(),regs),regs);
        helper.assertTrue(restored.wallet(p).equals(pop.wallet(p))&&restored.hunt(p.id()).equals(pop.hunt(p.id()))&&restored.state(1,p.id()).equals(pop.state(1,p.id())),"Contract, wallet and injury persist");
        helper.assertFalse(restored.openHunt(1,p.id(),9,quarry),"Replay refused after reload");
        helper.assertTrue(restored.settleScouting(1,p.id(),0,1)==10&&restored.wallet(p).col()==120,"Separate actual hunt and scouting rewards");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void archerHuntSupplyPersistence(GameTestHelper helper){
        var pop=new dev.anjo7778.sao.world.NpcPopulationData();pop.discover(1,42);
        var archer=pop.roster(1).stream().filter(s->s.profile().combatClass()==dev.anjo7778.sao.simulation.NpcRoster.CombatClass.ARCHER).findFirst().orElseThrow();
        var p=archer.profile();var id=p.id();var quarry=new java.util.UUID(65,66);
        helper.assertTrue(dev.anjo7778.sao.simulation.HuntContract.ready(archer),"Archer eligible");
        helper.assertTrue(pop.openHunt(1,id,4,quarry),"Archer contract");
        helper.assertFalse(pop.spendHuntArrow(1,id,3,quarry),"Wrong survey cannot consume supplies");
        helper.assertFalse(pop.spendHuntArrow(1,id,4,new java.util.UUID(67,68)),"Wrong quarry refused");
        helper.assertTrue(pop.spendHuntArrow(1,id,4,quarry),"One real launch debit fixture");
        helper.assertTrue(pop.state(1,id).needs().supplies()==archer.needs().supplies()-1,"Supply debit");
        var regs=helper.getLevel().registryAccess();var saved=dev.anjo7778.sao.world.NpcPopulationData.load(pop.save(new CompoundTag(),regs),regs);
        helper.assertTrue(saved.hunt(id).equals(pop.hunt(id))&&saved.state(1,id).equals(pop.state(1,id)),"Active ranged contract and cost persist");
        helper.assertTrue(saved.finishHunt(1,id,4,quarry),"Exact archer bounty settlement fixture");
        helper.assertFalse(saved.spendHuntArrow(1,id,4,quarry),"Closed contract cannot shoot");
        var n=saved.state(1,id).needs();
        saved.updateNeeds(1,id,new dev.anjo7778.sao.simulation.AdventurerMind.Needs(n.health(),15,n.equipment(),n.partyReadiness(),n.danger()));
        helper.assertFalse(saved.openHunt(1,id,6,quarry),"Insufficient preparation");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void frenzyBoarRegistration(GameTestHelper helper){
        var boar=dev.anjo7778.sao.entity.SaoEntities.FRENZY_BOAR.get().create(helper.getLevel());
        helper.assertTrue(boar!=null&&boar.getMaxHealth()==24,"Registered monster and health");
        helper.assertTrue(boar.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)==4,"Boar attack attribute");
        helper.assertTrue(dev.anjo7778.sao.item.SaoItems.BOAR_TUSK.get()!=null,"Tusk registered");
        helper.assertFalse(dev.anjo7778.sao.world.BoarSpawns.habitat(1,0,0),"No capital spawn");
        helper.assertTrue(dev.anjo7778.sao.world.BoarSpawns.habitat(1,400,0),"First-floor field");
        helper.assertFalse(dev.anjo7778.sao.world.BoarSpawns.habitat(1,4000,0),"No void-edge spawn");
        var site=dev.anjo7778.sao.simulation.RegionalSettlements.sites(1).getFirst();
        helper.assertFalse(dev.anjo7778.sao.world.BoarSpawns.habitat(1,site.x(),site.z()),"No regional safe-zone spawn");
        helper.assertFalse(dev.anjo7778.sao.world.BoarSpawns.habitat(2,400,0),"Mountain habitat excluded");
        boar.discard();helper.succeed();
    }
    @GameTest(template="empty")
    public static void sharedRpgProgression(GameTestHelper helper){
        var data=new dev.anjo7778.sao.world.CharacterProgressData();
        var id=new java.util.UUID(87,88);
        var player=new dev.anjo7778.sao.world.CharacterProgressData.Owner(dev.anjo7778.sao.world.CharacterProgressData.Kind.PLAYER,id);
        var npc=new dev.anjo7778.sao.world.CharacterProgressData.Owner(dev.anjo7778.sao.world.CharacterProgressData.Kind.NPC,id);
        var combat=dev.anjo7778.sao.simulation.CharacterProgress.Source.COMBAT;
        var craft=dev.anjo7778.sao.simulation.CharacterProgress.Source.CRAFT;
        var sword=dev.anjo7778.sao.simulation.CharacterProgress.Mastery.SWORD;
        var smith=dev.anjo7778.sao.simulation.CharacterProgress.Mastery.BLACKSMITHING;
        var scythe=dev.anjo7778.sao.simulation.CharacterProgress.Mastery.SCYTHE;
        var strength=dev.anjo7778.sao.simulation.CharacterProgress.Attribute.STRENGTH;
        helper.assertTrue(data.state(player).level()==1&&data.state(npc).level()==1,"New identities at level one");
        helper.assertTrue(data.award(player,combat,1,100,sword,50),"Combat fixture");
        helper.assertTrue(data.award(npc,craft,1,100,smith,50),"Noncombat progression fixture");
        helper.assertTrue(data.state(player).level()==2&&data.state(npc).level()==2,"Same character level curve");
        helper.assertTrue(data.state(player).mastery(sword)==2&&data.state(player).mastery(scythe)==1&&data.state(npc).mastery(sword)==1&&data.state(npc).mastery(smith)==2,"Independent masteries");
        helper.assertFalse(data.award(player,combat,1,100,sword,50),"Duplicate event refused");
        helper.assertTrue(data.allocate(player,strength,3)&&data.state(player).attribute(strength)==13&&data.state(player).unspent()==0,"Spend only earned points");
        helper.assertFalse(data.allocate(player,strength,1),"Overspend refused");
        var reg=helper.getLevel().registryAccess();var restored=dev.anjo7778.sao.world.CharacterProgressData.load(data.save(new CompoundTag(),reg),reg);
        helper.assertTrue(restored.state(player).equals(data.state(player))&&restored.state(npc).equals(data.state(npc)),"Both identity kinds persist");
        helper.assertFalse(restored.award(player,combat,1,100,sword,50),"Reload prevents replay");
        helper.assertTrue(restored.award(player,combat,2,200,scythe,50)&&restored.state(player).level()==3&&restored.state(player).mastery(scythe)==2&&restored.state(player).mastery(sword)==2,"New weapon learns independently");
        long previous=0;
        for(long xp:new long[]{0,99,100,299,300,1_000_000,100_000_000_000L,Long.MAX_VALUE}){
            long level=dev.anjo7778.sao.simulation.CharacterProgress.levelForXp(xp);
            helper.assertTrue(level>=previous,"Monotonic overflow-safe level curve");previous=level;
        }
        helper.assertTrue(previous>1000,"No ordinary gameplay level cap");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void rpgDiscoveryCraftPersistence(GameTestHelper helper){
        var data=new dev.anjo7778.sao.world.CharacterProgressData();
        var owner=new dev.anjo7778.sao.world.CharacterProgressData.Owner(dev.anjo7778.sao.world.CharacterProgressData.Kind.PLAYER,new java.util.UUID(100,101));
        var smith=dev.anjo7778.sao.simulation.CharacterProgress.Mastery.BLACKSMITHING;
        helper.assertTrue(data.discover(owner,1,0)&&data.discover(owner,1,1),"Two distinct locations");
        helper.assertFalse(data.discover(owner,1,1),"No rediscovery reward");
        helper.assertFalse(data.discover(owner,0,0),"Invalid floor");
        for(int i=0;i<8;i++)helper.assertTrue(data.craft(owner,1,smith),"Completed craft fixture");
        helper.assertFalse(data.craft(owner,1,smith),"Daily craft budget");
        helper.assertTrue(data.state(owner).xp()==104&&data.state(owner).level()==2&&data.state(owner).masteryExperience(smith)==80,"Combined exploration and craft levels");
        var reg=helper.getLevel().registryAccess();
        var restored=dev.anjo7778.sao.world.CharacterProgressData.load(data.save(new CompoundTag(),reg),reg);
        helper.assertTrue(restored.state(owner).equals(data.state(owner)),"XP persists");
        helper.assertFalse(restored.discover(owner,1,0),"Saved discovery replay protection");
        helper.assertFalse(restored.craft(owner,1,smith),"Saved craft budget");
        helper.assertFalse(restored.craft(owner,0,smith),"No reverse-time craft credit");
        helper.assertTrue(restored.craft(owner,2,smith),"New elapsed day budget");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void believableWeaponCompatibility(GameTestHelper helper){
        var F=dev.anjo7778.sao.simulation.WeaponRules.Family.SWORD;
        var D=dev.anjo7778.sao.simulation.WeaponRules.Family.DAGGER;
        var G=dev.anjo7778.sao.simulation.WeaponRules.Family.GREATSWORD;
        var S=dev.anjo7778.sao.simulation.WeaponRules.Family.SCYTHE;
        var K=dev.anjo7778.sao.simulation.WeaponRules.Family.SICKLE;
        var H=dev.anjo7778.sao.simulation.WeaponRules.Family.SHIELD;
        var P=dev.anjo7778.sao.simulation.WeaponRules.Family.SPEAR;
        var M=dev.anjo7778.sao.simulation.CharacterProgress.Mastery.DUAL_SWORDS;
        helper.assertTrue(dev.anjo7778.sao.simulation.WeaponRules.resolve(F,false,F,false).mastery()==M,"Paired blades separate style");
        helper.assertTrue(dev.anjo7778.sao.simulation.WeaponRules.resolve(F,false,D,false).compatible(),"Sword and dagger");
        helper.assertTrue(dev.anjo7778.sao.simulation.WeaponRules.resolve(K,false,K,false).compatible(),"Small sickles");
        helper.assertFalse(dev.anjo7778.sao.simulation.WeaponRules.resolve(S,false,S,false).compatible(),"No paired full scythes");
        helper.assertFalse(dev.anjo7778.sao.simulation.WeaponRules.resolve(G,false,H,false).compatible(),"Greatsword needs both hands");
        helper.assertFalse(dev.anjo7778.sao.simulation.WeaponRules.resolve(F,false,G,false).compatible(),"Cannot bypass by reversing hands");
        helper.assertTrue(dev.anjo7778.sao.simulation.WeaponRules.resolve(P,false,H,false).compatible(),"Versatile spear and shield");
        helper.assertTrue(dev.anjo7778.sao.simulation.WeaponRules.resolve(S,false,null,true).compatible(),"Solo two-handed scythe");
        var sword=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        var shield=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD);
        helper.assertTrue(dev.anjo7778.sao.world.WeaponItems.classify(sword)==F&&dev.anjo7778.sao.world.WeaponItems.classify(shield)==H,"Actual vanilla items classified");
        helper.assertTrue(dev.anjo7778.sao.world.WeaponItems.resolve(sword,shield).mastery()==dev.anjo7778.sao.simulation.CharacterProgress.Mastery.SWORD_SHIELD,"Actual compatible loadout");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void npcDisplayProgressProjection(GameTestHelper helper){
        var level=helper.getLevel();
        var npc=dev.anjo7778.sao.entity.SaoEntities.CITY_NPC.get().create(level);
        helper.assertTrue(npc!=null&&npc.displayOccupation()==null&&npc.displayLevel()==0,"Unbound NPC has no invented role or level");
        var s=dev.anjo7778.sao.simulation.NpcRoster.generate(42,1).stream().filter(v->v.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER).findFirst().orElseThrow();
        var state=dev.anjo7778.sao.world.CharacterProgressData.get(level.getServer());
        var owner=new dev.anjo7778.sao.world.CharacterProgressData.Owner(dev.anjo7778.sao.world.CharacterProgressData.Kind.NPC,s.profile().id());
        npc.bind(s);
        helper.assertTrue(npc.displayOccupation()==s.profile().occupation()&&npc.displayLevel()==state.state(owner).level(),"Display matches authoritative data");
        npc.discard();helper.succeed();
    }

    @GameTest(template="empty")
    public static void npcMenuReviewTransitions(GameTestHelper helper){
        var s=dev.anjo7778.sao.simulation.NpcRoster.generate(42,1).stream().filter(v->v.profile().occupation()==dev.anjo7778.sao.simulation.NpcRoster.Occupation.ADVENTURER).findFirst().orElseThrow();
        var before=dev.anjo7778.sao.simulation.AdventureProgress.start(1);
        var after=dev.anjo7778.sao.simulation.AdventureProgress.advance(before,2,dev.anjo7778.sao.simulation.AdventureProgress.Mode.PREPARE,false);
        var plan=dev.anjo7778.sao.simulation.NpcDailyPlan.at(s,1000);
        var review=dev.anjo7778.sao.simulation.NpcMenuReview.fromTransition(s,before,after,plan,true);
        helper.assertTrue(review!=null&&review.kind()==dev.anjo7778.sao.simulation.NpcMenuReview.Kind.PREPARE&&review.detail().contains("Supplies "+s.needs().supplies()),"Real preparation review");
        helper.assertTrue(dev.anjo7778.sao.simulation.NpcMenuReview.fromTransition(s,before,after,plan,false)==null,"Offscreen activity is not projected");
        helper.assertTrue(dev.anjo7778.sao.simulation.NpcMenuReview.fromTransition(s,after,after,plan,true)==null,"No replay on identical ledger");
        long now=(1L<<28)-2;int session=review.encode(now);
        helper.assertTrue(dev.anjo7778.sao.simulation.NpcMenuReview.kind(session)==review.kind(),"Synced kind");
        helper.assertTrue(dev.anjo7778.sao.simulation.NpcMenuReview.age(session,now+15)==15,"Time wrap handled");
        helper.assertTrue(dev.anjo7778.sao.simulation.NpcMenuReview.age(session,now+66)==-1,"Session expires");
        helper.assertTrue(dev.anjo7778.sao.simulation.NpcMenuReview.reveal(5)==0&&dev.anjo7778.sao.simulation.NpcMenuReview.reveal(25)==1,"Summon precedes projection");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=180)
    public static void steelRefiningAndMaterialIdentity(GameTestHelper helper){
        var level=helper.getLevel();
        var steel=dev.anjo7778.sao.simulation.MaterialRules.Material.STEEL;
        var iron=dev.anjo7778.sao.simulation.MaterialRules.Material.IRON;
        var mithril=dev.anjo7778.sao.simulation.MaterialRules.Material.MITHRIL;
        var adamantite=dev.anjo7778.sao.simulation.MaterialRules.Material.ADAMANTITE;
        helper.assertTrue(steel.properties.durability()>iron.properties.durability(),"Steel has distinct material properties");
        helper.assertTrue(mithril.properties.mass()<iron.properties.mass()&&adamantite.properties.mass()>iron.properties.mass(),"Material tradeoffs");
        helper.assertTrue(mithril.intendedDeposit(10)&&mithril.intendedDeposit(25)&&!mithril.intendedDeposit(9)&&!mithril.intendedDeposit(26),"Intended natural floor band");
        helper.assertFalse(steel.intendedDeposit(1),"Alloys are never natural ore");
        var standard=dev.anjo7778.sao.simulation.MaterialRules.CraftQuality.STANDARD;
        var common=new dev.anjo7778.sao.simulation.MaterialRules.EquipmentMaterial(steel,dev.anjo7778.sao.simulation.MaterialRules.Rarity.COMMON,standard);
        var unique=new dev.anjo7778.sao.simulation.MaterialRules.EquipmentMaterial(steel,dev.anjo7778.sao.simulation.MaterialRules.Rarity.UNIQUE,standard);
        helper.assertTrue(common.effective().equals(unique.effective()),"Rarity is not a hidden stat multiplier");
        var masterwork=new dev.anjo7778.sao.simulation.MaterialRules.EquipmentMaterial(steel,dev.anjo7778.sao.simulation.MaterialRules.Rarity.COMMON,dev.anjo7778.sao.simulation.MaterialRules.CraftQuality.MASTERWORK);
        helper.assertTrue(masterwork.effective().durability()>common.effective().durability()&&masterwork.effective().mass()==common.effective().mass(),"Craftsmanship separate from rarity and base mass");
        var items=net.minecraft.world.item.Items.IRON_INGOT;
        var carbon=net.minecraft.world.item.Items.CHARCOAL;
        var bloom=dev.anjo7778.sao.item.SaoItems.STEEL_BLOOM.get();
        var ingot=dev.anjo7778.sao.item.SaoItems.STEEL_INGOT.get();
        helper.assertTrue(ingot instanceof dev.anjo7778.sao.item.RefinedMetalItem metal&&metal.metal()==steel,"Native steel material identity");
        var input=net.minecraft.world.item.crafting.CraftingInput.of(2,1,java.util.List.of(new net.minecraft.world.item.ItemStack(items),new net.minecraft.world.item.ItemStack(carbon)));
        var recipe=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,level).orElseThrow();
        var output=recipe.value().assemble(input,level.registryAccess());
        helper.assertTrue(output.is(bloom)&&output.getCount()==1,"One iron plus carbon makes one bloom");
        var single=new net.minecraft.world.item.crafting.SingleRecipeInput(output);
        var blast=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.BLASTING,single,level).orElseThrow();
        helper.assertTrue(blast.value().assemble(single,level.registryAccess()).is(ingot),"Blast furnace recipe resolves actual steel");
        helper.assertFalse(level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,single,level).isPresent(),"Ordinary furnace cannot refine the steel bloom");
        var pos=helper.absolutePos(new net.minecraft.core.BlockPos(0,1,0));
        level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.BLAST_FURNACE.defaultBlockState());
        var furnace=(net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity)level.getBlockEntity(pos);
        helper.assertTrue(furnace!=null,"Actual blast furnace block entity");
        furnace.setItem(0,new net.minecraft.world.item.ItemStack(bloom));
        furnace.setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL));
        helper.runAfterDelay(115,()->{
            helper.assertTrue(furnace.getItem(0).isEmpty()&&furnace.getItem(1).isEmpty(),"Input and fuel consumed");
            helper.assertTrue(furnace.getItem(2).is(ingot)&&furnace.getItem(2).getCount()==1,"Exactly one real finished steel ingot");
            helper.succeed();
        });
    }
}
