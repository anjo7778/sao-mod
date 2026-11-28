package dev.anjo7778.sao.simulation;

import static dev.anjo7778.sao.simulation.CityPlan.*;

/** Capital architecture v2. Stable v1 plot footprints, entrances and service coordinates.
 * All decisions are column-local so generation order and chunk boundaries do not matter.
 */
public final class CityArchitecture {
    public static final int MAX_HEIGHT=28;
    public enum Style { COTTAGE, TOWNHOUSE, INN, MARKET, GUILDHALL, FORGE, CLINIC, QUEST_HALL }
    public enum Piece {
        AIR, PAVING, ROAD, TRIM, FOUNDATION, FLOOR, MASONRY, PLASTER, POST, BEAM_X, BEAM_Z,
        ROOF, ROOF_EAST, ROOF_WEST, ROOF_NORTH, ROOF_SOUTH, CHIMNEY, GLASS, LIGHT,
        GRASS, LEAVES, FLOWER, FENCE, BOOKSHELF, BARREL, CRAFTING, FURNACE, ANVIL,
        TABLE, BENCH, BED_HEAD, BED_FOOT, CARPET
    }
    private CityArchitecture(){}
    public static Style style(Plot p){
        if(p==null)return Style.COTTAGE;
        return switch(p.role()){
            case HOUSE -> Math.floorMod(p.x()/LOT+p.z()/LOT,3)==0?Style.TOWNHOUSE:Style.COTTAGE;
            case INN -> Style.INN;case MARKET -> Style.MARKET;case GUILD -> Style.GUILDHALL;
            case FORGE -> Style.FORGE;case CLINIC -> Style.CLINIC;case QUEST_HALL -> Style.QUEST_HALL;
        };
    }
    public static boolean ridgeAlongZ(Plot p){
        return switch(style(p)){
            case GUILDHALL,QUEST_HALL,MARKET -> false;
            case INN,FORGE,CLINIC -> true;
            default -> Math.floorMod(p.x()/LOT+p.z()/LOT,2)==0;
        };
    }
    public static int eaves(Plot p){
        return switch(style(p)){
            case COTTAGE -> 6+Math.floorMod(p.x()/LOT,2);
            case TOWNHOUSE -> 10;
            case INN -> 9;
            case MARKET -> 7;
            case GUILDHALL,QUEST_HALL -> 11;
            case FORGE -> 7;
            case CLINIC -> 8;
        };
    }
    public static int roofY(Plot p,int dx,int dz){
        int span=ridgeAlongZ(p)?p.width():p.depth(),cross=ridgeAlongZ(p)?Math.abs(dx):Math.abs(dz);
        return eaves(p)+1+Math.max(0,(span-cross)/2);
    }
    private static Piece roof(Plot p,int dx,int dz){
        boolean z=ridgeAlongZ(p);
        int cross=z?dx:dz,span=z?p.width():p.depth();
        if(cross==0||Math.floorMod(span-Math.abs(cross),2)==1)return Piece.ROOF;
        return z?(cross<0?Piece.ROOF_EAST:Piece.ROOF_WEST):(cross<0?Piece.ROOF_SOUTH:Piece.ROOF_NORTH);
    }
    public static Piece piece(Column c,int y){
        if(y<0||y>MAX_HEIGHT||c.zone()==Zone.OUTSIDE)return Piece.AIR;
        int x=c.x(),z=c.z(),ax=Math.abs(x),az=Math.abs(z),h=c.half();
        return switch(c.zone()){
            case PLAZA -> plaza(x,z,y);
            case ROAD -> road(x,z,y,h);
            case WALL -> wall(x,z,y,h);
            case GARDEN -> garden(x,z,y);
            case LOT -> building(c.plot(),x-c.plot().x(),z-c.plot().z(),y);
            default -> Piece.AIR;
        };
    }
    private static Piece plaza(int x,int z,int y){
        int ax=Math.abs(x),az=Math.abs(z),r=x*x+z*z;
        if(y==0){
            if(r>=196&&r<=256||Math.max(ax,az)==31||Math.max(ax,az)==32)return Piece.TRIM;
            if(r>=100&&r<=144&&Math.floorMod(x+z,4)==0)return Piece.LIGHT;
            return Piece.PAVING;
        }
        // Corner landscaping is clear of the central 43x43 NPC activity space.
        if(ax>=24&&ax<=30&&az>=24&&az<=30){
            int dx=ax-27,dz=az-27;
            if(y==1&&Math.max(Math.abs(dx),Math.abs(dz))==3)return Piece.TRIM;
            if(y==1&&Math.abs(dx)<=2&&Math.abs(dz)<=2)return Piece.GRASS;
            if(dx==0&&dz==0&&y>=2&&y<=6)return Piece.POST;
            if(y>=5&&y<=8&&Math.abs(dx)+Math.abs(dz)<= (y==8?2:3))return Piece.LEAVES;
            if(y==2&&Math.abs(dx)==2&&Math.abs(dz)==2)return Piece.FLOWER;
        }
        // An open monumental arch north of the spawn ring, not a blocking portal at spawn.
        if(z>=-29&&z<=-27&&ax<=9){
            if(y==6&&ax==7&&z==-27)return Piece.LIGHT;
            if(ax>=7&&y<=7)return y==1||y==7?Piece.TRIM:Piece.MASONRY;
            if(y==8||y==9&&ax<=7||y==10&&ax<=4)return y==8?Piece.TRIM:Piece.MASONRY;
        }
        if(ax==18&&az==28)return y<=3?Piece.POST:y==4?Piece.LIGHT:Piece.AIR;
        return Piece.AIR;
    }
    private static Piece road(int x,int z,int y,int h){
        int ax=Math.abs(x),az=Math.abs(z),edge=Math.max(ax,az),cross=Math.min(ax,az);
        if(y==0){
            if(cross==7&&(ax<=7||az<=7))return Piece.TRIM;
            return Piece.ROAD;
        }
        // Gate lintels sit well above the 3-block-high walking clearance.
        if(edge>=h-6&&cross<=7){
            if(y>=8&&y<=11)return y==8||y==11?Piece.TRIM:Piece.MASONRY;
            if(y==12&&Math.floorMod(x+z,4)<2)return Piece.MASONRY;
            return Piece.AIR;
        }
        boolean lamp=ax==6&&az>PLAZA&&Math.floorMod(z,48)==0||
            az==6&&ax>PLAZA&&Math.floorMod(x,48)==0;
        if(lamp)return y==1?Piece.TRIM:y<=4?Piece.FENCE:y==5?Piece.LIGHT:Piece.AIR;
        return Piece.AIR;
    }
    private static Piece wall(int x,int z,int y,int h){
        int ax=Math.abs(x),az=Math.abs(z);
        if(y==0)return Piece.FOUNDATION;
        int along=ax>az?z:x;
        boolean tower=ax>=h-16&&az>=h-16||Math.floorMod(along,96)<9;
        int height=tower?17:10;
        if(y>height+1)return Piece.AIR;
        if(y==height+1)return Math.floorMod(x+z,4)<2?Piece.MASONRY:Piece.AIR;
        if(y==1||y==height||y==height-2||tower&&y==6)return Piece.TRIM;
        if(y==5&&tower&&Math.floorMod(along,8)==4)return Piece.GLASS;
        return Piece.MASONRY;
    }
    private static Piece garden(int x,int z,int y){
        if(y==0)return Piece.GRASS;
        // Sparse nonblocking flower beds; keep every building entrance and road verge accessible.
        int dx=Math.floorMod(x+LOT/2,LOT)-LOT/2,dz=Math.floorMod(z+LOT/2,LOT)-LOT/2;
        if(y==1&&Math.abs(dx)>=8&&Math.abs(dz)>=8&&Math.floorMod(x*31+z*17,13)==0)return Piece.FLOWER;
        return Piece.AIR;
    }
    private static Piece building(Plot p,int dx,int dz,int y){
        int ax=Math.abs(dx),az=Math.abs(dz),e=eaves(p),roofY=roofY(p,dx,dz);
        Style style=style(p);
        boolean masonry=style==Style.FORGE||style==Style.GUILDHALL||style==Style.QUEST_HALL;
        int x=p.x()+dx,z=p.z()+dz;
        boolean clippedX=Math.abs(z)<=PLAZA&&(Math.abs(x-1)<=PLAZA||Math.abs(x+1)<=PLAZA);
        boolean clippedZ=Math.abs(x)<=PLAZA&&(Math.abs(z-1)<=PLAZA||Math.abs(z+1)<=PLAZA);
        boolean edge=ax==p.width()||az==p.depth()||clippedX||clippedZ;
        boolean chimney=(style==Style.FORGE||style==Style.INN||style==Style.COTTAGE||style==Style.TOWNHOUSE)
            &&dx>=p.width()-4&&dx<=p.width()-3&&dz>=p.depth()-4&&dz<=p.depth()-3;
        if(y==0)return Piece.FLOOR;
        if(chimney&&y>=roofY-1&&y<=roofY+4)return Piece.CHIMNEY;
        if(y>roofY)return Piece.AIR;
        if(y==roofY)return roof(p,dx,dz);
        if(edge){
            if(dz==-p.depth()&&ax<=1&&y<=3)return Piece.AIR;
            if(dz==-p.depth()&&ax<=2&&y==4)return Piece.BEAM_X;
            int along=ax==p.width()?dz:dx;
            if(y==1||y==2)return Piece.MASONRY;
            if(ax==p.width()&&az==p.depth()||Math.floorMod(along,6)==0)return Piece.POST;
            if(y==5||y==e)return ax==p.width()?Piece.BEAM_Z:Piece.BEAM_X;
            if(y==3||y==4||e>=9&&(y==7||y==8)){
                int window=Math.floorMod(along,6);
                if(window==2||window==3)return Piece.GLASS;
            }
            if(masonry&&y<=e)return Piece.MASONRY;
            return Piece.PLASTER;
        }
        // Open ground floor, with fixtures tucked along walls. Core destinations and front aisle stay clear.
        if(y==1){
            if(dx==-p.width()+2&&dz==-p.depth()+2)return Piece.LIGHT;
            if(dx==p.width()-2&&Math.abs(dz)<=2)return switch(style){
                case FORGE -> dz==0?Piece.FURNACE:dz==2?Piece.ANVIL:Piece.CRAFTING;
                case GUILDHALL,QUEST_HALL -> Piece.BOOKSHELF;
                case MARKET,INN -> Piece.BARREL;
                case CLINIC -> Piece.BARREL;
                default -> Piece.BARREL;
            };
            if(dz==2&&ax<=2)return Piece.TABLE;
            if(dz==3&&ax<=2)return Piece.BENCH;
            // South-end sleeping area. Bed head and foot are paired by the world feature.
            int bedColumns=style==Style.INN?5:style==Style.CLINIC?3:1;
            if(dz==p.depth()-3||dz==p.depth()-4){
                for(int i=0;i<bedColumns;i++)if(dx==-p.width()+3+3*i)
                    return dz==p.depth()-3?Piece.BED_HEAD:Piece.BED_FOOT;
            }
            if(dz==p.depth()-2&&Math.floorMod(dx,3)==0&&ax>=5&&ax<p.width()-1)
                return switch(style){
                    case GUILDHALL,QUEST_HALL->Piece.BOOKSHELF;
                    case MARKET,FORGE,INN->Piece.BARREL;
                    default->Piece.AIR;
                };
            if(style==Style.MARKET&&dx==-p.width()+3&&Math.abs(dz)<=5)return Piece.TABLE;
            if(style==Style.FORGE&&dx==-p.width()+3&&Math.abs(dz)<=3)return Piece.CRAFTING;
            if((style==Style.GUILDHALL||style==Style.QUEST_HALL)&&ax>=5&&ax<=7&&az<=6)return Piece.CARPET;
        }
        // Visible rafters high over the ground floor; never obstruct walking.
        if(y==e-1&&(ridgeAlongZ(p)?Math.floorMod(dz,8)==0:Math.floorMod(dx,8)==0))
            return ridgeAlongZ(p)?Piece.BEAM_X:Piece.BEAM_Z;
        return Piece.AIR;
    }
}
