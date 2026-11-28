package dev.anjo7778.sao.simulation;

import java.util.Objects;

/**
 * Shared material balance catalog, independent of equipment rarity, weapon shape
 * and craftsmanship. Multipliers use ordinary iron as the reference (1.0).
 * Floor bands describe intended natural deposit availability, not access,
 * item-use restrictions or already-installed world generation.
 */
public final class MaterialRules {
    private MaterialRules(){}
    public enum Origin { NATURAL_ORE, REFINED_ALLOY, SPECIAL_REWARD }
    public record Properties(double edge,double mass,double durability,double forgeDifficulty){
        public Properties{
            if(!valid(edge)||!valid(mass)||!valid(durability)||!valid(forgeDifficulty))
                throw new IllegalArgumentException("Invalid material properties");
        }
        private static boolean valid(double n){return Double.isFinite(n)&&n>0&&n<=10;}
    }
    public enum Material {
        COPPER(Origin.NATURAL_ORE,1,5,.65,1.08,.58,.60),
        TIN(Origin.NATURAL_ORE,1,8,.35,.88,.40,.60),
        IRON(Origin.NATURAL_ORE,1,10,1,1,1,1),
        GOLD(Origin.NATURAL_ORE,1,15,.50,1.80,.45,.75),
        SILVER(Origin.NATURAL_ORE,3,15,.85,1.25,.75,.90),
        MITHRIL(Origin.NATURAL_ORE,10,25,1.20,.65,1.35,1.70),
        ADAMANTITE(Origin.NATURAL_ORE,20,40,1.48,1.65,1.85,2.30),
        ORICHALCUM(Origin.NATURAL_ORE,35,55,1.62,1.05,2.00,2.75),
        CELESTIUM(Origin.NATURAL_ORE,50,75,1.80,.82,2.20,3.25),
        ASTRALITE(Origin.NATURAL_ORE,75,100,2.00,.92,2.50,4.00),
        BRONZE(Origin.REFINED_ALLOY,0,0,.90,1.10,.82,.85),
        STEEL(Origin.REFINED_ALLOY,0,0,1.15,1.04,1.35,1.25),
        SILVERSTEEL(Origin.REFINED_ALLOY,0,0,1.20,1.12,1.38,1.55),
        MITHRIL_STEEL(Origin.REFINED_ALLOY,0,0,1.38,.78,1.65,2.00),
        DARKSTEEL(Origin.REFINED_ALLOY,0,0,1.45,1.32,1.95,2.30),
        DRAGONSTEEL(Origin.REFINED_ALLOY,0,0,1.85,1.28,2.35,3.80),
        GUARDIAN_CORE(Origin.SPECIAL_REWARD,0,0,1.65,1.10,2.00,3.50),
        DRAGON_CRYSTAL(Origin.SPECIAL_REWARD,0,0,1.40,.90,1.50,3.00);
        public final Origin origin;
        public final int firstFloor,lastFloor;
        public final Properties properties;
        Material(Origin origin,int first,int last,double edge,double mass,double durability,double forgeDifficulty){
            this.origin=origin;firstFloor=first;lastFloor=last;
            if(origin==Origin.NATURAL_ORE?(first<1||last<first||last>100):(first!=0||last!=0))
                throw new IllegalArgumentException("Invalid intended deposit band");
            properties=new Properties(edge,mass,durability,forgeDifficulty);
        }
        public boolean intendedDeposit(long floor){return origin==Origin.NATURAL_ORE&&floor>=firstFloor&&floor<=lastFloor;}
    }
    /** Rarity is about acquisition/identity; it does not silently multiply every stat. */
    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, UNIQUE }
    public enum CraftQuality {
        POOR(.82,.72), STANDARD(1,1), FINE(1.05,1.10), EXCELLENT(1.10,1.20), MASTERWORK(1.15,1.35);
        public final double edge,durability;
        CraftQuality(double edge,double durability){this.edge=edge;this.durability=durability;}
    }
    /** Deterministic, skill-dependent forging quality. No random retry or rarity upgrade. */
    public static CraftQuality steelQuality(long smithMastery){
        if(smithMastery<1)throw new IllegalArgumentException("Mastery must be positive");
        if(smithMastery>=12)return CraftQuality.MASTERWORK;
        if(smithMastery>=6)return CraftQuality.EXCELLENT;
        if(smithMastery>=3)return CraftQuality.FINE;
        return CraftQuality.STANDARD;
    }
    public record EquipmentMaterial(Material material,Rarity rarity,CraftQuality quality){
        public EquipmentMaterial{Objects.requireNonNull(material);Objects.requireNonNull(rarity);Objects.requireNonNull(quality);}
        public Properties effective(){
            var p=material.properties;
            return new Properties(p.edge()*quality.edge,p.mass(),p.durability()*quality.durability,p.forgeDifficulty());
        }
    }
}
