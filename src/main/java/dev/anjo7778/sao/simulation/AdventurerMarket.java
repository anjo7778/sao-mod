package dev.anjo7778.sao.simulation;

import static dev.anjo7778.sao.simulation.NpcRoster.*;
import static dev.anjo7778.sao.simulation.AdventurerMind.*;

/** Finite NPC-only service economy. Col and stock are simulation units, not Minecraft items. */
public final class AdventurerMarket {
    public static final int STARTING_COL=100;
    public enum Service {
        SUPPLIES(Occupation.MERCHANT,12,24,24,60), REPAIR(Occupation.BLACKSMITH,18,25,12,70);
        private final Occupation seller;
        private final int price,gain,stock,limit;
        Service(Occupation seller,int price,int gain,int stock,int limit){
            this.seller=seller;this.price=price;this.gain=gain;this.stock=stock;this.limit=limit;
        }
        public Occupation seller(){return seller;}
        public int price(){return price;}
        public int gain(){return gain;}
        public int initialStock(){return stock;}
        public boolean needsService(Needs n){return (this==SUPPLIES?n.supplies():n.equipment())<limit;}
        public Needs apply(Needs n){
            return this==SUPPLIES
                ?new Needs(n.health(),n.supplies()+gain,n.equipment(),n.partyReadiness(),n.danger())
                :new Needs(n.health(),n.supplies(),n.equipment()+gain,n.partyReadiness(),n.danger());
        }
    }
    public record Wallet(int supplyOrders,int repairOrders,long lastEpoch,int observedOrders){
        public Wallet {
            long spent=(long)supplyOrders*Service.SUPPLIES.price()+(long)repairOrders*Service.REPAIR.price();
            if(supplyOrders<0||repairOrders<0||spent>STARTING_COL||lastEpoch< -1||observedOrders<0
                ||observedOrders>(long)supplyOrders+repairOrders||((supplyOrders+repairOrders==0)!=(lastEpoch==-1)))
                throw new IllegalArgumentException("Invalid wallet");
        }
        public int col(){return STARTING_COL-supplyOrders*Service.SUPPLIES.price()-repairOrders*Service.REPAIR.price();}
        public int orders(){return supplyOrders+repairOrders;}
    }
    public static final Wallet NEW_WALLET=new Wallet(0,0,-1,0);
    public record Shop(Service service,int sold){
        public Shop{
            if(service==null||sold<0||sold>service.initialStock())throw new IllegalArgumentException("Invalid shop stock");
        }
        public int remaining(){return service.initialStock()-sold;}
        public int till(){return sold*service.price();}
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
            epoch,w.observedOrders()+(observed?1:0));
        return new Sale(next,new Shop(service,shop.sold()+1),service.apply(n));
    }
    public static Needs wear(Needs n,int amount){
        if(amount<0)throw new IllegalArgumentException("Negative wear");
        return new Needs(n.health(),n.supplies(),Math.max(0,n.equipment()-amount),n.partyReadiness(),n.danger());
    }
}
