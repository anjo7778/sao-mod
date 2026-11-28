package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.CityPlan.*;

public final class CityFabricChecks {
    public static void main(String[] args){
        int totalPrimary=0,totalExtra=0,totalRoutes=0,totalConnected=0;
        for(long floor:new long[]{1,2,3,6,7,1000,FloorLayout.MAX_FLOOR}){
            int h=half(floor),size=h*2,n=h/LOT;
            Set<Plot> primary=new HashSet<>(),extra=new HashSet<>();
            for(int i=-n;i<=n;i++)for(int k=-n;k<=n;k++){
                Plot p=plot(floor,i,k);if(p!=null)primary.add(p);
                Plot infill=CityFabric.infillPlot(floor,i,k);if(infill!=null)extra.add(infill);
            }
            check(extra.size()*2>=primary.size(),"Density target");
            Set<Plot> all=new HashSet<>(primary);all.addAll(extra);
            for(Plot p:all){
                var b=BuildingTemplates.forPlot(p,CityFabric.TEMPLATE_VERSION);
                for(int x=-b.halfWidth();x<=b.halfWidth();x++)for(int z=-b.halfDepth();z<=b.halfDepth();z++){
                    int px=p.x()+x,pz=p.z()+z;
                    check(inside(floor,px,pz),"Footprint outside city");
                    check(p.equals(CityFabric.buildingAt(floor,column(floor,px,pz))),"Clipped or overlapping exact blueprint "+p);
                    if(extra.contains(p)){
                        check(Math.abs(px)>7&&Math.abs(pz)>7,"Main gate avenue obstructed");
                        check(Math.abs(Math.floorMod(pz,LOT)-LOT/2)>5,"Cross street obstructed");
                        check(Math.abs(px)>PLAZA||Math.abs(pz)>PLAZA,"Plaza overlap");
                    }
                }
            }
            boolean[] clear=new boolean[size*size],seen=new boolean[size*size];
            for(int x=-h;x<h;x++)for(int z=-h;z<h;z++){
                clear[(z+h)*size+x+h]=CityFabric.clearAt(floor,x,z,1)&&CityFabric.clearAt(floor,x,z,2);
                if(Math.abs(x)<=5&&Math.abs(z)>PLAZA||Math.abs(z)<=5&&Math.abs(x)>PLAZA)
                    check(clear[(z+h)*size+x+h],"Gate artery obstructed");
                if(Math.abs(Math.floorMod(z,LOT)-LOT/2)<=2&&Math.abs(x)<h-7&&Math.abs(z)<h-7
                    &&Math.max(Math.abs(x),Math.abs(z))>PLAZA+1)
                    check(clear[(z+h)*size+x+h],"Cross street interrupted");
            }
            int[] queue=new int[size*size];
            int start=h*size+h,head=0,tail=0;seen[start]=true;queue[tail++]=start;
            check(clear[start],"Blocked spawn");
            while(head<tail){
                int cell=queue[head++],x=cell%size,z=cell/size;
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                    int ax=x+d[0],az=z+d[1];if(ax<0||ax>=size||az<0||az>=size)continue;
                    int next=az*size+ax;if(clear[next]&&!seen[next]){seen[next]=true;queue[tail++]=next;}
                }
            }
            for(Plot p:all){
                int dest=(p.z()-3+h)*size+p.x()+3+h;
                check(clear[dest]&&seen[dest],"Unreachable service/home "+p);
                var b=BuildingTemplates.forPlot(p,CityFabric.TEMPLATE_VERSION);
                int entry=(p.z()-b.halfDepth()+h)*size+p.x()+h;
                check(clear[entry]&&seen[entry],"Unreachable entrance "+p);
            }
            for(Plot p:primary){
                var points=NpcDailyPlan.route(floor,new NpcDailyPlan.Point(0,0),new NpcDailyPlan.Point(p.x()+3,p.z()-3));
                for(var v:points){
                    check(inside(floor,v.x(),v.z())&&seen[(v.z()+h)*size+v.x()+h],"Existing waypoint obstructed "+p+" "+v);
                    totalRoutes++;
                }
            }
            for(int[] v:new int[][]{{0,-h},{0,h-1},{-h,0},{h-1,0}})check(seen[(v[1]+h)*size+v[0]+h],"Disconnected gate");
            totalPrimary+=primary.size();totalExtra+=extra.size();totalConnected+=tail;
            System.out.printf("PASS: floor %d compact district %d primary + %d infill houses, %,d connected walkable cells%n",floor,primary.size(),extra.size(),tail);
        }
        System.out.printf("PASS: %d original plots + %d infill, %,d reachable cells, %,d existing route waypoints%n",totalPrimary,totalExtra,totalConnected,totalRoutes);
    }
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
