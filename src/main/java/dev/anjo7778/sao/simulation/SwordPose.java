package dev.anjo7778.sao.simulation;

/** Visual-only authored keyframes. The packed stamp is presentation, never a hit trigger. */
public final class SwordPose {
    private static final int CLOCK_MASK=0x0fffffff;
    public enum Kind {
        BASIC(4,14,-1.65f,-.45f,.35f,-.60f,.40f,-.55f),
        FOLLOW_UP(4,14,-.85f,.90f,-.35f,-1.25f,-.65f,.40f),
        FINISHER(5,18,-2.45f,-.10f,.18f,-.35f,.20f,-.20f),
        COUNTER(2,12,-1.15f,-.18f,.10f,-1.55f,.10f,.03f),
        HORIZONTAL(7,23,-1.05f,-1.10f,.40f,-1.10f,1.10f,-.55f),
        GUARD(3,14,-1.15f,-.42f,.14f,-1.15f,-.42f,.14f),
        DODGE(3,18,-.60f,-.30f,.35f,-.75f,-.20f,.28f),
        SLANT(5,18,-2.10f,-.75f,.72f,-.70f,.58f,-.62f),
        VERTICAL(10,28,-2.90f,.04f,.06f,-.25f,.02f,-.12f),
        SONIC_LEAP(9,29,-.95f,-.25f,.08f,-1.58f,.05f,.02f);
        public final int impact,duration;
        final float wx,wy,wz,hx,hy,hz;
        Kind(int impact,int duration,float wx,float wy,float wz,float hx,float hy,float hz){
            this.impact=impact;this.duration=duration;this.wx=wx;this.wy=wy;this.wz=wz;this.hx=hx;this.hy=hy;this.hz=hz;
        }
    }
    public record Frame(float x,float y,float z,float weight){}
    private SwordPose(){}
    public static int encode(Kind kind,long start){return kind==null?0:(((int)start&CLOCK_MASK)<<4)|(kind.ordinal()+1);}
    public static Kind kind(int packed){
        int i=(packed&15)-1;return i>=0&&i<Kind.values().length?Kind.values()[i]:null;
    }
    public static int age(int packed,long now){
        var kind=kind(packed);if(kind==null)return -1;
        int elapsed=((int)now-(packed>>>4))&CLOCK_MASK;
        return elapsed<kind.duration?elapsed:-1;
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    public static Frame sample(Kind kind,float elapsed){
        if(kind==null||!Float.isFinite(elapsed)||elapsed<0||elapsed>=kind.duration)return new Frame(0,0,0,0);
        float strike=smooth((elapsed-(kind.impact-1f))/2f);
        float blend=smooth(elapsed/Math.max(1f,kind.impact-1f))*(1-smooth((elapsed-kind.impact-1f)/Math.max(1f,kind.duration-kind.impact-1f)));
        return new Frame(lerp(kind.wx,kind.hx,strike),lerp(kind.wy,kind.hy,strike),lerp(kind.wz,kind.hz,strike),blend);
    }
}
