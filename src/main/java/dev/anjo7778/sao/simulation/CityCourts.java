package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.CityPlan.*;

/** Four fixed, north-facing public-space layouts occupying reserved empty infill courts.
 * This library is independent of the unchanged, verified v2 building templates.
 * No generated fluids, entities, item rewards, or decorative writes outside the site.
 */
public final class CityCourts {
    public static final int HALF_X=7, HALF_Z=8, HEIGHT=5;
    public static final String SYMBOLS=".=#q+pxgftbBTruwACld";
    public enum Kind { RESIDENTIAL_GARDEN, MARKET_COURT, GUILD_SQUARE, TRAINING_YARD }
    public record Site(int x,int z,Kind kind){
        public boolean covers(int x,int z){return Math.abs(x-this.x)<=HALF_X&&Math.abs(z-this.z)<=HALF_Z;}
        public char at(int x,int y,int z){return CityCourts.cell(kind,x-this.x,y,z-this.z);}
    }
    private static final EnumMap<Kind,char[][][]> LIBRARY=library();
    private CityCourts(){}
    public static Site site(long floor,int gap,int row){
        if(row==0||gap==-1||gap==0||!FloorLayout.supported(floor))return null;
        if(plot(floor,gap,row)==null||plot(floor,gap+1,row)==null||CityFabric.infillPlot(floor,gap,row)!=null)return null;
        int x=gap*LOT+LOT/2,z=row*LOT;
        if(Math.abs(x)+HALF_X>=half(floor)-8||Math.abs(z)+HALF_Z>=half(floor)-8)return null;
        Kind kind=x<0?(z<0?Kind.RESIDENTIAL_GARDEN:Kind.MARKET_COURT):(z<0?Kind.GUILD_SQUARE:Kind.TRAINING_YARD);
        return new Site(x,z,kind);
    }
    public static Site at(long floor,int x,int z){
        var s=site(floor,Math.floorDiv(x,LOT),Math.floorDiv(z+LOT/2,LOT));
        return s!=null&&s.covers(x,z)?s:null;
    }
    public static char cell(Kind kind,int x,int y,int z){
        if(Math.abs(x)>HALF_X||Math.abs(z)>HALF_Z||y<0||y>=HEIGHT)return '.';
        return LIBRARY.get(kind)[y][z+HALF_Z][x+HALF_X];
    }
    public static boolean clearAt(long floor,int x,int z,int y){
        var s=at(floor,x,z);
        return s==null?CityFabric.clearAt(floor,x,z,y):s.at(x,y,z)=='.';
    }
    private static EnumMap<Kind,char[][][]> library(){
        var result=new EnumMap<Kind,char[][][]>(Kind.class);
        for(var kind:Kind.values()){
            var b=new Builder();
            for(int x=-HALF_X;x<=HALF_X;x++)for(int z=-HALF_Z;z<=HALF_Z;z++)
                b.put(x,0,z,Math.abs(x)==HALF_X||Math.abs(z)==HALF_Z?'#':'=');
            // Two small, fully supported stone-and-lantern posts, set back from the perimeter.
            for(int x:new int[]{-6,6}){
                b.put(x,1,-6,'#');b.put(x,2,-6,'p');b.put(x,3,-6,'l');
            }
            switch(kind){
                case RESIDENTIAL_GARDEN -> {
                    // Two raised planters, with contained soil, flowers and facing seating.
                    for(int side:new int[]{-1,1}){
                        for(int x=4;x<=6;x++)for(int z=-3;z<=5;z++)
                            b.put(side*x,1,z,x==4||x==6||z==-3||z==5?'#':'g');
                        for(int z:new int[]{-2,0,2,4})b.put(side*5,2,z,'f');
                        for(int z=-2;z<=1;z++)b.put(side*2,1,z,'b');
                    }
                    b.put(0,0,0,'q');
                }
                case MARKET_COURT -> {
                    // Fixed two five-by-five awnings. Corner posts support the continuous fabric roof.
                    for(int side:new int[]{-1,1}){
                        int lo=side<0?-6:2,hi=lo+4;
                        for(int x=lo;x<=hi;x++)for(int z=2;z<=6;z++)
                            b.put(x,4,z,(x-lo)%2==0?(side<0?'r':'u'):'w');
                        for(int x:new int[]{lo,hi})for(int z:new int[]{2,6})
                            for(int y=1;y<=3;y++)b.put(x,y,z,'p');
                        for(int x=lo+1;x<hi;x++)b.put(x,1,5,'T');
                        b.put(lo+1,1,6,'B');b.put(hi-1,1,6,'B');
                    }
                }
                case GUILD_SQUARE -> {
                    // A monument and two reading/notice stations, not a functional quest board yet.
                    for(int x=-2;x<=2;x++)for(int z=4;z<=6;z++)b.put(x,1,z,'#');
                    b.put(0,2,5,'q');b.put(0,3,5,'+');
                    b.put(-4,1,2,'d');b.put(4,1,2,'d');
                    for(int x:new int[]{-5,5})for(int z=-2;z<=0;z++)b.put(x,1,z,'b');
                    for(int x=-2;x<=2;x++)b.put(x,0,0,'q');
                }
                case TRAINING_YARD -> {
                    // Distinct physical targets and preparation stations, without awarding XP.
                    for(int x:new int[]{-4,0,4}){
                        b.put(x,1,5,'p');b.put(x,2,5,'t');b.put(x,0,1,'q');
                    }
                    b.put(-5,1,-3,'A');b.put(-5,1,-2,'C');b.put(5,1,-3,'B');
                    b.put(5,1,-2,'T');
                }
            }
            result.put(kind,b.blocks);
        }
        return result;
    }
    private static final class Builder{
        final char[][][] blocks=new char[HEIGHT][2*HALF_Z+1][2*HALF_X+1];
        Builder(){for(var layer:blocks)for(var row:layer)Arrays.fill(row,'.');}
        void put(int x,int y,int z,char c){
            if(Math.abs(x)>HALF_X||Math.abs(z)>HALF_Z||y<0||y>=HEIGHT||SYMBOLS.indexOf(c)<0)
                throw new IllegalArgumentException("Invalid court blueprint cell");
            if(y>0&&blocks[y][z+HALF_Z][x+HALF_X]!='.')throw new IllegalStateException("Overlapping court furniture");
            blocks[y][z+HALF_Z][x+HALF_X]=c;
        }
    }
}
