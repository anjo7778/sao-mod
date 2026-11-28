package dev.anjo7778.sao.simulation;

import static dev.anjo7778.sao.simulation.NpcRoster.*;
import static dev.anjo7778.sao.simulation.AdventurerMind.*;

/** Finite NPC-only service economy. Col and stock are simulation units, not Minecraft items. */
public final class AdventurerMarket {
    public static final int STARTING_COL=100, SCOUT_COMMISSION=10, STARTING_TREASURY=1200;
    public static final int SUPPLY_RESERVE=256, REPAIR_RESERVE=128, WHOLESALE_BATCH=6, MAX=100_000_000;
    public enum Service {
        SUPPLIES(Occupation.MERCHANT,12,24,24,60,8), REPAIR(Occupation.BLACKSMITH,18,25,12,70,12);
        private final Occupation seller;
        private final int price,gain,stock,limit,wholesale;
        Service(Occupation seller,int price,int gain,int stock,int limit,int wholesale){
            this.seller=seller;this.price=price;this.gain=gain;this.stock=stock;this.limit=limit;this.wholesale=wholesale;
        }
        public Occupation seller(){return seller;}
        public int price(){return price;}
        public int gain(){return gain;}
        public int wholesale(){return wholesale;}
        public int initialStock(){return stock;}
        public boolean needsService(Needs n){return (this==SUPPLIES?n.supplies():n.equipment())<limit;}
        public Needs apply(Needs n){
            return this==SUPPLIES
                ?new Needs(n.health(),n.supplies()+gain,n.equipment(),n.partyReadiness(),n.danger())
                :new Needs(n.health(),n.supplies(),n.equipment()+gain,n.partyReadiness(),n.danger());
        }
    }
    /** Starting capital plus explicitly earned guild commissions, keyed by lifetime scouting count. */
    public record Wallet(int supplyOrders,int repairOrders,long lastEpoch,int observedOrders,int earned,int claimedThrough){
        public Wallet(int supplyOrders,int repairOrders,long lastEpoch,int observedOrders){
            this(supplyOrders,repairOrders,lastEpoch,observedOrders,0,0);
        }
        public Wallet {
            long spent=(long)supplyOrders*Service.SUPPLIES.price()+(long)repairOrders*Service.REPAIR.price();
            if(supplyOrders<0||repairOrders<0||earned<0||earned>MAX||claimedThrough<0
                ||earned%SCOUT_COMMISSION!=0||earned/SCOUT_COMMISSION>claimedThrough
                ||spent>(long)STARTING_COL+earned||lastEpoch< -1||observedOrders<0
                ||observedOrders>(long)supplyOrders+repairOrders||(((long)supplyOrders+repairOrders==0)!=(lastEpoch==-1)))
                throw new IllegalArgumentException("Invalid wallet");
        }
        public int col(){return STARTING_COL+earned-supplyOrders*Service.SUPPLIES.price()-repairOrders*Service.REPAIR.price();}
        public int orders(){return supplyOrders+repairOrders;}
    }
    public static final Wallet NEW_WALLET=new Wallet(0,0,-1,0);
    public record Shop(Service service,int sold,int restocked){
        public Shop(Service service,int sold){this(service,sold,0);}
        public Shop{
            if(service==null||sold<0||sold>MAX||restocked<0||restocked>MAX
                ||(long)sold*service.price()<(long)restocked*service.wholesale()
                ||(long)service.initialStock()+restocked-sold<0
                ||(long)service.initialStock()+restocked-sold>service.initialStock())
                throw new IllegalArgumentException("Invalid shop stock");
        }
        public int remaining(){return service.initialStock()+restocked-sold;}
        public int till(){return (int)((long)sold*service.price()-(long)restocked*service.wholesale());}
    }
    /** One finite, explicitly seeded reserve per discovered capital. Wholesale sales recycle Col. */
    public record Treasury(int supplyReleased,int repairReleased,int commissions){
        public Treasury{
            if(supplyReleased<0||supplyReleased>SUPPLY_RESERVE||repairReleased<0||repairReleased>REPAIR_RESERVE
                ||commissions<0||balance(supplyReleased,repairReleased,commissions)<0)
                throw new IllegalArgumentException("Invalid guild treasury");
        }
        private static long balance(int s,int r,int c){
            return (long)STARTING_TREASURY+(long)s*Service.SUPPLIES.wholesale()+(long)r*Service.REPAIR.wholesale()-(long)c*SCOUT_COMMISSION;
        }
        public int col(){return (int)balance(supplyReleased,repairReleased,commissions);}
        public int reserve(Service service){return service==Service.SUPPLIES?SUPPLY_RESERVE-supplyReleased:REPAIR_RESERVE-repairReleased;}
    }
    public static final Treasury NEW_TREASURY=new Treasury(0,0,0);
    public record Commission(Wallet wallet,Treasury treasury,int paid){}
    public record Restock(Shop shop,Treasury treasury,int quantity){}
    public static Commission commission(Wallet w,Treasury treasury,int before,int after){
        // Never credit historical completions or replay a paid/unfunded completion.
        if(before<0||after!=(long)before+1||after<=w.claimedThrough())return null;
        int paid=treasury.col()>=SCOUT_COMMISSION&&w.earned()<=MAX-SCOUT_COMMISSION?SCOUT_COMMISSION:0;
        var next=new Wallet(w.supplyOrders(),w.repairOrders(),w.lastEpoch(),w.observedOrders(),w.earned()+paid,after);
        return new Commission(next,paid==0?treasury:new Treasury(treasury.supplyReleased(),treasury.repairReleased(),treasury.commissions()+1),paid);
    }
    public static Restock restock(Shop shop,Treasury treasury){
        if(shop.remaining()!=0)return null;
        var s=shop.service();
        int n=Math.min(WHOLESALE_BATCH,Math.min(treasury.reserve(s),Math.min(shop.till()/s.wholesale(),s.initialStock())));
        if(n<=0)return null;
        return new Restock(new Shop(s,shop.sold(),shop.restocked()+n),
            new Treasury(treasury.supplyReleased()+(s==Service.SUPPLIES?n:0),treasury.repairReleased()+(s==Service.REPAIR?n:0),treasury.commissions()),n);
    }
    public static boolean canOffer(Needs n,Wallet w,Shop shop,Treasury treasury){
        if(canBuy(n,w,shop))return true;
        if(n.health()<35||w.col()<shop.service().price()||!shop.service().needsService(n))return false;
        return restock(shop,treasury)!=null;
    }
    public record Sale(Wallet wallet,Shop shop,Needs needs){}
    private AdventurerMarket(){}
    public static Service forRole(Occupation job){
        return switch(job){case MERCHANT->Service.SUPPLIES;case BLACKSMITH->Service.REPAIR;default->null;};
    }
    public static boolean canBuy(Needs n,Wallet w,Shop shop){
        return n.health()>=35&&shop.remaining()>0&&w.col()>=shop.service().price()&&shop.service().needsService(n);
    }
    public static Sale buy(Needs n,Wallet w,Shop shop,long epoch,boolean observed){
        if(epoch<=w.lastEpoch()||!canBuy(n,w,shop))return null;
        var service=shop.service();
        var next=new Wallet(w.supplyOrders()+(service==Service.SUPPLIES?1:0),w.repairOrders()+(service==Service.REPAIR?1:0),
            epoch,w.observedOrders()+(observed?1:0),w.earned(),w.claimedThrough());
        return new Sale(next,new Shop(service,shop.sold()+1,shop.restocked()),service.apply(n));
    }
    public static Needs wear(Needs n,int amount){
        if(amount<0)throw new IllegalArgumentException("Negative wear");
        return new Needs(n.health(),n.supplies(),Math.max(0,n.equipment()-amount),n.partyReadiness(),n.danger());
    }
}
