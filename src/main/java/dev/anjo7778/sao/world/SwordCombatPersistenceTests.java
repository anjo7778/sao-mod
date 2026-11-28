package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.SwordActions;
import dev.anjo7778.sao.simulation.SwordActions.Skill;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("sao")
@PrefixGameTestTemplate(false)
public final class SwordCombatPersistenceTests {
    @GameTest(template="empty")
    public static void identityAndCooldownRoundTrip(GameTestHelper helper){
        var data=new SwordCombatData();
        var id=UUID.randomUUID();
        var player=new SwordCombatData.Key(false,id);
        var npc=new SwordCombatData.Key(true,id);
        var first=new SwordActions();
        helper.assertTrue(first.skill(100,7,Skill.SONIC_LEAP),"Player skill fixture");
        data.store(player,new SwordCombatData.Entry(first.save(103),Skill.SONIC_LEAP));
        var second=new SwordActions();
        helper.assertTrue(second.skill(100,4,Skill.SLANT),"NPC skill fixture");
        data.store(npc,new SwordCombatData.Entry(second.save(103),Skill.SLANT));
        var reg=helper.getLevel().registryAccess();
        var saved=data.save(new CompoundTag(),reg);
        var restored=SwordCombatData.load(saved,reg);
        helper.assertTrue(restored.entry(player).equals(data.entry(player))&&restored.entry(npc).equals(data.entry(npc)),"Separate durable identities and selections");
        var clock=SwordActions.restore(restored.entry(player).clock(),103);
        helper.assertTrue(clock.stamina()==66&&clock.phase()==SwordActions.Phase.RECOVERY
            &&clock.skillCooldown(Skill.SONIC_LEAP,103)==157,"Reload preserves exact paid state");
        helper.assertFalse(clock.claimHit(109),"No replay of a saved windup");
        var rested=SwordActions.restore(restored.entry(player).clock(),300);
        restored.store(player,new SwordCombatData.Entry(rested.save(300),Skill.SONIC_LEAP));
        helper.assertTrue(restored.entry(player)!=null&&restored.entry(player).clock().rested(),"Rested character retains selected non-default skill");
        restored.store(player,new SwordCombatData.Entry(rested.save(300),Skill.HORIZONTAL));
        helper.assertTrue(restored.entry(player)==null&&restored.entry(npc)!=null,"Sparse default state does not erase NPC");
        var list=saved.getList("combatants",10);
        list.add(list.getCompound(0).copy());
        boolean rejected=false;
        try{SwordCombatData.load(saved,reg);}catch(IllegalArgumentException expected){rejected=true;}
        helper.assertTrue(rejected,"Reject duplicated durable identity");
        helper.succeed();
    }
}
