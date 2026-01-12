package dev.thedocruby.resounding.mixin;

import dev.thedocruby.resounding.Engine;
import dev.thedocruby.resounding.config.PrecomputedConfig;
import dev.thedocruby.resounding.toolbox.SourceAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEventListener;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.Map;

import static dev.thedocruby.resounding.Engine.mc;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

@Environment(EnvType.CLIENT)
@Mixin(SoundEngine.class)
public class SoundSystemMixin {

	@Shadow @Final private com.mojang.blaze3d.audio.Listener listener;
	@Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

	@Inject(
			method = "play",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/client/sounds/SoundEngine;instanceBySource:Lcom/google/common/collect/Multimap;",
					opcode = Opcodes.GETFIELD
			)
	)
	private void soundInfoYeeter(SoundInstance soundInstance, CallbackInfo ci) {
		if (Engine.isOff) return;
		Engine.updateYeetedSoundInfo(soundInstance);
	}

	@ModifyArg(
			method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/util/Mth;clamp(FFF)F"
			),
			index = 0
	)
	private float volumeMultiplierInjector(float vol) {
		if (Engine.isOff) return vol;
		return vol * PrecomputedConfig.globalVolumeMultiplier;
	}

	@Inject(
			method = "tickNonPaused",
			at = @At(
					value = "JUMP",
					opcode = Opcodes.IFEQ,
					ordinal = 3,
					shift = At.Shift.AFTER
			),
			locals = LocalCapture.CAPTURE_FAILHARD
	)
	private void recalculate(CallbackInfo ci, Iterator<Map.Entry<SoundInstance, ChannelAccess.ChannelHandle>> iterator,
							 Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry,
							 ChannelAccess.ChannelHandle channelHandle2, SoundInstance soundInstance) {
		if (Engine.isOff) return;
		if (mc.level != null && mc.level.getGameTime() % pC.srcRefrRate == 0) {
			channelHandle2.execute(source -> {
				if (source != null) {
					((SourceAccessor) source).calculateReverb(soundInstance, (SoundEventListener) this.listener);
				}
			});
		}
	}

	@Inject(
			method = "tickNonPaused",
			at = @At("TAIL")
	)
	private void cleanupInvalidSounds(CallbackInfo ci) {
		if (Engine.isOff) return;

		instanceToChannel.entrySet().removeIf(entry -> {
			if (entry.getValue() == null) return true;

			try {
				entry.getValue().execute(source -> {
				});
				return false;
			} catch (Exception e) {
				return true;
			}
		});
	}
}