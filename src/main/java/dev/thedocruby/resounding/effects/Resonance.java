package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.openal.*;
import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.Engine;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.AL10;

public class Resonance extends Effect {
    private float[] resonanceFrequencies = {110.0f, 220.0f, 440.0f, 880.0f, 1760.0f};
    private long lastEffectTime = 0;

    public Resonance() {
        name = "Resonance";
    }

    @Override
    public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
        if (!active) return context;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastEffectTime > 1000) {
            double resonanceStrength = calculateResonanceStrength(slot);
            if (resonanceStrength > 0.05) {
                int filter = findAvailableFilter();
                EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_BANDPASS);

                float freq = resonanceFrequencies[(int)(Math.random() * resonanceFrequencies.length)];
                float bandwidth = (float)(freq * 0.2 * resonanceStrength);
                float gain = (float)(0.3 * resonanceStrength);

                EXTEfx.alFilterf(filter, EXTEfx.AL_BANDPASS_GAIN, gain);
                EXTEfx.alFilterf(filter, EXTEfx.AL_BANDPASS_GAINLF, 0.3f);
                EXTEfx.alFilterf(filter, EXTEfx.AL_BANDPASS_GAINHF, 0.3f);

                AL10.alSourcei(sound.sourceID(), EXTEfx.AL_DIRECT_FILTER, filter);

                if (pC.dLog && resonanceStrength > 0.1) {
                    Engine.LOGGER.debug("Resonance: strength={}", String.format("%.2f", resonanceStrength));
                }
            }
            lastEffectTime = currentTime;
        }

        return context;
    }

    private double calculateResonanceStrength(SlotProfile slot) {
        //TODO:Redo this
        double gain = slot.gain();
        double cutoff = slot.cutoff();
        double materialFactor = Math.max(0.1, 1.0 - cutoff);
        double volumeFactor = Math.min(1.0, gain * 2.0);

        return materialFactor * volumeFactor * 0.5;
    }

    private int findAvailableFilter() {
        for (int i = 1; i < ALset.filters.length; i++) {
            if (ALset.filters[i] != 0) return ALset.filters[i];
        }
        return ALset.filters[0];
    }

    @Override
    public void init() {
        resonanceFrequencies = new float[] {82.41f, 164.81f, 329.63f, 659.25f, 1318.51f};
        active = true;
    }
}