package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.CityPlan.*;

/** Version 1: four deterministic regional hubs, separated from the capital and floor edge.
 * Fixed, tested v2 building templates; all centers and envelopes are chunk aligned.
 * Geography alone, no fake population, shop inventory, travel rewards or automatic discovery.
 */
public final class RegionalSettlements {
    public static final int BLEND=48, TEMPLATE_VERSION=2;
    /** Two chunks of decoration-free biome beyond the built envelope, not a safe-zone extension. */
    public static final int DECORATION_BUFFER=32;
    public enum Kind { MARKET_TOWN, LODGING_VILLAGE, CRAFTS_VILLAGE, EXPEDITION_REFUGE }
    public record Site(Kind kind,int x,int z,int half){
        public boolean contains(int px,int pz){
            return (long)px>=x-half&&(long)px<x+half&&(long)pz>=z-half&&(long)pz<z+half;
        }
        public String name(){return switch(kind){
            case MARKET_TOWN->"Crossroads Market";
            case LODGING_VILLAGE->"Hearthvale";
            case CRAFTS_VILLAGE->"Ironbrook";
            case EXPEDITION_REFUGE->"Wayfarer's Rest";
        };}
    }
    public record Placement(Plot plot,int x,int z){
        public BuildingTemplates.Blueprint model(){return BuildingTemplates.forPlot(plot,TEMPLATE_VERSION);}
        public boolean contains(int px,int pz){return model().covers(px-x,pz-z);}
    }
    private RegionalSettlements(){}
    public static int distance(long floor){
        if(!FloorLayout.supported(floor))throw new IllegalArgumentException("Unsupported floor");
        return Math.max(512,Math.floorDiv(FloorCatalog.radiusFor(floor)/4,16)*16);
    }
    public static Site site(long floor,Kind kind){
        int d=distance(floor);
        return switch(kind){
            case MARKET_TOWN->new Site(kind,d,0,64);
            case LODGING_VILLAGE->new Site(kind,0,-d,48);
            case CRAFTS_VILLAGE->new Site(kind,-d,0,48);
            case EXPEDITION_REFUGE->new Site(kind,0,d,48);
        };
    }
    public static List<Site> sites(long floor){
        if(!FloorLayout.supported(floor))return List.of();
        return Arrays.stream(Kind.values()).map(k->site(floor,k)).toList();
    }
    /** O(1) classification without constructing a list of sites on each density sample. */
    private static Kind nearestKind(int x,int z){
        if(Math.abs((long)x)>=Math.abs((long)z))return x>=0?Kind.MARKET_TOWN:Kind.CRAFTS_VILLAGE;
        return z>=0?Kind.EXPEDITION_REFUGE:Kind.LODGING_VILLAGE;
    }
    public static Site at(long floor,int x,int z){
        if(!FloorLayout.supported(floor))return null;
        var s=site(floor,nearestKind(x,z));return s.contains(x,z)?s:null;
    }
    /** Use only in the versioned biome source. The actual settlement, roads and combat rules are unchanged. */
    public static boolean inDecorationBuffer(long floor,int x,int z){
        if(!FloorLayout.supported(floor))return false;
        var s=site(floor,nearestKind(x,z));
        long half=(long)s.half()+DECORATION_BUFFER;
        long dx=(long)x-s.x(),dz=(long)z-s.z();
        return dx>=-half&&dx<half&&dz>=-half&&dz<half;
    }
    public static double blend(long floor,int x,int z){
        if(!FloorLayout.supported(floor))return 0;
        // Beyond this broad bound avoid allocation and unnecessary floor distance work.
        long r=Math.max(Math.abs((long)x),Math.abs((long)z));
        if(r<400||r>1152)return 0;
        var s=site(floor,nearestKind(x,z));
        double d=Math.max(Math.abs((long)x-s.x()),Math.abs((long)z-s.z()));
        return Math.max(0,Math.min(1,(s.half()+BLEND-d)/(BLEND-8.0)));
    }
    private static Placement building(Site s,int x,int z,Role role){
        // Plot is local to the village. Style selection remains deterministic and independent of
        // world coordinates; no modifications or rotations of the validated template layers.
        return new Placement(new Plot(x,z,17,17,9,role),s.x()+x,s.z()+z);
    }
    public static List<Placement> buildings(Site s){
        List<Placement> out=new ArrayList<>();
        if(s.kind()==Kind.MARKET_TOWN){
            out.add(building(s,-48,-24,Role.HOUSE));out.add(building(s,-20,-24,Role.INN));
            out.add(building(s,20,-24,Role.MARKET));out.add(building(s,48,-24,Role.HOUSE));
            out.add(building(s,-48,24,Role.FORGE));out.add(building(s,-20,24,Role.MARKET));
            out.add(building(s,20,24,Role.CLINIC));out.add(building(s,48,24,Role.HOUSE));
        }else{
            Role first=s.kind()==Kind.CRAFTS_VILLAGE?Role.FORGE:Role.INN;
            Role second=s.kind()==Kind.EXPEDITION_REFUGE?Role.CLINIC:
                s.kind()==Kind.CRAFTS_VILLAGE?Role.MARKET:Role.HOUSE;
            out.add(building(s,-20,-20,first));out.add(building(s,20,-20,second));
            out.add(building(s,-20,20,Role.HOUSE));
            out.add(building(s,20,20,s.kind()==Kind.EXPEDITION_REFUGE?Role.QUEST_HALL:Role.HOUSE));
        }
        return List.copyOf(out);
    }
    public static Placement buildingAt(List<Placement> buildings,int x,int z){
        for(var b:buildings)if(b.contains(x,z))return b;
        return null;
    }
    /** Flat connected lanes and short entry approaches. All obstacles stay on their authored sites. */
    public static CityArchitecture.Piece surface(Site s,List<Placement> buildings,int x,int z){
        int dx=x-s.x(),dz=z-s.z();
        if(Math.abs(dx)<=4||Math.abs(dz)<=4)return CityArchitecture.Piece.ROAD;
        if(Math.abs(dx)<=6||Math.abs(dz)<=6)return CityArchitecture.Piece.PAVING;
        if(Math.abs(dx)<=12&&Math.abs(dz)<=12)return CityArchitecture.Piece.PAVING;
        for(var b:buildings){
            int lx=x-b.x(),lz=z-b.z();
            var m=b.model();
            if(Math.abs(lx)<=2&&lz<=0)return CityArchitecture.Piece.ROAD;
            if(Math.abs(lx)<=m.halfWidth()+2&&Math.abs(lz)<=m.halfDepth()+2)return CityArchitecture.Piece.PAVING;
        }
        if(Math.abs(dx)==s.half()-1||Math.abs(dz)==s.half()-1)return CityArchitecture.Piece.TRIM;
        return CityArchitecture.Piece.GRASS;
    }
    public static CityArchitecture.Piece publicPiece(Site s,int x,int z,int y){
        int ax=Math.abs(x-s.x()),az=Math.abs(z-s.z());
        // Four small supported posts in the central square, out of the main lane.
        if(ax==8&&az==8&&y>0)return y==1?CityArchitecture.Piece.TRIM:
            y<=3?CityArchitecture.Piece.POST:y==4?CityArchitecture.Piece.LIGHT:CityArchitecture.Piece.AIR;
        return CityArchitecture.Piece.AIR;
    }
    public static boolean clear(Site s,List<Placement> buildings,int x,int z){
        if(!s.contains(x,z))return false;
        var b=buildingAt(buildings,x,z);
        if(b==null)return publicPiece(s,x,z,1)==CityArchitecture.Piece.AIR&&publicPiece(s,x,z,2)==CityArchitecture.Piece.AIR;
        return b.model().at(x-b.x(),1,z-b.z())=='.'&&b.model().at(x-b.x(),2,z-b.z())=='.';
    }
}
