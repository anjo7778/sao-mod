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
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
