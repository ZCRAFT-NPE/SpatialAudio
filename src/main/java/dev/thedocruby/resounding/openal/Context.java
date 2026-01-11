package dev.thedocruby.resounding.openal;

import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.Engine;
import dev.thedocruby.resounding.Utils;
import dev.thedocruby.resounding.effects.*;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.ArrayUtils;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.EXTThreadLocalContext;

import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

import javax.annotation.Nullable;

import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.function.Consumer;


@Environment(EnvType.CLIENT)
public class Context extends Utils {

	private ALset[] contexts;
	public  boolean active  = false;
	public  boolean enabled = true ;
	public  boolean garbage = false;

	private long old  = -1;
	private long self = 0 ;

	private boolean bound = false;

	@Nullable public String id = null;
	public  Context[] children;
	public  Effect[]  effects;

	public void activate() {
		old = EXTThreadLocalContext.alcGetThreadContext();
		EXTThreadLocalContext.alcSetThreadContext(self);
		ALUtils.checkErrors("Error while activating openAL context " + self + ".");
	}
	public void deactivate() {
		if (old == -1) return;
		EXTThreadLocalContext.alcSetThreadContext(old);
		ALUtils.checkErrors("Error while reactivating openAL context " + old + ".");
	}

	private void populateEffects() {
		effects = new Effect[] {
				new Reverb(),

				new Occlusion(),
				new Doppler(),
				//new Resonance(),
				//new Water(),
				//new Atmosphere(),
				new Echo(),
				//new Travel()
		};
		contexts = new ALset[effects.length];
	}

	public         boolean bind(long context, @Nullable final String name) {
		bound = true;
		self = context;
		return setup(name);
	}

	public         boolean setup(@Nullable final String name) {
		if (active) return false;
		children = new Context[0];
		effects  = new Effect[0];
		garbage = false;
		id = name;
		if (bound) old  = EXTThreadLocalContext.alcGetThreadContext();
		else       self = EXTThreadLocalContext.alcGetThreadContext();
		activate();
		populateEffects();
		for (int i = 0; i<effects.length; i++) {
			contexts[i] = effects[i].setup(self);
			effects[i].init();
		}
		deactivate();
		active = true;
		return true;
	}
	public boolean clean(final boolean force) {
		if (garbage) return force;
		Engine.LOGGER.info("{}: cleaning children[{}]", id, children.length);
		boolean success = cleanObjects();
		activate();
		for (Context child : children) {
			if (!child.clean(force)) success = false;
		}
		deactivate();
		garbage = force || success;
		active = false;
		if (pC.dLog) {
			if (success) Engine.LOGGER.info ("Cleaned context: {}.", id);
			else         Engine.LOGGER.error("Context remains: {}.", id);
		}
		return force;
	}

	public  boolean cleanObjects() {
		boolean success = true;
		for (ALset ignored : contexts) {
			success = cleanSlots() && success;
			success = cleanEffects() && success;
			success = cleanFilters() && success;
			success = cleanDirect() && success;
		}
		return success;
	}
	private int[]   deleteAL(final String type, int[] set, Consumer<int[]> delete, IntPredicate verify) {
		if (pC.dLog) Engine.LOGGER.info("Removing {}[{}]", type, set.length);
		delete.accept(set.clone());
		for (int bit : set) {
			if (verify.test(bit)) { Engine.LOGGER.error("Failed to delete {}.{}", type, bit); continue; }
			set = ArrayUtils.removeElement(set, bit);
			if (pC.dLog) Engine.LOGGER.info("Deleting {}.{}", type, bit);
		}
		return set;
	}
	private boolean cleanSlots() {
		ALset.slots = deleteAL(
				"slot", ALset.slots,
				EXTEfx::alDeleteAuxiliaryEffectSlots,
				EXTEfx::alIsAuxiliaryEffectSlot
		);
		return ALset.slots.length == 0;
	}
	private boolean cleanEffects() {
		ALset.effects = deleteAL(
				"effect", ALset.effects,
				EXTEfx::alDeleteEffects,
				EXTEfx::alIsEffect
		);
		return ALset.effects.length == 0;
	}
	private boolean cleanFilters() {
		ALset.filters = deleteAL(
				"filter", ALset.filters,
				EXTEfx::alDeleteFilters,
				EXTEfx::alIsFilter
		);
		return ALset.filters.length == 0;
	}
	private boolean cleanDirect() {
		EXTEfx.alDeleteFilters(ALset.direct);
		if (EXTEfx.alIsFilter(ALset.direct)) {
			Engine.LOGGER.error("Failed to delete direct filter object!"); return false;
		} else if (pC.dLog) {
			Engine.LOGGER.info("Direct filter object deleted with ID {}", ALset.direct);
		}
		return true;
	}

	public  boolean addChild(Context child) {
		final boolean query = queryChild(child.getID()) == -1;
		if (query) {
			children = ArrayUtils.add(children, child);
		}
		return query;
	}
	public  int queryChild(@Nullable String name) {
		for (int i = 0; i<children.length; i++) {
			if (children[i].getID(name)) return i;
		}
		return -1;
	}

	public  boolean getID(@Nullable String guess) {return Objects.equals(guess, id);}
	public  String  getID()                       {return id;}
	public  boolean isGarbage()                   {return garbage;}

	public  void update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
		if (!(active && enabled)) return;
		activate();
		for (int i = 0; i<effects.length; i++) {
			contexts[i] = effects[i].update(slot, sound, isGentle);
		}
		deactivate();
	}

}