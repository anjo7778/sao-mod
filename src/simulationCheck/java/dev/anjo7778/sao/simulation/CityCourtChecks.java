package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.CityPlan.*;
import static dev.anjo7778.sao.simulation.CityCourts.*;

public final class CityCourtChecks {
    private record P(int x,int z){}
    private static final int[][] DIR={{1,0},{-1,0},{0,1},{0,-1}};
    public static void main(String[] args){
        int checked=0,sites=0,connected=0,waypoints=0;
        for(Kind kind:Kind.values()){
            Set<P> seen=new HashSet<>();
            var q=new ArrayDeque<P>();q.add(new P(0,-HALF_Z));seen.add(q.peek());
            while(!q.isEmpty()){
                var p=q.remove();
                for(var dir:DIR){
                    var a=new P(p.x+dir[0],p.z+dir[1]);
                    if(Math.abs(a.x)>HALF_X||Math.abs(a.z)>HALF_Z||cell(kind,a.x,1,a.z)!='.'||cell(kind,a.x,2,a.z)!='.')continue;
                    if(seen.add(a))q.add(a);
                }
            }
            int fixtures=0,lights=0,roof=0;
            for(int x=-HALF_X;x<=HALF_X;x++)for(int z=-HALF_Z;z<=HALF_Z;z++){
                check(cell(kind,x,0,z)!='.',"Missing ground");
                if(Math.abs(x)==HALF_X||Math.abs(z)==HALF_Z)check(seen.contains(new P(x,z)),"Blocked court perimeter");
                if(cell(kind,x,1,z)=='.'&&cell(kind,x,2,z)=='.')check(seen.contains(new P(x,z)),"Disconnected courtyard floor");
                for(int y=1;y<HEIGHT;y++){
                    char c=cell(kind,x,y,z);checked++;
                    if(c=='.')continue;
                    fixtures++;
                    char below=cell(kind,x,y-1,z);
                    if(c=='r'||c=='u'||c=='w'){check(y==4,"Awning height");roof++;continue;}
                    check(below!='.'&&below!='f'&&below!='l',"Unsupported block "+kind+" "+x+","+y+","+z);
                    if(c=='f')check(below=='g',"Flower soil");
                    if(c=='l'){check(below=='p'||below=='#',"Lantern support");lights++;}
                    if(c=='+' )lights++;
                }
                if("ACTBd".indexOf(cell(kind,x,1,z))>=0){
                    boolean near=false;
                    for(var dir:DIR)if(seen.contains(new P(x+dir[0],z+dir[1])))near=true;
                    check(near,"Inaccessible fixture "+kind+" "+x+","+z);
                }
            }
            if(kind==Kind.MARKET_COURT){
                check(roof==50,"Two complete awnings");
                // Each 5x5 wool panel must have four solid corner posts.
                for(int lo:new int[]{-6,2}){
                    for(int x=lo;x<=lo+4;x++)for(int z=2;z<=6;z++)check("ruw".indexOf(cell(kind,x,4,z))>=0,"Awning gap");
                    for(int x:new int[]{lo,lo+4})for(int z:new int[]{2,6})
                        for(int y=1;y<=3;y++)check(cell(kind,x,y,z)=='p',"Missing canopy support");
                }
            }
            check(lights>=2&&seen.size()>=150,"Insufficient light sources or walking area");
            System.out.printf("PASS: court %s, %d fixtures, %d connected ground positions, %d lights%n",kind,fixtures,seen.size(),lights);
        }
        for(long floor:new long[]{1,2,3,6,7,1000,FloorLayout.MAX_FLOOR}){
            int h=half(floor),n=h/LOT,sz=h*2;
            var local=new ArrayList<Site>();var kinds=EnumSet.noneOf(Kind.class);
            var plots=new ArrayList<Plot>();
            for(int gap=-n;gap<=n;gap++)for(int row=-n;row<=n;row++){
                Plot p=plot(floor,gap,row);if(p!=null)plots.add(p);
                p=CityFabric.infillPlot(floor,gap,row);if(p!=null)plots.add(p);
                var s=site(floor,gap,row);if(s!=null){local.add(s);kinds.add(s.kind());}
            }
            if(floor==1)check(kinds.size()==4,"Missing starting city district kinds");
            for(var s:local)for(int dx=-HALF_X;dx<=HALF_X;dx++)for(int dz=-HALF_Z;dz<=HALF_Z;dz++){
                int x=s.x()+dx,z=s.z()+dz;
                check(s.equals(at(floor,x,z)),"Site split");
                var c=column(floor,x,z);
                check(c.zone()!=Zone.WALL&&c.zone()!=Zone.PLAZA&&c.zone()!=Zone.OUTSIDE,"Court overlaps protected geometry");
                var b=CityFabric.buildingAt(floor,c);
                check(b==null||!BuildingTemplates.forPlot(b,2).covers(x-b.x(),z-b.z()),"Court overlaps building");
                check(Math.abs(x)>7&&Math.abs(z)>7&&Math.abs(Math.floorMod(z,LOT)-LOT/2)>5,"Court obstructs main road");
            }
            boolean[] clear=new boolean[sz*sz],seen=new boolean[sz*sz];
            for(int z=-h;z<h;z++)for(int x=-h;x<h;x++)clear[(z+h)*sz+x+h]=clearAt(floor,x,z,1)&&clearAt(floor,x,z,2);
            int[] q=new int[sz*sz];int head=0,tail=0,origin=h*sz+h;
            check(clear[origin],"Spawn blocked");q[tail++]=origin;seen[origin]=true;
            while(head<tail){
                int p=q[head++],x=p%sz,z=p/sz;
                for(int[] dir:DIR){int a=x+dir[0],b=z+dir[1];if(a<0||a>=sz||b<0||b>=sz)continue;
                    int v=b*sz+a;if(clear[v]&&!seen[v]){seen[v]=true;q[tail++]=v;}}
            }
            for(var p:plots){
                check(seen[(p.z()-3+h)*sz+p.x()+3+h],"Building destination lost");
                // Only primary plots have existing NPC routes.
                if(!p.equals(plot(floor,p.x()/LOT,p.z()/LOT)))continue;
                for(var w:NpcDailyPlan.route(floor,new NpcDailyPlan.Point(0,0),new NpcDailyPlan.Point(p.x()+3,p.z()-3))){
                    check(seen[(w.z()+h)*sz+w.x()+h],"Legacy waypoint obstructed");waypoints++;
                }
            }
            for(var s:local)for(int x=-HALF_X;x<=HALF_X;x++)for(int z=-HALF_Z;z<=HALF_Z;z++){
                if(cell(s.kind(),x,1,z)=='.'&&cell(s.kind(),x,2,z)=='.')
                    check(seen[(s.z()+z+h)*sz+s.x()+x+h],"Court unreachable");
            }
            for(int[] v:new int[][]{{0,-h},{0,h-1},{-h,0},{h-1,0}})check(seen[(v[1]+h)*sz+v[0]+h],"Gate disconnected");
            int shortcuts=0,forgeRoutes=0;
            for(var gate:ScoutingTrip.Gate.values()){
                var from=ScoutingTrip.gatePoint(floor,gate,-16);
                for(var p:plots){
                    if(p.role()!=Role.FORGE||!p.equals(plot(floor,p.x()/LOT,p.z()/LOT)))continue;
                    var to=new NpcDailyPlan.Point(p.x()+3,p.z()-3);
                    var legacy=NpcDailyPlan.route(floor,from,to);
                    var route=CityVenueRoutine.route(floor,from,to,null,true);
                    check(!route.isEmpty()&&route.getLast().equals(to),"Forge route must finish");
                    check(routeLength(from,route)<=routeLength(from,legacy)+.001,"Delivery route longer than legacy");
                    if(!route.equals(legacy))shortcuts++;
                    var prev=from;
                    for(var w:route){
                        check(inside(floor,w.x(),w.z()),"Delivery left capital");
                        check(seen[(w.z()+h)*sz+w.x()+h],"Delivery waypoint not reachable");
                        check(Math.hypot(w.x()-prev.x(),w.z()-prev.z())<=26,"Delivery step too long");
                        prev=w;
                    }
                    forgeRoutes++;
                }
            }
            if(floor==1)check(shortcuts>0,"No starter capital delivery shortcuts");
            System.out.printf("PASS: floor %d, %d forge delivery routes, %d shorter local routes%n",floor,forgeRoutes,shortcuts);
            connected+=tail;sites+=local.size();
            System.out.printf("PASS: floor %d landmark districts, %d public courts, %,d connected positions%n",floor,local.size(),tail);
        }
        check(sites>0,"No public courts");
        System.out.printf("PASS: 4 fixed public-space layouts, %,d cell checks, %d court placements, %,d connected cells, %,d original waypoints%n",checked,sites,connected,waypoints);
    }
    private static double routeLength(NpcDailyPlan.Point from,List<NpcDailyPlan.Point> route){
        double length=0;
        for(var p:route){length+=Math.hypot(p.x()-from.x(),p.z()-from.z());from=p;}
        return length;
    }
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
}
