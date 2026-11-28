package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.GateMinePlan.*;
import static dev.anjo7778.sao.simulation.GateProspects.Resource;

/** Finite crosscut, connected to the original mine without moving its existing ore pockets.
 * The crosscut is flat, one block wide, and remains inside the surveyed wilderness. */
public final class GateMineExtension {
    private GateMineExtension(){}
    public static final int LENGTH=14;
    private static Point point(long floor,ScoutingTrip.Gate gate,int side,int outward){
        var end=GateMinePlan.point(floor,gate,GateMinePlan.STEPS-1,0);
        int dx=gate==ScoutingTrip.Gate.EAST?1:gate==ScoutingTrip.Gate.WEST?-1:0;
        int dz=gate==ScoutingTrip.Gate.SOUTH?1:gate==ScoutingTrip.Gate.NORTH?-1:0;
        return new Point(end.x()+dx*outward+dz*side,end.y(),end.z()+dz*outward-dx*side);
    }
    public static List<Point> crosscut(long floor,ScoutingTrip.Gate gate){
        if(!supported(floor))return List.of();
        var out=new ArrayList<Point>();for(int side=0;side<=LENGTH;side++)out.add(point(floor,gate,side,1));
        return List.copyOf(out);
    }
    public static List<Point> route(long floor,ScoutingTrip.Gate gate){
        if(!supported(floor))return List.of();
        var out=new ArrayList<Point>(GateMinePlan.route(floor,gate));out.addAll(crosscut(floor,gate));return List.copyOf(out);
    }
    public static List<Vein> veins(long floor,ScoutingTrip.Gate gate){
        if(!supported(floor))return List.of();
        var out=new ArrayList<Vein>();
        // Side 2 is occupied by an old starter vein; do not touch it.
        for(int side=4;side<=LENGTH;side+=2)for(int outward:new int[]{0,2}){
            var p=point(floor,gate,side,outward);
            out.add(new Vein(new Point(p.x(),p.y()+1,p.z()),(side/2+outward/2)%2==0?Resource.IRON:Resource.COAL));
        }
        return List.copyOf(out);
    }
    public static Resource resourceAt(long floor,ScoutingTrip.Gate gate,int x,int y,int z){
        for(var vein:veins(floor,gate))if(vein.point().equals(new Point(x,y,z)))return vein.resource();
        return null;
    }
}
