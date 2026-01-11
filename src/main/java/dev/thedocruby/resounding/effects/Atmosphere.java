package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.openal.*;
import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.Engine;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.AL10;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.Level;

import java.util.Arrays;

public class Atmosphere extends Effect {
    private float humidity = 0.5f;
    private float temperature = 20.0f;
    private float pressure = 101.325f;
    private float altitude = 0.0f;
    private boolean isUnderground = false;
    private boolean isCave = false;
    private long lastUpdateTime = 0;
    private Biome currentBiome = null;
    private float windSpeed = 0.0f;
    private float turbulence = 0.1f;
    private float atmosphericScattering = 0.0f;
    private float soundSpeed = 343.0f;
    private final float[] frequencyAbsorption = new float[8];
    private float timeOfDay = 0.0f;
    private float seasonFactor = 0.0f;

    public Atmosphere() {
        name = "Atmosphere";
        Arrays.fill(frequencyAbsorption, 1.0f);
    }

    @Override
    public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
        if (!active || Engine.mc.player == null || Engine.mc.level == null) {
            return context;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > 2000) {
            updateAtmosphericConditions();
            updateWindEffects();
            calculateFrequencyAbsorption();
            lastUpdateTime = currentTime;
        }

        applyAtmosphericEffects(sound);
        applyWindEffects();
        applyTimeOfDayEffects(sound);

        return context;
    }

    private void updateAtmosphericConditions() {
        assert Engine.mc.player != null;
        Vec3 playerPos = Engine.mc.player.getEyePosition();
        BlockPos playerBlockPos = BlockPos.containing(playerPos);
        altitude = (float)playerPos.y;
        assert Engine.mc.level != null;
        currentBiome = Engine.mc.level.getBiome(playerBlockPos).value();

        Level level = Engine.mc.level;
        int surfaceHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                playerBlockPos.getX(), playerBlockPos.getZ());
        isUnderground = playerPos.y < surfaceHeight - 2;

        isCave = isInCave(playerBlockPos);

        updateBiomeBasedParameters(playerBlockPos);
        updateWeatherEffects(playerBlockPos);
        updateTimeAndSeason();

        if (isUnderground) {
            updateUndergroundEffects(playerBlockPos);
        }

        pressure = calculatePressure(altitude, temperature);
        calculateAirDensity();
        soundSpeed = calculateSoundSpeed(temperature, humidity, pressure);
        atmosphericScattering = calculateAtmosphericScattering();
    }

    private void updateTimeAndSeason() {
        if (Engine.mc.level != null) {
            timeOfDay = (float)(Engine.mc.level.getDayTime() % 24000) / 24000.0f;

            long day = Engine.mc.level.getDayTime() / 24000;
            seasonFactor = (float)Math.sin(day / 90.0 * Math.PI * 2);
        }
    }

    private void updateWindEffects() {
        if (Engine.mc.level != null) {
            float time = Engine.mc.level.getGameTime() * 0.05f;

            windSpeed = 0.5f + 0.3f * (float)Math.sin(time * 0.1f)
                    + 0.2f * (float)Math.sin(time * 0.03f);

            new Vec3(
                    (float) Math.cos(time * 0.07f),
                    0,
                    (float) Math.sin(time * 0.07f)
            ).normalize();

            turbulence = 0.05f + 0.1f * (float)Math.sin(time * 0.2f);

            if (Engine.mc.level.isThundering()) {
                windSpeed *= 2.5f;
                turbulence *= 3.0f;
            } else if (Engine.mc.level.isRaining()) {
                windSpeed *= 1.5f;
            }
        }
    }

    private boolean isInCave(BlockPos pos) {
        if (!isUnderground) return false;

        int openSpace = 0;
        int solidBlocks = 0;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    assert Engine.mc.level != null;
                    if (Engine.mc.level.getBlockState(checkPos).isAir()) {
                        openSpace++;
                    } else if (Engine.mc.level.getBlockState(checkPos).getFluidState().getType() == Fluids.WATER) {
                    } else {
                        solidBlocks++;
                    }
                }
            }
        }

        return openSpace > 20 && solidBlocks > 30;
    }

    private void updateBiomeBasedParameters(BlockPos pos) {
        if (currentBiome == null) return;

        temperature = currentBiome.getBaseTemperature() * 20.0f + 10.0f;
        temperature += seasonFactor * 15.0f;

        boolean isSnowy = currentBiome.coldEnoughToSnow(pos);
        Biome.Precipitation precipitation = currentBiome.getPrecipitationAt(pos);

        if (isSnowy) {
            humidity = 0.7f + 0.2f * seasonFactor;
            temperature -= 15.0f * (1.0f + seasonFactor);
        } else if (precipitation == Biome.Precipitation.RAIN) {
            humidity = 0.8f;
        } else if (precipitation == Biome.Precipitation.NONE) {
            humidity = 0.2f;
        } else {
            humidity = 0.5f;
        }

        String biomeName = currentBiome.toString().toLowerCase();
        if (biomeName.contains("swamp") || biomeName.contains("mushroom")) {
            humidity = 0.95f;
            temperature -= 3.0f;
        } else if (biomeName.contains("desert") || biomeName.contains("badlands")) {
            assert Engine.mc.level != null;
            humidity = 0.05f + 0.05f * (float)Math.sin(Engine.mc.level.getGameTime() * 0.001f);
            temperature += 10.0f;
        } else if (biomeName.contains("taiga") || biomeName.contains("snowy")) {
            humidity = 0.6f;
            temperature -= 12.0f;
        } else if (biomeName.contains("jungle")) {
            humidity = 0.98f;
            temperature += 5.0f;
        } else if (biomeName.contains("ocean") || biomeName.contains("river")) {
            humidity = 0.9f;
            temperature -= 2.0f;
        }

        humidity = Mth.clamp(humidity, 0.01f, 0.99f);
        temperature = Mth.clamp(temperature, -40.0f, 50.0f);
    }

    private void updateWeatherEffects(BlockPos pos) {
        assert Engine.mc.level != null;
        if (Engine.mc.level.isRaining()) {
            humidity = Math.min(humidity + 0.4f, 0.99f);
            temperature -= 4.0f;
            atmosphericScattering += 0.3f;

            if (Engine.mc.level.canSeeSky(pos)) {
                humidity = 0.99f;
                atmosphericScattering += 0.5f;
            }
        }

        if (Engine.mc.level.isThundering()) {
            humidity = 0.99f;
            temperature -= 5.0f;
            pressure -= 5.0f;
            atmosphericScattering += 0.8f;
            turbulence += 0.3f;
        }
    }

    private void updateUndergroundEffects(BlockPos pos) {
        humidity = Math.min(humidity + 0.3f, 0.95f);
        assert Engine.mc.level != null;
        temperature = 8.0f + 4.0f * (float)Math.sin(Engine.mc.level.getGameTime() * 0.0005f);
        pressure += 10.0f;

        if (isCave) {
            int waterCount = 0;

            for (int dy = 0; dy < 5; dy++) {
                BlockPos checkPos = pos.above(dy);
                assert Engine.mc.level != null;
                FluidState fluidState = Engine.mc.level.getFluidState(checkPos);
                if (fluidState.getType() == Fluids.WATER ||
                        fluidState.getType() == Fluids.FLOWING_WATER) {
                    waterCount++;
                    humidity = 0.99f;
                }
            }

            humidity += waterCount * 0.05f;
            atmosphericScattering += 0.2f;
        }
    }

    private float calculatePressure(float altitude, float temperature) {
        float heightKm = altitude / 1000.0f;
        float tempK = temperature + 273.15f;

        float exponent = -0.03416f * heightKm / (tempK / 288.15f);
        float pressure = 101.325f * (float)Math.exp(exponent);

        float humidityFactor = 1.0f - humidity * 0.003f;
        pressure *= humidityFactor;

        return Math.max(pressure, 20.0f);
    }

    private void calculateAirDensity() {

    }

    private float calculateSoundSpeed(float temperature, float humidity, float pressure) {
        float tempK = temperature + 273.15f;

        float baseSpeed = 331.3f * (float)Math.sqrt(tempK / 273.15f);

        float humidityCorrection = 0.12f * humidity;
        float pressureCorrection = 0.0001f * (pressure - 101.325f);

        return baseSpeed + humidityCorrection + pressureCorrection;
    }

    private float calculateAtmosphericScattering() {
        float scattering = 0.0f;

        scattering += humidity * 0.3f;
        scattering += (temperature - 20.0f) / 50.0f * 0.2f;
        scattering += windSpeed * 0.1f;
        scattering += turbulence * 0.15f;

        if (Engine.mc.level != null && Engine.mc.level.isRaining()) {
            scattering += 0.4f;
        }

        return Mth.clamp(scattering, 0.0f, 1.0f);
    }

    private void calculateFrequencyAbsorption() {
        float[] frequencies = {125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f};

        for (int i = 0; i < frequencies.length; i++) {
            float freq = frequencies[i];

            float alpha = 0.0f;

            float tempK = temperature + 273.15f;
            float T0 = 293.15f;

            float humidityFactor = humidity * 100.0f;

            if (humidityFactor > 0.1f) {
                float frO = pressure / 101.325f * (24.0f + 40400.0f * humidityFactor * (0.02f + humidityFactor) / (0.391f + humidityFactor));
                float frN = pressure / 101.325f * (float)Math.sqrt(T0 / tempK) * (9.0f + 280.0f * humidityFactor * (float)Math.exp(-4.17f * (Math.pow(T0 / tempK, 1.0/3.0) - 1)));

                alpha = (float)(8.686 * freq * freq * (1.84e-11 / (Math.sqrt(T0 / tempK) * pressure / 101.325f)
                        + Math.pow(tempK / T0, -2.5) * (0.01275 * Math.exp(-2239.1 / tempK) / (frO + freq * freq / frO)
                        + 0.1068 * Math.exp(-3352.0 / tempK) / (frN + freq * freq / frN))));
            }

            frequencyAbsorption[i] = (float)Math.exp(-alpha * 0.1);
            frequencyAbsorption[i] = Mth.clamp(frequencyAbsorption[i], 0.01f, 1.0f);
        }
    }

    private void applyAtmosphericEffects(SoundProfile sound) {
        float humidityEffect = calculateHumidityEffect();
        float temperatureEffect = calculateTemperatureEffect();
        float pressureEffect = calculatePressureEffect();
        float altitudeEffect = calculateAltitudeEffect();
        float undergroundEffect = calculateUndergroundEffect();
        float scatteringEffect = 1.0f - atmosphericScattering * 0.5f;

        float atmosphericAbsorption = humidityEffect * temperatureEffect *
                pressureEffect * altitudeEffect * undergroundEffect * scatteringEffect;

        if (atmosphericAbsorption < 0.99f) {
            int filter = ALset.filters[3];
            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

            float baseCutoff = 20000.0f;
            float cutoff = baseCutoff * atmosphericAbsorption;

            float[] eqGains = calculateFrequencyEQ();
            applyFrequencyEQ(eqGains);

            float hfRolloff = (float)Math.pow(atmosphericAbsorption, 1.5f);
            float lfBoost = 1.0f + (1.0f - atmosphericAbsorption) * 0.3f;

            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, lfBoost);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF,
                    Mth.clamp(cutoff * hfRolloff, 100.0f, 20000.0f));

            AL10.alSourcei(sound.sourceID(), EXTEfx.AL_DIRECT_FILTER, filter);

            if (isCave || isUnderground) {
                applyCaveReverb(atmosphericAbsorption);
            }
        }

        if (humidity < 0.1f && temperature > 35.0f) {
            applyDesertEffects(1.0f - humidity);
        }

        if (windSpeed > 2.0f) {
            applyWindFilter(sound);
        }

        if (pC.dLog && atmosphericAbsorption < 0.98f) {
            Engine.LOGGER.debug("Atmosphere: temp={}°C, hum={}%, press={}kPa, alt={}m, wind={}m/s, speed={}m/s",
                    String.format("%.1f", temperature),
                    String.format("%.0f", humidity * 100),
                    String.format("%.1f", pressure),
                    String.format("%.0f", altitude),
                    String.format("%.1f", windSpeed),
                    String.format("%.1f", soundSpeed));
        }
    }

    private float[] calculateFrequencyEQ() {
        float[] eq = new float[8];

        for (int i = 0; i < 8; i++) {
            eq[i] = frequencyAbsorption[i];

            if (i < 2) {
                eq[i] *= 1.0f + (1.0f - humidity) * 0.2f;
            }

            if (i > 5) {
                eq[i] *= 1.0f - humidity * 0.4f;
            }

            eq[i] = Mth.clamp(eq[i], 0.1f, 1.5f);
        }

        return eq;
    }

    private void applyFrequencyEQ(float[] eqGains) {
        if (ALset.effects.length > 6) {
            int eqEffect = ALset.effects[6];
            EXTEfx.alEffecti(eqEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EQUALIZER);

            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_LOW_GAIN, eqGains[0]);
            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_LOW_CUTOFF, 125.0f);
            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_MID1_GAIN, eqGains[2]);
            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_MID1_CENTER, 500.0f);
            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_MID1_WIDTH, 1.0f);
            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_MID2_GAIN, eqGains[4]);
            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_MID2_CENTER, 2000.0f);
            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_MID2_WIDTH, 1.0f);
            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_HIGH_GAIN, eqGains[6]);
            EXTEfx.alEffectf(eqEffect, EXTEfx.AL_EQUALIZER_HIGH_CUTOFF, 8000.0f);

            if (ALset.slots.length > 6) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[6], EXTEfx.AL_EFFECTSLOT_EFFECT, eqEffect);
            }
        }
    }

    private void applyWindEffects() {
        if (windSpeed > 0.5f && ALset.effects.length > 8) {
            int windEffect = ALset.effects[8];
            EXTEfx.alEffecti(windEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_FLANGER);

            float windRate = 0.1f + windSpeed * 0.3f + turbulence * 0.4f;
            float windDepth = 0.01f + windSpeed * 0.02f + turbulence * 0.03f;
            float windFeedback = 0.1f + turbulence * 0.3f;

            EXTEfx.alEffectf(windEffect, EXTEfx.AL_FLANGER_RATE, windRate);
            EXTEfx.alEffectf(windEffect, EXTEfx.AL_FLANGER_DEPTH, windDepth);
            EXTEfx.alEffectf(windEffect, EXTEfx.AL_FLANGER_FEEDBACK, windFeedback);
            EXTEfx.alEffectf(windEffect, EXTEfx.AL_FLANGER_DELAY, 0.001f + turbulence * 0.003f);

            if (ALset.slots.length > 8) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[8], EXTEfx.AL_EFFECTSLOT_EFFECT, windEffect);
            }

            if (windSpeed > 5.0f) {
                applyWindNoise(windSpeed);
            }
        }
    }

    private void applyWindNoise(float windSpeed) {
        if (ALset.effects.length > 10) {
            int noiseEffect = ALset.effects[10];
            EXTEfx.alEffecti(noiseEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_FREQUENCY_SHIFTER);

            float windNoise = windSpeed * 0.1f;
            float time = (float)(System.currentTimeMillis() % 2000) / 2000.0f;
            float freqShift = 5.0f + windNoise * 30.0f * (float)Math.sin(time * Math.PI * 4);

            EXTEfx.alEffectf(noiseEffect, EXTEfx.AL_FREQUENCY_SHIFTER_FREQUENCY, freqShift);
            EXTEfx.alEffectf(noiseEffect, EXTEfx.AL_FREQUENCY_SHIFTER_LEFT_DIRECTION,
                    EXTEfx.AL_FREQUENCY_SHIFTER_DIRECTION_DOWN);
            EXTEfx.alEffectf(noiseEffect, EXTEfx.AL_FREQUENCY_SHIFTER_RIGHT_DIRECTION,
                    EXTEfx.AL_FREQUENCY_SHIFTER_DIRECTION_UP);

            if (ALset.slots.length > 10) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[10], EXTEfx.AL_EFFECTSLOT_EFFECT, noiseEffect);
            }
        }
    }

    private void applyWindFilter(SoundProfile sound) {
        if (ALset.filters.length > 4) {
            int windFilter = ALset.filters[4];
            EXTEfx.alFilteri(windFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_BANDPASS);

            EXTEfx.alFilterf(windFilter, EXTEfx.AL_BANDPASS_GAIN, 1.0f);
            EXTEfx.alFilterf(windFilter, EXTEfx.AL_BANDPASS_GAINLF, 0.5f);
            EXTEfx.alFilterf(windFilter, EXTEfx.AL_BANDPASS_GAINHF, 0.5f);

            if (ALset.slots.length > 11) {
                AL10.alSource3f(sound.sourceID(), EXTEfx.AL_AUXILIARY_SEND_FILTER, ALset.slots[11], 2, windFilter);
            }
        }
    }

    private void applyTimeOfDayEffects(SoundProfile sound) {
        float nightFactor = Math.abs(timeOfDay - 0.5f) * 2.0f;

        if (nightFactor > 0.7f && !isUnderground) {
            float nightAttenuation = 0.9f - 0.2f * nightFactor;

            if (ALset.filters.length > 5) {
                int nightFilter = ALset.filters[5];
                EXTEfx.alFilteri(nightFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

                EXTEfx.alFilterf(nightFilter, EXTEfx.AL_LOWPASS_GAIN, nightAttenuation);
                EXTEfx.alFilterf(nightFilter, EXTEfx.AL_LOWPASS_GAINHF, 8000.0f * nightAttenuation);

                if (ALset.slots.length > 12) {
                    AL10.alSource3f(sound.sourceID(), EXTEfx.AL_AUXILIARY_SEND_FILTER, ALset.slots[12], 3, nightFilter);
                }
            }
        }
    }

    private void applyDesertEffects(float aridity) {
        if (ALset.effects.length > 11 && aridity > 0.8f) {
            int desertEffect = ALset.effects[11];
            EXTEfx.alEffecti(desertEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_ECHO);

            float echoDelay = 0.05f + aridity * 0.1f;
            float echoLRDelay = echoDelay * 1.1f;

            EXTEfx.alEffectf(desertEffect, EXTEfx.AL_ECHO_DELAY, echoDelay);
            EXTEfx.alEffectf(desertEffect, EXTEfx.AL_ECHO_LRDELAY, echoLRDelay);
            EXTEfx.alEffectf(desertEffect, EXTEfx.AL_ECHO_DAMPING, 0.7f);
            EXTEfx.alEffectf(desertEffect, EXTEfx.AL_ECHO_FEEDBACK, 0.3f + aridity * 0.2f);
            EXTEfx.alEffectf(desertEffect, EXTEfx.AL_ECHO_SPREAD, 0.8f);

            if (ALset.slots.length > 13) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[13], EXTEfx.AL_EFFECTSLOT_EFFECT, desertEffect);
            }
        }
    }

    private float calculateHumidityEffect() {
        float baseEffect = 1.0f - humidity * 0.6f;

        if (temperature > 30.0f) {
            baseEffect *= 1.0f - (temperature - 30.0f) / 50.0f * 0.3f;
        }

        return Mth.clamp(baseEffect, 0.3f, 1.0f);
    }

    private float calculateTemperatureEffect() {
        float idealTemp = 20.0f;
        float tempDiff = Math.abs(temperature - idealTemp);

        float effect = 1.0f - tempDiff / 80.0f * 0.6f;

        if (temperature < 0.0f) {
            effect *= 0.8f + 0.2f * (temperature + 40.0f) / 40.0f;
        }

        return Mth.clamp(effect, 0.2f, 1.0f);
    }

    private float calculatePressureEffect() {
        float normalPressure = 101.325f;
        float pressureRatio = pressure / normalPressure;

        float effect = 0.7f + pressureRatio * 0.3f;

        if (pressure < 70.0f) {
            effect *= 0.8f;
        }

        return Mth.clamp(effect, 0.5f, 1.2f);
    }

    private float calculateAltitudeEffect() {
        if (altitude <= 0) return 1.0f;

        float altitudeKm = altitude / 1000.0f;
        float effect = 1.0f - altitudeKm * 0.35f;

        if (altitudeKm > 1.0f) {
            effect *= 0.9f;
        }

        return Mth.clamp(effect, 0.3f, 1.0f);
    }

    private float calculateUndergroundEffect() {
        if (!isUnderground) return 1.0f;

        if (isCave) {
            return 0.6f + humidity * 0.2f;
        } else {
            return 0.8f - humidity * 0.1f;
        }
    }

    private void applyCaveReverb(float intensity) {
        if (ALset.effects.length > 7 && intensity > 0.1f) {
            int effectId = ALset.effects[7];

            EXTEfx.alEffecti(effectId, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);

            if (isCave) {
                float caveSize = Math.min(intensity * 2.0f, 1.0f);

                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DECAY_TIME, 5.0f * caveSize);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DENSITY, 0.9f);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DIFFUSION, 0.95f);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, 0.7f);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, 0.02f * caveSize);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, 0.8f * caveSize);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, 0.04f * caveSize);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, 0.7f);
            } else {
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DECAY_TIME, 2.5f * intensity);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DENSITY, 0.7f);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_DIFFUSION, 0.8f);
                EXTEfx.alEffectf(effectId, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, 0.4f);
            }

            if (ALset.slots.length > 7) {
                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[7], EXTEfx.AL_EFFECTSLOT_EFFECT, effectId);
            }
        }
    }

    @Override
    public void init() {
        humidity = 0.5f;
        temperature = 20.0f;
        pressure = 101.325f;
        altitude = 0.0f;
        windSpeed = 0.0f;
        turbulence = 0.1f;
        atmosphericScattering = 0.0f;
        soundSpeed = 343.0f;
        isUnderground = false;
        isCave = false;
        active = true;

        Arrays.fill(frequencyAbsorption, 1.0f);
    }
}