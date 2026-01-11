package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.openal.*;
import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.Engine;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.AL10;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Water extends Effect {
    private boolean emitterInWater = false;
    private boolean listenerInWater = false;
    private boolean emitterInBubbleColumn = false;
    private boolean listenerInBubbleColumn = false;
    private long lastCheckTime = 0;
    private int waterSubmersionLevel = 0;
    private float waterTemperature = 15.0f;
    private float waterSalinity = 0.035f;
    private float waterDepth = 0.0f;
    private float waterCurrent = 0.0f;
    private float waterClarity = 1.0f;
    private float underwaterCaustics = 0.0f;
    private final List<BlockPos> nearbyWaterBlocks = new ArrayList<>();
    private float surfaceWaves = 0.0f;
    private float waterPressure = 101.325f;

    public Water() {
        name = "Water";
    }

    @Override
    public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
        if (!active || Engine.mc.player == null || Engine.mc.level == null) {
            return context;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCheckTime > 500) {
            updateWaterState(sound);
            updateWaterProperties();
            lastCheckTime = currentTime;
        }

        WaterEffect effect = calculateWaterEffect();

        int filter = ALset.filters[1];
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

        float temperatureEffect = calculateTemperatureEffect();
        float depthEffect = calculateDepthEffect();
        float clarityEffect = calculateClarityEffect();

        effect.volume *= temperatureEffect * depthEffect * clarityEffect;
        effect.cutoff *= temperatureEffect * depthEffect * clarityEffect;

        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, effect.volume);
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, effect.cutoff);

        if (emitterInWater || listenerInWater) {
            applyWaterReverb(sound.sourceID(), effect);
            applyWaterAbsorption(sound.sourceID());

            if (emitterInWater && !listenerInWater) {
                applySurfaceTransmission();
            }

            if (waterSubmersionLevel > 2) {
                applyDepthPressure(sound.sourceID());
            }

            if (emitterInBubbleColumn || listenerInBubbleColumn) {
                applyBubbleEffect();
            }

            if (waterCurrent > 0.1f) {
                applyCurrentEffect();
            }

            if (underwaterCaustics > 0.1f) {
                applyCausticsEffect();
            }
        }

        if (surfaceWaves > 0.1f) {
            applySurfaceWaves();
        }

        if (pC.dLog && (emitterInWater || listenerInWater)) {
            Engine.LOGGER.debug("Water effect: emitter={}, listener={}, depth={}m, temp={}°C, salinity={}",
                    emitterInWater, listenerInWater, waterDepth,
                    String.format("%.1f", waterTemperature),
                    String.format("%.3f", waterSalinity));
        }

        return context;
    }

    private void updateWaterState(SoundProfile sound) {
        assert Engine.mc.player != null;
        Vec3 listenerPos = Engine.mc.player.getEyePosition();
        BlockPos listenerBlockPos = BlockPos.containing(listenerPos);

        listenerInWater = Engine.mc.player.isUnderWater();
        listenerInBubbleColumn = isInBubbleColumn(listenerBlockPos);

        waterSubmersionLevel = calculateSubmersionLevel(listenerPos);
        waterDepth = calculateWaterDepth(listenerPos);

        Vec3 emitterPos = getEmitterPosition(sound);
        if (emitterPos != null) {
            BlockPos emitterBlockPos = BlockPos.containing(emitterPos);
            emitterInWater = isPositionInWater(emitterPos, emitterBlockPos);
            emitterInBubbleColumn = isInBubbleColumn(emitterBlockPos);

            if (emitterInWater) {
                calculateNearbyWater(emitterBlockPos);
            }
        } else {
            emitterInWater = false;
            emitterInBubbleColumn = false;
        }

        updateWaterSurfaceEffects(listenerPos);
    }

    private void updateWaterProperties() {
        if (Engine.mc.level != null) {
            long time = Engine.mc.level.getGameTime();

            waterTemperature = 10.0f + 5.0f * (float)Math.sin(time * 0.0001);

            if (Engine.mc.level.isRaining()) {
                waterTemperature -= 2.0f;
                waterClarity = 0.7f;
            } else {
                waterClarity = 0.9f + 0.1f * (float)Math.sin(time * 0.00005);
            }

            waterCurrent = 0.2f + 0.3f * (float)Math.sin(time * 0.0002);

            if (Engine.mc.level.isThundering()) {
                waterCurrent *= 2.0f;
                waterClarity = 0.5f;
            }

            waterPressure = 101.325f + waterDepth * 10.0f;
            underwaterCaustics = calculateCaustics();
        }
    }

    private boolean isPositionInWater(Vec3 position, BlockPos blockPos) {
        assert Engine.mc.level != null;
        FluidState fluidState = Engine.mc.level.getFluidState(blockPos);

        if (fluidState.getType() == Fluids.WATER ||
                fluidState.getType() == Fluids.FLOWING_WATER) {

            double fluidHeight = blockPos.getY() + fluidState.getHeight(Engine.mc.level, blockPos);
            return position.y < fluidHeight;
        }

        return false;
    }

    private boolean isInBubbleColumn(BlockPos pos) {
        assert Engine.mc.level != null;
        BlockState state = Engine.mc.level.getBlockState(pos);
        return state.is(Blocks.BUBBLE_COLUMN);
    }

    private int calculateSubmersionLevel(Vec3 position) {
        BlockPos pos = BlockPos.containing(position);
        int level = 0;

        for (int i = 0; i < 20; i++) {
            BlockPos checkPos = pos.above(i);
            assert Engine.mc.level != null;
            FluidState fluidState = Engine.mc.level.getFluidState(checkPos);

            if (fluidState.getType() == Fluids.WATER ||
                    fluidState.getType() == Fluids.FLOWING_WATER) {
                level++;
            } else {
                break;
            }
        }

        return level;
    }

    private float calculateWaterDepth(Vec3 position) {
        BlockPos pos = BlockPos.containing(position);
        float depth = 0.0f;

        for (int i = 0; i < 64; i++) {
            BlockPos checkPos = pos.below(i);
            assert Engine.mc.level != null;
            FluidState fluidState = Engine.mc.level.getFluidState(checkPos);

            if (fluidState.getType() == Fluids.WATER ||
                    fluidState.getType() == Fluids.FLOWING_WATER) {
                depth += 1.0f;
            } else {
                break;
            }
        }

        return depth;
    }

    private void calculateNearbyWater(BlockPos center) {
        nearbyWaterBlocks.clear();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = center.offset(dx, dy, dz);
                    assert Engine.mc.level != null;
                    FluidState fluidState = Engine.mc.level.getFluidState(checkPos);

                    if (fluidState.getType() == Fluids.WATER ||
                            fluidState.getType() == Fluids.FLOWING_WATER) {
                        nearbyWaterBlocks.add(checkPos);
                    }
                }
            }
        }
    }

    private void updateWaterSurfaceEffects(Vec3 listenerPos) {
        surfaceWaves = 0.0f;

        if (Engine.mc.level != null) {
            long time = Engine.mc.level.getGameTime();

            if (Engine.mc.level.isRaining()) {
                surfaceWaves = 0.5f + 0.3f * (float)Math.sin(time * 0.002);
            }

            if (Engine.mc.level.isThundering()) {
                surfaceWaves = 1.0f;
            }

            float wind;
            if (Engine.mc.level.canSeeSky(BlockPos.containing(listenerPos))) {
                wind = 0.3f;
                surfaceWaves += wind;
            }
        }
    }

    private float calculateCaustics() {
        if (Engine.mc.level == null || !Engine.mc.level.canSeeSky(BlockPos.containing(Objects.requireNonNull(Engine.mc.player).getEyePosition()))) {
            return 0.0f;
        }

        long time = Engine.mc.level.getGameTime();
        float timeOfDay = (float)(time % 24000) / 24000.0f;

        float caustics = 0.0f;
        if (timeOfDay > 0.25f && timeOfDay < 0.75f) {
            caustics = (float)Math.sin((timeOfDay - 0.25f) * Math.PI * 2);
        }

        if (Engine.mc.level.isRaining()) {
            caustics *= 0.3f;
        }

        return caustics * waterClarity;
    }

    private WaterEffect calculateWaterEffect() {
        float volume = 1.0f;
        float cutoff = 1.0f;
        float reverbAmount = 0.0f;
        float pitchShift = 0.0f;

        if (emitterInWater && listenerInWater) {
            volume = 0.9f - waterDepth * 0.01f;
            cutoff = 0.4f + waterSubmersionLevel * 0.03f;
            reverbAmount = 0.7f + waterDepth * 0.005f;
            pitchShift = -0.05f - waterDepth * 0.001f;

            cutoff *= waterClarity;
            volume *= waterClarity;
        } else if (emitterInWater) {
            volume = 0.3f;
            cutoff = 0.25f;
            reverbAmount = 0.2f;
            pitchShift = -0.1f;

            float surfaceFactor = 1.0f - waterSubmersionLevel * 0.1f;
            volume *= surfaceFactor;
            cutoff *= surfaceFactor;
        } else if (listenerInWater) {
            volume = 0.7f - waterDepth * 0.02f;
            cutoff = 0.2f + waterSubmersionLevel * 0.02f;
            reverbAmount = 0.5f;
            pitchShift = 0.05f + waterDepth * 0.002f;

            cutoff *= waterClarity;
        }

        if (emitterInBubbleColumn) {
            cutoff *= 0.7f;
            volume *= 1.2f;
            pitchShift += 0.1f;
        }

        if (listenerInBubbleColumn) {
            cutoff *= 0.6f;
            volume *= 1.1f;
            pitchShift -= 0.05f;
        }

        volume = Mth.clamp(volume, 0.1f, 1.5f);
        cutoff = Mth.clamp(cutoff, 0.05f, 1.0f);
        pitchShift = Mth.clamp(pitchShift, -0.2f, 0.2f);

        return new WaterEffect(volume, cutoff, reverbAmount, pitchShift);
    }

    private float calculateTemperatureEffect() {
        float idealTemp = 20.0f;
        float tempDiff = Math.abs(waterTemperature - idealTemp);
        return 1.0f - tempDiff / 40.0f * 0.3f;
    }

    private float calculateDepthEffect() {
        return 1.0f - waterDepth * 0.02f;
    }

    private float calculateClarityEffect() {
        return waterClarity;
    }

    private void applyWaterReverb(int sourceID, WaterEffect effect) {
        if (ALset.slots.length > 0 && effect.reverbAmount > 0.05f) {
            int slot = ALset.slots[0];
            int effectId = ALset.effects[0];

            EXTEfx.alEffecti(effectId, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);

            float depthFactor = 1.0f + waterDepth * 0.02f;
            float salinityFactor = 1.0f + waterSalinity * 10.0f;

            EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DECAY_TIME, 3.0f * effect.reverbAmount * depthFactor);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DENSITY, 0.9f);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_GAINHF, 0.2f * effect.cutoff);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, 0.4f * effect.reverbAmount);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, 0.015f * salinityFactor);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, 0.03f * depthFactor);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, 0.8f * effect.reverbAmount);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, 0.15f);

            EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effectId);

            applyPitchShift(sourceID, effect.pitchShift);
        }
    }

    private void applyWaterAbsorption(int sourceID) {
        if (ALset.filters.length > 3) {
            int absorptionFilter = ALset.filters[3];
            EXTEfx.alFilteri(absorptionFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

            float absorption = 0.3f + waterDepth * 0.01f;
            absorption *= waterSalinity * 5.0f;

            EXTEfx.alFilterf(absorptionFilter, EXTEfx.AL_LOWPASS_GAIN, 1.0f);
            EXTEfx.alFilterf(absorptionFilter, EXTEfx.AL_LOWPASS_GAINHF, 5000.0f * (1.0f - absorption));

            if (ALset.slots.length > 15) {
                AL10.alSource3f(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, ALset.slots[15], 1, absorptionFilter);
            }
        }
    }

    private void applySurfaceTransmission() {
        if (ALset.effects.length > 13) {
            int surfaceEffect = ALset.effects[13];
            EXTEfx.alEffecti(surfaceEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_ECHO);

            float surfaceDelay = 0.02f + waterSubmersionLevel * 0.005f;

            EXTEfx.alEffectf(surfaceEffect, EXTEfx.AL_ECHO_DELAY, surfaceDelay);
            EXTEfx.alEffectf(surfaceEffect, EXTEfx.AL_ECHO_LRDELAY, surfaceDelay * 1.1f);
            EXTEfx.alEffectf(surfaceEffect, EXTEfx.AL_ECHO_DAMPING, 0.6f);
            EXTEfx.alEffectf(surfaceEffect, EXTEfx.AL_ECHO_FEEDBACK, 0.2f);
            EXTEfx.alEffectf(surfaceEffect, EXTEfx.AL_ECHO_SPREAD, 0.5f);

            if (ALset.slots.length > 16) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[16], EXTEfx.AL_EFFECTSLOT_EFFECT, surfaceEffect);
            }
        }
    }

    private void applyDepthPressure(int sourceID) {
        float pressureFactor = waterPressure / 101.325f;

        try {
            float currentPitch = AL10.alGetSourcef(sourceID, AL10.AL_PITCH);
            float newPitch = currentPitch * (1.0f - (pressureFactor - 1.0f) * 0.02f);
            newPitch = Mth.clamp(newPitch, 0.7f, 1.3f);
            AL10.alSourcef(sourceID, AL10.AL_PITCH, newPitch);

            if (pressureFactor > 1.5f) {
                applyPressureFilter(sourceID);
            }
        } catch (Exception e) {
            Engine.LOGGER.warn("Failed to apply depth pressure effect: {}", e.getMessage());
        }
    }

    private void applyPressureFilter(int sourceID) {
        if (ALset.filters.length > 6) {
            int pressureFilter = ALset.filters[6];
            EXTEfx.alFilteri(pressureFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_BANDPASS);

            EXTEfx.alFilterf(pressureFilter, EXTEfx.AL_BANDPASS_GAIN, 1.0f);
            EXTEfx.alFilterf(pressureFilter, EXTEfx.AL_BANDPASS_GAINLF, 0.7f);
            EXTEfx.alFilterf(pressureFilter, EXTEfx.AL_BANDPASS_GAINHF, 0.7f);

            if (ALset.slots.length > 17) {
                AL10.alSource3f(sourceID, EXTEfx.AL_AUXILIARY_SEND_FILTER, ALset.slots[17], 2, pressureFilter);
            }
        }
    }

    private void applyBubbleEffect() {
        if (ALset.effects.length > 9 && waterSubmersionLevel > 0) {
            int effectId = ALset.effects[9];
            EXTEfx.alEffecti(effectId, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_FLANGER);

            System.currentTimeMillis();
            float rate = 0.4f + waterSubmersionLevel * 0.08f;
            float depth = 0.08f + waterSubmersionLevel * 0.015f;
            float bubbleDensity = 0.5f + waterSubmersionLevel * 0.1f;

            EXTEfx.alEffectf(effectId, EXTEfx.AL_FLANGER_RATE, rate);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_FLANGER_DEPTH, depth);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_FLANGER_FEEDBACK, 0.3f * bubbleDensity);
            EXTEfx.alEffectf(effectId, EXTEfx.AL_FLANGER_DELAY, 0.003f);

            if (ALset.slots.length > 9) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[9], EXTEfx.AL_EFFECTSLOT_EFFECT, effectId);
            }

            applyBubbleNoise();
        }
    }

    private void applyBubbleNoise() {
        if (ALset.effects.length > 14) {
            int bubbleEffect = ALset.effects[14];
            EXTEfx.alEffecti(bubbleEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_FREQUENCY_SHIFTER);

            float time = (float)(System.currentTimeMillis() % 3000) / 3000.0f;
            float bubbleRate = 2.0f + waterSubmersionLevel * 0.5f;
            float freqShift = 15.0f * (float)Math.sin(time * Math.PI * 2 * bubbleRate);

            EXTEfx.alEffectf(bubbleEffect, EXTEfx.AL_FREQUENCY_SHIFTER_FREQUENCY, freqShift);
            EXTEfx.alEffectf(bubbleEffect, EXTEfx.AL_FREQUENCY_SHIFTER_LEFT_DIRECTION,
                    EXTEfx.AL_FREQUENCY_SHIFTER_DIRECTION_DOWN);
            EXTEfx.alEffectf(bubbleEffect, EXTEfx.AL_FREQUENCY_SHIFTER_RIGHT_DIRECTION,
                    EXTEfx.AL_FREQUENCY_SHIFTER_DIRECTION_UP);

            if (ALset.slots.length > 18) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[18], EXTEfx.AL_EFFECTSLOT_EFFECT, bubbleEffect);
            }
        }
    }

    private void applyCurrentEffect() {
        if (ALset.effects.length > 15) {
            int currentEffect = ALset.effects[15];
            EXTEfx.alEffecti(currentEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_CHORUS);

            float currentRate = 0.3f + waterCurrent * 0.5f;
            float currentDepth = 0.05f + waterCurrent * 0.1f;

            EXTEfx.alEffectf(currentEffect, EXTEfx.AL_CHORUS_RATE, currentRate);
            EXTEfx.alEffectf(currentEffect, EXTEfx.AL_CHORUS_DEPTH, currentDepth);
            EXTEfx.alEffectf(currentEffect, EXTEfx.AL_CHORUS_FEEDBACK, 0.2f);
            EXTEfx.alEffectf(currentEffect, EXTEfx.AL_CHORUS_DELAY, 0.02f);

            if (ALset.slots.length > 19) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[19], EXTEfx.AL_EFFECTSLOT_EFFECT, currentEffect);
            }
        }
    }

    private void applyCausticsEffect() {
        if (ALset.effects.length > 16) {
            int causticsEffect = ALset.effects[16];
            EXTEfx.alEffecti(causticsEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_FREQUENCY_SHIFTER);

            float time = (float)(System.currentTimeMillis() % 5000) / 5000.0f;
            float causticsRate = 0.5f + underwaterCaustics * 2.0f;
            float freqShift = 8.0f * (float)Math.sin(time * Math.PI * 2 * causticsRate);

            EXTEfx.alEffectf(causticsEffect, EXTEfx.AL_FREQUENCY_SHIFTER_FREQUENCY, freqShift);
            EXTEfx.alEffectf(causticsEffect, EXTEfx.AL_FREQUENCY_SHIFTER_LEFT_DIRECTION,
                    EXTEfx.AL_FREQUENCY_SHIFTER_DIRECTION_DOWN);
            EXTEfx.alEffectf(causticsEffect, EXTEfx.AL_FREQUENCY_SHIFTER_RIGHT_DIRECTION,
                    EXTEfx.AL_FREQUENCY_SHIFTER_DIRECTION_UP);

            if (ALset.slots.length > 20) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[20], EXTEfx.AL_EFFECTSLOT_EFFECT, causticsEffect);
            }
        }
    }

    private void applySurfaceWaves() {
        if (ALset.effects.length > 17 && surfaceWaves > 0.1f) {
            int waveEffect = ALset.effects[17];
            EXTEfx.alEffecti(waveEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_FLANGER);

            float waveRate = 0.2f + surfaceWaves * 0.4f;
            float waveDepth = 0.03f + surfaceWaves * 0.07f;

            EXTEfx.alEffectf(waveEffect, EXTEfx.AL_FLANGER_RATE, waveRate);
            EXTEfx.alEffectf(waveEffect, EXTEfx.AL_FLANGER_DEPTH, waveDepth);
            EXTEfx.alEffectf(waveEffect, EXTEfx.AL_FLANGER_FEEDBACK, 0.15f);
            EXTEfx.alEffectf(waveEffect, EXTEfx.AL_FLANGER_DELAY, 0.001f + surfaceWaves * 0.002f);

            if (ALset.slots.length > 21) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[21], EXTEfx.AL_EFFECTSLOT_EFFECT, waveEffect);
            }
        }
    }

    private void applyPitchShift(int sourceID, float pitchShift) {
        try {
            if (Math.abs(pitchShift) > 0.01f) {
                float currentPitch = AL10.alGetSourcef(sourceID, AL10.AL_PITCH);
                float newPitch = currentPitch * (1.0f + pitchShift);
                newPitch = Mth.clamp(newPitch, 0.5f, 2.0f);
                AL10.alSourcef(sourceID, AL10.AL_PITCH, newPitch);
            }
        } catch (Exception e) {
            Engine.LOGGER.warn("Failed to apply water pitch shift: {}", e.getMessage());
        }
    }

    @Override
    public void init() {
        if (ALset.filters.length > 1) {
            EXTEfx.alFilteri(ALset.filters[1], EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        }
        active = true;
        waterTemperature = 15.0f;
        waterSalinity = 0.035f;
        waterDepth = 0.0f;
        waterCurrent = 0.0f;
        waterClarity = 1.0f;
        nearbyWaterBlocks.clear();
    }

    private Vec3 getEmitterPosition(SoundProfile sound) {
        if (sound.position() != null) {
            return sound.position();
        }

        try {
            float[] posArray = new float[3];
            AL10.alGetSourcefv(sound.sourceID(), AL10.AL_POSITION, posArray);

            if (!ALUtils.checkErrors("Failed to get OpenAL source position")) {
                return new Vec3(posArray[0], posArray[1], posArray[2]);
            }
        } catch (Exception e) {
            Engine.LOGGER.warn("Failed to get position for sound source {}: {}",
                    sound.sourceID(), e.getMessage());
        }

        if (Engine.mc.player != null) {
            return Engine.mc.player.getEyePosition();
        }

        return Vec3.ZERO;
    }

    private static class WaterEffect {
        float volume;
        float cutoff;
        final float reverbAmount;
        final float pitchShift;

        WaterEffect(float volume, float cutoff, float reverbAmount, float pitchShift) {
            this.volume = Mth.clamp(volume, 0.1f, 2.0f);
            this.cutoff = Mth.clamp(cutoff, 0.05f, 1.0f);
            this.reverbAmount = Mth.clamp(reverbAmount, 0.0f, 1.0f);
            this.pitchShift = Mth.clamp(pitchShift, -0.3f, 0.3f);
        }

    }
}