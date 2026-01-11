package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.openal.*;
import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.Engine;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.AL11;
import net.minecraft.util.Mth;

import java.util.*;

public class Echo extends Effect {
    private float delay = 0f;
    private float decay = 0f;
    private float spread = 0f;
    private float damping = 0.7f;
    private float lrDelay = 0f;

    private static final float SPEED_OF_SOUND = 343.0f; // meters per second
    private static final float MIN_ECHO_ENERGY = 0.02f;
    private static final float MAX_DELAY = 0.5f; // seconds
    private static final float MIN_DELAY = 0.02f; // seconds
    private final List<EchoPath> echoPaths = new ArrayList<>();

    public Echo() {
        name = "Echo";
    }

    @Override
    public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
        if (!active) return context;

        analyzeEchoPaths(sound);
        calculateEchoParameters(sound, isGentle);
        applyEchoEffect(sound);

        return context;
    }

    private void analyzeEchoPaths(SoundProfile sound) {
        echoPaths.clear();

        if (sound.position() == null || Engine.mc == null || Engine.mc.level == null ||
                Engine.mc.player == null) return;

        // Get existing reflection data from Engine (simulate by creating test data)
        // In practice, we would access the actual reflection data calculated by Engine.evalEnv()
        analyzeSoundEnvironment(sound);
    }

    private void analyzeSoundEnvironment(SoundProfile sound) {
        Vec3 soundPos = sound.position();
        Vec3 listenerPos = Engine.mc.player.getEyePosition();
        float directDistance = (float) soundPos.distanceTo(listenerPos);

        // Use existing ray tracing data from Engine to detect echo paths
        // This is a simplified analysis based on environment factors
        boolean isEnclosed = detectEnclosedSpace(soundPos);
        boolean isUnderground = Engine.mc.player.getY() < 48;

        float enclosureFactor = calculateEnclosureFactor(soundPos);
        float distanceFactor = Mth.clamp(directDistance / 50.0f, 0.1f, 0.8f);

        // Calculate potential echo paths based on environment
        float echoProbability = 0f;
        float avgDelay = 0f;
        float avgEnergy = 0f;

        if (isEnclosed && isUnderground) {
            // Cave echo: multiple strong reflections
            echoProbability = 0.6f;
            avgDelay = 0.15f + 0.1f * distanceFactor;
            avgEnergy = 0.2f + 0.1f * enclosureFactor;
        } else if (isEnclosed) {
            // Indoor echo: moderate reflections
            echoProbability = 0.4f;
            avgDelay = 0.1f + 0.05f * distanceFactor;
            avgEnergy = 0.15f + 0.05f * enclosureFactor;
        } else if (isUnderground) {
            // Underground but not enclosed: weak echo
            echoProbability = 0.3f;
            avgDelay = 0.08f + 0.03f * distanceFactor;
            avgEnergy = 0.1f;
        } else {
            // Outdoor: very weak or no echo
            echoProbability = 0.1f;
            avgDelay = 0.05f;
            avgEnergy = 0.05f;
        }

        // Reduce echo in rain
        if (Engine.mc.level.isRaining()) {
            echoProbability *= 0.7f;
            avgEnergy *= 0.6f;
        }

        // Add main echo path if probability is high enough
        if (echoProbability > 0.2f && avgEnergy > MIN_ECHO_ENERGY) {
            echoPaths.add(new EchoPath(avgDelay, avgEnergy, echoProbability));
        }
    }

    private boolean detectEnclosedSpace(Vec3 pos) {
        if (Engine.mc.level == null) return false;

        BlockPos soundPos = new BlockPos(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.y),
                (int) Math.floor(pos.z)
        );

        int solidCount = 0;
        int checkCount = 0;

        // Check surrounding blocks
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    BlockPos checkPos = soundPos.offset(dx, dy, dz);
                    if (!Engine.mc.level.getBlockState(checkPos).isAir()) {
                        solidCount++;
                    }
                    checkCount++;
                }
            }
        }

        return checkCount > 0 && (float)solidCount / checkCount > 0.65f;
    }

    private float calculateEnclosureFactor(Vec3 pos) {
        if (Engine.mc.level == null) return 0f;

        BlockPos soundPos = new BlockPos(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.y),
                (int) Math.floor(pos.z)
        );

        int solidCount = 0;
        int totalChecked = 0;

        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dy = -3; dy <= 3; dy += 2) {
                for (int dz = -4; dz <= 4; dz += 2) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    BlockPos checkPos = soundPos.offset(dx, dy, dz);
                    if (!Engine.mc.level.getBlockState(checkPos).isAir()) {
                        solidCount++;
                    }
                    totalChecked++;
                }
            }
        }

        return totalChecked > 0 ? (float)solidCount / totalChecked : 0f;
    }

    private void calculateEchoParameters(SoundProfile sound, boolean isGentle) {
        if (echoPaths.isEmpty()) {
            // No echo detected
            delay = 0f;
            decay = 0f;
            spread = 0f;
            damping = 0.8f;
            lrDelay = 0f;
            return;
        }

        // Combine multiple echo paths into single echo effect
        float totalDelay = 0f;
        float totalEnergy = 0f;
        float totalProbability = 0f;

        for (EchoPath path : echoPaths) {
            totalDelay += path.delay * path.probability;
            totalEnergy += path.energy * path.probability;
            totalProbability += path.probability;
        }

        if (totalProbability > 0f) {
            float avgDelay = totalDelay / totalProbability;
            float avgEnergy = totalEnergy / totalProbability;

            // Calculate echo parameters
            delay = Mth.clamp(avgDelay, MIN_DELAY, MAX_DELAY);
            decay = Mth.clamp(avgEnergy * 0.8f, 0.01f, 0.3f);
            spread = Mth.clamp(avgEnergy * 1.5f, 0.1f, 0.5f);
            lrDelay = delay * 0.6f;

            // Adjust damping based on environment
            if (Engine.mc.level != null && Engine.mc.level.isRaining()) {
                damping = 0.5f; // More damping in rain
            } else {
                damping = 0.7f; // Normal damping
            }
        }

        // Apply gentle mode reduction
        if (isGentle) {
            delay *= 0.6f;
            decay *= 0.4f;
            spread *= 0.5f;
            damping *= 1.1f;
        }

        // Final clamping
        delay = Mth.clamp(delay, MIN_DELAY, MAX_DELAY);
        decay = Mth.clamp(decay, 0f, 0.25f);
        spread = Mth.clamp(spread, 0f, 0.4f);
        damping = Mth.clamp(damping, 0.4f, 0.9f);
        lrDelay = Mth.clamp(lrDelay, MIN_DELAY * 0.5f, MAX_DELAY * 0.8f);
    }

    private void applyEchoEffect(SoundProfile sound) {
        if (ALset.effects.length > 6 && ALset.slots.length > 6) {
            int effectId = ALset.effects[6];

            EXTEfx.alEffecti(effectId, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_ECHO);

            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_DELAY, delay);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_LRDELAY, lrDelay);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_DAMPING, damping);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_FEEDBACK, decay);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_SPREAD, spread);

            // Only apply echo if it's significant
            if (delay > MIN_DELAY && decay > 0.01f) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[6], EXTEfx.AL_EFFECTSLOT_EFFECT, effectId);

                // Calculate appropriate send level
                float echoSend = Mth.clamp(decay * 0.7f, 0f, 0.25f);

                try {
                    int slotId = ALset.slots[6];
                    int filter = EXTEfx.AL_FILTER_NULL;
                    AL11.alSource3i(sound.sourceID(), EXTEfx.AL_AUXILIARY_SEND_FILTER, slotId, 0, filter);
                } catch (Exception e) {
                    AL11.alSourcei(sound.sourceID(), EXTEfx.AL_AUXILIARY_SEND_FILTER, ALset.slots[6]);
                }

                if (pC.dLog && delay > MIN_DELAY) {
                    Engine.LOGGER.debug("Echo: delay={}s, decay={}, spread={}",
                            String.format("%.3f", delay),
                            String.format("%.2f", decay),
                            String.format("%.2f", spread));
                }
            } else {
                // Disable echo if too weak
                AL11.alSourcei(sound.sourceID(), EXTEfx.AL_AUXILIARY_SEND_FILTER, 0);
            }
        }
    }

    @Override
    public void init() {
        delay = 0f;
        decay = 0f;
        spread = 0f;
        damping = 0.7f;
        lrDelay = 0f;

        if (ALset.effects.length > 6) {
            int effectId = ALset.effects[6];
            EXTEfx.alEffecti(effectId, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_ECHO);

            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_DELAY, delay);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_LRDELAY, lrDelay);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_DAMPING, damping);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_FEEDBACK, decay);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_ECHO_SPREAD, spread);
        }

        active = true;
    }

    private static class EchoPath {
        final float delay;
        final float energy;
        final float probability;

        EchoPath(float delay, float energy, float probability) {
            this.delay = delay;
            this.energy = energy;
            this.probability = probability;
        }
    }
}