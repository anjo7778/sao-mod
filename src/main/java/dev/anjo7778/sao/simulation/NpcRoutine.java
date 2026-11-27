package dev.anjo7778.sao.simulation;

import static dev.anjo7778.sao.simulation.NpcRoster.*;

/** Stable individual sleep preferences, with deterministic day-to-day variation.
 * This is a schedule preference, not an actual fatigue or bed-occupancy simulation.
 */
public final class NpcRoutine {
    public enum Habit { EARLY, REGULAR, LATE, NOCTURNAL, FLEXIBLE }
    public record RestWindow(int start,int duration) {
        public RestWindow {
            if(start<0||start>=24000||duration<7200||duration>10000)
                throw new IllegalArgumentException("Invalid rest window");
        }
    }
    private NpcRoutine(){}
    public static Habit habit(Profile p){
        int roll=Math.floorMod(p.id().hashCode(),100);
        if(p.occupation()==Occupation.ADVENTURER){
            return roll<18?Habit.EARLY:roll<50?Habit.REGULAR:roll<73?Habit.LATE:
                roll<90?Habit.NOCTURNAL:Habit.FLEXIBLE;
        }
        // Inns and clinics can have night staff, without modeling worker production chains.
        boolean nightService=p.occupation()==Occupation.INNKEEPER||p.occupation()==Occupation.HEALER;
        if(nightService&&roll<25)return Habit.NOCTURNAL;
        return roll<32?Habit.EARLY:roll<76?Habit.REGULAR:roll<92?Habit.LATE:
            roll<96?Habit.NOCTURNAL:Habit.FLEXIBLE;
    }
    public static RestWindow window(Profile p,long day){
        long hash=mix(p.id().getMostSignificantBits()^Long.rotateLeft(p.id().getLeastSignificantBits(),23)
            ^day*0x9E3779B97F4A7C15L);
        int jitter=(int)Math.floorMod(hash,1201)-600;
        int duration=8000+(int)Math.floorMod(hash>>>16,1601);
        int start=switch(habit(p)){
            case EARLY -> 12500;
            case REGULAR -> 14800;
            case LATE -> 19400;
            case NOCTURNAL -> 2000;
            // Occasionally stay up for the full night and rest during the following day.
            case FLEXIBLE -> Math.floorMod(hash>>>32,4)==0?2600:18500;
        };
        return new RestWindow(start+jitter,duration);
    }
    public static boolean resting(Profile p,long time){
        long day=Math.floorDiv(time,24000L);
        int clock=(int)Math.floorMod(time,24000L);
        RestWindow today=window(p,day), yesterday=window(p,day-1);
        return (clock>=today.start()&&clock<today.start()+today.duration())
            ||clock+24000<yesterday.start()+yesterday.duration();
    }
    public static boolean night(long time){
        long clock=Math.floorMod(time,24000L);
        return clock>=13000||clock<1000;
    }
    public static boolean nightTraining(Profile p,long time){
        long day=Math.floorDiv(time,24000L);
        int motivation=p.personality().ambition()+p.personality().courage()/2;
        return motivation>=80&&Math.floorMod(mix(p.id().getLeastSignificantBits()+day),100)<motivation-40;
    }
    private static long mix(long n){
        n=(n^(n>>>30))*0xBF58476D1CE4E5B9L;
        n=(n^(n>>>27))*0x94D049BB133111EBL;
        return n^(n>>>31);
    }
}
