package dev.anjo7778.sao;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SaoMod.MOD_ID)
public final class SaoMod {
    public static final String MOD_ID = "sao";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SaoMod() {
        LOGGER.info("SAO Mod loaded. Ready for Minecraft 1.21.1 with NeoForge.");
    }
}
