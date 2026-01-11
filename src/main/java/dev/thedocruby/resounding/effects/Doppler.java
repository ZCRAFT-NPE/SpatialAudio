package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.openal.*;
import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.Engine;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;
import static dev.thedocruby.resounding.effects.Occlusion.MAX_GAINHF;
import static dev.thedocruby.resounding.effects.Occlusion.MIN_GAINHF;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import java.util.LinkedList;
import java.util.Queue;

public class Doppler extends Effect {
    private Vec3 lastEmitterPos = Vec3.ZERO;
    private Vec3 lastListenerPos = Vec3.ZERO;
    private Vec3 lastEmitterVelocity = Vec3.ZERO;
    private Vec3 lastListenerVelocity = Vec3.ZERO;
    private long lastUpdateTime = 0;
    private double lastDopplerFactor = 1.0;
    private float currentSpeedOfSound = 343.0f;
    private final Queue<Double> dopplerHistory = new LinkedList<>();
    private static final int HISTORY_SIZE = 5;
    private float humidity = 0.5f;
    private boolean emitterInWater = false;
    private boolean listenerInWater = false;
    private float underwaterSpeedFactor = 0.3f;
    private Vec3 windVelocity = Vec3.ZERO;

    private static final float SOUND_SPEED_AIR_20C = 343.0f;
    private static final float SOUND_SPEED_WATER = 1480.0f;
    private static final float SOUND_SPEED_HUMIDITY_FACTOR = 0.12f;
    private static final float TEMPERATURE_COEFFICIENT = 0.606f;

    public Doppler() {
        name = "Doppler";
        for (int i = 0; i < HISTORY_SIZE; i++) {
            dopplerHistory.offer(1.0);
        }
    }

    @Override
    public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
        if (!active || Engine.mc.player == null || Engine.mc.level == null) {
            return context;
        }

        long currentTime = System.currentTimeMillis();
        long deltaTimeMs = currentTime - lastUpdateTime;

        if (deltaTimeMs > 10 && deltaTimeMs < 1000) {
            updateEnvironmentalFactors(sound);

            Vec3 listenerPos = Engine.mc.player.getEyePosition();
            Vec3 emitterPos = getEmitterPosition(sound);

            if (emitterPos != null && lastUpdateTime > 0) {
                double deltaTimeSec = deltaTimeMs / 1000.0;

                Vec3 listenerVelocity = calculateVelocity(
                        listenerPos, lastListenerPos, deltaTimeSec, lastListenerVelocity
                );
                Vec3 emitterVelocity = calculateVelocity(
                        emitterPos, lastEmitterPos, deltaTimeSec, lastEmitterVelocity
                );

                Vec3 direction = listenerPos.subtract(emitterPos);
                double distance = direction.length();

                if (distance > 0.1) {
                    updateSpeedOfSound(listenerPos, emitterPos);

                    Vec3 effectiveWind = calculateEffectiveWind(emitterPos, listenerPos);
                    Vec3 effectiveListenerVelocity = listenerVelocity.add(effectiveWind);
                    Vec3 effectiveEmitterVelocity = emitterVelocity.add(effectiveWind);

                    Vec3 normalizedDir = direction.normalize();

                    double emitterRadialSpeed = effectiveEmitterVelocity.dot(normalizedDir);
                    double listenerRadialSpeed = effectiveListenerVelocity.dot(normalizedDir);
                    double relativeRadialSpeed = emitterRadialSpeed - listenerRadialSpeed;

                    double terrainFactor = calculateTerrainFactor(emitterPos, listenerPos);
                    relativeRadialSpeed *= terrainFactor;

                    double dopplerFactor = calculateDopplerFactor(relativeRadialSpeed, distance);
                    dopplerFactor = applyMediumEffects(dopplerFactor);

                    dopplerHistory.poll();
                    dopplerHistory.offer(dopplerFactor);
                    double smoothedFactor = smoothDopplerHistory();

                    applyDopplerShift(sound, smoothedFactor, distance);
                    applySpatialEffects(sound, direction, relativeRadialSpeed);

                    if (pC.dLog && deltaTimeMs > 50 && Math.abs(smoothedFactor - 1.0) > 0.001) {
                        Engine.LOGGER.debug("Doppler: factor={}, relSpeed={}m/s, soundSpeed={}m/s, dist={}m, water={}",
                                String.format("%.5f", smoothedFactor),
                                String.format("%.2f", relativeRadialSpeed),
                                String.format("%.1f", currentSpeedOfSound),
                                String.format("%.1f", distance),
                                emitterInWater || listenerInWater ? "yes" : "no");
                    }
                }

                lastEmitterPos = emitterPos;
                lastListenerPos = listenerPos;
                lastEmitterVelocity = emitterVelocity;
                lastListenerVelocity = listenerVelocity;

            } else if (emitterPos != null) {
                lastEmitterPos = emitterPos;
                lastListenerPos = listenerPos;
                lastEmitterVelocity = Vec3.ZERO;
                lastListenerVelocity = Vec3.ZERO;
            }
        }

        lastUpdateTime = currentTime;
        return context;
    }

    private void updateEnvironmentalFactors(SoundProfile sound) {
        if (Engine.mc.player != null && Engine.mc.level != null) {
            BlockPos listenerPos = BlockPos.containing(Engine.mc.player.getEyePosition());
            FluidState listenerFluid = Engine.mc.level.getFluidState(listenerPos);
            listenerInWater = listenerFluid.getType() == Fluids.WATER ||
                    listenerFluid.getType() == Fluids.FLOWING_WATER;

            if (sound.position() != null) {
                BlockPos emitterPos = BlockPos.containing(sound.position());
                FluidState emitterFluid = Engine.mc.level.getFluidState(emitterPos);
                emitterInWater = emitterFluid.getType() == Fluids.WATER ||
                        emitterFluid.getType() == Fluids.FLOWING_WATER;
            }

            if (Engine.mc.level.isRaining()) {
                humidity = 0.8f;
            } else {
                humidity = 0.5f;
            }

            float time = Engine.mc.level.getGameTime() * 0.05f;
            windVelocity = new Vec3(
                    (float)Math.cos(time * 0.1f) * 2.0f,
                    0,
                    (float)Math.sin(time * 0.1f) * 2.0f
            );

            if (Engine.mc.level.isThundering()) {
                windVelocity = windVelocity.scale(1.5);
            }
        }
    }

    private void updateSpeedOfSound(Vec3 listenerPos, Vec3 emitterPos) {
        currentSpeedOfSound = SOUND_SPEED_AIR_20C;

        if (humidity > 0.5f) {
            currentSpeedOfSound += SOUND_SPEED_HUMIDITY_FACTOR * (humidity - 0.5f) * 100.0f;
        }

        float temperature = 20.0f;
        if (Engine.mc.level != null) {
            if (Engine.mc.level.isRaining()) temperature -= 5.0f;
            if (Engine.mc.level.isThundering()) temperature -= 2.0f;
        }
        currentSpeedOfSound += TEMPERATURE_COEFFICIENT * (temperature - 20.0f);

        if (emitterInWater && listenerInWater) {
            currentSpeedOfSound = SOUND_SPEED_WATER;
        } else if (emitterInWater != listenerInWater) {
            currentSpeedOfSound = (SOUND_SPEED_AIR_20C + SOUND_SPEED_WATER) * 0.5f;
        }
    }

    private Vec3 calculateEffectiveWind(Vec3 emitterPos, Vec3 listenerPos) {
        float distance = (float)emitterPos.distanceTo(listenerPos);
        float windAttenuation = (float)Math.exp(-distance / 200.0f);

        return windVelocity.scale(windAttenuation * 0.1f);
    }

    private float calculateTerrainFactor(Vec3 emitterPos, Vec3 listenerPos) {
        if (Engine.mc.level == null) return 1.0f;

        int steps = 20;
        float factor = 1.0f;

        for (int i = 1; i < steps; i++) {
            float t = i / (float)steps;
            Vec3 point = emitterPos.lerp(listenerPos, t);
            BlockPos pos = BlockPos.containing(point);

            if (!Engine.mc.level.getBlockState(pos).isAir()) {
                factor *= 0.92f;
            }

            FluidState fluid = Engine.mc.level.getFluidState(pos);
            if (fluid.getType() == Fluids.WATER || fluid.getType() == Fluids.FLOWING_WATER) {
                factor *= 0.8f;
            } else if (!fluid.isEmpty()) {
                factor *= 0.9f;
            }
        }

        return Mth.clamp(factor, 0.1f, 1.0f);
    }

    private Vec3 calculateVelocity(Vec3 currentPos, Vec3 lastPos, double deltaTimeSec, Vec3 lastVelocity) {
        if (deltaTimeSec < 0.001 || lastPos.equals(Vec3.ZERO)) {
            return Vec3.ZERO;
        }

        Vec3 instantVelocity = currentPos.subtract(lastPos).scale(1.0 / deltaTimeSec);

        float smoothingFactor = 0.3f;
        Vec3 smoothedVelocity;

        if (lastVelocity.length() < 0.1) {
            smoothedVelocity = instantVelocity;
        } else {
            smoothedVelocity = lastVelocity.scale(1.0 - smoothingFactor)
                    .add(instantVelocity.scale(smoothingFactor));
        }
        float maxSpeed = (emitterInWater || listenerInWater) ? 10.0f : 100.0f;
        if (smoothedVelocity.length() > maxSpeed) {
            smoothedVelocity = smoothedVelocity.normalize().scale(maxSpeed);
        }

        return smoothedVelocity;
    }

    private Vec3 getEmitterPosition(SoundProfile sound) {
        if (sound.position() != null) {
            return sound.position();
        }

        try {
            float[] posArray = new float[3];
            AL10.alGetSourcefv(sound.sourceID(), AL10.AL_POSITION, posArray);

            if (!ALUtils.checkErrors("Failed to get OpenAL source position")) {
                Vec3 pos = new Vec3(posArray[0], posArray[1], posArray[2]);

                BlockPos blockPos = BlockPos.containing(pos);
                if (Engine.mc.level != null) {
                    FluidState fluid = Engine.mc.level.getFluidState(blockPos);
                    emitterInWater = fluid.getType() == Fluids.WATER ||
                            fluid.getType() == Fluids.FLOWING_WATER;
                }

                return pos;
            }
        } catch (Exception e) {
            if (pC.dLog) {
                Engine.LOGGER.debug("Failed to get position for sound source {}: {}",
                        sound.sourceID(), e.getMessage());
            }
        }

        if (Engine.mc.player != null) {
            return Engine.mc.player.getEyePosition();
        }

        return Vec3.ZERO;
    }

    private double calculateDopplerFactor(double relativeRadialSpeed, double distance) {
        if (Math.abs(relativeRadialSpeed) < 0.01) {
            return 1.0;
        }

        double factor;
        if (relativeRadialSpeed > 0) {
            factor = currentSpeedOfSound / (currentSpeedOfSound + relativeRadialSpeed);
        } else {
            factor = currentSpeedOfSound / (currentSpeedOfSound - relativeRadialSpeed);
        }

        double distanceEffect = 1.0;
        if (distance > 50.0) {
            distanceEffect = Math.max(0.3, 1.0 - (distance - 50.0) / 200.0);
        }

        factor = 1.0 + (factor - 1.0) * distanceEffect;

        return applySmoothing(Mth.clamp(factor, 0.3, 3.0));
    }

    private double applySmoothing(double newFactor) {
        float smoothing = 0.5f;
        double smoothed = lastDopplerFactor * (1.0 - smoothing) + newFactor * smoothing;
        lastDopplerFactor = smoothed;
        return smoothed;
    }

    private double smoothDopplerHistory() {
        double sum = 0.0;
        for (Double value : dopplerHistory) {
            sum += value;
        }
        return sum / HISTORY_SIZE;
    }

    private double applyMediumEffects(double dopplerFactor) {
        double modifiedFactor = dopplerFactor;

        if (emitterInWater != listenerInWater) {
            modifiedFactor *= 0.85;
        }

        if (humidity > 0.8f) {
            modifiedFactor *= 1.0 + (humidity - 0.8f) * 0.05;
        }

        float windSpeed = (float)windVelocity.length();
        if (windSpeed > 2.0f) {
            modifiedFactor *= 1.0 + windSpeed * 0.01;
        }

        return Mth.clamp(modifiedFactor, 0.2, 2.5);
    }

    private void applyDopplerShift(SoundProfile sound, double dopplerFactor, double distance) {
        try {
            float currentPitch = AL10.alGetSourcef(sound.sourceID(), AL10.AL_PITCH);
            float dopplerPitch = (float)(currentPitch * dopplerFactor);
            float distanceAttenuation = 1.0f;
            if (distance > 20.0) {
                distanceAttenuation = (float)Math.max(0.3, 1.0 - (distance - 20.0) / 100.0);
            }

            float mediumAttenuation = 1.0f;
            if (emitterInWater != listenerInWater) {
                mediumAttenuation = 0.7f;
            } else if (emitterInWater && listenerInWater) {
                mediumAttenuation = 0.9f;
            }

            float newPitch = 1.0f + (dopplerPitch - 1.0f) * distanceAttenuation * mediumAttenuation;

            float pitchVariance = 0.0f;
            if (windVelocity.length() > 1.0f) {
                pitchVariance = (float)(Math.sin(System.currentTimeMillis() * 0.002) * 0.01f);
            }

            newPitch += pitchVariance;
            newPitch = Mth.clamp(newPitch, 0.3f, 2.5f);

            AL10.alSourcef(sound.sourceID(), AL10.AL_PITCH, newPitch);
            if (Math.abs(dopplerFactor - 1.0) > 0.05) {
                applyDopplerFilter(sound, dopplerFactor);
            }

            ALUtils.checkErrors("Failed to apply Doppler pitch shift");
        } catch (Exception e) {
            if (pC.dLog) {
                Engine.LOGGER.debug("Failed to apply Doppler effect to source {}: {}",
                        sound.sourceID(), e.getMessage());
            }
        }
    }

    private void applyDopplerFilter(SoundProfile sound, double dopplerFactor) {
        if (ALset.filters.length > 2) {
            int filter = ALset.filters[2];
            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

            float baseGainHF = MAX_GAINHF;
            float gainHF;

            double speedFactor = Math.abs(dopplerFactor - 1.0);
            gainHF = baseGainHF * (float)Math.pow(0.8, speedFactor * 3.0);

            gainHF = Mth.clamp(gainHF, MIN_GAINHF, MAX_GAINHF);

            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, 1.0f);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gainHF);

            AL10.alSourcei(sound.sourceID(), EXTEfx.AL_DIRECT_FILTER, filter);
        }
    }

    private void applySpatialEffects(SoundProfile sound, Vec3 direction, double relativeSpeed) {
        try {
            float[] posArray = new float[3];
            AL10.alGetSourcefv(sound.sourceID(), AL10.AL_POSITION, posArray);

            // 基于速度的声像定位微调
            float speedFactor = (float)Math.abs(relativeSpeed) / 30.0f;
            speedFactor = Mth.clamp(speedFactor, 0.0f, 1.0f);

            float panningAmount = speedFactor * 0.3f;

            if (direction.length() > 0.1) {
                Vec3 normDir = direction.normalize();
                float pan = (float)normDir.x * panningAmount;

                AL10.alSource3f(sound.sourceID(), AL10.AL_POSITION,
                        posArray[0] + pan,
                        posArray[1],
                        posArray[2]);
            }

            if (Math.abs(relativeSpeed) > 15.0) {
                applyMotionBlur(relativeSpeed, sound);
            }

        } catch (Exception e) {
            if (pC.dLog) {
                Engine.LOGGER.debug("Failed to apply spatial effects to source {}: {}",
                        sound.sourceID(), e.getMessage());
            }
        }
    }

    private void applyMotionBlur(double speed, SoundProfile sound) {
        if (ALset.effects.length > 12 && ALset.slots.length > 12) {
            try {
                int blurEffect = ALset.effects[12];
                EXTEfx.alEffecti(blurEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_CHORUS);

                float speedFactor = (float)Math.min(Math.abs(speed) / 50.0, 1.0);

                EXTEfx.alEffectf(blurEffect, EXTEfx.AL_CHORUS_RATE, 0.3f + speedFactor * 1.5f);
                EXTEfx.alEffectf(blurEffect, EXTEfx.AL_CHORUS_DEPTH, 0.05f + speedFactor * 0.15f);
                EXTEfx.alEffectf(blurEffect, EXTEfx.AL_CHORUS_FEEDBACK, 0.2f);
                EXTEfx.alEffectf(blurEffect, EXTEfx.AL_CHORUS_DELAY, 0.02f);

                EXTEfx.alAuxiliaryEffectSloti(ALset.slots[12], EXTEfx.AL_EFFECTSLOT_EFFECT, blurEffect);

                AL10.alSourcei(sound.sourceID(), EXTEfx.AL_AUXILIARY_SEND_FILTER, ALset.slots[12]);

            } catch (Exception e) {
                if (pC.dLog) {
                    Engine.LOGGER.debug("Failed to apply motion blur effect: {}", e.getMessage());
                }
            }
        }
    }

    public void reset() {
        lastEmitterPos = Vec3.ZERO;
        lastListenerPos = Vec3.ZERO;
        lastEmitterVelocity = Vec3.ZERO;
        lastListenerVelocity = Vec3.ZERO;
        lastDopplerFactor = 1.0;
        lastUpdateTime = 0;
        dopplerHistory.clear();
        for (int i = 0; i < HISTORY_SIZE; i++) {
            dopplerHistory.offer(1.0);
        }
        currentSpeedOfSound = SOUND_SPEED_AIR_20C;
        emitterInWater = false;
        listenerInWater = false;
        windVelocity = Vec3.ZERO;
    }

    @Override
    public void init() {
        AL10.alDopplerFactor(1.0f);
        AL10.alDopplerVelocity(SOUND_SPEED_AIR_20C);
        reset();
        active = true;
        if (pC.dLog) {
            Engine.LOGGER.info("Doppler effect initialized with advanced physical simulation");
        }
    }
}