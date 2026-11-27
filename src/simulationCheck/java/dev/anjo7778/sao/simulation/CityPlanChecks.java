package dev.anjo7778.sao.simulation;

import java.util.*;

public final class CityPlanChecks{
    private static void check(boolean b,String label){if(!b)throw new AssertionError(label);}
    public static void main(String[] args){
        for(long f:new long[]{1,2,3,6,7,1000,FloorLayout.MAX_FLOOR}){
            int h=CityPlan.half(f);
            Set<CityPlan.Plot> plots=new HashSet<>();
            EnumSet<CityPlan.Role> roles=EnumSet.noneOf(CityPlan.Role.class);
            for(int x=-h;x<h;x++)for(int z=-h;z<h;z++){
                var c=CityPlan.column(f,x,z);
                check(c.zone()!=CityPlan.Zone.OUTSIDE,"City boundary");
                if(c.plot()!=null){plots.add(c.plot());roles.add(c.plot().role());}
            }
            for(var p:plots){
                var door=CityPlan.column(f,p.x(),p.z()-p.depth());
                check(CityPlan.piece(door,1)==CityPlan.Piece.AIR&&CityPlan.piece(door,3)==CityPlan.Piece.AIR,"Doorway");
                var interior=CityPlan.column(f,p.x(),p.z());
                check(CityPlan.piece(interior,0)==CityPlan.Piece.FLOOR&&CityPlan.piece(interior,2)==CityPlan.Piece.AIR,"Walkable interior");
            }
            check(roles.size()==7,"Missing district role "+f+" "+roles);
            check(CityPlan.column(f,0,0).zone()==CityPlan.Zone.PLAZA,"Central spawn");
            check(CityPlan.piece(CityPlan.column(f,0,0),1)==CityPlan.Piece.AIR,"Open spawn");
            check(CityPlan.column(f,0,h-1).zone()==CityPlan.Zone.ROAD,"Gate");
            check(CityPlan.column(f,30,h-1).zone()==CityPlan.Zone.WALL,"Outer wall");
            check(!CityPlan.inside(f,h,0),"Outside");
            check(CityPlan.blend(f,h,0)==1&&CityPlan.blend(f,h+64,0)==0,"Terrain transition");
            System.out.printf("PASS: floor %d capital %dx%d, %d plots, %d roles%n",f,h*2,h*2,plots.size(),roles.size());
        }
    }
}
