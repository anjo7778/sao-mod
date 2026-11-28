package dev.anjo7778.sao.simulation;

import static dev.anjo7778.sao.simulation.CityPlan.*;
import static dev.anjo7778.sao.simulation.CityArchitecture.Piece;

/** Version 1 of compact districts. Reuses exact v2 blueprints; does not move canonical
 * plot centers, NPC assignments, the plaza, main gate avenues, or cross-street waypoints.
 * The additional houses are scenery/lodging capacity for future use, not new NPC assignments.
 */
public final class CityFabric {
    public static final int TEMPLATE_VERSION=2;
    private CityFabric(){}
    public static Plot infillPlot(long floor,int gap,int row){
        if(row==0||!FloorLayout.supported(floor))return null;
        Plot left=CityPlan.plot(floor,gap,row),right=CityPlan.plot(floor,gap+1,row);
        boolean boulevard=gap==-1||gap==0;
        if(boulevard?(left==null&&right==null):(left==null||right==null))return null;
        // Leave deliberately unbuilt neighborhood courts among the secondary homes.
        if(!boulevard&&Math.floorMod(floor*19+gap*13L+row*7L,5)==0)return null;
        int x=gap==0?18:gap==-1?-18:gap*LOT+LOT/2;
        int z=row*LOT+Math.floorMod((int)(floor%7)+gap*3+row*5,5)-2;
        Plot p=new Plot(x,z,8,9,5,Role.HOUSE);
        var model=BuildingTemplates.forPlot(p,TEMPLATE_VERSION);
        if(Math.abs(x)-model.halfWidth()<8||Math.abs(z)-model.halfDepth()<=PLAZA
            &&Math.abs(x)-model.halfWidth()<=PLAZA)return null;
        if(Math.abs(x)+model.halfWidth()>=half(floor)-8||Math.abs(z)+model.halfDepth()>=half(floor)-8)return null;
        return p;
    }
    private static Plot nearestInfill(long floor,int x,int z){
        int gap=Math.floorDiv(x,LOT),row=Math.floorDiv(z+LOT/2,LOT);
        return infillPlot(floor,gap,row);
    }
    public static Plot infillAt(long floor,int x,int z){
        Plot p=nearestInfill(floor,x,z);
        if(p==null)return null;
        return BuildingTemplates.forPlot(p,TEMPLATE_VERSION).covers(x-p.x(),z-p.z())?p:null;
    }
    public static Plot buildingAt(long floor,Column c){
        Plot primary=c.plot();
        if(primary!=null&&BuildingTemplates.forPlot(primary,TEMPLATE_VERSION).covers(c.x()-primary.x(),c.z()-primary.z()))return primary;
        Plot extra=infillAt(floor,c.x(),c.z());
        return extra!=null?extra:primary;
    }
    /** Narrow stone carriageway, contrasting sidewalk, continuous door approaches and
     * paved neighborhood courts. All surface pieces have normal full-block support.
     */
    public static Piece surface(long floor,Column c,Plot building){
        if(c.zone()==Zone.PLAZA||c.zone()==Zone.WALL||c.zone()==Zone.OUTSIDE
            ||Math.abs(c.x())<=7||Math.abs(c.z())<=7)
            return CityArchitecture.piece(c,0);
        int x=c.x(),z=c.z();
        int cross=Math.abs(Math.floorMod(z,LOT)-LOT/2);
        if(cross<=2)return Piece.ROAD;
        if(cross==3)return Piece.TRIM;
        if(cross<=5)return Piece.PAVING;
        int row=Math.floorDiv(z+LOT/2,LOT);
        int gap=Math.floorDiv(x,LOT);
        Plot extra=nearestInfill(floor,x,z);
        if(extra==null&&row!=0&&gap!=-1&&gap!=0
            &&CityPlan.plot(floor,gap,row)!=null&&CityPlan.plot(floor,gap+1,row)!=null){
            int dx=Math.abs(x-(gap*LOT+LOT/2)),dz=Math.abs(z-row*LOT);
            if(dx<=7&&dz<=8){
                if(dx==7||dz==8)return Piece.TRIM;
                if(dx==0&&dz==0)return Piece.LIGHT;
                return Piece.PAVING;
            }
        }
        // Side alleys follow the gaps between primary and infill homes.
        int localX=Math.floorMod(x,LOT);
        int lane=Math.min(Math.abs(localX-12),Math.abs(localX-36));
        if(lane<=1)return Piece.ROAD;
        if(lane==2)return Piece.TRIM;
        if(building!=null){
            int dx=x-building.x(),dz=z-building.z();
            var b=BuildingTemplates.forPlot(building,TEMPLATE_VERSION);
            if(Math.abs(dx)<=2&&dz<=0)return Piece.ROAD;
            if(Math.abs(dx)<=b.halfWidth()+2&&Math.abs(dz)<=b.halfDepth()+2)return Piece.PAVING;
            if(building.role()==Role.MARKET||building.role()==Role.INN||building.role()==Role.GUILD||building.role()==Role.QUEST_HALL)
                return Piece.PAVING;
        }
        if(extra!=null){
            int dx=x-extra.x(),dz=z-extra.z();
            if(Math.abs(dx)<=2&&dz<=0)return Piece.ROAD;
        }
        return Piece.GRASS;
    }
    /** Model interiors use exact fixed cells; preserve city wall/plaza furniture outside them. */
    public static boolean clearAt(long floor,int x,int z,int y){
        if(!inside(floor,x,z))return false;
        Column c=column(floor,x,z);
        Plot p=buildingAt(floor,c);
        if(p!=null)return BuildingTemplates.forPlot(p,TEMPLATE_VERSION).at(x-p.x(),y,z-p.z())=='.';
        Piece q=CityArchitecture.piece(c,y);
        return q==Piece.AIR||q==Piece.FLOWER;
    }
}
