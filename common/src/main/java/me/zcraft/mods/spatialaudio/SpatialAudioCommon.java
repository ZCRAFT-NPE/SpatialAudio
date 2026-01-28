package me.zcraft.mods.spatialaudio;

import me.zcraft.mods.spatialaudio.config.BlueTapePack.ConfigManager;
import me.zcraft.mods.spatialaudio.toolbox.RoomAcousticsAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpatialAudioCommon {
    public static final String MOD_ID = "spatialaudio";
    public static final String MOD_NAME = "SpatialAudio";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static void init() {
        ConfigManager.registerAutoConfig();
        Runtime.getRuntime().addShutdownHook(new Thread(RoomAcousticsAnalyzer::cleanup));
    }
}