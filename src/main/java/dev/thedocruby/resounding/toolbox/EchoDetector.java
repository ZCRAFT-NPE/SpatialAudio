package dev.thedocruby.resounding.toolbox;

import dev.thedocruby.resounding.Engine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

public class EchoDetector {

    public static class EchoAnalysis {
        public final List<Double> echoTimes;
        public final List<Double> echoAmplitudes;
        public final double echoDensity;
        public final boolean hasClearEcho;

        public EchoAnalysis(List<Double> times, List<Double> amps,
                            double density, boolean hasClearEcho) {
            this.echoTimes = times;
            this.echoAmplitudes = amps;
            this.echoDensity = density;
            this.hasClearEcho = hasClearEcho;
        }
    }

    public static EchoAnalysis detectEchoes(Level level, Vec3 sourcePos, Vec3 listenerPos) {
        List<Double> echoTimes = new ArrayList<>();
        List<Double> echoAmplitudes = new ArrayList<>();

        List<ReflectiveSurface> surfaces = findReflectiveSurfaces(level, sourcePos, listenerPos);

        for (ReflectiveSurface surface : surfaces) {
            double pathLength = calculateEchoPathLength(sourcePos, listenerPos, surface);
            double echoTime = pathLength / 343.0;

            if (echoTime > 0.05) {
                double amplitude = calculateEchoAmplitude(surface, pathLength);

                echoTimes.add(echoTime);
                echoAmplitudes.add(amplitude);

                if (pC.dLog) {
                    Engine.LOGGER.info("Detected echo: time={}s, amplitude={}, surface={}",
                            echoTime, amplitude, surface.type);
                }
            }
        }

        double echoDensity = calculateEchoDensity(echoTimes);

        boolean hasClearEcho = false;
        for (int i = 0; i < echoTimes.size(); i++) {
            if (echoTimes.get(i) > 0.1 && echoAmplitudes.get(i) > 0.3) {
                hasClearEcho = true;
                break;
            }
        }

        return new EchoAnalysis(echoTimes, echoAmplitudes, echoDensity, hasClearEcho);
    }

    private static class ReflectiveSurface {
        public final Vec3 position;
        public final Vec3 normal;
        public final double reflectivity;
        public final String type;

        public ReflectiveSurface(Vec3 pos, Vec3 norm, double refl, String type) {
            this.position = pos;
            this.normal = norm;
            this.reflectivity = refl;
            this.type = type;
        }
    }

    private static List<ReflectiveSurface> findReflectiveSurfaces(Level level, Vec3 source, Vec3 listener) {
        List<ReflectiveSurface> surfaces = new ArrayList<>();

        int searchRadius = 16;
        BlockPos center = BlockPos.containing(source);

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);

                    if (!level.isEmptyBlock(pos)) {
                        Vec3 blockCenter = Vec3.atCenterOf(pos);
                        Vec3 toSource = source.subtract(blockCenter).normalize();
                        Vec3 toListener = listener.subtract(blockCenter).normalize();

                        Vec3 normal = findFaceNormal(pos, toSource, toListener);

                        if (normal != null) {
                            double reflectivity = calculateSurfaceReflectivity(level, pos, normal);

                            if (reflectivity > 0.3) {
                                String surfaceType = classifySurface(pos, normal, level);
                                surfaces.add(new ReflectiveSurface(blockCenter, normal, reflectivity, surfaceType));
                            }
                        }
                    }
                }
            }
        }

        return surfaces;
    }

    private static Vec3 findFaceNormal(BlockPos pos, Vec3 toSource, Vec3 toListener) {
        Vec3[] possibleNormals = {
                new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 1, 0), new Vec3(0, -1, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1)
        };

        Vec3 bestNormal = null;
        double bestScore = 0;

        for (Vec3 normal : possibleNormals) {
            double sourceDot = Math.abs(toSource.dot(normal));
            double listenerDot = Math.abs(toListener.dot(normal));
            double score = (1.0 - sourceDot) * (1.0 - listenerDot);

            if (score > bestScore) {
                bestScore = score;
                bestNormal = normal;
            }
        }

        return bestScore > 0.3 ? bestNormal : null;
    }

    private static double calculateSurfaceReflectivity(Level level, BlockPos pos, Vec3 normal) {
        return BlockPhysicsUtil.getAbsorptionCoefficient(level.getBlockState(pos));
    }

    private static String classifySurface(BlockPos pos, Vec3 normal, Level level) {
        if (normal.y == 1) return "ceiling";
        if (normal.y == -1) return "floor";

        int solidNeighbors = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos neighbor = pos.offset(dx, 0, dz);
                if (!level.isEmptyBlock(neighbor)) {
                    solidNeighbors++;
                }
            }
        }

        if (solidNeighbors >= 3) return "corner";
        return "wall";
    }

    private static double calculateEchoPathLength(Vec3 source, Vec3 listener, ReflectiveSurface surface) {
        double sourceToSurface = source.distanceTo(surface.position);
        double surfaceToListener = surface.position.distanceTo(listener);
        return sourceToSurface + surfaceToListener;
    }

    private static double calculateEchoAmplitude(ReflectiveSurface surface, double pathLength) {
        double distanceAttenuation = 1.0 / (pathLength * pathLength);
        double airAbsorption = Math.pow(0.994, pathLength);

        return surface.reflectivity * distanceAttenuation * airAbsorption;
    }

    private static double calculateEchoDensity(List<Double> echoTimes) {
        if (echoTimes.size() < 2) return 0.0;
        Collections.sort(echoTimes);

        double totalTimeRange = echoTimes.get(echoTimes.size() - 1) - echoTimes.get(0);
        if (totalTimeRange < 0.01) return 0.0;

        return echoTimes.size() / totalTimeRange;
    }

    public static EchoAnalysis quickEchoDetection(Level level, Vec3 source, Vec3 listener) {

        List<Double> echoTimes = new ArrayList<>();
        List<Double> echoAmplitudes = new ArrayList<>();

        Vec3[] directions = {
                new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 1, 0), new Vec3(0, -1, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1)
        };

        for (Vec3 dir : directions) {
            double echoTime = findEchoInDirection(level, source, listener, dir);
            if (echoTime > 0.05) {
                echoTimes.add(echoTime);

                double distance = echoTime * 343.0;
                double amplitude = 0.7 / (distance * distance);
                echoAmplitudes.add(amplitude);
            }
        }

        double density = !echoTimes.isEmpty() ? echoTimes.size() / 0.5 : 0.0;
        boolean hasClearEcho = echoTimes.stream().anyMatch(t -> t > 0.1);

        return new EchoAnalysis(echoTimes, echoAmplitudes, density, hasClearEcho);
    }

    private static double findEchoInDirection(Level level, Vec3 source, Vec3 listener, Vec3 direction) {
        double maxDistance = 32.0;
        Vec3 rayEnd = source.add(direction.scale(maxDistance));

        BlockPos hitPos = findFirstSolidInDirection(level, BlockPos.containing(source), direction, (int)maxDistance);

        if (hitPos != null) {
            Vec3 hitPoint = Vec3.atCenterOf(hitPos);
            double sourceToWall = source.distanceTo(hitPoint);

            double wallToListener = hitPoint.distanceTo(listener);

            return (sourceToWall + wallToListener) / 343.0;
        }

        return 0.0;
    }

    private static BlockPos findFirstSolidInDirection(Level level, BlockPos start, Vec3 direction, int maxSteps) {
        for (int i = 1; i <= maxSteps; i++) {
            BlockPos pos = start.offset(
                    (int)(direction.x * i),
                    (int)(direction.y * i),
                    (int)(direction.z * i)
            );

            if (!level.isEmptyBlock(pos)) {
                return pos;
            }
        }
        return null;
    }
}