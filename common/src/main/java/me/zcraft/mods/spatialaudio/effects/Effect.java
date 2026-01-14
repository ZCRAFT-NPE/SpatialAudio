package me.zcraft.mods.spatialaudio.effects;

import me.zcraft.mods.spatialaudio.Engine;
import me.zcraft.mods.spatialaudio.Utils;
import me.zcraft.mods.spatialaudio.openal.ALUtils;
import me.zcraft.mods.spatialaudio.openal.ALset;
import me.zcraft.mods.spatialaudio.toolbox.SlotProfile;
import me.zcraft.mods.spatialaudio.toolbox.SoundProfile;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

import static me.zcraft.mods.spatialaudio.config.PrecomputedConfig.pC;

public class Effect extends Utils {
	public ALset context;
	public String name = "";
	public boolean active = false;

	public boolean init() { return false; }

	public ALset setup(final long id) {
		if (pC.dLog) Engine.LOGGER.info("loading effect {}", name);

		context = new ALset();
		context.self = id;
		active = true;

		if (!(setupSlots() && setupEffects() && setupFilters() && setupDirect())) {
			Engine.LOGGER.error("Failed to setup effect: {}", name);
			active = false;
		} else {
			if (pC.dLog) Engine.LOGGER.info("Setup effect: {}", name);
		}
		return context;
	}

	public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) { return context; }

	private int[] generAL(final String type, Consumer<int[]> generate, IntPredicate verify, IntConsumer init) {
		if (pC.dLog) Engine.LOGGER.info("Creating {}[{}]", type, pC.resolution);

		int[] set = new int[pC.resolution];
		generate.accept(set);

		for (int i = 0; i < set.length; i++) {
			int bit = set[i];
			if (bit != 0) {
				if (verify.test(bit)) {
					init.accept(bit);
					if (ALUtils.checkErrors(s -> Engine.LOGGER.info(s + "Failed to create {}.{}", type, bit))) {
						active = false;
						set[i] = 0;
					} else {
						if (pC.dLog) Engine.LOGGER.info("Created {}.{}", type, bit);
					}
				} else {
					Engine.LOGGER.error("Failed create {}.{}", type, bit);
					active = false;
					set[i] = 0;
				}
			}
		}

		int validCount = 0;
		for (int bit : set) if (bit != 0) validCount++;

		if (validCount == 0) return new int[0];

		int[] validSet = new int[validCount];
		int idx = 0;
		for (int bit : set) if (bit != 0) validSet[idx++] = bit;

		return validSet;
	}

	private boolean setupSlots() {
		context.slots = generAL(
				"slot",
				EXTEfx::alGenAuxiliaryEffectSlots,
				EXTEfx::alIsAuxiliaryEffectSlot,
				s -> EXTEfx.alAuxiliaryEffectSloti(s, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE)
		);
		return context.slots.length > 0;
	}

	private boolean setupEffects() {
		context.effects = generAL(
				"effect",
				EXTEfx::alGenEffects,
				EXTEfx::alIsEffect,
				e -> EXTEfx.alEffecti(e, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB)
		);
		return context.effects.length > 0;
	}

	private boolean setupFilters() {
		context.filters = generAL(
				"filter",
				EXTEfx::alGenFilters,
				EXTEfx::alIsFilter,
				f -> EXTEfx.alFilteri(f, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS)
		);
		return context.filters.length > 0;
	}

	private boolean setupDirect() {
		context.direct = EXTEfx.alGenFilters();
		if (context.direct <= 0) {
			Engine.LOGGER.error("Failed to create direct filter object!");
			return false;
		}

		if (!EXTEfx.alIsFilter(context.direct)) {
			Engine.LOGGER.error("Direct filter object is not a valid filter!");
			context.direct = 0;
			return false;
		}

		EXTEfx.alFilteri(context.direct, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
		if (ALUtils.checkErrors("Failed to initialize direct filter")) {
			EXTEfx.alDeleteFilters(new int[]{context.direct});
			context.direct = 0;
			return false;
		}

		if (pC.dLog) Engine.LOGGER.info("Direct filter object created with ID {}", context.direct);
		return true;
	}
}