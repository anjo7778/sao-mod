package dev.anjo7778.sao.simulation;

import static dev.anjo7778.sao.simulation.SwordActions.Phase.*;
import static dev.anjo7778.sao.simulation.SwordActions.Defense.*;

public final class SwordActionChecks {
    public static void main(String[] args){
        var a=new SwordActions();
        check(a.attack(0)&&a.stamina()==88&&a.phase()==WINDUP,"Attack intent spends actual stamina");
        check(!a.attack(1)&&!a.claimHit(3),"No spam and no early hit");
        check(a.claimHit(4)&&a.phase()==RECOVERY&&!a.claimHit(4),"Exactly one hit window");
        a.advance(13);check(a.phase()==RECOVERY,"Full recovery");
        a.advance(14);check(a.phase()==IDLE&&a.stamina()==90,"Recovery returns to idle");
        check(a.attack(14),"Next action");
        check(!a.claimHit(20)&&a.phase()==RECOVERY,"Expired hit window cannot replay");
        var b=new SwordActions();
        check(b.guard(0)&&b.stamina()==92,"Guard cost");
        check(b.defend(1,false,false)==NONE,"Back attack cannot be parried");
        check(b.defend(2,true,false)==PARRY,"Early frontal attack parried");
        check(b.attack(2)&&b.stamina()==84&&!b.claimHit(3)&&b.claimHit(4),"Real counter shortens windup");
        var c=new SwordActions();
        check(c.guard(0)&&c.defend(4,true,false)==BLOCK&&c.stamina()==84,"Later block costs stamina");
        check(c.defend(4,true,false)==NONE,"One defense per window");
        var d=new SwordActions();
        check(d.dodge(0)&&d.stamina()==76,"Dodge cost");
        check(d.defend(1,true,false)==NONE,"Standing still does not evade");
        check(d.defend(2,true,true)==EVADE&&d.defend(2,true,true)==NONE,"Only one moving evade");
        var e=new SwordActions();
        check(e.attack(0),"Initial strike");
        e.cancel(1);check(e.stamina()==88&&!e.claimHit(4),"Cancel cannot refund or damage");
        e.advance(14);check(e.phase()==IDLE&&e.stamina()==90,"Canceled strike still recovers");
        var f=new SwordActions();
        for(int i=0;i<4;i++){check(f.dodge(i*18L),"Finite dodge budget");f.advance((i+1)*18L);}
        check(!f.dodge(72)&&f.stamina()>=0&&f.stamina()<SwordActions.DODGE_COST,"Cannot dodge at low stamina");
        int before=f.stamina();
        f.advance(100000);check(f.stamina()==before+2,"No catch-up stamina burst");
        boolean rejected=false;
        try{f.advance(99999);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Reject backward clock");
        System.out.println("PASS: sword action costs, single hit, miss, guard/parry/block, counter, dodge, cancellation and stamina");
        var combo=new SwordActions();
        check(combo.attack(0)&&combo.move()==SwordActions.Move.BASIC&&combo.claimHit(4),"First strike");
        check(combo.confirmHit(4,true)&&combo.combo()==1&&!combo.confirmHit(4,true),"Only one real hit confirmation");
        combo.advance(14);
        check(combo.attack(14)&&combo.move()==SwordActions.Move.FOLLOW_UP&&combo.claimHit(18),"Confirmed follow-up");
        check(combo.confirmHit(18,true)&&combo.combo()==2,"Second real strike");
        combo.advance(28);
        check(combo.attack(28)&&combo.move()==SwordActions.Move.FINISHER&&combo.claimHit(33),"Third strike");
        check(combo.confirmHit(33,true)&&combo.combo()==0,"Finisher clears chain");
        combo.advance(46);
        check(combo.attack(46)&&combo.claimHit(50)&&combo.confirmHit(50,false)&&combo.combo()==0,"Blocked or missed strike cannot advance");
        var expired=new SwordActions();
        check(expired.attack(0)&&expired.claimHit(4)&&expired.confirmHit(4,true),"Chain seed");
        expired.advance(26);check(expired.combo()==0&&expired.attack(26)&&expired.move()==SwordActions.Move.BASIC,"Chain expires");
        var swapped=new SwordActions();
        check(swapped.attack(0)&&swapped.claimHit(4)&&swapped.confirmHit(4,true),"Chain before weapon switch");
        swapped.advance(14);swapped.cancel(14);
        check(swapped.combo()==0&&swapped.attack(14)&&swapped.move()==SwordActions.Move.BASIC,"Weapon switch drops chain");
        var skill=new SwordActions();
        check(!skill.horizontal(0,2)&&skill.stamina()==100,"Mastery gate");
        check(skill.horizontal(0,3)&&skill.move()==SwordActions.Move.HORIZONTAL&&skill.stamina()==74,"Horizontal activation");
        check(skill.skillCooldown(0)==100&&!skill.claimHit(6)&&skill.claimHit(7)&&skill.confirmHit(7,true),"One real skill hit");
        check(skill.combo()==0&&!skill.claimHit(7),"Skill cannot create ordinary chain or duplicate hit");
        skill.advance(23);check(!skill.horizontal(23,3)&&skill.skillCooldown(23)==77,"Independent cooldown");
        skill.advance(100);check(skill.horizontal(100,3),"Skill cooldown completed");
        skill.cancel(101);check(skill.skillCooldown(101)==99&&!skill.claimHit(107),"Cancellation cannot refund skill cooldown");
        var pose=new SwordActions();
        check(pose.horizontal(0,3),"Pose source action");
        int stamp=pose.visualCode(0,0x0ffffffeL);
        check(SwordPose.kind(stamp)==SwordPose.Kind.HORIZONTAL&&SwordPose.age(stamp,0x10000005L)==7,"Packed visual stamp wraps safely");
        check(pose.visualCode(7,0x10000005L)==stamp,"Stamp stable across ticks");
        pose.cancel(8);check(pose.visualCode(8,0x10000006L)==0,"Cancel clears rendered action");
        for(var kind:SwordPose.Kind.values()){
            check(SwordPose.sample(kind,0).weight()==0&&SwordPose.sample(kind,kind.duration).weight()==0,"Smooth pose boundaries");
            for(float age=0;age<kind.duration;age+=.25f){
                var frame=SwordPose.sample(kind,age);
                check(Float.isFinite(frame.x())&&Float.isFinite(frame.y())&&Float.isFinite(frame.z())&&frame.weight()>=0&&frame.weight()<=1,"Finite pose");
            }
        }
        check(SwordPose.sample(SwordPose.Kind.HORIZONTAL,5).y()<0&&SwordPose.sample(SwordPose.Kind.HORIZONTAL,9).y()>0,"Horizontal crosses actual pose axis");
        System.out.println("PASS: confirmed three-hit combos, failure/expiry reset, mastery, cooldown, timestamp and authored poses");
        for(var selected:SwordActions.Skill.values()){
            var action=new SwordActions();
            check(!action.skill(0,selected.mastery-1,selected)&&action.stamina()==100,"Locked skill costs nothing");
            check(action.skill(0,selected.mastery,selected)&&action.move()==selected.move,"Actual mastery unlock");
            check(action.stamina()==100-selected.move.cost,"Skill-specific cost");
            for(int i=0;i<selected.move.hits;i++){
                long tick=selected.move.windup+i*selected.move.spacing;
                check(!action.claimHit(tick-1)&&action.claimHit(tick),"Skill-specific hit window");
                check(!action.claimHit(tick)&&action.claimedDamage()==selected.move.hitDamage(i),"Exactly one claim per strike and correct multiplier");
                check(action.confirmHit(tick,true)&&!action.confirmHit(tick,true)&&!action.claimHit(tick),"Exactly one confirmation per strike");
                check(action.landedHits()==i+1,"Confirmed strike count");
                check(SwordPose.kind(action.visualCode(tick,tick))==SwordPose.Kind.valueOf(selected.name()),"Skill-specific pose");
            }
            check(action.phase()==RECOVERY&&action.combo()==0,"Finished skill enters recovery without granting an ordinary combo");
            action.advance(selected.move.duration());
            check(!action.skill(selected.move.duration(),selected.mastery,selected),"Individual cooldown");
        }
        var shared=new SwordActions();
        check(shared.skill(0,7,SwordActions.Skill.SLANT),"Initial skill");
        shared.advance(18);
        check(!shared.skill(18,7,SwordActions.Skill.VERTICAL)&&shared.skillCooldown(SwordActions.Skill.VERTICAL,18)==12,"Switching skill cannot bypass shared delay");
        shared.advance(30);
        check(shared.skill(30,7,SwordActions.Skill.VERTICAL)&&shared.skillCooldown(SwordActions.Skill.SLANT,30)==60,"Independent timers survive other skill activation");
        var durable=new SwordActions();
        check(durable.skill(100,7,SwordActions.Skill.SONIC_LEAP),"Save during windup");
        var saved=durable.save(102);
        var reload=SwordActions.restore(saved,102);
        check(reload.stamina()==66&&reload.phase()==RECOVERY&&reload.visualCode(102,102)==0,"Reload cancels active pose and retains stamina/recovery");
        check(reload.skillCooldown(SwordActions.Skill.SONIC_LEAP,102)==158&&!reload.claimHit(109),"Reload cannot replay pending strike");
        reload.advance(129);
        check(reload.phase()==IDLE&&!reload.skill(129,7,SwordActions.Skill.SONIC_LEAP),"Reload keeps independent cooldown");
        var elapsed=SwordActions.restore(saved,260);
        check(elapsed.stamina()==100&&elapsed.phase()==IDLE&&elapsed.skillCooldown(SwordActions.Skill.SONIC_LEAP,260)==0,"Elapsed world ticks allow bounded recovery");
        var rollback=SwordActions.restore(saved,98);
        check(rollback.stamina()==66&&rollback.skillCooldown(SwordActions.Skill.SONIC_LEAP,98)==158,"Clock rollback cannot grant free stamina or shorter cooldown");
        boolean invalid=false;
        try{new SwordActions.Saved(101,0,0,0,java.util.List.of(0,0,0,0));}catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"Invalid checkpoint rejected");
        System.out.println("PASS: all six skill gates, distinct timings and poses, shared cooldown, reload recovery and rollback protection");
        var square=new SwordActions();
        var hs=SwordActions.Skill.HORIZONTAL_SQUARE;
        check(square.skill(0,9,hs)&&square.stamina()==48,"Square charges its entire cost at activation");
        check(square.claimHit(8)&&square.confirmHit(8,true)&&square.phase()==WINDUP,"Next strike remains armed");
        check(!square.attack(9)&&!square.guard(9)&&!square.dodge(9),"No ordinary action during sequence");
        check(square.claimHit(22)&&square.confirmHit(22,false),"Miss the second strike");
        check(square.phase()==RECOVERY&&square.landedHits()==1&&square.visualCode(22,22)==0,"Miss aborts remaining animation and damage");
        check(!square.claimHit(36)&&!square.claimHit(50)&&square.skillCooldown(hs,50)==170,"No third/fourth strike or cooldown refund");
        var stale=new SwordActions();
        check(stale.skill(0,11,SwordActions.Skill.VERTICAL_SQUARE)&&stale.claimHit(10),"Unconfirmed strike fixture");
        check(!stale.claimHit(11)&&!stale.confirmHit(11,true)&&stale.phase()==RECOVERY,"Stale confirmation aborts without replay");
        var late=new SwordActions();
        check(late.skill(0,9,hs)&&late.claimHit(8)&&late.confirmHit(8,true),"Missed time window fixture");
        check(!late.claimHit(24)&&late.phase()==RECOVERY&&!late.claimHit(36),"Expired second strike aborts later strikes");
        var interrupted=new SwordActions();
        check(interrupted.skill(0,9,hs)&&interrupted.claimHit(8)&&interrupted.confirmHit(8,true),"Interrupted square fixture");
        interrupted.cancel(16);
        check(!interrupted.claimHit(22)&&interrupted.phase()==RECOVERY&&interrupted.stamina()==48,"Cancel remaining strikes without refund");
        var savedMid=new SwordActions();
        check(savedMid.skill(100,9,hs)&&savedMid.claimHit(108)&&savedMid.confirmHit(108,true),"Save after first strike");
        var restoredMid=SwordActions.restore(savedMid.save(110),110);
        check(restoredMid.phase()==RECOVERY&&restoredMid.stamina()==48&&restoredMid.skillCooldown(hs,110)==210
            &&!restoredMid.claimHit(122),"Reload never replays pending multi-hit attacks");
        for(var selected:new SwordActions.Skill[]{hs,SwordActions.Skill.VERTICAL_SQUARE}){
            double total=0;
            for(int i=0;i<selected.move.hits;i++)total+=selected.move.hitDamage(i);
            check(total>3&&total<4.2&&selected.move.spacing>=13,"Bounded four-strike damage and native recovery interval");
            check(SwordPose.Kind.valueOf(selected.name()).duration==selected.move.duration(),"Pose length follows complete sequence");
        }
        System.out.println("PASS: four separately confirmed strikes, duplicate/late/missed hit rejection, interruption and multi-hit reload");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
