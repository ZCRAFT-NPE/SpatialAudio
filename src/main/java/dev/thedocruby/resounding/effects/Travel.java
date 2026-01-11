package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.openal.*;
import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.Engine;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;
import org.lwjgl.openal.AL10;
import net.minecraft.util.Mth;

public class Travel extends Effect {
    private double maxDelay = 3.0;
    private long lastDelayTime = 0;

    public Travel() {
        name = "Travel";
    }

    @Override
    public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
        if (!active) return context;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDelayTime > 100) {
            double distance = estimateDistance();
            double delay = calculateTravelDelay(distance);

            if (delay > 0.02) {
                applyTravelDelay(sound, delay);

                if (pC.dLog && delay > 0.1) {
                    Engine.LOGGER.debug("Travel delay: {}s for estimated {}m",
                            String.format("%.3f", delay),
                            String.format("%.1f", distance));
                }
            }
            lastDelayTime = currentTime;
        }

        return context;
    }

    private double estimateDistance() {
        //TODO:Redo this
        return 10.0 + Math.random() * 40.0;//?
    }

    private double calculateTravelDelay(double distance) {
        double adjustedSpeed = adjustSpeedForConditions();
        double baseDelay = distance / adjustedSpeed;

        //TODO:Redo this
        double obstructionFactor = 1.0 + Math.random() * 0.5;
        baseDelay *= obstructionFactor;

        return Math.min(baseDelay, maxDelay);
    }

    private double adjustSpeedForConditions() {
        //TODO:Redo this
        double baseSpeed = 331.3;

        if (Engine.mc != null && Engine.mc.level != null) {
            if (Engine.mc.level.isRaining()) {
                baseSpeed -= 5.0;
            }
            if (Engine.mc.level.isThundering()) {
                baseSpeed -= 8.0;
            }
        }

        return baseSpeed;
    }

    private void applyTravelDelay(SoundProfile sound, double delay) {
        if (delay > 0.5) {
            double absorption = Math.exp(-delay * 0.2);

            float currentGain = AL10.alGetSourcef(sound.sourceID(), AL10.AL_GAIN);
            float attenuatedGain = (float)(currentGain * absorption);
            AL10.alSourcef(sound.sourceID(), AL10.AL_GAIN, attenuatedGain);
        }

        if (delay > 1.0) {
            applyAtmosphericDistortion(sound, delay);
        }
    }

    private void applyAtmosphericDistortion(SoundProfile sound, double delay) {
        float time = (float)(System.currentTimeMillis() % 10000) / 1000.0f;
        float subtlePitchShift = 1.0f + (float)(Math.sin(time * 1.5f) * 0.008 * delay);

        float currentPitch = AL10.alGetSourcef(sound.sourceID(), AL10.AL_PITCH);
        float newPitch = Mth.clamp(currentPitch * subtlePitchShift, 0.7f, 1.3f);
        AL10.alSourcef(sound.sourceID(), AL10.AL_PITCH, newPitch);
    }

    @Override
    public void init() {
        maxDelay = 3.0;
        active = true;
    }
}