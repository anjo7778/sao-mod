package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.CityArchitecture.Style;
import static dev.anjo7778.sao.simulation.BuildingTemplates.*;

public final class BuildingTemplateChecks{
    private record P(int x,int z){}
    public static void main(String[] args){
        int beds=0,fixtures=0,cells=0;
        for(int version=1;version<=LATEST;version++)for(Style style:Style.values()){
            var b=forStyle(style,version);
            int hw=b.halfWidth(),hd=b.halfDepth();
            int front=0;for(int z=-hd;z<=0;z++)if(b.at(0,0,z)=='='){front=z;break;}
            check(front<0,"Front floor "+style);
            check(b.at(0,1,front)=='.'&&b.at(0,2,front)=='.'&&b.at(0,3,front)=='.',"Open entry "+style);
            check(walk(b,3,-3),"NPC destination "+style);
            Set<P> reached=new HashSet<>();ArrayDeque<P> queue=new ArrayDeque<>();
            queue.add(new P(0,front));reached.add(new P(0,front));
            while(!queue.isEmpty()){
                var p=queue.remove();
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                    var next=new P(p.x+d[0],p.z+d[1]);
                    if(walk(b,next.x,next.z)&&reached.add(next))queue.add(next);
                }
            }
            check(reached.contains(new P(3,-3)),"NPC route "+style);
            int heads=0,feet=0,lights=0,stations=0,stairs=0;
            for(int z=-hd;z<=hd;z++)for(int x=-hw;x<=hw;x++){
                char ground=b.at(x,0,z);
                char first=b.at(x,1,z);
                if(walk(b,x,z))check(reached.contains(new P(x,z)),"Inaccessible walkable interior "+style+" "+x+","+z);
                if(ground=='+'||b.at(x,2,z)=='l'||b.at(x,3,z)=='H')lights++;
                if(first=='h'){heads++;check(b.at(x,1,z-1)=='b',"Unpaired head "+style);}
                if(first=='b'){feet++;check(b.at(x,1,z+1)=='h',"Unpaired foot "+style);}
                if("BCFATbhSLJ".indexOf(first)>=0){
                    check(ground=='='||ground=='+'||ground=='i',"Unsupported furnishing "+style);
                    boolean adjacent=false;
                    for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})
                        if(reached.contains(new P(x+d[0],z+d[1])))adjacent=true;
                    // Paired beds and adjacent shelving may be accessible via a shared fixture face.
                    if("hBbS".indexOf(first)<0)check(adjacent,"Unreachable workstation "+style+" "+first+" "+x+","+z);
                    if(first=='F'||first=='A'||first=='C')stations++;
                    fixtures++;
                }
                if(b.at(x,2,z)=='l')check(first=='T',"Floating lantern");
                if(b.at(x,3,z)=='H')check(b.at(x,4,z)=='x',"Unsupported hanging light");
                if(ground!='.'&&(Math.abs(x)==hw-1||Math.abs(z)==hd-1)){
                    for(int y=1;y<b.height();y++){
                        if(b.at(x,y,z)=='r'||b.at(x,y,z)=='k')break;
                        if(z==-hd+1&&Math.abs(x)<=1&&y<=3)continue;
                        check(b.at(x,y,z)!='.',"Unintended wall gap "+style+" "+x+","+y+","+z);
                    }
                }
                if(ground!='.'){
                    boolean cover=false;
                    for(int y=4;y<b.height();y++)if(b.at(x,y,z)=='r'||b.at(x,y,z)=='k'){cover=true;break;}
                    check(cover,"Roof gap "+style+" "+x+","+z);
                }
                for(int y=0;y<b.height();y++){if("<>v^".indexOf(b.at(x,y,z))>=0){stairs++;check(b.at(x,y-1,z)=='r',"Roof stair support");}cells++;}
            }
            check(heads==feet&&lights>=4&&stairs>0,"Bed/light/roof counts "+style);
            if(style==Style.FORGE)check(stations>=4,"Forge equipment");
            if(style==Style.INN)check(heads>=5,"Inn beds");
            if(version==2){
                int min=conservativeLight(b);
                check(min>=8,"Insufficient conservative light "+style+" "+min);
                if(style==Style.INN){for(int x:new int[]{-7,-4,-1,2,5})check(walk(b,x,3),"Inn alcove entry");}
                System.out.printf("PASS: v2 %s conservative block-light bound %d%n",style,min);
            }
            beds+=heads;
            System.out.printf("PASS: %s %dx%dx%d, %d reachable floor cells, %d lights, %d bed pairs%n",style,b.width(),b.height(),b.depth(),reached.size(),lights,heads);
        }
        // Verify every template fits every corresponding stable lot and avoids the plaza.
        int lots=0;
        for(long floor:new long[]{1,2,3,6,7,1000,FloorLayout.MAX_FLOOR}){
            int n=CityPlan.half(floor)/CityPlan.LOT;
            for(int i=-n;i<=n;i++)for(int k=-n;k<=n;k++){
                var p=CityPlan.plot(floor,i,k);if(p==null)continue;
                var b=forPlot(p);
                check(b.halfWidth()<p.width()&&b.halfDepth()<p.depth(),"Template footprint");
                for(int x=-b.halfWidth();x<=b.halfWidth();x++)for(int z=-b.halfDepth();z<=b.halfDepth();z++)
                    check(p.equals(CityPlan.column(floor,p.x()+x,p.z()+z).plot()),"Template clipped by city plan");
                lots++;
            }
        }
        System.out.printf("PASS: %d templates, %,d layer cells, %d fixtures, %d paired beds, %d fitting lots%n",Style.values().length*LATEST,cells,fixtures,beds,lots);
    }
    /** Conservative air-only propagation, deliberately excluding transparent partial blocks. */
    private static int conservativeLight(Blueprint b){
        int w=b.width(),h=b.height(),d=b.depth(),hw=b.halfWidth(),hd=b.halfDepth();
        int[][][] light=new int[h][d][w];
        record V(int x,int y,int z){}
        ArrayDeque<V> q=new ArrayDeque<>();
        for(int y=0;y<h;y++)for(int z=0;z<d;z++)for(int x=0;x<w;x++){
            char c=b.at(x-hw,y,z-hd);
            int val=c=='+'?15:c=='l'||c=='H'?14:0;
            if(val>0){light[y][z][x]=val;q.add(new V(x,y,z));}
        }
        int[][] delta={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while(!q.isEmpty()){
            V p=q.remove();int v=light[p.y][p.z][p.x]-1;if(v<=0)continue;
            for(int[] dir:delta){
                int x=p.x+dir[0],y=p.y+dir[1],z=p.z+dir[2];
                if(x<0||x>=w||y<0||y>=h||z<0||z>=d||b.at(x-hw,y,z-hd)!='.'||light[y][z][x]>=v)continue;
                light[y][z][x]=v;q.add(new V(x,y,z));
            }
        }
        int min=15;
        for(int z=0;z<d;z++)for(int x=0;x<w;x++)if(walk(b,x-hw,z-hd))min=Math.min(min,light[1][z][x]);
        return min;
    }
    private static boolean walk(Blueprint b,int x,int z){
        char floor=b.at(x,0,z);return (floor=='='||floor=='+'||floor=='i')&&b.at(x,1,z)=='.'&&b.at(x,2,z)=='.';
    }
    private static void check(boolean b,String label){if(!b)throw new AssertionError(label);}
}
