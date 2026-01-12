package dev.thedocruby.resounding.openal;

import dev.thedocruby.resounding.Engine;
import dev.thedocruby.resounding.Utils;
import dev.thedocruby.resounding.effects.Effect;
import dev.thedocruby.resounding.effects.Reverb;
import dev.thedocruby.resounding.toolbox.SlotProfile;
import dev.thedocruby.resounding.toolbox.SoundProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.ArrayUtils;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.EXTThreadLocalContext;

import javax.annotation.Nullable;
import java.util.Objects;

import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

@Environment(EnvType.CLIENT)
public class Context extends Utils {
	public ALset[] contexts;
	public boolean active = false;
	public boolean enabled = true;
	public boolean garbage = false;
	private long old = -1;
	private long self = 0;
	private boolean bound = false;
	@Nullable public String id = null;
	public Context[] children;
	public Effect[] effects;

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
		contexts = new ALset[effects.length];
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

		deactivate();
		active = true;
		Engine.LOGGER.info("Context {} setup complete with {} effects", id, effects.length);
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

		if (pC.dLog) {
			if (success) Engine.LOGGER.info("Cleaned context: {}.", id);
			else Engine.LOGGER.error("Context remains: {}.", id);
		}

		return success;
	}

	public boolean cleanObjects() {
		if (contexts == null || effects == null) return true;

		boolean success = true;

		for (int i = 0; i < contexts.length; i++) {
			ALset context = contexts[i];
			if (context != null) {
				success = cleanSlots(context) && success;
				success = cleanEffects(context) && success;
				success = cleanFilters(context) && success;
				success = cleanDirect(context) && success;
				context.clear();
			}
		}

		contexts = null;
		effects = null;
		return success;
	}

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

	public void update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
		if (!(active && enabled)) return;

		if (!ALUtils.isValidSource(sound.sourceID())) return;

		activate();
		for (int i = 0; i < effects.length; i++) {
			contexts[i] = effects[i].update(slot, sound, isGentle);
		}
		deactivate();
	}
}