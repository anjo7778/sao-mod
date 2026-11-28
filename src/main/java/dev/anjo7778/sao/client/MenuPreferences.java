package dev.anjo7778.sao.client;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.Properties;

/** Local persisted input and accessibility preferences. */
public final class MenuPreferences {
    public enum Mode { KEY_ONLY, BOTH, GESTURE_ONLY;
        public boolean key(){return this!=GESTURE_ONLY;}
        public boolean gesture(){return this!=KEY_ONLY;}
    }
    public enum Sensitivity { LOW(64,17), NORMAL(42,12), HIGH(28,8);
        public final double pixels;public final float pitch;
        Sensitivity(double pixels,float pitch){this.pixels=pixels;this.pitch=pitch;}
    }
    public static Mode mode=Mode.KEY_ONLY;
    public static Sensitivity sensitivity=Sensitivity.NORMAL;
    public static boolean combatGuard=false,npcMenus=true,hud=true,fingers=true;
    private static boolean loaded;
    private MenuPreferences(){}
    private static Path path(){return FMLPaths.CONFIGDIR.get().resolve("sao-menu.properties");}
    public static void load(){
        if(loaded)return;loaded=true;var p=new Properties();
        try{
            if(Files.exists(path()))try(var in=Files.newInputStream(path())){p.load(in);}
            mode=Mode.valueOf(p.getProperty("mode","KEY_ONLY"));
            sensitivity=Sensitivity.valueOf(p.getProperty("sensitivity","NORMAL"));
            combatGuard=Boolean.parseBoolean(p.getProperty("combatGuard","false"));
            npcMenus=Boolean.parseBoolean(p.getProperty("npcMenus","true"));
            hud=Boolean.parseBoolean(p.getProperty("hud","true"));
            fingers=Boolean.parseBoolean(p.getProperty("fingers","true"));
        }catch(Exception e){LogUtils.getLogger().warn("Invalid Aincrad preferences",e);mode=Mode.KEY_ONLY;sensitivity=Sensitivity.NORMAL;combatGuard=false;npcMenus=true;hud=true;fingers=true;}
    }
    public static void save(){
        var p=new Properties();p.setProperty("mode",mode.name());p.setProperty("sensitivity",sensitivity.name());
        p.setProperty("combatGuard",Boolean.toString(combatGuard));p.setProperty("npcMenus",Boolean.toString(npcMenus));p.setProperty("hud",Boolean.toString(hud));p.setProperty("fingers",Boolean.toString(fingers));
        try{Files.createDirectories(path().getParent());try(var out=Files.newOutputStream(path())){p.store(out,"Aincrad local menu controls");}}
        catch(Exception e){LogUtils.getLogger().warn("Could not save menu preferences",e);}
    }
    public static void cycle(int row){
        switch(row){case 0->mode=Mode.values()[(mode.ordinal()+1)%Mode.values().length];
            case 1->sensitivity=Sensitivity.values()[(sensitivity.ordinal()+1)%Sensitivity.values().length];
            case 2->combatGuard=!combatGuard;case 3->npcMenus=!npcMenus;case 4->hud=!hud;default->{return;}}
        save();
    }
}
