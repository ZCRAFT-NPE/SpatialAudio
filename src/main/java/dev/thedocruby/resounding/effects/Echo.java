package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.Engine;
import dev.thedocruby.resounding.openal.ALUtils;
import dev.thedocruby.resounding.openal.ALset;
import dev.thedocruby.resounding.toolbox.SlotProfile;
import dev.thedocruby.resounding.toolbox.SoundProfile;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

public class Echo extends Effect {

    private int echoSlot = 0;
    private int echoEffect = 0;
    private boolean echoEnabled = false;

    @Override
    public ALset setup(final long id) {
        if (pC.dLog) Engine.LOGGER.info("loading echo effect {}", name);

        context = new ALset();
        context.self = id;
        active = true;

        if (!setupEchoSlot() && !setupEchoEffect()) {
            Engine.LOGGER.error("Failed to setup echo effect: {}", name);
            active = false;
        } else {
            echoEnabled = true;
            if (pC.dLog) Engine.LOGGER.info("Setup echo effect: {}", name);
        }
        return context;
    }

    private boolean setupEchoSlot() {
        int[] slots = new int[1];
        EXTEfx.alGenAuxiliaryEffectSlots(slots);
        echoSlot = slots[0];

        if (echoSlot <= 0 || !EXTEfx.alIsAuxiliaryEffectSlot(echoSlot)) {
            Engine.LOGGER.error("Failed to create echo slot");
            return false;
        }

        EXTEfx.alAuxiliaryEffectSloti(echoSlot, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL10.AL_TRUE);
        context.echoSlot = echoSlot;

        if (ALUtils.checkErrors("Failed to initialize echo slot")) {
            EXTEfx.alDeleteAuxiliaryEffectSlots(new int[]{echoSlot});
            echoSlot = 0;
            return false;
        }

        return true;
    }

    private boolean setupEchoEffect() {
        int[] effects = new int[1];
        EXTEfx.alGenEffects(effects);
        echoEffect = effects[0];

        if (echoEffect <= 0 || !EXTEfx.alIsEffect(echoEffect)) {
            Engine.LOGGER.error("Failed to create echo effect");
            return false;
        }

        EXTEfx.alEffecti(echoEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_ECHO);

        EXTEfx.alAuxiliaryEffectSloti(echoSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, echoEffect);
        context.echoEffect = echoEffect;

        if (ALUtils.checkErrors("Failed to initialize echo effect")) {
            EXTEfx.alDeleteEffects(new int[]{echoEffect});
            echoEffect = 0;
            return false;
        }

        return true;
    }

    public void applyEcho(float delay, float lrDelay, float damping, float feedback, float spread) {
        if (!echoEnabled || echoEffect <= 0) return;

        EXTEfx.alEffectf(echoEffect, EXTEfx.AL_ECHO_DELAY, Math.max(0.075f, Math.min(0.25f, delay)));
        EXTEfx.alEffectf(echoEffect, EXTEfx.AL_ECHO_LRDELAY, Math.max(0.0f, Math.min(0.25f, lrDelay)));
        EXTEfx.alEffectf(echoEffect, EXTEfx.AL_ECHO_DAMPING, Math.max(0.0f, Math.min(0.99f, damping)));
        EXTEfx.alEffectf(echoEffect, EXTEfx.AL_ECHO_FEEDBACK, Math.max(0.0f, Math.min(1.0f, feedback)));
        EXTEfx.alEffectf(echoEffect, EXTEfx.AL_ECHO_SPREAD, Math.max(-1.0f, Math.min(1.0f, spread)));

        ALUtils.checkErrors("Failed to apply echo parameters");
    }

    public void applyFromAnalysis(dev.thedocruby.resounding.toolbox.EchoDetector.EchoAnalysis analysis) {
        if (analysis == null || !analysis.hasClearEcho || analysis.echoTimes.isEmpty()) {
            disableEcho();
            return;
        }

        float primaryDelay = (float) Math.min(0.25, analysis.primaryEchoDelay);
        float feedback = (float) Math.min(0.9, analysis.primaryEchoGain * 0.7f);

        float damping = 0.5f;
        if (analysis.averageAbsorption > 0.3) {
            damping = 0.3f + (float)analysis.averageAbsorption * 0.6f;
        }

        float spread = 0.0f;
        if (analysis.echoSpaciousness > 0.5) {
            spread = (float)Math.min(0.8, (analysis.echoSpaciousness - 0.5) * 1.6);
        }

        float lrDelay = 0.0f;
        if (analysis.echoTimes.size() > 1) {
            lrDelay = (float)Math.min(0.1, (analysis.echoTimes.get(1) - analysis.echoTimes.get(0)) * 0.5);
        }

        applyEcho(primaryDelay, lrDelay, damping, feedback, spread);
    }

    public void disableEcho() {
        if (echoSlot > 0 && EXTEfx.alIsAuxiliaryEffectSlot(echoSlot)) {
            EXTEfx.alAuxiliaryEffectSloti(echoSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
        }
    }

    @Override
    public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
        return context;
    }

    public void attachToSource(int sourceId, int slotId, boolean enableEcho) {
        if (!echoEnabled || echoSlot <= 0 || sourceId <= 0) return;

        if (enableEcho && ALUtils.isValidSource(sourceId)) {
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER,
                    echoSlot, 1, 0);
            ALUtils.checkErrors("Failed to attach echo to source");
        }
    }

    @Override
    public boolean init() {
        applyEcho(0.1f, 0.05f, 0.5f, 0.3f, 0.0f);
        return true;
    }

    public void cleanup() {
        if (echoEffect > 0 && EXTEfx.alIsEffect(echoEffect)) {
            disableEcho();
            EXTEfx.alDeleteEffects(new int[]{echoEffect});
            echoEffect = 0;
        }

        if (echoSlot > 0 && EXTEfx.alIsAuxiliaryEffectSlot(echoSlot)) {
            EXTEfx.alDeleteAuxiliaryEffectSlots(new int[]{echoSlot});
            echoSlot = 0;
        }

        echoEnabled = false;
        active = false;
    }
}