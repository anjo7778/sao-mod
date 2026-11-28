package dev.anjo7778.sao.simulation;

/** Original full-body locomotion curves. Inputs describe measured movement,
 * not keyboard intent or an animation's ability to move the entity. */
public final class HumanoidMotion {
    public enum Mode { IDLE,WALK,RUN,STRAFE,BACKPEDAL,CROUCH,CROUCH_WALK,JUMP,FALL,LAND,SWIM,CLIMB,CRAWL,SIT,SLEEP,GLIDE }
    public record Input(double forward,double side,double vertical,boolean ground,boolean sprint,boolean crouch,
                        boolean swim,boolean climb,boolean crawl,boolean sit,boolean sleep,boolean glide,
                        boolean sword,boolean leftHanded) {
        public Input {
            forward=clamp(finite(forward),-1,1);
            side=clamp(finite(side),-1,1);
            vertical=clamp(finite(vertical),-1,1);
        }
        public double speed(){return Math.min(1,Math.hypot(forward,side));}
    }
    public record Joint(float x,float y,float z) {
        public Joint blend(Joint b,float t){return new Joint(mix(x,b.x,t),mix(y,b.y,t),mix(z,b.z,t));}
    }
    public record Pose(Joint body,Joint rightArm,Joint leftArm,Joint rightLeg,Joint leftLeg,float bob,float hipBack) {
        public Pose blend(Pose b,float t){
            return new Pose(body.blend(b.body,t),rightArm.blend(b.rightArm,t),leftArm.blend(b.leftArm,t),
                rightLeg.blend(b.rightLeg,t),leftLeg.blend(b.leftLeg,t),mix(bob,b.bob,t),mix(hipBack,b.hipBack,t));
        }
    }
    private static final double TAU=Math.PI*2;
    private HumanoidMotion(){}
    private static double finite(double v){return Double.isFinite(v)?v:0;}
    private static double clamp(double v,double low,double high){return Math.max(low,Math.min(high,v));}
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    private static Joint j(double x,double y,double z){return new Joint((float)x,(float)y,(float)z);}
    public static Mode choose(Input in){
        if(in.sleep)return Mode.SLEEP;
        if(in.sit)return Mode.SIT;
        if(in.glide)return Mode.GLIDE;
        if(in.swim)return Mode.SWIM;
        if(in.climb)return Mode.CLIMB;
        if(in.crawl)return Mode.CRAWL;
        if(!in.ground)return in.vertical>.025?Mode.JUMP:Mode.FALL;
        double speed=in.speed();
        if(in.crouch)return speed>.018?Mode.CROUCH_WALK:Mode.CROUCH;
        if(speed<=.018)return Mode.IDLE;
        if(Math.abs(in.side)>Math.abs(in.forward)*1.20)return Mode.STRAFE;
        if(in.forward<-.018)return Mode.BACKPEDAL;
        return in.sprint||speed>.31?Mode.RUN:Mode.WALK;
    }
    /** One controller per visible character. Updates at most once per game tick,
     * interpolates for render frames, and never advances from repeated render calls. */
    public static final class Controller {
        private long tick=Long.MIN_VALUE,landUntil=Long.MIN_VALUE;
        private Mode mode=Mode.IDLE;
        private double phase;
        private Pose previous,current;
        public Mode mode(){return mode;}
        public double phase(){return phase;}
        public Pose update(long now,Input in,float partial){
            if(now!=tick){
                boolean reset=current==null||now<tick||now-tick>8;
                Mode next=choose(in);
                if(reset){phase=0;landUntil=Long.MIN_VALUE;}
                else if((mode==Mode.JUMP||mode==Mode.FALL)&&in.ground&&next!=Mode.SIT&&next!=Mode.SLEEP)landUntil=now+5;
                if(in.ground&&now<landUntil&&(next==Mode.IDLE||next==Mode.WALK||next==Mode.RUN))next=Mode.LAND;
                if(next==Mode.SWIM||next==Mode.GLIDE)phase=(phase+.34)%TAU;
                else if(next==Mode.CLIMB)phase=(phase+Math.abs(in.vertical)*TAU/1.15)%TAU;
                else if(in.ground)phase=(phase+in.speed()*TAU/(next==Mode.RUN?1.75:1.15))%TAU;
                Pose desired=sample(next,in,phase,now);
                previous=reset?desired:current;
                current=reset?desired:current.blend(desired,next==mode?.58f:.38f);
                tick=now;mode=next;
            }
            return previous.blend(current,(float)clamp(finite(partial),0,1));
        }
    }
    /** Handedness only changes the carry pose, not the physical step sequence. */
    public static Pose sample(Mode mode,Input in,double phase,long tick){
        double a=Math.sin(phase),b=Math.cos(phase),breath=Math.sin(tick*.105)*.018;
        double drive=clamp(in.speed()/.23,0,1);
        double side=clamp(in.side/.24,-1,1),back=in.forward<0?-1:1;
        Joint body=j(breath,0,0),ra=j(-.05,0,.07),la=j(-.05,0,-.07),rl=j(0,0,.025),ll=j(0,0,-.025);
        float bob=(float)(breath*7),hip=0;
        switch(mode){
            case IDLE->{body=j(breath,0,breath*.5);ra=j(-.08+breath,0,.10);la=j(-.04-breath,0,-.08);}
            case WALK,BACKPEDAL,STRAFE->{
                double stride=a*.72*drive*back;
                body=j(.07*drive,.09*b*drive,-side*.10);
                ra=j(-.1-stride*.56,0,.10);la=j(-.1+stride*.56,0,-.10);
                rl=j(stride,side*.16,side*.21+.035);ll=j(-stride,side*.16,side*.21-.035);
                if(mode==Mode.STRAFE){
                    body=j(.08,.03,-side*.19);ra=j(-.25+a*.17,.08,.19+side*.08);la=j(-.25-a*.17,-.08,-.19+side*.08);
                    rl=j(a*.28,side*.24,.13+side*.32*b);ll=j(-a*.28,side*.24,-.13-side*.32*b);
                }
                bob=(float)(-.65*Math.abs(a)*drive);
            }
            case RUN->{
                body=j(.31,.14*b,-side*.12);
                ra=j(-.48-a*.78,0,.16);la=j(-.48+a*.78,0,-.16);
                rl=j(a*1.04,.04,.06);ll=j(-a*1.04,-.04,-.06);
                bob=(float)(-1.15*Math.abs(a));
            }
            case CROUCH,CROUCH_WALK->{
                double stride=mode==Mode.CROUCH_WALK?a*.40*drive*back:0;
                body=j(.39,.04*b*drive,-side*.06);ra=j(-.36-stride*.5,0,.12);la=j(-.36+stride*.5,0,-.12);
                rl=j(.19+stride,side*.08,.09);ll=j(.19-stride,side*.08,-.09);
                bob=(float)(2.2-.3*Math.abs(a)*drive);hip=2.2f;
            }
            case JUMP->{body=j(.15,0,0);ra=j(-.75,.1,.22);la=j(-.45,-.1,-.28);rl=j(-.70,0,.10);ll=j(.34,0,-.1);bob=-.8f;}
            case FALL->{body=j(-.08,0,0);ra=j(-.88,0,.55);la=j(-.88,0,-.55);rl=j(.22,0,.18);ll=j(.40,0,-.18);}
            case LAND->{body=j(.45,0,0);ra=j(-.48,0,.25);la=j(-.48,0,-.25);rl=j(.30,0,.1);ll=j(.30,0,-.1);bob=1.8f;hip=1.6f;}
            case SWIM->{body=j(.04,0,.08*b);ra=j(-1.5+a*1.15,.25,.18);la=j(-1.5-a*1.15,-.25,-.18);rl=j(.38*a,0,.05);ll=j(-.38*a,0,-.05);}
            case CLIMB->{body=j(.17,0,0);ra=j(-1.75+a*.55,.10,.12);la=j(-1.75-a*.55,-.10,-.12);rl=j(-.45-a*.55,0,.08);ll=j(-.45+a*.55,0,-.08);}
            case CRAWL->{body=j(.05,0,.08*b);ra=j(-1.10+a*.5,.10,.22);la=j(-1.10-a*.5,-.10,-.22);rl=j(.20*a,0,.1);ll=j(-.20*a,0,-.1);}
            case SIT->{body=j(.08,0,0);ra=j(-.4,0,.1);la=j(-.4,0,-.1);rl=j(-1.35,.26,.10);ll=j(-1.35,-.26,-.10);}
            case SLEEP->{body=j(0,0,0);ra=j(-.05,0,.08);la=j(-.05,0,-.08);rl=j(0,0,0);ll=j(0,0,0);}
            case GLIDE->{body=j(0,0,0);ra=j(.35,0,.33);la=j(.35,0,-.33);rl=j(.05,0,.05);ll=j(.05,0,-.05);}
        }
        if(in.sword&&(mode==Mode.IDLE||mode==Mode.WALK||mode==Mode.RUN||mode==Mode.BACKPEDAL||mode==Mode.STRAFE||mode==Mode.CROUCH||mode==Mode.CROUCH_WALK||mode==Mode.LAND)){
            Joint carry=j(mode==Mode.RUN?-.85:-.43,.07,in.leftHanded?-.19:.19);
            if(in.leftHanded)la=carry;else ra=carry;
        }
        return new Pose(body,ra,la,rl,ll,bob,hip);
    }
}
