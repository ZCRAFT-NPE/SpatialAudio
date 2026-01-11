package dev.thedocruby.resounding.toolbox;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;

@Environment(EnvType.CLIENT)
public interface SourceAccessor {
	void calculateReverb(SoundInstance sound, SoundEventListener listener);
}