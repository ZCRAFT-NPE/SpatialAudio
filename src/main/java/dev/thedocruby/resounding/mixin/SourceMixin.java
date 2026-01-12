package dev.thedocruby.resounding.mixin;

import com.mojang.blaze3d.audio.Channel;
import dev.thedocruby.resounding.Engine;
import dev.thedocruby.resounding.openal.ALUtils;
import dev.thedocruby.resounding.toolbox.SourceAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Channel.class)
public abstract class SourceMixin implements SourceAccessor {

	@Shadow @Final private int source;

	private Vec3 pos;
	private boolean sourceValid = false;

	@Inject(method = "setSelfPosition(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
	private void soundPosStealer(Vec3 poss, CallbackInfo ci) {
		if (Engine.isOff) return;
		this.pos = poss;
	}

	@Inject(method = "play()V", at = @At("HEAD"))
	private void onPlaySoundInjector(CallbackInfo ci) {
		if (Engine.isOff) return;

		sourceValid = ALUtils.isValidSource(source);
		if (!sourceValid) return;

		Engine.playSound(Engine.root, pos.x, pos.y, pos.z, source, false);
	}

	@Inject(method = "stop()V", at = @At("HEAD"))
	private void onStopSound(CallbackInfo ci) {
		sourceValid = false;
	}

	public void calculateReverb(SoundInstance sound, SoundEventListener listener) {
		if (Engine.isOff) return;

		if (!sourceValid && !ALUtils.isValidSource(source)) return;

		Engine.updateYeetedSoundInfo(sound);
		Engine.playSound(Engine.root, pos.x, pos.y, pos.z, source, false);
	}
}