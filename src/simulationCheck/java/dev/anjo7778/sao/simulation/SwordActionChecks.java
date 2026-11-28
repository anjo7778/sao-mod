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
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
