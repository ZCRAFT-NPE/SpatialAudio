package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.openal.*;
import static dev.thedocruby.resounding.Engine.LOGGER;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

import net.minecraft.util.Mth;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

public class Reverb extends Effect {

	public void apply(
			int id,
			float decayTime,
			float density,
			float diffusion,
			float gainHF,
			float decayHFRatio,
			float reflectionsGain,
			float reflectionsDelay,
			float lateReverbGain,
			float lateReverbDelay
	)  {
		int slot   = ALset.slots[id];
		int effect = ALset.effects[id];

		SIF[] effects = {
				new SIF("density"            , EXTEfx.AL_EAXREVERB_DENSITY              , density         ),
				new SIF("diffusion"          , EXTEfx.AL_EAXREVERB_DIFFUSION            , diffusion       ),
				new SIF("air_absorption_gain", EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, 1f              ),
				new SIF("late_delay"         , EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY    , lateReverbDelay ),
				new SIF("late_gain"          , EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN     , lateReverbGain  ),
				new SIF("reflections_delay"  , EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY    , reflectionsDelay),
				new SIF("reflections_gain"   , EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN     , reflectionsGain ),
				new SIF("HF_decay_ratio"     , EXTEfx.AL_EAXREVERB_DECAY_HFRATIO        , decayHFRatio    ),
				new SIF("decay_time"         , EXTEfx.AL_EAXREVERB_DECAY_TIME           , decayTime       ),
				new SIF("HF_gain"            , EXTEfx.AL_EAXREVERB_GAINHF               , gainHF          )
		};
		for (SIF sif : effects) {
			EXTEfx.alEffectf(effect, sif.s, sif.t);
			ALUtils.errorSet("effect", sif.f, effect, sif.t);
		}
		EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
		if (pC.dLog && !ALUtils.errorApply("effect", effect, "slot", slot)) {
			LOGGER.info("Initialized effect.{}", effect);
		}
	}

	public void lowpass(int filter, float gain, float cutoff) {
		if (Float.isNaN(gain)) gain = 1.0f;
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

		try {
			int reverbSlot = Mth.clamp(id, 0, ALset.slots.length - 1);
			AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, reverbSlot, 0, filter);
		} catch (Exception e) {
			AL10.alSourcei(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot);
		}

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
		setFilter(sound.sourceID(), slot.slot(), (float) slot.gain(), (float) slot.cutoff());
		setDirect(sound.sourceID(), (float) sound.directGain(), (float) sound.directCutoff(), isGentle);
		return context;
	}

	@Override
	public void init() {
		boolean success;
		for(int i = 1; i <= pC.resolution; i++){
			double t = (double) i / pC.resolution;
			apply(i - 1,
					(float) Math.max(t * pC.maxDecayTime, 0.1),          // decayTime
					(float) (t * 0.5 + 0.5),                             // density
					(float) Mth.lerp(pC.rvrbDiff, 1-t, 1),        // diffusion
					(float) (0.95 - (0.75 * t)),                         // gainHF
					(float) Math.max(0.95 - (0.3 * t), 0.1),             // decayHFRatio
					(float) Math.max(Math.pow(1 - t, 0.5) + 0.618, 0.1), // reflectionsGain
					(float) (t * 0.01),                                  // reflectionsDelay
					(float) (Math.pow(t, 0.5) + 0.618),                  // lateReverbGain
					(float) (t * 0.01)                                   // lateReverbDelay
			);
		}
		EXTEfx.alFilteri(ALset.direct, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
		success = !ALUtils.checkErrors("Failed to initialize direct filter object!");
		if (success) {
			if (pC.dLog) LOGGER.info("Finished initializing OpenAL Auxiliary Effect slots!");
			return;
		}
		LOGGER.warn("Some errors occurred during OpenAL Auxiliary Effect slots initialization, but continuing anyway.");
	}

	private static class SIF {
		public final String f;
		public final int s;
		public final float t;

		public SIF(String f, int s, float t) {
			this.f = f;
			this.s = s;
			this.t = t;
		}
	}
}