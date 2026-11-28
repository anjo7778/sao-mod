package dev.anjo7778.sao.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Original short electronic cues, resolved from the mod's own Vorbis resources. */
public final class AincradSounds {
    public enum Cue {
        OPEN("menu_open"), SELECT("menu_select"), CONFIRM("menu_confirm"), CLOSE("menu_close"), LEVEL_UP("level_up");
        public final ResourceLocation id;
        private final SoundEvent event;
        Cue(String path){
            id=ResourceLocation.fromNamespaceAndPath("sao","ui."+path);
            event=SoundEvent.createVariableRangeEvent(id);
        }
    }
    private AincradSounds(){}
    public static void play(Cue cue){
        MenuPreferences.load();
        if(!MenuPreferences.sounds)return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(cue.event,1f));
    }
}
