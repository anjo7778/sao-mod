package dev.anjo7778.sao.simulation;

import dev.anjo7778.sao.simulation.NpcRoster.CombatClass;

/** Provisional class-specific physical combat parameters, not RPG levels or enchanted gear. */
public enum HuntStyle {
    SWORDSMAN(20,3.0,4.0,1.00,1.00,false),
    KNIGHT(28,3.0,4.5,.88,.75,false),
    ASSASSIN(14,2.5,3.0,1.10,1.00,false),
    ARCHER(30,12.0,0,1.00,1.00,true);
    public final int interval;
    public final double reach,baseAttack,speed,incoming;
    public final boolean ranged;
    HuntStyle(int interval,double reach,double baseAttack,double speed,double incoming,boolean ranged){
        this.interval=interval;this.reach=reach;this.baseAttack=baseAttack;this.speed=speed;this.incoming=incoming;this.ranged=ranged;
    }
    public static HuntStyle of(CombatClass type){
        return switch(type){
            case SWORDSMAN->SWORDSMAN;case KNIGHT->KNIGHT;case ASSASSIN->ASSASSIN;case ARCHER->ARCHER;
            default->null;
        };
    }
    public static final int ARROW_SUPPLY_COST=1;
    public static final int ARROW_RESERVE=15;
}
