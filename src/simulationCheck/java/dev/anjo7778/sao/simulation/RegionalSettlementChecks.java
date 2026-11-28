package dev.anjo7778.sao.simulation;
import java.util.*;
import static dev.anjo7778.sao.simulation.RegionalSettlements.*;
public final class RegionalSettlementChecks {
    public static void main(String[] args){
        int count=0,placements=0,paths=0;
        // 2,000 address cases, including the minimum radius and world-border-adjacent cells.
        for(long floor=1;floor<=2000;floor++)geometry(floor);
        geometry(FloorLayout.MAX_FLOOR);
        for(long floor:new long[]{1,2,3,6,7,1000,1601,FloorLayout.MAX_FLOOR}){
            for(var s:sites(floor)){
                var buildings=buildings(s);
                var occupied=new HashSet<Long>();
                for(var b:buildings){
                    var m=b.model();
                    for(int x=-m.halfWidth();x<=m.halfWidth();x++)for(int z=-m.halfDepth();z<=m.halfDepth();z++){
                        int px=b.x()+x,pz=b.z()+z;
                        check(s.equals(at(floor,px,pz)),"Clipped site");
                        check(occupied.add(((long)px<<32)^(pz&0xffffffffL)),"Overlapping template");
                        check(publicPiece(s,px,pz,1)==CityArchitecture.Piece.AIR,"Fixture overlaps building");
                    }
                }
                int size=s.half()*2;
                boolean[] clear=new boolean[size*size],seen=new boolean[size*size];
                int minX=s.x()-s.half(),minZ=s.z()-s.half();
                for(int z=0;z<size;z++)for(int x=0;x<size;x++)
                    clear[z*size+x]=clear(s,buildings,minX+x,minZ+z);
                int[] queue=new int[clear.length];int start=s.half()*size+s.half(),head=0,tail=0;
                check(clear[start],"Spawn blocked");seen[start]=true;queue[tail++]=start;
                while(head<tail){
                    int p=queue[head++],x=p%size,z=p/size;
                    for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                        int a=x+d[0],b=z+d[1];if(a<0||b<0||a>=size||b>=size)continue;
                        int i=b*size+a;if(clear[i]&&!seen[i]){seen[i]=true;queue[tail++]=i;}
                    }
                }
                for(var b:buildings){
                    int dest=(b.z()-3-minZ)*size+b.x()+3-minX;
                    int entry=(b.z()-b.model().halfDepth()-minZ)*size+b.x()-minX;
                    check(seen[dest]&&seen[entry],"Unreachable building entry or service");
                }
                for(int i=0;i<size;i++){
                    check(seen[s.half()*size+i]&&seen[i*size+s.half()],"Main road blocked");
                }
                placements+=buildings.size();paths+=tail;count++;
            }
        }
        System.out.printf("PASS: %,d regional geometry cases, %d settlements, %d reused building placements, %,d connected positions%n",2001,count,placements,paths);
    }
    private static void geometry(long f){
        var list=sites(f);check(list.size()==4,"Site count");
        for(var s:list){
            check(s.x()%16==0&&s.z()%16==0&&s.half()%16==0,"Chunk alignment");
            check(Math.max(Math.abs(s.x()),Math.abs(s.z()))-s.half()-BLEND>CityPlan.half(f)+64,"Capital blend overlap");
            check(Math.hypot(Math.abs(s.x())+s.half()+BLEND,Math.abs(s.z())+s.half()+BLEND)<FloorCatalog.radiusFor(f),"Floor edge overlap");
            check(at(f,s.x(),s.z()).equals(s)&&blend(f,s.x(),s.z())==1,"Site selection");
            int h=s.half(),b=h+DECORATION_BUFFER;
            // Half-open, chunk-aligned buffer on all four sides; it does not move the safe boundary.
            check(inDecorationBuffer(f,s.x()-b,s.z())&&inDecorationBuffer(f,s.x()+b-1,s.z()),"West/east buffer gap");
            check(inDecorationBuffer(f,s.x(),s.z()-b)&&inDecorationBuffer(f,s.x(),s.z()+b-1),"North/south buffer gap");
            check(!inDecorationBuffer(f,s.x()-b-1,s.z())&&!inDecorationBuffer(f,s.x()+b,s.z()),"West/east buffer leak");
            check(!inDecorationBuffer(f,s.x(),s.z()-b-1)&&!inDecorationBuffer(f,s.x(),s.z()+b),"North/south buffer leak");
            check(at(f,s.x()+h,s.z())==null&&inDecorationBuffer(f,s.x()+h,s.z()),"Decoration buffer changes site boundary");
            check(at(f,0,0)==null&&blend(f,0,0)==0&&!inDecorationBuffer(f,0,0),"Capital modified");
        }
    }
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
}
