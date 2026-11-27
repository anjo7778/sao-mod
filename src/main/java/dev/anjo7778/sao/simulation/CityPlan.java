package dev.anjo7778.sao.simulation;

/** Stable capital plan v1. All geometry is local to a chunk; no global list of buildings. */
public final class CityPlan {
    public static final int GROUND=96, LOT=48, PLAZA=32, FIRST_HALF=304, UPPER_HALF=192;
    public enum Role { HOUSE, INN, MARKET, GUILD, FORGE, CLINIC, QUEST_HALL }
    public enum Zone { OUTSIDE, PLAZA, ROAD, WALL, LOT, GARDEN }
    public enum Piece { AIR, PAVING, PATH, FOUNDATION, FLOOR, WALL, FRAME, ROOF, GLASS, LIGHT, GRASS,
        BOOKSHELF, BARREL, CRAFTING, FURNACE, TABLE, BENCH, WOOL }
    public record Plot(int x,int z,int width,int depth,int eaves,Role role){}
    public record Column(Zone zone,Plot plot,int x,int z,int half){}
    private CityPlan(){}
    public static int half(long floor){return floor==1?FIRST_HALF:UPPER_HALF;}
    public static boolean inside(long floor,int x,int z){
        if(!FloorLayout.supported(floor))return false;
        int h=half(floor);return x>=-h&&x<h&&z>=-h&&z<h;
    }
    public static double blend(long floor,int x,int z){
        if(!FloorLayout.supported(floor))return 0;
        long d=Math.max(Math.abs((long)x),Math.abs((long)z));
        return Math.max(0,Math.min(1,(half(floor)+64.0-d)/48.0));
    }
    public static Plot plot(long floor,int gridX,int gridZ){
        if(gridX==0||gridZ==0)return null;
        int x=gridX*LOT,z=gridZ*LOT;
        if(Math.abs(x)+20>=half(floor)-8||Math.abs(z)+20>=half(floor)-8)return null;
        int variation=(int)Math.floorMod(floor*17+gridX*31L+gridZ*13L,5);
        Role role;
        if(gridX<0&&gridZ<0) role=variation==0?Role.INN:Role.HOUSE;
        else if(gridX>0&&gridZ<0) role=variation<2?Role.GUILD:variation==2?Role.QUEST_HALL:Role.HOUSE;
        else if(gridX<0) role=variation<3?Role.MARKET:variation==3?Role.INN:Role.HOUSE;
        else role=variation<2?Role.FORGE:variation==2?Role.CLINIC:Role.HOUSE;
        int w=role==Role.HOUSE?12+variation:17;
        int d=role==Role.HOUSE?13+(variation%3):17;
        return new Plot(x,z,w,d,role==Role.HOUSE?6+variation%2:9,role);
    }
    public static Column column(long floor,int x,int z){
        int h=half(floor);
        if(!inside(floor,x,z))return new Column(Zone.OUTSIDE,null,x,z,h);
        int ax=Math.abs(x),az=Math.abs(z);
        if(ax<=PLAZA&&az<=PLAZA)return new Column(Zone.PLAZA,null,x,z,h);
        if((ax>=h-6||az>=h-6)&&ax>=8&&az>=8)return new Column(Zone.WALL,null,x,z,h);
        int gx=Math.floorDiv(x+LOT/2,LOT),gz=Math.floorDiv(z+LOT/2,LOT);
        int dx=x-gx*LOT,dz=z-gz*LOT;
        if(ax<=7||az<=7||Math.abs(dx)>=19||Math.abs(dz)>=19)return new Column(Zone.ROAD,null,x,z,h);
        Plot p=plot(floor,gx,gz);
        if(p!=null&&Math.abs(dx)<=p.width&&Math.abs(dz)<=p.depth)return new Column(Zone.LOT,p,x,z,h);
        return new Column(Zone.GARDEN,null,x,z,h);
    }
    public static Piece piece(Column c,int y){
        int x=c.x,z=c.z;
        if(c.zone==Zone.OUTSIDE||y<0)return Piece.AIR;
        if(y==0)return switch(c.zone){
            case PLAZA -> {int q=x*x+z*z; yield q>=100&&q<=144&&Math.floorMod(x+z,4)==0?Piece.LIGHT:Piece.PAVING;}
            case ROAD -> Piece.PATH;
            case WALL -> Piece.FOUNDATION;
            case LOT -> Piece.FLOOR;
            default -> Piece.GRASS;
        };
        if(c.zone==Zone.WALL){
            boolean tower=Math.max(Math.abs(x),Math.abs(z))>=c.half-6&&
                (Math.abs(x)>=c.half-16&&Math.abs(z)>=c.half-16||Math.floorMod(Math.abs(x)<Math.abs(z)?x:z,96)<9);
            int height=tower?14:9;
            return y<height||y==height&&Math.floorMod(x+z,4)<2?Piece.WALL:Piece.AIR;
        }
        if(c.zone==Zone.PLAZA){
            if(Math.abs(x)==24&&Math.abs(z)==24)return y<=3?Piece.FRAME:y==4?Piece.LIGHT:Piece.AIR;
            return Piece.AIR;
        }
        if(c.zone==Zone.ROAD){
            boolean post=Math.abs(x)==6&&Math.abs(z)>PLAZA&&Math.floorMod(z,48)==0||
                Math.abs(z)==6&&Math.abs(x)>PLAZA&&Math.floorMod(x,48)==0;
            return post?(y<=3?Piece.FRAME:y==4?Piece.LIGHT:Piece.AIR):Piece.AIR;
        }
        if(c.zone!=Zone.LOT)return Piece.AIR;
        Plot p=c.plot;int dx=x-p.x,dz=z-p.z,ax=Math.abs(dx),az=Math.abs(dz);
        int roof=p.eaves+1+(p.width-ax)/3;
        boolean edge=ax==p.width||az==p.depth;
        if(y>roof)return Piece.AIR;
        if(y==roof)return Piece.ROOF;
        if(edge){
            if(dz==-p.depth&&ax<=1&&y<=3)return Piece.AIR;
            if(ax==p.width&&az==p.depth||y==1||y==p.eaves)return Piece.FRAME;
            if((y==3||y==4)&&y<p.eaves&&Math.floorMod(ax==p.width?dz:dx,6)==0)return Piece.GLASS;
            return Piece.WALL;
        }
        if(y==1){
            if(dx==-p.width+2&&dz==-p.depth+2)return Piece.LIGHT;
            if(dx==p.width-2&&Math.abs(dz)<=2)return switch(p.role){
                case FORGE -> dz==0?Piece.FURNACE:Piece.CRAFTING;
                case GUILD,QUEST_HALL -> Piece.BOOKSHELF;
                case MARKET,INN -> Piece.BARREL;
                case CLINIC -> Piece.WOOL;
                default -> Piece.BARREL;
            };
            if(Math.abs(dx)<=2&&dz==2)return Piece.TABLE;
            if(Math.abs(dx)<=2&&dz==3)return Piece.BENCH;
        }
        return Piece.AIR;
    }
}
