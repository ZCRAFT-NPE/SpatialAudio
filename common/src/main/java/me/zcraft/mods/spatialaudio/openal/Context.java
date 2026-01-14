package me.zcraft.mods.spatialaudio.openal;

import me.zcraft.mods.spatialaudio.Engine;
import me.zcraft.mods.spatialaudio.Utils;
import me.zcraft.mods.spatialaudio.effects.Doppler;
import me.zcraft.mods.spatialaudio.effects.Echo;
import me.zcraft.mods.spatialaudio.effects.Effect;
import me.zcraft.mods.spatialaudio.effects.Reverb;
import me.zcraft.mods.spatialaudio.toolbox.SlotProfile;
import me.zcraft.mods.spatialaudio.toolbox.SoundProfile;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.EXTThreadLocalContext;

import java.util.Objects;

import static me.zcraft.mods.spatialaudio.config.PrecomputedConfig.pC;

public class Context extends Utils {
	public ALset[] contexts;
	public boolean active = false;
	public boolean enabled = true;
	public boolean garbage = false;
	private long old = -1;
	private long self = 0;
	private boolean bound = false;
	@Nullable
	public String id = null;
	public Context[] children;
	public Effect[] effects;
	public Echo echoEffect;
	private boolean echoActive = true;
	public Doppler dopplerEffect;
	public boolean dopplerActive = true;

	public void activate() {
		old = EXTThreadLocalContext.alcGetThreadContext();
		if (self != 0) {
			EXTThreadLocalContext.alcSetThreadContext(self);
			ALUtils.checkErrors("Error while activating openAL context " + self + ".");
		}
	}

	public void deactivate() {
		if (old == -1) return;
		EXTThreadLocalContext.alcSetThreadContext(old);
		ALUtils.checkErrors("Error while reactivating openAL context " + old + ".");
		old = -1;
	}

	private void populateEffects() {
		effects = new Effect[] { new Reverb() };
		echoEffect = new Echo();
		dopplerEffect = new Doppler();
		contexts = new ALset[effects.length + 2];
		for (int i = 0; i < contexts.length; i++) {
			contexts[i] = new ALset();
		}
	}

	public boolean bind(long context, @Nullable final String name) {
		bound = true;
		self = context;
		return setup(name);
	}

	public boolean setup(@Nullable final String name) {
		if (active) {
			Engine.LOGGER.warn("Context {} is already active, cleaning first", name);
			clean(true);
		}

		children = new Context[0];
		effects = new Effect[0];
		echoEffect = null;
		garbage = false;
		id = name;

		if (bound) {
			old = EXTThreadLocalContext.alcGetThreadContext();
		} else {
			self = EXTThreadLocalContext.alcGetThreadContext();
		}

		activate();
		populateEffects();

		for (int i = 0; i < effects.length; i++) {
			contexts[i] = effects[i].setup(self);
			effects[i].init();
		}

		if (pC.enableEcho) {
			contexts[effects.length] = echoEffect.setup(self);
			echoEffect.init();
			echoActive = true;
		}

		contexts[effects.length + 1] = dopplerEffect.setup(self);
		dopplerEffect.init();
		dopplerActive = true;

		deactivate();
		active = true;
		Engine.LOGGER.info("Context {} setup complete with {} effects + echo", id, effects.length);
		return true;
	}

	public boolean clean(final boolean force) {
		if (garbage && !force) return true;

		Engine.LOGGER.info("{}: cleaning children[{}]", id, children.length);
		boolean success = true;

		activate();
		for (Context child : children) {
			if (!child.clean(force)) success = false;
		}
		deactivate();

		if (!cleanObjects()) success = false;

		garbage = force || success;
		active = false;
		echoActive = false;

		if (pC.dLog) {
			if (success) Engine.LOGGER.info("Cleaned context: {}.", id);
			else Engine.LOGGER.error("Context remains: {}.", id);
		}

		return success;
	}

	public boolean cleanObjects() {
		if (contexts == null || effects == null) return true;

		boolean success = true;

        for (ALset context : contexts) {
            if (context != null) {
                success = cleanSlots(context) && success;
                success = cleanEffects(context) && success;
                success = cleanFilters(context) && success;
                success = cleanDirect(context) && success;
                success = cleanEcho(context) && success;
                context.clear();
            }
        }

		contexts = null;
		effects = null;
		if (echoEffect != null) {
			echoEffect.cleanup();
			echoEffect = null;
		}
		if (dopplerEffect != null) {
			dopplerEffect.cleanup();
			dopplerEffect = null;
		}
		return success;
	}

	public boolean isDopplerActive() { return dopplerActive; }

	private boolean cleanSlots(ALset context) {
		if (context.slots == null || context.slots.length == 0) return true;

		if (pC.dLog) Engine.LOGGER.info("Removing {} slots", context.slots.length);

		for (int slot : context.slots) {
			if (ALUtils.isValidSlot(slot)) {
				EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
			}
		}

		EXTEfx.alDeleteAuxiliaryEffectSlots(context.slots);

		boolean allDeleted = true;
		for (int slot : context.slots) {
			if (ALUtils.isValidSlot(slot)) {
				Engine.LOGGER.error("Failed to delete slot.{}", slot);
				allDeleted = false;
			}
		}

		context.slots = new int[0];
		return allDeleted;
	}

	private boolean cleanEffects(ALset context) {
		if (context.effects == null || context.effects.length == 0) return true;

		if (pC.dLog) Engine.LOGGER.info("Removing {} effects", context.effects.length);

		EXTEfx.alDeleteEffects(context.effects);

		boolean allDeleted = true;
		for (int effect : context.effects) {
			if (ALUtils.isValidEffect(effect)) {
				Engine.LOGGER.error("Failed to delete effect.{}", effect);
				allDeleted = false;
			}
		}

		context.effects = new int[0];
		return allDeleted;
	}

	private boolean cleanFilters(ALset context) {
		if (context.filters == null || context.filters.length == 0) return true;

		if (pC.dLog) Engine.LOGGER.info("Removing {} filters", context.filters.length);

		EXTEfx.alDeleteFilters(context.filters);

		boolean allDeleted = true;
		for (int filter : context.filters) {
			if (ALUtils.isValidFilter(filter)) {
				Engine.LOGGER.error("Failed to delete filter.{}", filter);
				allDeleted = false;
			}
		}

		context.filters = new int[0];
		return allDeleted;
	}

	private boolean cleanDirect(ALset context) {
		if (context.direct == 0) return true;

		if (ALUtils.isValidFilter(context.direct)) {
			EXTEfx.alDeleteFilters(new int[]{context.direct});
			if (ALUtils.isValidFilter(context.direct)) {
				Engine.LOGGER.error("Failed to delete direct filter object!");
				return false;
			}
		}

		context.direct = 0;
		return true;
	}

	private boolean cleanEcho(ALset context) {
		if (context.echoSlot == 0 && context.echoEffect == 0) return true;

		if (pC.dLog) Engine.LOGGER.info("Removing echo effect");

		if (context.echoSlot != 0 && ALUtils.isValidSlot(context.echoSlot)) {
			EXTEfx.alAuxiliaryEffectSloti(context.echoSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
			EXTEfx.alDeleteAuxiliaryEffectSlots(new int[]{context.echoSlot});

			if (ALUtils.isValidSlot(context.echoSlot)) {
				Engine.LOGGER.error("Failed to delete echo slot!");
				return false;
			}
		}

		if (context.echoEffect != 0 && ALUtils.isValidEffect(context.echoEffect)) {
			EXTEfx.alDeleteEffects(new int[]{context.echoEffect});

			if (ALUtils.isValidEffect(context.echoEffect)) {
				Engine.LOGGER.error("Failed to delete echo effect!");
				return false;
			}
		}

		context.echoSlot = 0;
		context.echoEffect = 0;
		return true;
	}

	public boolean addChild(Context child) {
		final boolean query = queryChild(child.getID()) == -1;
		if (query) children = ArrayUtils.add(children, child);
		return query;
	}

	public int queryChild(@Nullable String name) {
		for (int i = 0; i < children.length; i++) {
			if (children[i].getID(name)) return i;
		}
		return -1;
	}

	public boolean getID(@Nullable String guess) { return Objects.equals(guess, id); }
	public String getID() { return id; }
	public boolean isGarbage() { return garbage; }
	public boolean isEchoActive() { return echoActive; }

	public void update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
		if (!(active && enabled)) return;

		if (!ALUtils.isValidSource(sound.sourceID())) return;

		activate();
		for (int i = 0; i < effects.length; i++) {
			contexts[i] = effects[i].update(slot, sound, isGentle);
		}

		if (echoActive && echoEffect != null && sound.echoAnalysis() != null) {
			echoEffect.applyFromAnalysis(sound.echoAnalysis());
			echoEffect.attachToSource(sound.sourceID(), 0,
					sound.echoAnalysis().hasClearEcho && sound.echoAnalysis().echoClarity > 0.3);
		}

		if (dopplerActive && dopplerEffect != null) {
			dopplerEffect.update(slot, sound, isGentle);
		}

		deactivate();
	}
}