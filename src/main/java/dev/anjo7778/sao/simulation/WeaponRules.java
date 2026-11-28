package dev.anjo7778.sao.simulation;

import static dev.anjo7778.sao.simulation.CharacterProgress.Mastery;
import java.util.Objects;

/** Shared, deterministic physical weapon rules. Values are provisional balance metadata,
 * not active damage/speed modifiers. A style describes compatibility, not learned moves.
 */
public final class WeaponRules {
    public enum Grip { ONE_HANDED, TWO_HANDED, VERSATILE }
    public enum Family {
        SWORD(Grip.ONE_HANDED,Mastery.SWORD,1.8,2.8),
        LONGSWORD(Grip.VERSATILE,Mastery.LONGSWORD,2.7,3.1),
        GREATSWORD(Grip.TWO_HANDED,Mastery.GREATSWORD,4.4,3.6),
        CURVED_SWORD(Grip.ONE_HANDED,Mastery.CURVED_SWORD,1.8,2.8),
        KATANA(Grip.VERSATILE,Mastery.KATANA,2.0,3.0),
        SHORT_KATANA(Grip.ONE_HANDED,Mastery.KATANA,1.2,2.5),
        RAPIER(Grip.ONE_HANDED,Mastery.RAPIER,1.1,3.0),
        SABRE(Grip.ONE_HANDED,Mastery.SABRE,1.5,2.8),
        DAGGER(Grip.ONE_HANDED,Mastery.DAGGER,.6,1.9),
        AXE(Grip.ONE_HANDED,Mastery.AXE,2.3,2.6),
        GREATAXE(Grip.TWO_HANDED,Mastery.GREATAXE,4.8,3.3),
        MACE(Grip.ONE_HANDED,Mastery.MACE,2.7,2.5),
        CLUB(Grip.ONE_HANDED,Mastery.CLUB,1.6,2.5),
        WARHAMMER(Grip.ONE_HANDED,Mastery.WARHAMMER,2.8,2.6),
        GREAT_HAMMER(Grip.TWO_HANDED,Mastery.GREAT_HAMMER,5.4,3.3),
        FLAIL(Grip.ONE_HANDED,Mastery.FLAIL,2.3,3),
        SPEAR(Grip.VERSATILE,Mastery.SPEAR,2.2,3.8),
        PIKE(Grip.TWO_HANDED,Mastery.PIKE,3.8,4.5),
        HALBERD(Grip.TWO_HANDED,Mastery.HALBERD,4,4),
        GLAIVE(Grip.TWO_HANDED,Mastery.GLAIVE,3.5,3.9),
        LANCE(Grip.VERSATILE,Mastery.LANCE,3.2,4),
        TRIDENT(Grip.VERSATILE,Mastery.TRIDENT,2.9,3.5),
        SCYTHE(Grip.TWO_HANDED,Mastery.SCYTHE,3.8,3.7),
        SICKLE(Grip.ONE_HANDED,Mastery.SICKLE,.8,2.1),
        BOW(Grip.TWO_HANDED,Mastery.BOW,1.3,0),
        CROSSBOW(Grip.TWO_HANDED,Mastery.CROSSBOW,3.1,0),
        THROWN(Grip.ONE_HANDED,Mastery.THROWN,.8,0),
        GAUNTLET(Grip.ONE_HANDED,Mastery.GAUNTLET,1,1.7),
        CLAW(Grip.ONE_HANDED,Mastery.CLAW,.8,1.8),
        WHIP(Grip.ONE_HANDED,Mastery.WHIP,1.1,4),
        CHAIN(Grip.TWO_HANDED,Mastery.CHAIN,2.8,4),
        CHAKRAM(Grip.ONE_HANDED,Mastery.CHAKRAM,.7,0),
        SHIELD(Grip.ONE_HANDED,Mastery.SHIELD,3.2,1.8),
        TOWER_SHIELD(Grip.ONE_HANDED,Mastery.SHIELD,6,1.8),
        STAFF(Grip.TWO_HANDED,Mastery.STAFF,2.2,3),
        WAND(Grip.ONE_HANDED,Mastery.WAND,.4,0),
        SPELLBOOK(Grip.ONE_HANDED,Mastery.SPELLBOOK,1.5,0),
        ORB(Grip.ONE_HANDED,Mastery.ORB,.9,0);
        public final Grip grip;
        public final Mastery mastery;
        public final double weight,reach;
        Family(Grip grip,Mastery mastery,double weight,double reach){this.grip=grip;this.mastery=mastery;this.weight=weight;this.reach=reach;}
        public boolean shield(){return this==SHIELD||this==TOWER_SHIELD;}
        public boolean sword(){return this==SWORD||this==CURVED_SWORD||this==SABRE||this==RAPIER;}
        public boolean katana(){return this==KATANA||this==SHORT_KATANA;}
    }
    public enum Style { UNARMED, SINGLE, TWO_HANDED, SHIELD, DUAL, INCOMPATIBLE, OTHER }
    public record Loadout(Style style,Mastery mastery){
        public Loadout{Objects.requireNonNull(style);}
        public boolean compatible(){return style!=Style.INCOMPATIBLE;}
    }
    private WeaponRules(){}
    /** Empty hands are distinct from unknown nonweapon items. A two-hand action
     * cannot use a second equipped weapon; a versatile weapon can use one hand.
     * Explicit pairings only. Mere equipping does not earn dual-wield mastery.
     */
    public static Loadout resolve(Family main,boolean mainEmpty,Family off,boolean offEmpty){
        if(main!=null&&mainEmpty||off!=null&&offEmpty)throw new IllegalArgumentException("Weapon in empty hand");
        if(main==null){
            if(mainEmpty&&offEmpty)return new Loadout(Style.UNARMED,Mastery.UNARMED);
            return new Loadout(Style.OTHER,null);
        }
        if(main.grip==Grip.TWO_HANDED){
            return offEmpty?new Loadout(Style.TWO_HANDED,main.mastery):new Loadout(Style.INCOMPATIBLE,null);
        }
        if(offEmpty)return new Loadout(main.grip==Grip.VERSATILE?Style.TWO_HANDED:Style.SINGLE,main.mastery);
        if(off==null)return new Loadout(Style.SINGLE,main.mastery);
        if(main.shield()||off.grip==Grip.TWO_HANDED)return new Loadout(Style.INCOMPATIBLE,null);
        if(off.shield()){
            return new Loadout(Style.SHIELD,main.sword()||main==Family.LONGSWORD||main.katana()?Mastery.SWORD_SHIELD:main.mastery);
        }
        Mastery paired=null;
        if(main.sword()&&off.sword())paired=Mastery.DUAL_SWORDS;
        else if(main.sword()&&off==Family.DAGGER||main==Family.DAGGER&&off.sword())paired=Mastery.SWORD_DAGGER;
        else if(main==Family.DAGGER&&off==Family.DAGGER)paired=Mastery.DUAL_DAGGERS;
        else if(main==Family.AXE&&off==Family.AXE)paired=Mastery.DUAL_AXES;
        else if(main==Family.SICKLE&&off==Family.SICKLE)paired=Mastery.DUAL_SICKLES;
        else if(main==Family.SHORT_KATANA&&off==Family.SHORT_KATANA)paired=Mastery.TWIN_KATANA;
        return paired==null?new Loadout(Style.INCOMPATIBLE,null):new Loadout(Style.DUAL,paired);
    }
}
