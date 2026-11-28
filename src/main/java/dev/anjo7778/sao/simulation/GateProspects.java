package dev.anjo7778.sao.simulation;

import java.util.*;

/** Small, finite, walk-up starter prospects. Stable columns, no chunk loads, no regeneration. */
public final class GateProspects {
    public enum Resource { IRON, COAL, DEADWOOD }
    public record Column(int x,int z,Resource resource){}
    private GateProspects(){}
    public static List<Column> columns(long floor,ScoutingTrip.Gate gate){
        if(!FloorLayout.supported(floor)||floor>10)return List.of();
        var p=ScoutingTrip.gatePoint(floor,gate,36);
        var center=FloorLayout.center(floor);
        boolean alongX=gate==ScoutingTrip.Gate.EAST||gate==ScoutingTrip.Gate.WEST;
        List<Column> out=new ArrayList<>();
        // Leave the actual gate approach and center walking corridor clear.
        for(int side:new int[]{-1,1})for(int along:new int[]{-3,-1,1,3}){
            int x=p.x()+(alongX?along:side*3),z=p.z()+(alongX?side*3:along);
            Resource material=along==-3||along==1?Resource.IRON:along==-1||along==3?Resource.COAL:Resource.COAL;
            out.add(new Column(center.x()+x,center.z()+z,material));
        }
        for(int side:new int[]{-1,1}){
            int x=p.x()+(alongX?0:side*4),z=p.z()+(alongX?side*4:0);
            out.add(new Column(center.x()+x,center.z()+z,Resource.DEADWOOD));
            int a=p.x()+(alongX?2:side*4),b=p.z()+(alongX?side*4:2);
            out.add(new Column(center.x()+a,center.z()+b,Resource.COAL));
        }
        return List.copyOf(out);
    }
    public static Optional<Resource> resourceAt(long floor,ScoutingTrip.Gate gate,int x,int z){
        return columns(floor,gate).stream().filter(c->c.x()==x&&c.z()==z).map(Column::resource).findFirst();
    }
}
