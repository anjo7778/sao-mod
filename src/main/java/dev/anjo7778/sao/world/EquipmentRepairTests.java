package dev.anjo7778.sao.world;

import dev.anjo7778.sao.item.NativeSteelSword;
import dev.anjo7778.sao.item.SaoItems;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.MaterialRules.CraftQuality;
import dev.anjo7778.sao.simulation.NpcRoster.Occupation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sao")
@PrefixGameTestTemplate(false)
public final class EquipmentRepairTests {
    @GameTest(template="empty")
    public static void actualSteelRepairConservation(GameTestHelper helper){
        var pop=new NpcPopulationData();pop.discover(1,42);
        long time=6000;
        var buyer=pop.roster(1).stream().filter(s->HuntContract.melee(s.profile())&&!NpcRoutine.resting(s.profile(),time)).findFirst().orElseThrow().profile();
        var smith=pop.roster(1).stream().filter(s->s.profile().occupation()==Occupation.BLACKSMITH
            &&NpcDailyPlan.at(s,time).activity()==NpcDailyPlan.Activity.WORK).findFirst().orElseThrow().profile();
        var original=NativeSteelSword.forged(CraftQuality.MASTERWORK,smith.id(),smith.name());
        helper.assertTrue(pop.buyGoods(1,buyer.id(),smith.id(),original,56,time,1),"Actual owned masterwork sword");
        int max=original.getMaxDamage(),quarter=max/4,wear=(max+1)/2+quarter;
        for(int i=0;i<wear;i++)helper.assertTrue(pop.wearBlade(buyer.id(),pop.blade(buyer.id())),"Real owned blade wear");
        var worn=pop.blade(buyer.id());
        helper.assertTrue(worn.getDamageValue()==wear&&NativeSteelSword.needsNpcRepair(worn),"Repair threshold");
        var stock=new SimpleContainer(27);
        var steel=new ItemStack(SaoItems.STEEL_INGOT.get(),3);
        helper.assertFalse(NpcEquipmentService.repairFromStock(pop,1,buyer.id(),smith.id(),stock,0,steel,time,2),"Missing steel prevents repair");
        stock.setItem(0,steel.copy());
        helper.assertFalse(NpcEquipmentService.repairFromStock(pop,1,buyer.id(),smith.id(),stock,0,steel,time,1),"Same epoch cannot charge twice");
        helper.assertTrue(NpcEquipmentService.repairFromStock(pop,1,buyer.id(),smith.id(),stock,0,steel,time,2),"One real ingot and Col repair");
        var repaired=pop.blade(buyer.id());
        helper.assertTrue(repaired.getDamageValue()==wear-quarter&&stock.getItem(0).getCount()==2,"Exact durability and steel usage");
        helper.assertTrue(pop.wallet(buyer).col()==28&&pop.goodsReceipts(smith.id())==72,"Col conserved");
        helper.assertFalse(NpcEquipmentService.repairFromStock(pop,1,buyer.id(),smith.id(),stock,0,steel,time,3),"Stale stock snapshot rejected");
        helper.assertFalse(NpcEquipmentService.repairFromStock(pop,1,buyer.id(),smith.id(),stock,0,stock.getItem(0).copy(),time,2),"Repeat charge rejected");
        helper.assertTrue(NpcEquipmentService.repairFromStock(pop,1,buyer.id(),smith.id(),stock,0,stock.getItem(0).copy(),time,3),"Second legitimate repair");
        repaired=pop.blade(buyer.id());
        helper.assertTrue(stock.getItem(0).getCount()==1&&pop.wallet(buyer).col()==12&&pop.goodsReceipts(smith.id())==88,"One steel and one fee per repair");
        helper.assertTrue(!NativeSteelSword.needsNpcRepair(repaired),"Healthy sword not unnecessarily repaired");
        var undamaged=repaired.copy();undamaged.setDamageValue(0);
        helper.assertTrue(ItemStack.matches(undamaged,original),"All original components including maker and quality retained");
        while(!NativeSteelSword.needsNpcRepair(pop.blade(buyer.id())))
            helper.assertTrue(pop.wearBlade(buyer.id(),pop.blade(buyer.id())),"Wear back to threshold");
        helper.assertFalse(NpcEquipmentService.repairFromStock(pop,1,buyer.id(),smith.id(),stock,0,stock.getItem(0).copy(),time,4),"Insufficient Col preserves steel");
        helper.assertTrue(stock.getItem(0).getCount()==1&&pop.wallet(buyer).col()==12,"Rejected transaction changes nothing");
        var regs=helper.getLevel().registryAccess();
        var loaded=NpcPopulationData.load(pop.save(new CompoundTag(),regs),regs);
        helper.assertTrue(ItemStack.matches(loaded.blade(buyer.id()),pop.blade(buyer.id())),"Repaired item persists");
        helper.assertTrue(loaded.wallet(buyer).col()==12&&loaded.goodsReceipts(smith.id())==88,"Repair income and expense persist");
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void paidMaterialDeliveryReplay(GameTestHelper helper){
        var pop=new NpcPopulationData();pop.discover(1,42);
        long time=6000;
        var buyer=pop.roster(1).stream().filter(s->HuntContract.melee(s.profile())&&!NpcRoutine.resting(s.profile(),time)).findFirst().orElseThrow().profile();
        var smith=pop.roster(1).stream().filter(s->s.profile().occupation()==Occupation.BLACKSMITH
            &&NpcDailyPlan.at(s,time).activity()==NpcDailyPlan.Activity.WORK).findFirst().orElseThrow().profile();
        var original=NativeSteelSword.forged(CraftQuality.STANDARD,smith.id(),smith.name());
        helper.assertTrue(pop.buyGoods(1,buyer.id(),smith.id(),original,32,time,1),"Equipment sale funds actual smith");
        var supplies=new NpcSupplyData();var stock=new SimpleContainer(27);
        var cargo=supplies.candidate(buyer.id(),1,smith.id())
            .gather(GateProspects.Resource.IRON,12);
        supplies.store(buyer.id(),cargo);
        var after=NpcForgeWorkshop.copy(stock);
        helper.assertTrue(NpcResourceService.deposit(after,cargo),"Real material transfer fits");
        helper.assertTrue(pop.settleDelivery(supplies,1,buyer.id(),cargo),"Funded delivery accepted");
        NpcForgeWorkshop.apply(stock,after);
        helper.assertTrue(pop.wallet(buyer).col()==92&&pop.supplierBudget(smith.id())==8,"24 Col moved from existing receipts");
        helper.assertTrue(stock.getItem(0).getCount()==12&&supplies.cargo(buyer.id()).size()==0,"Exact material transfer");
        helper.assertFalse(pop.settleDelivery(supplies,1,buyer.id(),cargo),"Old cargo cannot replay");
        var second=supplies.cargo(buyer.id()).gather(GateProspects.Resource.IRON,12);
        supplies.store(buyer.id(),second);
        after=NpcForgeWorkshop.copy(stock);
        helper.assertTrue(NpcResourceService.deposit(after,second),"Second delivery fits");
        helper.assertTrue(pop.settleDelivery(supplies,1,buyer.id(),second),"Partially funded second delivery");
        NpcForgeWorkshop.apply(stock,after);
        helper.assertTrue(stock.getItem(0).getCount()==24&&pop.wallet(buyer).col()==100&&pop.supplierBudget(smith.id())==0,"No currency minted");
        var regs=helper.getLevel().registryAccess();
        var loaded=NpcPopulationData.load(pop.save(new CompoundTag(),regs),regs);
        helper.assertTrue(loaded.wallet(buyer).materialEarned()==32&&loaded.supplierPayments(smith.id())==32,"Balanced income survives reload");
        helper.succeed();
    }
}
