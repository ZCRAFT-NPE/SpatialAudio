package me.zcraft.mods.spatialaudio;

import net.fabricmc.api.ModInitializer;

public class SpatialAudio implements ModInitializer {
	@Override
	public void onInitialize() {
		SpatialAudioCommon.init();
	}
}

