package dev.anjo7778.sao.simulation;

import dev.anjo7778.sao.simulation.AdventureProgress.Ledger;
import dev.anjo7778.sao.simulation.NpcRoster.State;
import java.util.Objects;

/** Short-lived visual review of a committed NPC decision or observed activity.
 * Presentation never changes economy, rewards or progression. Times are server game ticks.
 */
public record NpcMenuReview(Kind kind,String detail) {
    public static final int SUMMON=12,HOLD=48,CLOSE=6,DURATION=SUMMON+HOLD+CLOSE;
    private static final int CLOCK_MASK=0x0fffffff;
    public enum Kind {
        PREPARE(0,1,"Equipment check"), PRACTICE(0,2,"Training review"),
        GOAL(3,1,"Current objective"), ROUTE(3,0,"Route planning"),
        ATTRIBUTES(0,0,"Attributes allocated"), PURCHASE(0,1,"Supplies or repairs");
        public final int group,page;public final String title;
        Kind(int group,int page,String title){this.group=group;this.page=page;this.title=title;}
    }
    public NpcMenuReview{
        Objects.requireNonNull(kind);Objects.requireNonNull(detail);
        if(detail.length()>160||detail.isBlank())throw new IllegalArgumentException("Invalid review detail");
    }
    public int encode(long now){return ((int)now&CLOCK_MASK)<<4|(kind.ordinal()+1);}
    public static Kind kind(int packed){
        int i=(packed&15)-1;return i>=0&&i<Kind.values().length?Kind.values()[i]:null;
    }
    public static int age(int packed,long now){
        if(kind(packed)==null)return -1;
        int delta=((int)now-(packed>>>4))&CLOCK_MASK;
        return delta<DURATION?delta:-1;
    }
    public static float reveal(int age){
        if(age<SUMMON||age>=DURATION)return 0;
        return Math.min(1,Math.min((age-SUMMON+1)/6f,(DURATION-age)/6f));
    }
    /** Only a newly committed ledger transition is eligible; never offscreen credit or repeats. */
    public static NpcMenuReview fromTransition(State state,Ledger before,Ledger after,NpcDailyPlan.Plan plan,boolean observed){
        if(!observed||before==null||after==null||after.epoch()<=before.epoch())return null;
        if(after.trip().active()&&!before.trip().active()){
            var to=ScoutingTrip.plan(state.profile().currentFloor(),after.trip()).destination();
            return new NpcMenuReview(Kind.ROUTE,"Scouting "+after.trip().gate().name()+"|Destination "+to.x()+", "+to.z());
        }
        if(!before.goal().equals(after.goal())&&after.goal().active()&&before.goal().kind()!=after.goal().kind())
            return new NpcMenuReview(Kind.GOAL,AdventurerGoals.description(after.goal()));
        if(after.preparations()>before.preparations()){
            var n=state.needs();return new NpcMenuReview(Kind.PREPARE,"Supplies "+n.supplies()+"/100|Condition "+n.equipment()+"/100");
        }
        if(after.practice()>before.practice())
            return new NpcMenuReview(Kind.PRACTICE,"Practice "+after.practice()+"|Fatigue "+after.fatigue()+"/100");
        return null;
    }
}
