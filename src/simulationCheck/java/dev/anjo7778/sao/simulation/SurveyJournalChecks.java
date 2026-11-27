package dev.anjo7778.sao.simulation;

import static dev.anjo7778.sao.simulation.ScoutingTrip.Gate;
public final class SurveyJournalChecks{
    public static void main(String[] args){
        int checks=0;
        for(int own=0;own<=15;own++)for(int seen=0;seen<=15;seen++)for(int heard=0;heard<=15;heard++){
            if((seen&~own)!=0||(heard&own)!=0)continue;
            var journal=new SurveyJournal(own,seen,heard);
            for(int roll=-8;roll<=8;roll++)for(boolean explorer:new boolean[]{true,false}){
                Gate gate=journal.choose(roll,explorer);
                if(own!=15)check(!journal.surveyed(gate),"Unsurveyed first");
                var next=journal.complete(gate,false);
                check(next.surveyed(gate)&&next.observed()==seen,"Abstract survey not physical");
                check((next.reported()&next.surveyed())==0,"Firsthand replaces hearsay");
                check(next.complete(gate,true).observed(gate),"Physical observation retained");
                for(Gate other:Gate.values()){
                    var peer=SurveyJournal.EMPTY.complete(other,true);
                    var learn=journal.learn(peer);
                    check(learn.surveyed()==own&&learn.observed()==seen,"Reports cannot grant firsthand credit");
                    check(learn.learn(peer).equals(learn),"Repeated report idempotent");
                    check(SurveyJournal.EMPTY.learn(learn).reported()==own,"No secondhand propagation");
                    checks+=3;
                }
                checks+=4;
            }
        }
        var knowledge=SurveyJournal.EMPTY;
        for(int i=0;i<4;i++)knowledge=knowledge.complete(knowledge.choose(0,true),false);
        check(knowledge.surveyed()==15&&knowledge.observed()==0,"All four directions visited");
        var known=SurveyJournal.EMPTY.learn(SurveyJournal.EMPTY.complete(Gate.EAST,true));
        check(known.choose(0,false)==Gate.EAST&&known.choose(0,true)!=Gate.EAST,"Cautious and discovery behavior");
        System.out.printf("PASS: %,d journal and route-choice checks; no repeated knowledge inflation%n",checks);
    }
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
}
