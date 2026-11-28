package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.GateProspects.Resource;

/** Finite starter-floor mine ramps. The plan is chunk-independent and contains no world edits. */
public final class GateMinePlan {
    public static final int STEPS=22;
    public record Point(int x,int y,int z){}
    public record Vein(Point point,Resource resource){}
    private GateMinePlan(){}
    public static boolean supported(long floor){return floor>=1&&floor<=10;}
    private static int dx(ScoutingTrip.Gate gate){return gate==ScoutingTrip.Gate.EAST?1:gate==ScoutingTrip.Gate.WEST?-1:0;}
    private static int dz(ScoutingTrip.Gate gate){return gate==ScoutingTrip.Gate.SOUTH?1:gate==ScoutingTrip.Gate.NORTH?-1:0;}
    public static Point point(long floor,ScoutingTrip.Gate gate,int step,int side){
        if(!supported(floor)||step<0||step>=STEPS||side< -2||side>2)throw new IllegalArgumentException("Invalid mine point");
        var g=ScoutingTrip.gatePoint(floor,gate,41);var c=FloorLayout.center(floor);
        int dX=dx(gate),dZ=dz(gate);
        int entryY=CityPlan.GROUND+1+(FloorLayout.surfaceBase(floor)-CityPlan.GROUND)/2;
        return new Point(c.x()+g.x()+dX*step+dZ*side,entryY-step,c.z()+g.z()+dZ*step-dX*side);
    }
    public static List<Point> route(long floor,ScoutingTrip.Gate gate){
        if(!supported(floor))return List.of();
        List<Point> result=new ArrayList<>();
        for(int i=0;i<STEPS;i++)result.add(point(floor,gate,i,0));
        return List.copyOf(result);
    }
    public static List<Vein> veins(long floor,ScoutingTrip.Gate gate){
        if(!supported(floor))return List.of();
        List<Vein> result=new ArrayList<>();
        for(int step=17;step<STEPS;step++)for(int side:new int[]{-2,2}){
            var p=point(floor,gate,step,side);
            result.add(new Vein(new Point(p.x(),p.y()+1,p.z()),
                (step+side/2)%2==0?Resource.IRON:Resource.COAL));
        }
        return List.copyOf(result);
    }
    public static Resource resourceAt(long floor,ScoutingTrip.Gate gate,int x,int y,int z){
        if(!supported(floor))return null;
        for(var v:veins(floor,gate))if(v.point().equals(new Point(x,y,z)))return v.resource();
        return null;
    }
}
