package me.zcraft.mods.spatialaudio.effects;

import me.zcraft.mods.spatialaudio.Engine;
import me.zcraft.mods.spatialaudio.openal.ALUtils;
import me.zcraft.mods.spatialaudio.openal.ALset;
import me.zcraft.mods.spatialaudio.toolbox.SlotProfile;
import me.zcraft.mods.spatialaudio.toolbox.SoundProfile;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import static me.zcraft.mods.spatialaudio.config.PrecomputedConfig.pC;

public class Doppler extends Effect {
    private float dopplerFactor = 1.0f;
    private boolean dopplerEnabled = true;
    private Vec3 lastListenerPosition = Vec3.ZERO;
    private Vec3 lastSourcePosition = Vec3.ZERO;
    private long lastUpdateTime = 0;

    @Override
    public ALset setup(final long id) {
        if (pC.dLog) Engine.LOGGER.info("loading doppler effect {}", name);

        context = new ALset();
        context.self = id;
        active = true;
        dopplerEnabled = true;

        if (pC.dLog) Engine.LOGGER.info("Setup doppler effect: {}", name);
        return context;
    }

    @Override
    public boolean init() {
        AL10.alDopplerFactor(1.0f);
        AL10.alDopplerVelocity(343.0f);
        ALUtils.checkErrors("Failed to initialize doppler parameters");
        return true;
    }

    public void applyToSource(int sourceId, Vec3 sourcePos, Vec3 sourceVel, Vec3 listenerPos, Vec3 listenerVel) {
        if (!dopplerEnabled || sourceId <= 0) return;

        if (!ALUtils.isValidSource(sourceId)) return;

        float speedOfSound = 343.0f;

        AL10.alDopplerFactor(dopplerFactor);
        AL10.alDopplerVelocity(speedOfSound);

        AL10.alSource3f(sourceId, AL10.AL_POSITION, (float) sourcePos.x, (float) sourcePos.y, (float) sourcePos.z);
        AL10.alSource3f(sourceId, AL10.AL_VELOCITY, (float) sourceVel.x, (float) sourceVel.y, (float) sourceVel.z);

        AL10.alListener3f(AL10.AL_VELOCITY, (float) listenerVel.x, (float) listenerVel.y, (float) listenerVel.z);

        ALUtils.checkErrors("Failed to apply doppler effect to source");
    }

    public void applyToSource(int sourceId, SoundProfile soundProfile) {
        if (!dopplerEnabled || sourceId <= 0) return;
        if (soundProfile.position() == null) return;

        if (Engine.mc == null || Engine.mc.player == null) return;

        Vec3 sourcePos = soundProfile.position();
        Vec3 sourceVel = soundProfile.velocity() != null ? soundProfile.velocity() : Vec3.ZERO;
        Vec3 listenerPos = Engine.mc.player.getEyePosition();

        Vec3 playerVelocity;
        if (Engine.mc.player.getVehicle() != null) {
            playerVelocity = Engine.mc.player.getVehicle().getDeltaMovement();
        } else {
            playerVelocity = Engine.mc.player.getDeltaMovement();
        }

        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime > 0) {
            float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;

            if (deltaTime > 0) {
                Vec3 sourceAcceleration = sourcePos.subtract(lastSourcePosition).scale(1.0 / deltaTime);
                Vec3 listenerAcceleration = listenerPos.subtract(lastListenerPosition).scale(1.0 / deltaTime);

                sourceVel = sourceVel.add(sourceAcceleration.scale(0.3));
                playerVelocity = playerVelocity.add(listenerAcceleration.scale(0.3));
            }
        }

        applyToSource(sourceId, sourcePos, sourceVel, listenerPos, playerVelocity);

        lastSourcePosition = sourcePos;
        lastListenerPosition = listenerPos;
        lastUpdateTime = currentTime;
    }

    public void calculateAndApplyDoppler(int sourceId, Vec3 sourcePos, Vec3 sourceVel) {
        if (!dopplerEnabled || sourceId <= 0) return;

        if (Engine.mc == null || Engine.mc.player == null) return;

        Vec3 listenerPos = Engine.mc.player.getEyePosition();
        Vec3 listenerVel = Engine.mc.player.getDeltaMovement();

        Vec3 relativeVelocity = sourceVel.subtract(listenerVel);
        Vec3 direction = listenerPos.subtract(sourcePos).normalize();

        double relativeSpeed = relativeVelocity.dot(direction);
        double speedOfSound = 343.0;

        double dopplerRatio;
        if (Math.abs(relativeSpeed) < 0.1) {
            dopplerRatio = 1.0;
        } else {
            dopplerRatio = speedOfSound / (speedOfSound + relativeSpeed);
        }

        dopplerRatio = Math.max(0.5, Math.min(2.0, dopplerRatio));

        if (ALUtils.isValidSource(sourceId)) {
            AL10.alSourcef(sourceId, AL11.AL_PITCH, (float) dopplerRatio);
            ALUtils.checkErrors("Failed to apply doppler pitch shift");
        }
    }

    public void setDopplerFactor(float factor) {
        this.dopplerFactor = Math.max(0.0f, Math.min(10.0f, factor));
        AL10.alDopplerFactor(this.dopplerFactor);
        ALUtils.checkErrors("Failed to set doppler factor");
    }

    public void setDopplerVelocity(float velocity) {
        float dopplerVelocity = Math.max(0.1f, Math.min(1000.0f, velocity));
        AL10.alDopplerVelocity(dopplerVelocity);
        ALUtils.checkErrors("Failed to set doppler velocity");
    }

    public void enableDoppler(boolean enable) {
        this.dopplerEnabled = enable;
        if (!enable) {
            AL10.alDopplerFactor(1.0f);
            AL10.alDopplerVelocity(343.0f);
        }
    }

    public boolean isDopplerEnabled() {
        return dopplerEnabled;
    }

    @Override
    public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
        if (dopplerEnabled && sound.sourceID() > 0) {
            applyToSource(sound.sourceID(), sound);
        }
        return context;
    }

    public void cleanup() {
        if (dopplerEnabled) {
            AL10.alDopplerFactor(1.0f);
            AL10.alDopplerVelocity(343.0f);
            ALUtils.checkErrors("Failed to cleanup doppler effect");
        }
        dopplerEnabled = false;
        active = false;
    }

    public void updateListenerVelocity(Vec3 velocity) {
        if (!dopplerEnabled) return;

        AL10.alListener3f(AL10.AL_VELOCITY, (float) velocity.x, (float) velocity.y, (float) velocity.z);
        ALUtils.checkErrors("Failed to update listener velocity");
    }

    public void updateSourcePosition(int sourceId, Vec3 position) {
        if (!dopplerEnabled || sourceId <= 0) return;

        if (ALUtils.isValidSource(sourceId)) {
            AL10.alSource3f(sourceId, AL10.AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
            ALUtils.checkErrors("Failed to update source position");
        }
    }

    public void updateSourceVelocity(int sourceId, Vec3 velocity) {
        if (!dopplerEnabled || sourceId <= 0) return;

        if (ALUtils.isValidSource(sourceId)) {
            AL10.alSource3f(sourceId, AL10.AL_VELOCITY, (float) velocity.x, (float) velocity.y, (float) velocity.z);
            ALUtils.checkErrors("Failed to update source velocity");
        }
    }
}