package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.CityArchitecture.*;

public final class CityArchitectureChecks{
    public static void main(String[] args){
        int buildings=0,columns=0,beds=0,roofs=0;
        var styles=EnumSet.noneOf(Style.class);
        for(long floor:new long[]{1,2,3,6,7,1000,FloorLayout.MAX_FLOOR}){
            int h=CityPlan.half(floor);
            Set<CityPlan.Plot> plots=new HashSet<>();
            for(int x=-h;x<h;x++)for(int z=-h;z<h;z++){
                var c=CityPlan.column(floor,x,z);
                if(c.plot()!=null)plots.add(c.plot());
                // All four gate axes remain walkable at ground level.
                if((x==0||z==0)&&Math.max(Math.abs(x),Math.abs(z))>=33){
                    check(piece(c,1)==Piece.AIR&&piece(c,2)==Piece.AIR&&piece(c,3)==Piece.AIR,"Main road obstructed");
                }
                columns++;
            }
            for(var p:plots){
                styles.add(style(p));buildings++;
                var door=CityPlan.column(floor,p.x(),p.z()-p.depth());
                var destination=CityPlan.column(floor,p.x()+3,p.z()-3);
                check(piece(door,1)==Piece.AIR&&piece(door,3)==Piece.AIR,"Door clearance");
                check(piece(destination,0)==Piece.FLOOR&&piece(destination,1)==Piece.AIR&&piece(destination,2)==Piece.AIR,"NPC destination clearance");
                int heads=0,feet=0;
                for(int dx=-p.width();dx<=p.width();dx++)for(int dz=-p.depth();dz<=p.depth();dz++){
                    var c=CityPlan.column(floor,p.x()+dx,p.z()+dz);
                    var bottom=piece(c,1);
                    if(bottom==Piece.BED_HEAD){heads++;check(piece(CityPlan.column(floor,c.x(),c.z()-1),1)==Piece.BED_FOOT,"Bed partner");}
                    if(bottom==Piece.BED_FOOT)feet++;
                    var top=piece(c,roofY(p,dx,dz));
                    check(top==Piece.ROOF||top==Piece.CHIMNEY||top.name().startsWith("ROOF_"),"Roof continuity");
                    if(top.name().startsWith("ROOF_"))roofs++;
                    check(roofY(p,dx,dz)+4<=MAX_HEIGHT,"Height bound");
                }
                check(heads==feet&&heads>=1,"Paired beds");
                beds+=heads;
            }
            var center=CityPlan.column(floor,0,0);
            check(piece(center,0)==Piece.PAVING&&piece(center,1)==Piece.AIR&&piece(center,2)==Piece.AIR,"Spawn clearance");
            for(int i=-5;i<=5;i++){
                var gate=CityPlan.column(floor,h-1,i);
                check(piece(gate,8)==Piece.TRIM&&piece(gate,1)==Piece.AIR,"Gate lintel clearance");
            }
        }
        check(styles.size()==Style.values().length,"Missing architectural style");
        System.out.printf("PASS: %,d detailed city columns, %d buildings, %d bed pairs, %,d sloped roof cells; %d styles%n",columns,buildings,beds,roofs,styles.size());
    }
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
}
