package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.Engine;
import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.openal.*;
import static dev.thedocruby.resounding.Engine.LOGGER;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

// this effect adds reverberation - sorta like echo, but instead of hearing the
// sound again, you're hearing it travel away from you.
public class Reverb extends Effect {

	//	public Reverb() {}

//	private ALset context;

	public void apply(int id, float decayTime, float density, float diffusion,
					  float gainHF, float decayHFRatio, float reflectionsGain,
					  float reflectionsDelay, float lateReverbGain, float lateReverbDelay) {

		EchoDetector.EchoAnalysis echoAnalysis = null;
		if (Engine.mc != null && Engine.mc.level != null && Engine.mc.player != null) {
			Vec3 listenerPos = Engine.mc.player.getEyePosition();
			Vec3 soundPos = Engine.soundPos;
			echoAnalysis = EchoDetector.quickEchoDetection(
					Engine.mc.level, soundPos, listenerPos);
		}

		if (echoAnalysis != null && echoAnalysis.hasClearEcho) {
			applyWithEcho(id, echoAnalysis, decayTime, density, diffusion,
					gainHF, decayHFRatio, reflectionsGain, reflectionsDelay,
					lateReverbGain, lateReverbDelay);
		} else {
			int slot = ALset.slots[id];
			int effect = ALset.effects[id];

			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, decayTime);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, density);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, diffusion);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAINHF, gainHF);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, decayHFRatio);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, reflectionsGain);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, reflectionsDelay);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, lateReverbGain);
			EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, lateReverbDelay);

			EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);

			if (pC.dLog && !ALUtils.errorApply("effect", effect, "slot", slot)) {
				LOGGER.info("Applied effect without significant echoes");
			}
		}
	}

	public void lowpass(int   filter, float gain, float cutoff) {  // Set reverb send filter values and set source to send to all reverb fx slots
		if (Float.isNaN(gain  )) gain   = 1.0f;
		if (Float.isNaN(cutoff)) cutoff = 1.0f;
		EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
		ALUtils.errorProperty("filter", filter, "gain", gain);

		EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, cutoff);
		ALUtils.errorProperty("filter", filter, "cutoff", cutoff);
	}
	public void setFilter(int source, int id, float gain, float cutoff) {
		final int filter = ALset.filters[id];
		final int slot   = ALset.slots[id];
		lowpass(filter, gain, cutoff);
		// TODO: figure out how to properly use `AL11.alSource3i(` so i don't have to predetermine reverb.
		AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 1, filter);
		ALUtils.errorApply(new String[]{"filter", "slot"}, new int[]{filter, slot}, "source", source);
	}

	public void setDirect(final int sourceID, float gain, float cutoff, boolean isGentle) {
		float min = isGentle ? 0.5f : 0;
		gain   = Mth.clamp(gain,   min, 1);
		cutoff = Mth.clamp(cutoff, min, 1);
		lowpass(ALset.direct, gain, cutoff);

		AL10.alSourcei(sourceID, EXTEfx.AL_DIRECT_FILTER, ALset.direct);
		ALUtils.errorApply("direct filter", ALset.direct, "source", sourceID);
	}

	@Override
	public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
		if (Engine.mc != null && Engine.mc.level != null && Engine.mc.player != null) {
			Vec3 soundPos = Engine.soundPos;
			RoomAcousticsAnalyzer.RoomAnalysis room =
					RoomAcousticsAnalyzer.analyzeRoom(Engine.mc.level, soundPos);
			adjustReverbForRoom(slot, sound, room);
		}

		setFilter(sound.sourceID(), slot.slot(), (float) slot.gain(), (float) slot.cutoff());
		setDirect(sound.sourceID(), (float) sound.directGain(), (float) sound.directCutoff(), isGentle);
		return context;
	}

	private void adjustReverbForRoom(SlotProfile slot, SoundProfile sound,
									 RoomAcousticsAnalyzer.RoomAnalysis room) {
		double rt60 = RoomAcousticsAnalyzer.calculateReverberationTime(room);
		double earlyDelay = RoomAcousticsAnalyzer.calculateEarlyReflectionsDelay(room);
		double lateDelay = RoomAcousticsAnalyzer.calculateLateReverbDelay(room);
		double density = RoomAcousticsAnalyzer.calculateDensity(room);
		double diffusion = RoomAcousticsAnalyzer.calculateDiffusion(room);

		int slotId = slot.slot();
		int effectId = ALset.effects[slotId];

		applyRoomAdjustedEffect(slotId, effectId, room, rt60, earlyDelay, lateDelay, density, diffusion);
	}

	private void applyRoomAdjustedEffect(int id, int effect, RoomAcousticsAnalyzer.RoomAnalysis room,
										 double rt60, double earlyDelay, double lateDelay,
										 double density, double diffusion) {
		int slot = ALset.slots[id];
		float decayTime = (float) Math.max(0.1, Math.min(20.0, rt60));
		float reflectionsGain = (float) Math.max(0.05, 0.5 * (1.0 - room.averageAbsorption));
		double volumeFactor = Math.min(1.0, room.volume / 10000.0);
		float lateReverbGain = (float) (0.618 + Math.sqrt(volumeFactor));

		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, decayTime);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, (float)density);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, (float)diffusion);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, reflectionsGain);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, (float)earlyDelay);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, lateReverbGain);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, (float)lateDelay);

		float gainHF = (float) Math.max(0.1, 0.95 - room.averageAbsorption * 0.7);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAINHF, gainHF);

		EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);

		if (pC.dLog && !ALUtils.errorApply("effect", effect, "slot", slot)) {
			LOGGER.info("Adjusted effect.{} for room type: {}", effect, room.roomType);
		}
	}

	@Override
	public boolean init() {
		boolean success;
		for(int i = 1; i <= pC.resolution; i++){
			double t = (double) i / pC.resolution;

			double woolRoomFactor = calculateWoolRoomPhysicalFactor();

			apply(i - 1,
					(float) Math.max(t * pC.maxDecayTime * woolRoomFactor, 0.05),
					(float) ((t * 0.5 + 0.5) * woolRoomFactor),
					(float) Mth.lerp(pC.rvrbDiff * woolRoomFactor, 1-t, 1),
					(float) (0.95 - (0.75 * t * woolRoomFactor)),
					(float) Math.max(0.95 - (0.3 * t * woolRoomFactor), 0.05),
					(float) Math.max(Math.pow(1 - t, 0.5) + 0.618, 0.05) * (float)woolRoomFactor,
					(float) (t * 0.01),
					(float) (Math.pow(t, 0.5) + 0.618) * (float)woolRoomFactor,
					(float) (t * 0.01)
			);
		}

		EXTEfx.alFilteri(ALset.direct, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
		success = !ALUtils.checkErrors("Failed to initialize direct filter object!");
		if (success) {
			if (pC.dLog) LOGGER.info("Finished initializing OpenAL Auxiliary Effect slots!");
			return success;
		}
		LOGGER.info("Failed to properly initialize OpenAL Auxiliary Effect slots. Aborting");
		return success;
	}

	public void applyWithEcho(int id, EchoDetector.EchoAnalysis echoAnalysis,
							  float decayTime, float density, float diffusion,
							  float gainHF, float decayHFRatio, float reflectionsGain,
							  float reflectionsDelay, float lateReverbGain, float lateReverbDelay) {

		int slot = ALset.slots[id];
		int effect = ALset.effects[id];

		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, decayTime);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, density);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, diffusion);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAINHF, gainHF);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, decayHFRatio);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, reflectionsGain);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, reflectionsDelay);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, lateReverbGain);
		EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, lateReverbDelay);

		if (echoAnalysis.hasClearEcho && echoAnalysis.echoTimes.size() > 0) {
			float echoTime = (float) echoAnalysis.echoTimes.get(0).doubleValue(); // 第一个明显回声的时间
			float echoDepth = (float) Math.min(1.0, echoAnalysis.echoDensity * 0.5);

			try {
				EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_ECHO_TIME, echoTime);
				EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_ECHO_DEPTH, echoDepth);

				float modulationTime = echoTime * 2.0f;
				float modulationDepth = echoDepth * 0.3f;
				EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_MODULATION_TIME, modulationTime);
				EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_MODULATION_DEPTH, modulationDepth);

			} catch (Exception e) {
				if (echoDepth > 0.3) {
					EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, Math.min(1.0f, density + echoDepth * 0.3f));
					EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, Math.min(1.0f, diffusion + echoDepth * 0.2f));
					EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN,
							reflectionsGain * (1.0f + echoDepth * 0.5f));
				}
			}
		}

		EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);

		if (pC.dLog && !ALUtils.errorApply("effect", effect, "slot", slot)) {
			LOGGER.info("Applied effect with echo detection. Echoes: {}",
					echoAnalysis.echoTimes.size());
		}
	}

	private double calculateWoolRoomPhysicalFactor() {
		if (Engine.mc == null || Engine.mc.player == null || Engine.mc.level == null) {
			return 1.0;
		}

		Vec3 playerPos = Engine.mc.player.position();
		BlockPos center = BlockPos.containing(playerPos);

		double totalAbsorption = 0.0;
		int sampleCount = 0;

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
						sampleCount++;
					}
				}
			}
		}

		if (sampleCount == 0) return 1.0;

		double avgAbsorption = totalAbsorption / sampleCount;

		if (avgAbsorption > 0.6) {
			return Math.max(0.1, 1.0 - avgAbsorption * 0.8);
		}

		return 1.0;
	}
}