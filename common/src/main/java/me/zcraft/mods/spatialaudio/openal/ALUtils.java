package me.zcraft.mods.spatialaudio.openal;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import java.util.function.Consumer;

import static me.zcraft.mods.spatialaudio.Engine.LOGGER;
import static me.zcraft.mods.spatialaudio.config.PrecomputedConfig.pC;

public class ALUtils {

	private ALUtils(){}

	@Contract(pure = true)
	private static @NotNull String getErrorMessage(int errorCode) {
		return switch (errorCode) {
			case AL10.AL_INVALID_NAME -> "Invalid name parameter. (AL_INVALID_NAME)";
			case AL10.AL_INVALID_ENUM -> "Illegal enum value. (AL_INVALID_ENUM)";
			case AL10.AL_INVALID_VALUE -> "Invalid input value. (AL_INVALID_VALUE)";
			case AL10.AL_INVALID_OPERATION -> "Invalid operation. (AL_INVALID_OPERATION)";
			case AL10.AL_OUT_OF_MEMORY -> "Unable to allocate memory. (AL_OUT_OF_MEMORY)";
			default -> "An unrecognized error. (AL_" + errorCode + ')';
		};
	}

	public static boolean checkErrors(Consumer<String> callback) {
		int i = AL10.alGetError();
		if (i != AL10.AL_NO_ERROR) {
			if (pC.dLog) callback.accept("OpenAL AL error "+getErrorMessage(i)+": ");
			return true;
		}
		return false;
	}

	public static boolean checkErrors(String message) {
		return checkErrors(s -> LOGGER.info(s+message));
	}

	@Contract(pure = true)
	private static @NotNull String getAlcErrorMessage(int errorCode) {
		return switch (errorCode) {
			case ALC10.ALC_INVALID_DEVICE -> "Invalid device. (ALC_INVALID_DEVICE)";
			case ALC10.ALC_INVALID_CONTEXT -> "Invalid context. (ALC_INVALID_CONTEXT)";
			case ALC10.ALC_INVALID_ENUM -> "Illegal enum value. (ALC_INVALID_ENUM)";
			case ALC10.ALC_INVALID_VALUE -> "Invalid input value. (ALC_INVALID_VALUE)";
			case ALC10.ALC_OUT_OF_MEMORY -> "Unable to allocate memory. (ALC_OUT_OF_MEMORY)";
			default -> "An unrecognized error. (ALC_" + errorCode + ')';
		};
	}

	private static boolean checkAlcErrors(long deviceHandle, String message) {
		int i = ALC10.alcGetError(deviceHandle);
		if (i != ALC10.ALC_NO_ERROR) {
			LOGGER.error("Caught new OpenAL ALC10 error!\n{}\nCaused by: {}\nDevice: {}", message, getAlcErrorMessage(i), deviceHandle);
			return true;
		}
		return false;
	}

	public static boolean errorApply(String   in, int   inID, String out, int outID) {
		return errorApply(new String[]{in}, new int[]{inID}, out, outID);
	}

	public static boolean errorApply(String[] in, int[] inIDs, String out, int outID) {
		if (in.length != inIDs.length) throw new IllegalStateException("differing input lengths");
		String[] message = {};
		for (int i = 0; i<in.length; i++) {
			message = ArrayUtils.add(message, in[i]+"."+inIDs[i]);
		}
		return ALUtils.checkErrors("Error while applying "+String.join(" & ", message)+" to "+out+"."+outID);
	}

	public static void errorProperty(String type, int id, String property, float value) {
		ALUtils.checkErrors("Error while setting " + type + "." + id + "." + property + " to " + value);
	}

	public static void errorSet(String type, String subset, int id, float value) {
		ALUtils.checkErrors("Error while setting " + type + "." + subset + "." + id + " to " + value);
	}

	public static boolean isValidSource(int source) {
		if (source <= 0) return false;
		return AL10.alIsSource(source);
	}

	public static boolean isValidBuffer(int buffer) {
		if (buffer <= 0) return false;
		return AL10.alIsBuffer(buffer);
	}

	public static boolean isValidEffect(int effect) {
		if (effect <= 0) return false;
		try {
			return org.lwjgl.openal.EXTEfx.alIsEffect(effect);
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean isValidFilter(int filter) {
		if (filter <= 0) return false;
		try {
			return org.lwjgl.openal.EXTEfx.alIsFilter(filter);
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean isValidSlot(int slot) {
		if (slot <= 0) return false;
		try {
			return org.lwjgl.openal.EXTEfx.alIsAuxiliaryEffectSlot(slot);
		} catch (Exception e) {
			return false;
		}
	}
}