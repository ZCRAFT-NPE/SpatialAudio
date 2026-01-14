package me.zcraft.mods.spatialaudio.mixin;

import me.zcraft.mods.spatialaudio.Engine;
import me.zcraft.mods.spatialaudio.config.BlueTapePack.ConfigManager;
import me.zcraft.mods.spatialaudio.openal.Context;


import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.zcraft.mods.spatialaudio.config.PrecomputedConfig.pC;


@Mixin(SoundEngine.class)
public class SoundEngineMixin {
	@Inject(method = "reload", at = @At("TAIL"))
	private void resoundingStartInjector(CallbackInfo ci) {
		if (!Engine.isOff) {
			Engine.LOGGER.info("Resounding engine is still running, forcing stop...");
			Engine.root.clean(true);
			Engine.isOff = true;
		}

		if (!pC.enabled) {
			Engine.LOGGER.info("Skipped starting Resounding engine: disabled in config.");
			Engine.isOff = true;
			return;
		}

		Engine.LOGGER.info("Starting Resounding engine...");

		Engine.setRoot(new Context());
		if (!Engine.root.setup("Base Game")) {
			Engine.LOGGER.error("Failed to prime OpenAL EFX for Resounding effects. ResoundingEngine will not be active.");
			Engine.isOff = true;
			return;
		}

		Engine.LOGGER.info("OpenAL EFX successfully primed for Resounding effects");
		if (ConfigManager.resetOnReload) {
			ConfigManager.resetToDefault();
			ConfigManager.resetOnReload = false;
		}

		Engine.mc = Minecraft.getInstance();
		Engine.updateRays();
		Engine.isOff = false;
	}

	@Inject(method = "destroy", at = @At("HEAD"))
	private void resoundingStopInjector(CallbackInfo ci) {
		if (Engine.isOff) return;
		Engine.LOGGER.info("Stopping Resounding engine...");
		Engine.root.clean(true);
		Engine.mc = null;
		Engine.isOff = true;
	}
}