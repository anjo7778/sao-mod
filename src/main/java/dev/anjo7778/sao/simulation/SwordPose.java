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
        SONIC_LEAP(9,29,-.95f,-.25f,.08f,-1.58f,.05f,.02f),
        HORIZONTAL_SQUARE(8,68,-1.05f,-1.10f,.40f,-1.10f,1.10f,-.55f),
        VERTICAL_SQUARE(10,72,-2.30f,-.65f,.45f,-.45f,.60f,-.45f);
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
        if(kind==Kind.HORIZONTAL_SQUARE||kind==Kind.VERTICAL_SQUARE)return square(kind,elapsed);
        float strike=smooth((elapsed-(kind.impact-1f))/2f);
        float blend=smooth(elapsed/Math.max(1f,kind.impact-1f))*(1-smooth((elapsed-kind.impact-1f)/Math.max(1f,kind.duration-kind.impact-1f)));
        return new Frame(lerp(kind.wx,kind.hx,strike),lerp(kind.wy,kind.hy,strike),lerp(kind.wz,kind.hz,strike),blend);
    }
    /** Alternating attack directions, with recovery after each of the four scheduled impacts. */
    private static Frame square(Kind kind,float elapsed){
        boolean horizontal=kind==Kind.HORIZONTAL_SQUARE;
        float first=kind.impact;
        float[] x=horizontal?new float[]{-1.05f,-1.05f,-1.20f,-1.15f,-.85f}
            :new float[]{-2.30f,-.45f,-2.20f,-.45f,-2.60f};
        float[] y=horizontal?new float[]{-1.10f,1.10f,-1.10f,1.10f,-1.25f}
            :new float[]{-.65f,.65f,-.65f,.65f,-.08f};
        float[] z=horizontal?new float[]{.40f,-.55f,.55f,-.55f,.60f}
            :new float[]{.45f,-.45f,.45f,-.45f,.10f};
        float weight=smooth(elapsed/Math.max(1,first-2))*(1-smooth((elapsed-(first+42+2))/(kind.duration-first-44)));
        float px=x[0],py=y[0],pz=z[0];
        for(int i=0;i<4;i++){
            float blend=smooth((elapsed-(first+i*14-2))/4);
            float nextX=(kind==Kind.VERTICAL_SQUARE&&i==3)?-.25f:x[i+1];
            px=lerp(px,nextX,blend);py=lerp(py,y[i+1],blend);pz=lerp(pz,z[i+1],blend);
        }
        return new Frame(px,py,pz,weight);
    }
}
