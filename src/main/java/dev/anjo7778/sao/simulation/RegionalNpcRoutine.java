package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.NpcDailyPlan.*;
import static dev.anjo7778.sao.simulation.NpcRoster.*;

/** Geometry-only local resident scheduling. Actual navigation still checks loaded world blocks. */
public final class RegionalNpcRoutine {
    private RegionalNpcRoutine(){}
    public static Plan plan(State state,long time,RegionalSettlements.Site site){
        var p=state.profile();
        Point home=new Point(p.homeX()+3,p.homeZ()-3);
        if(state.needs().health()<35||NpcRoutine.resting(p,time))return new Plan(Activity.REST,home);
        long day=Math.floorMod(time,24000L);
        boolean nightService=(p.occupation()==Occupation.INNKEEPER||p.occupation()==Occupation.HEALER)
            &&NpcRoutine.habit(p)==NpcRoutine.Habit.NOCTURNAL;
        if(nightService?NpcRoutine.night(time):day>=1000&&day<11000)
            return new Plan(Activity.WORK,new Point(p.workX()+3,p.workZ()-3));
        int hash=p.id().hashCode();
        return new Plan(Activity.SOCIALIZE,new Point(site.x()+Math.floorMod(hash,7)-3,site.z()+Math.floorMod(hash>>>8,7)-3));
    }
    // Only four fixed template layouts; cache an immutable walkable grid per kind.
    private record Grid(int size,boolean[] clear){}
    private static final Map<RegionalSettlements.Kind,Grid> GRIDS=buildGrids();
    private static Map<RegionalSettlements.Kind,Grid> buildGrids(){
        var result=new EnumMap<RegionalSettlements.Kind,Grid>(RegionalSettlements.Kind.class);
        for(var kind:RegionalSettlements.Kind.values()){
            var s=RegionalSettlements.site(1,kind);var buildings=RegionalSettlements.buildings(s);int n=2*s.half();
            boolean[] clear=new boolean[n*n];
            for(int z=0;z<n;z++)for(int x=0;x<n;x++)
                clear[z*n+x]=RegionalSettlements.clear(s,buildings,s.x()-s.half()+x,s.z()-s.half()+z);
            result.put(kind,new Grid(n,clear));
        }
        return Collections.unmodifiableMap(result);
    }
    private static int index(RegionalSettlements.Site s,Point p,int n){
        return (p.z()-s.z()+s.half())*n+p.x()-s.x()+s.half();
    }
    private static Point point(RegionalSettlements.Site s,int i,int n){
        return new Point(s.x()-s.half()+i%n,s.z()-s.half()+i/n);
    }
    /** Bounded BFS over fixed local templates. Waypoints turn at corners or every 10 blocks. */
    public static List<Point> route(RegionalSettlements.Site s,Point from,Point to){
        if(!s.contains(from.x(),from.z())||!s.contains(to.x(),to.z())||from.equals(to))return List.of();
        Grid g=GRIDS.get(s.kind());int n=g.size();
        int start=index(s,from,n),goal=index(s,to,n);
        if(!g.clear()[goal])return List.of();
        // An actor can briefly stand on furniture or an altered block: route from a clear neighbor.
        if(!g.clear()[start]){
            int sx=start%n,sz=start/n;
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                int x=sx+d[0],z=sz+d[1];
                if(x>=0&&x<n&&z>=0&&z<n&&g.clear()[z*n+x]){start=z*n+x;break;}
            }
            if(!g.clear()[start])return List.of();
        }
        int[] prev=new int[n*n],queue=new int[n*n];Arrays.fill(prev,-1);
        int head=0,tail=0;prev[start]=start;queue[tail++]=start;
        int[] dx={1,-1,0,0},dz={0,0,1,-1};
        while(head<tail&&prev[goal]<0){
            int v=queue[head++],x=v%n,z=v/n;
            for(int k=0;k<4;k++){
                int a=x+dx[k],b=z+dz[k];
                if(a<0||a>=n||b<0||b>=n)continue;
                int next=b*n+a;
                if(prev[next]<0&&g.clear()[next]){prev[next]=v;queue[tail++]=next;}
            }
        }
        if(prev[goal]<0)return List.of();
        var reverse=new ArrayList<Integer>();for(int v=goal;v!=start;v=prev[v])reverse.add(v);
        Collections.reverse(reverse);
        var out=new ArrayList<Point>();if(start!=index(s,from,n))out.add(point(s,start,n));
        int last=start,dir=0,run=0;
        for(int v:reverse){
            int step=v-last;
            if(run>0&&(step!=dir||run>=10)){out.add(point(s,last,n));run=0;}
            dir=step;run++;last=v;
        }
        if(out.isEmpty()||!out.getLast().equals(to))out.add(to);
        return List.copyOf(out);
    }
}
