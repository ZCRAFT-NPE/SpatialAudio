package dev.thedocruby.resounding.effects;

import dev.thedocruby.resounding.openal.*;
import dev.thedocruby.resounding.toolbox.*;
import dev.thedocruby.resounding.Engine;
import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.AL10;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class Occlusion extends Effect {
    protected static final float MAX_GAINHF = 1.0f;
    protected static final float MIN_GAINHF = 0.01f;
    private static final float MIN_GAIN = 0.1f;

    public Occlusion() {
        name = "Occlusion";
    }

    @Override
    public ALset update(SlotProfile slot, SoundProfile sound, boolean isGentle) {
        if (!active) return context;

        double occlusionFactor = calculateOcclusion(sound, slot);

        if (ALset.filters.length > 0) {
            int filter = ALset.filters[0];

            float gain = calculateOcclusionGain(occlusionFactor);
            float gainHF = calculateOcclusionGainHF(occlusionFactor);

            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gainHF);

            AL10.alSourcei(sound.sourceID(), EXTEfx.AL_DIRECT_FILTER, filter);

            if (pC.dLog && occlusionFactor < 0.9) {
                Engine.LOGGER.debug("Occlusion applied: factor={}, gain={}, gainHF={}",
                        String.format("%.2f", occlusionFactor),
                        String.format("%.2f", gain),
                        String.format("%.2f", gainHF));
            }
        }

        return context;
    }

    private double calculateOcclusion(SoundProfile sound, SlotProfile slot) {
        if (Engine.mc == null || Engine.mc.level == null || Engine.mc.player == null) {
            return 1.0;
        }

        double gain = slot.gain();
        double baseOcclusion = 1.0 - gain * 0.5;

        if (sound.position() != null) {
            Vec3 soundPos = sound.position();
            Vec3 listenerPos = Engine.mc.player.getEyePosition();

            double distance = soundPos.distanceTo(listenerPos);

            int occludingBlocks = countOccludingBlocks(soundPos, listenerPos);
            double occlusionFromBlocks = Math.pow(0.85, occludingBlocks);

            double distanceFactor = Mth.clamp(distance / 50.0, 0.0, 1.0);
            baseOcclusion = Mth.lerp(distanceFactor, baseOcclusion, baseOcclusion * 0.7);

            baseOcclusion *= occlusionFromBlocks;
        }

        return Mth.clamp(baseOcclusion, 0.1, 1.0);
    }

    private int countOccludingBlocks(Vec3 start, Vec3 end) {
        if (Engine.mc.level == null) return 0;

        int steps = 20;
        int occludingBlocks = 0;

        for (int i = 1; i <= steps; i++) {
            float t = i / (float)steps;
            Vec3 point = start.lerp(end, t);
            BlockPos pos = BlockPos.containing(point);

            if (!Engine.mc.level.getBlockState(pos).isAir()) {
                occludingBlocks++;
            }
        }

        return occludingBlocks;
    }

    private float calculateOcclusionGain(double occlusionFactor) {
        return (float) Mth.lerp(occlusionFactor, MIN_GAIN, 1.0f);
    }

    private float calculateOcclusionGainHF(double occlusionFactor) {
        return (float) Mth.lerp(occlusionFactor, MIN_GAINHF, MAX_GAINHF);
    }

    @Override
    public void init() {
        for (int i = 0; i < ALset.filters.length; i++) {
            EXTEfx.alFilteri(ALset.filters[i], EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(ALset.filters[i], EXTEfx.AL_LOWPASS_GAIN, 1.0f);
            EXTEfx.alFilterf(ALset.filters[i], EXTEfx.AL_LOWPASS_GAINHF, MAX_GAINHF);
        }
        active = true;
    }
}