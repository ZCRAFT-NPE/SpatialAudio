package me.zcraft.mods.spatialaudio.effects;

import me.zcraft.mods.spatialaudio.Engine;
import me.zcraft.mods.spatialaudio.openal.ALUtils;
import me.zcraft.mods.spatialaudio.openal.ALset;
import me.zcraft.mods.spatialaudio.toolbox.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.EXTEfx;

import static me.zcraft.mods.spatialaudio.Engine.LOGGER;
import static me.zcraft.mods.spatialaudio.config.PrecomputedConfig.pC;

public class Reverb extends Effect {
	private static final double SABINE_CONSTANT = 0.161;
	private static final double EYRING_CONSTANT = 0.161;
	private static final double AIR_ABSORPTION_COEFFICIENT_1K = 0.0083;
	private static final double SPEED_OF_SOUND = 343.0;
	private static final double REFERENCE_FREQUENCY = 500.0;
	private static final double SCHROEDER_FREQUENCY = 100.0;

	public void apply(int id, float decayTime, float density, float diffusion,
					  float gainHF, float decayHFRatio, float reflectionsGain,
					  float reflectionsDelay, float lateReverbGain, float lateReverbDelay) {

		if (context == null || id >= context.slots.length || id >= context.effects.length) return;

		int slot = context.slots[id];
		int effect = context.effects[id];

		if (!ALUtils.isValidEffect(effect) || !ALUtils.isValidSlot(slot)) return;

		org.lwjgl.openal.EXTEfx.alEffectf(effect, org.lwjgl.openal.EXTEfx.AL_EAXREVERB_DECAY_TIME, decayTime);
		org.lwjgl.openal.EXTEfx.alEffectf(effect, org.lwjgl.openal.EXTEfx.AL_EAXREVERB_DENSITY, density);
		org.lwjgl.openal.EXTEfx.alEffectf(effect, org.lwjgl.openal.EXTEfx.AL_EAXREVERB_DIFFUSION, diffusion);
		org.lwjgl.openal.EXTEfx.alEffectf(effect, org.lwjgl.openal.EXTEfx.AL_EAXREVERB_GAINHF, gainHF);
		org.lwjgl.openal.EXTEfx.alEffectf(effect, org.lwjgl.openal.EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, decayHFRatio);
		org.lwjgl.openal.EXTEfx.alEffectf(effect, org.lwjgl.openal.EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, reflectionsGain);
		org.lwjgl.openal.EXTEfx.alEffectf(effect, org.lwjgl.openal.EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, reflectionsDelay);
		org.lwjgl.openal.EXTEfx.alEffectf(effect, org.lwjgl.openal.EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, lateReverbGain);
		org.lwjgl.openal.EXTEfx.alEffectf(effect, org.lwjgl.openal.EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, lateReverbDelay);

		org.lwjgl.openal.EXTEfx.alAuxiliaryEffectSloti(slot, org.lwjgl.openal.EXTEfx.AL_EFFECTSLOT_EFFECT, effect);

		ALUtils.errorApply("effect", effect, "slot", slot);
	}


	public void lowpass(int filter, float gain, float cutoff) {
		if (!ALUtils.isValidFilter(filter)) return;

		if (Float.isNaN(gain)) gain = 1.0f;
		if (Float.isNaN(cutoff)) cutoff = 1.0f;

		org.lwjgl.openal.EXTEfx.alFilterf(filter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAIN, gain);
		ALUtils.errorProperty("filter", filter, "gain", gain);

		org.lwjgl.openal.EXTEfx.alFilterf(filter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAINHF, cutoff);
		ALUtils.errorProperty("filter", filter, "cutoff", cutoff);
	}

	public void setFilter(int source, int id, float gain, float cutoff) {
		if (id >= context.filters.length) return;

		final int filter = context.filters[id];
		final int slot = context.slots[id];

		if (!ALUtils.isValidFilter(filter) || !ALUtils.isValidSlot(slot)) return;

		lowpass(filter, gain, cutoff);

		if (ALUtils.isValidSource(source)) {
			org.lwjgl.openal.AL11.alSource3i(source, org.lwjgl.openal.EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 1, filter);
			ALUtils.errorApply(new String[]{"filter", "slot"}, new int[]{filter, slot}, "source", source);
		}
	}

	public void setDirect(final int sourceID, float gain, float cutoff, boolean isGentle) {
		if (context.direct <= 0 || !ALUtils.isValidFilter(context.direct)) return;

		float min = isGentle ? 0.5f : 0;
		gain = net.minecraft.util.Mth.clamp(gain, min, 1);
		cutoff = net.minecraft.util.Mth.clamp(cutoff, min, 1);

		lowpass(context.direct, gain, cutoff);

		if (ALUtils.isValidSource(sourceID)) {
			org.lwjgl.openal.AL10.alSourcei(sourceID, org.lwjgl.openal.EXTEfx.AL_DIRECT_FILTER, context.direct);
			ALUtils.errorApply("direct filter", context.direct, "source", sourceID);
		}
	}


	@Override
	public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
		if (Engine.mc != null && Engine.mc.level != null && Engine.mc.player != null) {
			Vec3 soundPos = Engine.soundPos;
			RoomAcousticsAnalyzer.RoomAnalysis room = RoomAcousticsAnalyzer.analyzeRoom(Engine.mc.level, soundPos);
			double estimatedFrequency = estimateSoundFrequency();
			EchoDetector.EchoAnalysis echoAnalysis = EchoDetector.detectEchoes(
					Engine.mc.level, soundPos, Engine.mc.player.getEyePosition(), estimatedFrequency);
			adjustReverbForRoomWithEnhancedEcho(slot, sound, room, echoAnalysis);
		}

		setFilter(sound.sourceID(), slot.slot(), (float) slot.gain(), (float) slot.cutoff());
		setDirect(sound.sourceID(), (float) sound.directGain(), (float) sound.directCutoff(), isGentle);
		return context;
	}

	private void adjustReverbForRoomWithEnhancedEcho(SlotProfile slot, SoundProfile sound,
													 RoomAcousticsAnalyzer.RoomAnalysis room,
													 EchoDetector.EchoAnalysis echoAnalysis) {
		double rt60 = calculateReverberationTimeSabine(room);
		double earlyDelay = calculateEarlyReflectionsDelay(room);
		double lateDelay = calculateLateReverbDelay(room, rt60);
		double density = calculateDensity(room, rt60);
		double diffusion = calculateDiffusion(room);

		int slotId = slot.slot();
		if (slotId >= context.effects.length) return;

		int effectId = context.effects[slotId];

		float decayTime = (float) Math.max(0.18, Math.min(12.0, rt60));
		float reflectionsGain = (float) Math.max(0.10, 0.55 * (1.0 - room.averageAbsorption));

		double volumeFactor = Math.min(1.3, Math.sqrt(room.volume / 4000.0));
		float lateReverbGain = (float) (0.62 + volumeFactor * 0.38);

		float echoBoostFactor = 1.0f;
		if (echoAnalysis != null && echoAnalysis.hasClearEcho) {
			echoBoostFactor += (float)(echoAnalysis.echoClarity * 0.35);
			echoBoostFactor += (float)(echoAnalysis.averageReflectivity * 0.25);
			echoBoostFactor -= (float)(echoAnalysis.averageAbsorption * 0.15);

			decayTime *= (1.0 + echoAnalysis.echoClarity * 0.4);
			density *= (1.0 + echoAnalysis.echoDensity * 0.25);
			diffusion *= (1.0 + echoAnalysis.echoSpaciousness * 0.3);
			reflectionsGain *= (1.0 + echoAnalysis.primaryEchoGain * 0.45);
			lateReverbGain *= (1.0 + echoAnalysis.echoSpaciousness * 0.25);

			if (echoAnalysis.echoDensity > 2.0) density = Math.min(1.0, density * 1.15);
		}

		float gainHF = calculateHighFrequencyGain(room, rt60);
		float decayHFRatio = calculateDecayHFRatio(room, rt60);

		applyRoomAdjustedEffect(slotId, effectId, room, echoAnalysis,
				decayTime, (float)density, (float)diffusion,
				gainHF, decayHFRatio, reflectionsGain,
				(float)earlyDelay, lateReverbGain, (float)lateDelay);
	}

	private double calculateReverberationTimeSabine(RoomAcousticsAnalyzer.RoomAnalysis room) {
		if (room.volume <= 0 || room.totalAbsorptionArea <= 0) return 1.0;
		double rt60 = SABINE_CONSTANT * room.volume / room.totalAbsorptionArea;
		double airAbsorption = calculateAirAbsorption(room.volume);
		rt60 *= (1.0 + airAbsorption);
		return Math.max(0.2, Math.min(10.0, rt60));
	}

	private double calculateEarlyReflectionsDelay(RoomAcousticsAnalyzer.RoomAnalysis room) {
		double meanFreePath = 4.0 * room.volume / room.surfaceArea;
		double earlyDelay = meanFreePath / SPEED_OF_SOUND;
		return Math.max(0.001, Math.min(0.1, earlyDelay));
	}

	private double calculateLateReverbDelay(RoomAcousticsAnalyzer.RoomAnalysis room, double rt60) {
		double criticalDistance = 0.057 * Math.sqrt(room.volume / (Math.PI * rt60));
		double lateDelay = criticalDistance / SPEED_OF_SOUND;
		return Math.max(0.005, Math.min(0.1, lateDelay * 2.0));
	}

	private double calculateDensity(RoomAcousticsAnalyzer.RoomAnalysis room, double rt60) {
		double modalDensity = room.volume * Math.pow(SCHROEDER_FREQUENCY, 3) / Math.pow(SPEED_OF_SOUND, 3);
		double density = 1.0 - Math.exp(-modalDensity * 0.0001 * rt60);
		return Math.max(0.1, Math.min(1.0, density));
	}

	private double calculateDiffusion(RoomAcousticsAnalyzer.RoomAnalysis room) {
		double aspectRatio = room.dimensions.x / Math.max(room.dimensions.y, room.dimensions.z);
		double diffusion = 1.0 - Math.abs(1.0 - aspectRatio) * 0.3;
		diffusion *= (1.0 - room.averageAbsorption * 0.4);
		return Math.max(0.2, Math.min(1.0, diffusion));
	}

	private float calculateHighFrequencyGain(RoomAcousticsAnalyzer.RoomAnalysis room, double rt60) {
		double airAbsorption = AIR_ABSORPTION_COEFFICIENT_1K * rt60 * 0.5;
		double materialAbsorption = room.averageAbsorption * 0.6;
		double gainHF = 0.94 - airAbsorption - materialAbsorption;

		switch (room.roomType) {
			case TINY: gainHF *= 0.85f; break;
			case SMALL: gainHF *= 0.9f; break;
			case MEDIUM: gainHF *= 0.95f; break;
			case LARGE: gainHF *= 1.05f; break;
			case HUGE: gainHF *= 1.1f; break;
			case CATHEDRAL: gainHF *= 1.15f; break;
		}

		return (float) Math.max(0.18, Math.min(1.0, gainHF));
	}

	private float calculateDecayHFRatio(RoomAcousticsAnalyzer.RoomAnalysis room, double rt60) {
		double baseRatio = 1.08 - room.averageAbsorption * 0.35;
		double airAbsorptionEffect = AIR_ABSORPTION_COEFFICIENT_1K * rt60 * 0.3;
		double ratio = baseRatio - airAbsorptionEffect;
		return (float) Math.max(0.3, Math.min(2.0, ratio));
	}

	private double calculateAirAbsorption(double volume) {
		double roomDimension = Math.cbrt(volume);
		return AIR_ABSORPTION_COEFFICIENT_1K * roomDimension * 0.01;
	}

	private void applyRoomAdjustedEffect(int id, int effect, RoomAcousticsAnalyzer.RoomAnalysis room,
										 EchoDetector.EchoAnalysis echoAnalysis,
										 float decayTime, float density, float diffusion,
										 float gainHF, float decayHFRatio, float reflectionsGain,
										 float reflectionsDelay, float lateReverbGain, float lateReverbDelay) {
		int slot = context.slots[id];

		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, decayTime);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, density);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, diffusion);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAINHF, gainHF);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, decayHFRatio);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, reflectionsGain);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, reflectionsDelay);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, lateReverbGain);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, lateReverbDelay);

		if (echoAnalysis != null && echoAnalysis.hasClearEcho) {
			applyEchoParameters(effect, echoAnalysis, room);
		}

		float roomSizeFactor = (float)Math.sqrt(room.volume / 1000.0);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, Math.min(10.0f, roomSizeFactor));

		float airAbsorptionHF = calculateAirAbsorptionHF(room, echoAnalysis);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, airAbsorptionHF);

		EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);

		if (pC.dLog && !ALUtils.errorApply("effect", effect, "slot", slot)) {
			String logMsg = echoAnalysis != null && echoAnalysis.hasClearEcho ?
					String.format("Applied enhanced room-adjusted effect for %s room with %d echoes",
							room.roomType, echoAnalysis.echoTimes.size()) :
					String.format("Applied enhanced room-adjusted effect for %s room", room.roomType);
			LOGGER.info(logMsg);
		}
	}

	private void applyEchoParameters(int effect, EchoDetector.EchoAnalysis echoAnalysis,
									 RoomAcousticsAnalyzer.RoomAnalysis room) {
		if (!echoAnalysis.hasClearEcho || echoAnalysis.echoTimes.isEmpty()) return;

		float primaryEchoTime = (float)Math.min(0.25, echoAnalysis.primaryEchoDelay);
		float primaryEchoGain = (float)Math.min(0.9, echoAnalysis.primaryEchoGain * 1.4);

		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_ECHO_TIME, primaryEchoTime);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_ECHO_DEPTH, primaryEchoGain);

		if (echoAnalysis.echoClarity > 0.3) {
			float modulationTime = primaryEchoTime * (1.0f + (float)echoAnalysis.echoClarity * 0.8f);
			float modulationDepth = primaryEchoGain * 0.4f;
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_MODULATION_TIME, modulationTime);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_MODULATION_DEPTH, modulationDepth);
		}

		if (echoAnalysis.echoDensity > 1.5) {
			float echoDensityBoost = (float)Math.min(0.45, (echoAnalysis.echoDensity - 1.0) * 0.3);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY,
					Math.min(1.0f, EXTEfx.alGetEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY) + echoDensityBoost));
		}

		if (echoAnalysis.echoSpaciousness > 0.4) {
			float spaciousnessBoost = (float)Math.min(0.35, echoAnalysis.echoSpaciousness * 0.3);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION,
					Math.min(1.0f, EXTEfx.alGetEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION) + spaciousnessBoost));
		}

		if (echoAnalysis.clarityIndex > 0) {
			float clarityBoost = (float)Math.min(0.6, echoAnalysis.clarityIndex * 0.05);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN,
					Math.min(3.2f, EXTEfx.alGetEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN) * (1.0f + clarityBoost)));
		}

		if (echoAnalysis.earlyDecayTime > 0) {
			float edtFactor = (float)(echoAnalysis.earlyDecayTime / Math.max(1.0, echoAnalysis.schroederEnergyRatio * 10.0));
			float lateReverbDelayMultiplier = 1.0f + edtFactor * 0.1f;
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY,
					EXTEfx.alGetEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY) * lateReverbDelayMultiplier);
		}
	}

	private float calculateAirAbsorptionHF(RoomAcousticsAnalyzer.RoomAnalysis room,
										   EchoDetector.EchoAnalysis echoAnalysis) {
		double baseAbsorption = Math.pow(0.992, Math.sqrt(room.volume / 500.0));

		if (echoAnalysis != null) {
			double echoFactor = 1.0 - echoAnalysis.averageAbsorption * 0.2;
			baseAbsorption *= echoFactor;
		}

		double humidityEffect = 1.0 - 0.15 * Math.sin(room.volume * 0.001);
		baseAbsorption *= humidityEffect;

		return (float)Math.max(0.7, Math.min(1.0, baseAbsorption));
	}

	private double estimateSoundFrequency() {
		if (Engine.mc == null || Engine.mc.player == null) return REFERENCE_FREQUENCY;
		return REFERENCE_FREQUENCY;
	}

	@Override
	public boolean init() {
		boolean success = true;
		for(int i = 1; i <= pC.resolution; i++){
			double t = (double) i / pC.resolution;

			double woolRoomFactor = calculateWoolRoomPhysicalFactor();
			double roomSizeFactor = calculateRoomSizeFactor();
			double echoEnhancement = calculateEchoEnhancementFactor();
			double materialReflectivity = calculateAverageMaterialReflectivity();

			float densityBase = (float)((t * 0.55 + 0.45) * woolRoomFactor * echoEnhancement);
			float diffusionBase = (float)net.minecraft.util.Mth.lerp(pC.rvrbDiff * woolRoomFactor * materialReflectivity, 0.75-t*0.25, 0.92);

			apply(i - 1,
					(float) Math.max(t * pC.maxDecayTime * woolRoomFactor * roomSizeFactor * echoEnhancement * materialReflectivity, 0.12),
					densityBase,
					diffusionBase,
					(float) (0.93 - (0.65 * t * woolRoomFactor * (1.0 - materialReflectivity))),
					(float) Math.max(0.88 - (0.4 * t * woolRoomFactor), 0.2),
					(float) Math.max(Math.pow(1 - t, 0.5) + 0.7, 0.12) * (float)woolRoomFactor * (float)echoEnhancement * (float)materialReflectivity,
					(float) (t * 0.015 * roomSizeFactor),
					(float) (Math.pow(t, 0.6) + 0.68) * (float)woolRoomFactor * (float)echoEnhancement * (float)materialReflectivity,
					(float) (t * 0.015 * roomSizeFactor * echoEnhancement)
			);
		}

		if (context.direct > 0 && ALUtils.isValidFilter(context.direct)) {
			org.lwjgl.openal.EXTEfx.alFilteri(context.direct, org.lwjgl.openal.EXTEfx.AL_FILTER_TYPE, org.lwjgl.openal.EXTEfx.AL_FILTER_LOWPASS);
			success = !ALUtils.checkErrors("Failed to initialize direct filter object!");
		}

		if (success) {
			if (pC.dLog) LOGGER.info("Finished initializing enhanced OpenAL Auxiliary Effect slots!");
			return success;
		}

		LOGGER.info("Failed to properly initialize OpenAL Auxiliary Effect slots. Aborting");
		return success;
	}

	private double calculateAverageMaterialReflectivity() {
		if (Engine.mc == null || Engine.mc.player == null || Engine.mc.level == null) return 1.0;
		Vec3 playerPos = Engine.mc.player.position();
		BlockPos center = BlockPos.containing(playerPos);
		double totalReflectivity = 0.0;
		double totalWeight = 0.0;
		int radius = 5;
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos pos = center.offset(x, y, z);
					if (Engine.mc.level.isLoaded(pos)) {
						BlockState state = Engine.mc.level.getBlockState(pos);
						double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
						double distance = Math.sqrt(x*x + y*y + z*z);
						double weight = 1.0 / (distance + 1.0);
						totalReflectivity += reflectivity * weight;
						totalWeight += weight;
					}
				}
			}
		}
		if (totalWeight == 0) return 1.0;
		double avgReflectivity = totalReflectivity / totalWeight;
		if (avgReflectivity > 0.8) return 1.0 + (avgReflectivity - 0.8) * 0.5;
		if (avgReflectivity < 0.3) return Math.max(0.3, avgReflectivity * 1.3);
		return 1.0;
	}

	private double calculateEchoEnhancementFactor() {
		if (Engine.mc == null || Engine.mc.player == null || Engine.mc.level == null) return 1.0;
		Vec3 playerPos = Engine.mc.player.position();
		RoomAcousticsAnalyzer.RoomAnalysis room = RoomAcousticsAnalyzer.analyzeRoom(Engine.mc.level, playerPos);
		double baseFactor = 1.0;
		switch (room.roomType) {
			case TINY: baseFactor = 0.6; break;
			case SMALL: baseFactor = 0.8; break;
			case MEDIUM: baseFactor = 1.0; break;
			case LARGE: baseFactor = 1.2; break;
			case HUGE: baseFactor = 1.4; break;
			case CATHEDRAL: baseFactor = 1.6; break;
		}
		if (room.isEnclosed && room.volume > 1000) baseFactor *= 1.2;
		if (room.averageAbsorption < 0.2) baseFactor *= 1.4;
		else if (room.averageAbsorption > 0.6) baseFactor *= 0.6;
		return Math.max(0.5, Math.min(2.0, baseFactor));
	}

	public void applyWithEcho(int id, EchoDetector.EchoAnalysis echoAnalysis,
							  float decayTime, float density, float diffusion,
							  float gainHF, float decayHFRatio, float reflectionsGain,
							  float reflectionsDelay, float lateReverbGain, float lateReverbDelay) {
		if (id >= context.slots.length || id >= context.effects.length) return;

		int slot = context.slots[id];
		int effect = context.effects[id];
		applyEchoParameters(effect, echoAnalysis, null);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, decayTime * (1.0f + (float)echoAnalysis.echoClarity * 0.3f));
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, density * (1.0f + (float)echoAnalysis.echoDensity * 0.2f));
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, diffusion * (1.0f + (float)echoAnalysis.echoSpaciousness * 0.25f));
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAINHF, gainHF);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, decayHFRatio);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, reflectionsGain * (1.0f + (float)echoAnalysis.primaryEchoGain * 0.4f));
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, reflectionsDelay);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, lateReverbGain * (1.0f + (float)echoAnalysis.echoSpaciousness * 0.2f));
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, lateReverbDelay * (1.0f + (float)echoAnalysis.echoTimes.size() * 0.05f));
		EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
		if (pC.dLog && !ALUtils.errorApply("effect", effect, "slot", slot)) {
			LOGGER.info("Applied effect with enhanced echo parameters, {} distinct echoes", echoAnalysis.echoTimes.size());
		}
	}

	private double calculateWoolRoomPhysicalFactor() {
		if (Engine.mc == null || Engine.mc.player == null || Engine.mc.level == null) return 1.0;
		Vec3 playerPos = Engine.mc.player.position();
		BlockPos center = BlockPos.containing(playerPos);
		double totalAbsorption = 0.0;
		double totalWeight = 0.0;
		int radius = 6;
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos pos = center.offset(x, y, z);
					if (Engine.mc.level.isLoaded(pos)) {
						BlockState state = Engine.mc.level.getBlockState(pos);
						double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
						double distance = Math.sqrt(x*x + y*y + z*z);
						double weight = 1.0 / (distance + 1.0);
						totalAbsorption += absorption * weight;
						totalWeight += weight;
					}
				}
			}
		}
		if (totalWeight == 0) return 1.0;
		double avgAbsorption = totalAbsorption / totalWeight;
		if (avgAbsorption > 0.5) return Math.max(0.3, 1.5 - avgAbsorption * 1.5);
		return 1.0;
	}

	private double calculateRoomSizeFactor() {
		if (Engine.mc == null || Engine.mc.player == null || Engine.mc.level == null) return 1.0;
		Vec3 playerPos = Engine.mc.player.position();
		RoomAcousticsAnalyzer.RoomAnalysis room = RoomAcousticsAnalyzer.analyzeRoom(Engine.mc.level, playerPos);
		switch (room.roomType) {
			case TINY: return 0.4;
			case SMALL: return 0.7;
			case MEDIUM: return 1.0;
			case LARGE: return 1.3;
			case HUGE: return 1.6;
			case CATHEDRAL: return 2.0;
			default: return 1.0;
		}
	}
}