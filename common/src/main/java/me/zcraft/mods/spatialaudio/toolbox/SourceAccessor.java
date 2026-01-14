package me.zcraft.mods.spatialaudio.toolbox;



import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;


public interface SourceAccessor {
	void calculateReverb(SoundInstance sound, SoundEventListener listener);
}