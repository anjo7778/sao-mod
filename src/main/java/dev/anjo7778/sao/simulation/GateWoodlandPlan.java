package dev.anjo7778.sao.simulation;

import java.util.List;

/** Two managed oak plots per starter gate; all coordinates are fixed and outside the city.
 * Tree growth itself is vanilla, never simulated by this plan. */
public final class GateWoodlandPlan {
    public record Site(int x,int z,int approachX,int approachZ){}
    private GateWoodlandPlan(){}
    public static List<Site> sites(long floor,ScoutingTrip.Gate gate){
        if(!FloorLayout.supported(floor)||floor>10)return List.of();
        var p=ScoutingTrip.gatePoint(floor,gate,36);
        var c=FloorLayout.center(floor);
        int dx=gate==ScoutingTrip.Gate.EAST?1:gate==ScoutingTrip.Gate.WEST?-1:0;
        int dz=gate==ScoutingTrip.Gate.SOUTH?1:gate==ScoutingTrip.Gate.NORTH?-1:0;
        int x=c.x()+p.x()-dx*6,z=c.z()+p.z()-dz*6;
        return List.of(new Site(x+dz*7,z-dx*7,x+dz*5,z-dx*5),
            new Site(x-dz*7,z+dx*7,x-dz*5,z+dx*5));
    }
    public static boolean isSite(long floor,int x,int z){
        for(var gate:ScoutingTrip.Gate.values())
            for(var site:sites(floor,gate))if(site.x()==x&&site.z()==z)return true;
        return false;
    }
}
