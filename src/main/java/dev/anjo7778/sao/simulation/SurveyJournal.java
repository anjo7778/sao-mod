package dev.anjo7778.sao.simulation;

import java.util.*;
import static dev.anjo7778.sao.simulation.ScoutingTrip.Gate;

/**
 * Compact, per-adventurer reconnaissance knowledge. A completed abstract survey is not a
 * physical terrain measurement, and a report from a peer is not a personal survey.
 * Bits are stable EAST=1, SOUTH=2, WEST=4, NORTH=8. No arbitrary maps or generated loot.
 */
public record SurveyJournal(int surveyed,int observed,int reported) {
    public static final int ALL=15;
    public static final SurveyJournal EMPTY=new SurveyJournal(0,0,0);
    public SurveyJournal{
        if((surveyed&~ALL)!=0||(observed&~surveyed)!=0||(reported&~ALL)!=0||(reported&surveyed)!=0)
            throw new IllegalArgumentException("Invalid survey journal");
    }
    public boolean surveyed(Gate gate){return (surveyed&bit(gate))!=0;}
    public boolean observed(Gate gate){return (observed&bit(gate))!=0;}
    public int known(){return surveyed|reported;}
    public SurveyJournal complete(Gate gate,boolean physical){
        int bit=bit(gate);
        return new SurveyJournal(surveyed|bit,observed|(physical?bit:0),reported&~bit);
    }
    /** Only first-hand accounts can be shared; never promote hearsay into observation. */
    public SurveyJournal learn(SurveyJournal peer){
        int next=(reported|peer.surveyed)&~surveyed;
        return next==reported?this:new SurveyJournal(surveyed,observed,next);
    }
    public Gate choose(int rotation,boolean preferUnknown){
        Gate[] gates=Gate.values();
        Gate best=null;int score=Integer.MIN_VALUE;
        for(int i=0;i<gates.length;i++){
            Gate g=gates[Math.floorMod(rotation+i,gates.length)];
            int bit=bit(g);
            boolean own=(surveyed&bit)!=0,heard=(reported&bit)!=0,seen=(observed&bit)!=0;
            // Complete firsthand coverage first, then physically verify abstract records.
            int rank=own?(seen?0:2):4+(preferUnknown?!heard?1:0:heard?1:0);
            if(rank>score){score=rank;best=g;}
        }
        return best;
    }
    public static int bit(Gate g){
        return switch(g){case EAST->1;case SOUTH->2;case WEST->4;case NORTH->8;};
    }
    public static String directions(int mask){
        StringJoiner out=new StringJoiner(", ");
        for(Gate g:Gate.values())if((mask&bit(g))!=0)out.add(g.name().toLowerCase(Locale.ROOT));
        return out.length()==0?"none":out.toString();
    }
    public String summary(){
        return "personally surveyed: "+directions(surveyed)+"; physically observed: "+directions(observed)+"; heard from others: "+directions(reported);
    }
}
