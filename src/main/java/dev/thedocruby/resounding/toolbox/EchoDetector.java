package dev.thedocruby.resounding.toolbox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class EchoDetector {

    public static class EchoAnalysis {
        public final List<Double> echoTimes;
        public final List<Double> echoAmplitudes;
        public final List<Vec3> echoDirections;
        public final List<String> echoMaterials;
        public final double echoDensity;
        public final double echoClarity;
        public final double echoSpaciousness;
        public final boolean hasClearEcho;
        public final double primaryEchoDelay;
        public final double primaryEchoGain;
        public final double averageReflectivity;
        public final double averageAbsorption;
        public final double schroederEnergyRatio;
        public final double earlyDecayTime;
        public final double clarityIndex;
        public final double definition;

        public EchoAnalysis(List<Double> times, List<Double> amps, List<Vec3> dirs,
                            List<String> mats, double density, double clarity,
                            double spaciousness, boolean hasClearEcho,
                            double primaryDelay, double primaryGain,
                            double avgRefl, double avgAbs,
                            double schroederRatio, double edt,
                            double clarityIdx, double definition) {
            this.echoTimes = times;
            this.echoAmplitudes = amps;
            this.echoDirections = dirs;
            this.echoMaterials = mats;
            this.echoDensity = density;
            this.echoClarity = clarity;
            this.echoSpaciousness = spaciousness;
            this.hasClearEcho = hasClearEcho;
            this.primaryEchoDelay = primaryDelay;
            this.primaryEchoGain = primaryGain;
            this.averageReflectivity = avgRefl;
            this.averageAbsorption = avgAbs;
            this.schroederEnergyRatio = schroederRatio;
            this.earlyDecayTime = edt;
            this.clarityIndex = clarityIdx;
            this.definition = definition;
        }
    }

    private static final double SPEED_OF_SOUND = 343.0;
    private static final double AIR_ABSORPTION_COEFFICIENT = 0.0083;
    private static final double CRITICAL_DISTANCE_FACTOR = 0.057;
    private static final double SCHROEDER_LIMIT = 0.001;

    public static EchoAnalysis detectEchoes(Level level, Vec3 sourcePos, Vec3 listenerPos, double soundFrequency) {
        long startTime = System.nanoTime();

        List<EchoPath> allPaths = new ArrayList<>();
        List<ReflectiveSurface> primarySurfaces = findPrimarySurfaces(level, sourcePos, listenerPos, 32);

        double roomVolume = calculateRoomVolume(level, listenerPos);
        double criticalDistance = calculateCriticalDistance(roomVolume, primarySurfaces);

        for (ReflectiveSurface surface : primarySurfaces) {
            List<EchoPath> paths = traceEchoPath(level, sourcePos, listenerPos, surface,
                    calculateMaxReflections(roomVolume),
                    soundFrequency, criticalDistance);
            allPaths.addAll(paths);

            if (allPaths.size() >= 64) break;
        }

        EchoAnalysis analysis = analyzeEchoPaths(allPaths, sourcePos, listenerPos, roomVolume, soundFrequency);

        long endTime = System.nanoTime();
        double processingTime = (endTime - startTime) / 1e6;

        if (processingTime > 5.0) {
            System.out.printf("Echo detection took %.2f ms for %d paths%n", processingTime, allPaths.size());
        }

        return analysis;
    }

    private static class ReflectiveSurface {
        public final Vec3 position;
        public final Vec3 normal;
        public final double reflectivity;
        public final double scattering;
        public final double absorption;
        public final double roughness;
        public final String materialType;
        public final BlockPos blockPos;
        public final BlockState blockState;
        public final double area;
        public final double distanceToSource;
        public final double distanceToListener;

        public ReflectiveSurface(Vec3 pos, Vec3 norm, double refl, double scat,
                                 double abs, double rough, String mat,
                                 BlockPos bpos, BlockState state, double area,
                                 double distSource, double distListener) {
            this.position = pos;
            this.normal = norm;
            this.reflectivity = refl;
            this.scattering = scat;
            this.absorption = abs;
            this.roughness = rough;
            this.materialType = mat;
            this.blockPos = bpos;
            this.blockState = state;
            this.area = area;
            this.distanceToSource = distSource;
            this.distanceToListener = distListener;
        }
    }

    private static class EchoPath {
        public final List<ReflectiveSurface> surfaces;
        public final double totalLength;
        public final double totalReflectivity;
        public final double totalAbsorption;
        public final double arrivalTime;
        public final double arrivalAngle;
        public final double frequencyResponse;
        public final double airAbsorption;
        public final double energy;
        public final double phaseShift;
        public final boolean isSpecular;
        public final double impulseResponseEnergy;

        public EchoPath(List<ReflectiveSurface> surfs, double length, double refl,
                        double abs, double time, double angle, double freqResp,
                        double airAbs, double energy, double phase, boolean specular,
                        double impulseEnergy) {
            this.surfaces = surfs;
            this.totalLength = length;
            this.totalReflectivity = refl;
            this.totalAbsorption = abs;
            this.arrivalTime = time;
            this.arrivalAngle = angle;
            this.frequencyResponse = freqResp;
            this.airAbsorption = airAbs;
            this.energy = energy;
            this.phaseShift = phase;
            this.isSpecular = specular;
            this.impulseResponseEnergy = impulseEnergy;
        }
    }

    private static List<ReflectiveSurface> findPrimarySurfaces(Level level, Vec3 source,
                                                               Vec3 listener, int maxDistance) {
        List<ReflectiveSurface> surfaces = new ArrayList<>();
        BlockPos sourcePos = BlockPos.containing(source);

        Vec3[] searchDirections = generateFibonacciSphereDirections(24);

        for (Vec3 dir : searchDirections) {
            BlockPos hitPos = traceRayToSurface(level, source, dir, maxDistance);
            if (hitPos != null) {
                BlockState state = level.getBlockState(hitPos);
                if (!state.isAir()) {
                    Vec3 hitCenter = Vec3.atCenterOf(hitPos);
                    Vec3 surfaceNormal = calculateSurfaceNormal(level, hitPos, dir);

                    double distanceToSource = source.distanceTo(hitCenter);
                    double distanceToListener = hitCenter.distanceTo(listener);

                    if (distanceToSource < 0.1) continue;

                    double incidenceAngle = Math.acos(Math.abs(dir.dot(surfaceNormal)));
                    double effectiveArea = calculateEffectiveArea(incidenceAngle, 1.0);

                    double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
                    double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
                    double scattering = BlockPhysicsUtil.getScatteringCoefficient(state);
                    double roughness = BlockPhysicsUtil.getRoughnessCoefficient(state);
                    String material = BlockPhysicsUtil.getMaterialType(state);

                    double surfaceQuality = reflectivity * (1.0 - absorption) *
                            (1.0 - scattering * 0.5) * effectiveArea;

                    if (surfaceQuality > 0.05 && reflectivity > 0.15) {
                        surfaces.add(new ReflectiveSurface(hitCenter, surfaceNormal, reflectivity,
                                scattering, absorption, roughness,
                                material, hitPos, state, effectiveArea,
                                distanceToSource, distanceToListener));
                    }
                }
            }
        }

        Collections.sort(surfaces, (a, b) -> {
            double scoreA = a.reflectivity * (1.0 / (1.0 + a.distanceToSource));
            double scoreB = b.reflectivity * (1.0 / (1.0 + b.distanceToSource));
            return Double.compare(scoreB, scoreA);
        });

        return surfaces.size() > 16 ? surfaces.subList(0, 16) : surfaces;
    }

    private static Vec3[] generateFibonacciSphereDirections(int count) {
        List<Vec3> directions = new ArrayList<>();
        double phi = Math.PI * (3.0 - Math.sqrt(5.0));

        for (int i = 0; i < count; i++) {
            double y = 1.0 - (i / (double)(count - 1)) * 2.0;
            double radius = Math.sqrt(1.0 - y * y);
            double theta = phi * i;

            double x = Math.cos(theta) * radius;
            double z = Math.sin(theta) * radius;

            directions.add(new Vec3(x, y, z).normalize());
        }

        return directions.toArray(new Vec3[0]);
    }

    private static List<EchoPath> traceEchoPath(Level level, Vec3 source, Vec3 listener,
                                                ReflectiveSurface startSurface, int maxDepth,
                                                double frequency, double criticalDistance) {
        List<EchoPath> paths = new ArrayList<>();
        Deque<PathState> queue = new ArrayDeque<>();

        double initialPhase = 2.0 * Math.PI * frequency * startSurface.distanceToSource / SPEED_OF_SOUND;
        queue.add(new PathState(source, startSurface, new ArrayList<>(), 0, 0.0, 1.0, 0.0, 1.0, 1.0, initialPhase, 0.0, 0.0));

        while (!queue.isEmpty() && paths.size() < 24) {
            PathState current = queue.poll();

            if (current.depth >= maxDepth) continue;

            Vec3 reflectionDir = calculateReflectionDirection(current.rayDirection,
                    current.surface.normal,
                    current.surface.roughness,
                    current.surface.scattering);

            double specularProbability = Math.exp(-Math.pow(current.surface.roughness * 3.0, 2));
            boolean isSpecularReflection = Math.random() < specularProbability;

            Vec3 nextHit = traceReflectionRay(level, current.surface.position, reflectionDir,
                    calculateRayLength(current.totalLength, maxDepth - current.depth));

            if (nextHit != null) {
                double segmentLength = current.surface.position.distanceTo(nextHit);
                double totalLength = current.totalLength + segmentLength;

                BlockState nextState = level.getBlockState(BlockPos.containing(nextHit));
                if (!nextState.isAir()) {
                    ReflectiveSurface nextSurface = createSurfaceFromHit(level, nextHit, reflectionDir,
                            nextState, segmentLength);

                    List<ReflectiveSurface> pathSurfaces = new ArrayList<>(current.surfaces);
                    pathSurfaces.add(current.surface);

                    double freqResp = BlockPhysicsUtil.getFrequencyResponse(nextSurface.blockState, frequency);
                    double newResponse = current.frequencyResponse * freqResp;

                    double airAbs = Math.exp(-AIR_ABSORPTION_COEFFICIENT * segmentLength *
                            Math.pow(frequency / 1000.0, 1.7));
                    double newAirAbsorption = current.airAbsorption * airAbs;

                    double distanceAttenuation = 1.0 / (1.0 + totalLength / criticalDistance);

                    double surfaceReflectivity = current.surface.reflectivity *
                            (1.0 - current.surface.absorption) *
                            (isSpecularReflection ? 1.0 : (1.0 - current.surface.scattering * 0.7)) *
                            newResponse * newAirAbsorption * distanceAttenuation;

                    double newReflectivity = current.totalReflectivity * surfaceReflectivity;
                    double newAbsorption = current.totalAbsorption + current.surface.absorption;

                    double phaseShift = 2.0 * Math.PI * frequency * segmentLength / SPEED_OF_SOUND;
                    double newPhase = (current.phaseShift + phaseShift) % (2.0 * Math.PI);

                    double impulseEnergy = calculateImpulseResponseEnergy(current.impulseEnergy,
                            segmentLength,
                            current.surface.absorption,
                            frequency);

                    if (newReflectivity > 0.001 && newAbsorption < 0.95) {
                        Vec3 toListener = listener.subtract(nextHit);
                        double listenerDist = toListener.length();
                        double arrivalTime = (totalLength + listenerDist) / SPEED_OF_SOUND;

                        if (arrivalTime > 0.005 && arrivalTime < 1.5) {
                            double arrivalAngle = Math.acos(Math.abs(reflectionDir.dot(toListener.normalize())));

                            double finalAirAbs = newAirAbsorption *
                                    Math.exp(-AIR_ABSORPTION_COEFFICIENT * listenerDist *
                                            Math.pow(frequency / 1000.0, 1.7));
                            double finalReflectivity = newReflectivity * finalAirAbs *
                                    (1.0 / (1.0 + listenerDist / criticalDistance));

                            double pathEnergy = finalReflectivity *
                                    Math.exp(-newAbsorption * pathSurfaces.size() * 0.3) *
                                    (isSpecularReflection ? 1.0 : 0.6);

                            double finalImpulseEnergy = impulseEnergy *
                                    Math.exp(-AIR_ABSORPTION_COEFFICIENT * listenerDist);

                            paths.add(new EchoPath(pathSurfaces, totalLength + listenerDist,
                                    finalReflectivity, newAbsorption,
                                    arrivalTime, arrivalAngle, newResponse,
                                    finalAirAbs, pathEnergy, newPhase,
                                    isSpecularReflection, finalImpulseEnergy));

                            if (current.depth < maxDepth - 1 && pathEnergy > 0.005) {
                                queue.add(new PathState(nextHit, nextSurface, pathSurfaces,
                                        current.depth + 1, totalLength, finalReflectivity,
                                        newAbsorption, newResponse, finalAirAbs,
                                        newPhase, pathEnergy, finalImpulseEnergy));
                            }
                        }
                    }
                }
            }
        }

        return paths;
    }

    private static EchoAnalysis analyzeEchoPaths(List<EchoPath> paths, Vec3 source,
                                                 Vec3 listener, double roomVolume,
                                                 double frequency) {
        if (paths.isEmpty()) {
            return createEmptyAnalysis();
        }

        paths.sort(Comparator.comparingDouble(p -> p.arrivalTime));

        List<Double> times = new ArrayList<>();
        List<Double> amps = new ArrayList<>();
        List<Vec3> dirs = new ArrayList<>();
        List<String> mats = new ArrayList<>();

        double totalReflectivity = 0.0;
        double totalAbsorption = 0.0;
        double primaryDelay = 0.0;
        double primaryGain = 0.0;
        double totalEnergy = 0.0;
        double earlyEnergy = 0.0;
        double lateEnergy = 0.0;

        double schroederTime = calculateSchroederTime(roomVolume);
        double earlyLimit = 0.05;
        double clarityLimit = 0.08;

        for (EchoPath path : paths) {
            if (path.arrivalTime > 0.002 && path.energy > 0.001) {
                double amplitude = Math.sqrt(path.energy);
                double weightedAmplitude = amplitude *
                        Math.exp(-path.arrivalTime / schroederTime) *
                        (1.0 - 0.3 * path.arrivalAngle / Math.PI);

                times.add(path.arrivalTime);
                amps.add(weightedAmplitude);

                if (!path.surfaces.isEmpty()) {
                    ReflectiveSurface lastSurface = path.surfaces.get(path.surfaces.size() - 1);
                    dirs.add(lastSurface.normal);
                    mats.add(lastSurface.materialType);
                } else {
                    dirs.add(new Vec3(0, 0, 0));
                    mats.add("unknown");
                }

                totalReflectivity += path.totalReflectivity;
                totalAbsorption += path.totalAbsorption;
                totalEnergy += path.energy;

                if (path.arrivalTime <= earlyLimit) {
                    earlyEnergy += path.energy;
                }
                if (path.arrivalTime > clarityLimit) {
                    lateEnergy += path.energy;
                }

                if (primaryGain < weightedAmplitude && path.arrivalTime > 0.015 &&
                        path.arrivalTime < 0.25) {
                    primaryGain = weightedAmplitude;
                    primaryDelay = path.arrivalTime;
                }
            }
        }

        double density = calculateEchoDensity(times, roomVolume);
        double clarity = calculateEchoClarity(times, amps, clarityLimit);
        double spaciousness = calculateEchoSpaciousness(dirs, listener, source);

        boolean hasClearEcho = primaryGain > 0.08 && primaryDelay > 0.02 &&
                primaryDelay < 0.5 && clarity > 0.25;

        double avgRefl = paths.isEmpty() ? 0.0 : totalReflectivity / paths.size();
        double avgAbs = paths.isEmpty() ? 0.0 : totalAbsorption / paths.size();

        double schroederRatio = calculateSchroederRatio(times, amps, schroederTime);
        double earlyDecayTime = calculateEarlyDecayTime(times, amps, schroederTime);
        double clarityIndex = calculateClarityIndex(earlyEnergy, lateEnergy);
        double definition = calculateDefinition(earlyEnergy, totalEnergy, earlyLimit);

        return new EchoAnalysis(times, amps, dirs, mats, density, clarity,
                spaciousness, hasClearEcho, primaryDelay, primaryGain,
                avgRefl, avgAbs, schroederRatio, earlyDecayTime,
                clarityIndex, definition);
    }

    private static BlockPos traceRayToSurface(Level level, Vec3 start, Vec3 dir, int maxDist) {
        Vec3 current = start.add(dir.scale(0.5));

        for (int i = 0; i < maxDist; i++) {
            BlockPos blockPos = BlockPos.containing(current);
            if (!level.isEmptyBlock(blockPos)) {
                return blockPos;
            }
            current = current.add(dir.scale(1.0));
        }
        return null;
    }

    private static Vec3 calculateSurfaceNormal(Level level, BlockPos pos, Vec3 rayDir) {
        Vec3[] normals = {
                new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 1, 0), new Vec3(0, -1, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1)
        };

        Vec3 bestNormal = normals[0];
        double bestScore = -1.0;

        Vec3 inverseRayDir = rayDir.scale(-1);

        for (Vec3 normal : normals) {
            double score = inverseRayDir.dot(normal);
            if (score > bestScore) {
                bestScore = score;
                bestNormal = normal;
            }
        }

        return bestNormal;
    }

    private static Vec3 calculateReflectionDirection(Vec3 incident, Vec3 normal,
                                                     double roughness, double scattering) {
        if (roughness < 0.01 && scattering < 0.05) {
            double dot = incident.dot(normal);
            return incident.subtract(normal.scale(2.0 * dot)).normalize();
        }

        return BlockPhysicsUtil.getScatteredDirection(incident, normal, roughness * 0.5 + scattering * 0.3);
    }

    private static Vec3 traceReflectionRay(Level level, Vec3 start, Vec3 dir, double maxDist) {
        Vec3 current = start.add(dir.scale(0.5));
        double traveled = 0.0;

        while (traveled < maxDist) {
            BlockPos blockPos = BlockPos.containing(current);
            if (!level.isEmptyBlock(blockPos)) {
                return current;
            }

            double step = 1.0;
            current = current.add(dir.scale(step));
            traveled += step;
        }
        return null;
    }

    private static ReflectiveSurface createSurfaceFromHit(Level level, Vec3 hit,
                                                          Vec3 dir, BlockState state,
                                                          double distance) {
        BlockPos pos = BlockPos.containing(hit);
        Vec3 normal = calculateSurfaceNormal(level, pos, dir.scale(-1));
        double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
        double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
        double scattering = BlockPhysicsUtil.getScatteringCoefficient(state);
        double roughness = BlockPhysicsUtil.getRoughnessCoefficient(state);
        String material = BlockPhysicsUtil.getMaterialType(state);

        double incidenceAngle = Math.acos(Math.abs(dir.dot(normal)));
        double effectiveArea = calculateEffectiveArea(incidenceAngle, distance);

        return new ReflectiveSurface(hit, normal, reflectivity, scattering, absorption,
                roughness, material, pos, state, effectiveArea, distance, 0.0);
    }

    private static double calculateEffectiveArea(double incidenceAngle, double distance) {
        double cosTheta = Math.cos(incidenceAngle);
        double solidAngle = 2.0 * Math.PI * (1.0 - cosTheta);
        double distanceFactor = 1.0 / (1.0 + distance * 0.1);
        return solidAngle * distanceFactor;
    }

    private static double calculateRayLength(double currentLength, int remainingReflections) {
        double baseLength = 10.0;
        double decayFactor = Math.exp(-currentLength * 0.05);
        return baseLength * decayFactor * (1.0 + remainingReflections * 0.2);
    }

    private static int calculateMaxReflections(double roomVolume) {
        double roomDimension = Math.cbrt(roomVolume);
        return Math.min(6, Math.max(2, (int)(roomDimension / 4.0)));
    }

    private static double calculateRoomVolume(Level level, Vec3 position) {
        BlockPos center = BlockPos.containing(position);
        int radius = 8;

        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight(), center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        int airCount = 0;
        int totalCount = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    totalCount++;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isEmptyBlock(pos)) {
                        airCount++;
                    }
                }
            }
        }

        double fillRatio = (double)airCount / totalCount;
        double boundingVolume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);

        return boundingVolume * fillRatio;
    }

    private static double calculateCriticalDistance(double roomVolume, List<ReflectiveSurface> surfaces) {
        double totalArea = 0.0;
        double avgAbsorption = 0.0;

        for (ReflectiveSurface surface : surfaces) {
            totalArea += surface.area;
            avgAbsorption += surface.absorption;
        }

        avgAbsorption /= Math.max(1, surfaces.size());

        double roomConstant = totalArea * avgAbsorption / (1.0 - avgAbsorption);
        return CRITICAL_DISTANCE_FACTOR * Math.sqrt(roomVolume / (Math.PI * roomConstant));
    }

    private static double calculateSchroederTime(double roomVolume) {
        return 0.161 * roomVolume / (343.0 * 0.01);
    }

    private static double calculateEchoDensity(List<Double> times, double roomVolume) {
        if (times.size() < 2) return 0.0;

        double timeSpan = times.get(times.size() - 1) - times.get(0);
        if (timeSpan < 0.01) return 0.0;

        double theoreticalDensity = roomVolume * 0.0001;
        double actualDensity = times.size() / timeSpan;

        return Math.min(50.0, actualDensity / Math.max(0.1, theoreticalDensity));
    }

    private static double calculateEchoClarity(List<Double> times, List<Double> amps, double limit) {
        if (times.isEmpty()) return 0.0;

        double earlyEnergy = 0.0;
        double lateEnergy = 0.0;

        for (int i = 0; i < times.size(); i++) {
            double energy = Math.pow(amps.get(i), 2);
            if (times.get(i) <= limit) {
                earlyEnergy += energy;
            } else {
                lateEnergy += energy;
            }
        }

        if (lateEnergy < 1e-10) return 1.0;

        return 10.0 * Math.log10(earlyEnergy / lateEnergy);
    }

    private static double calculateEchoSpaciousness(List<Vec3> directions, Vec3 listener, Vec3 source) {
        if (directions.size() < 3) return 0.0;

        Vec3 sourceDirection = source.subtract(listener).normalize();
        double[] directionAngles = new double[directions.size()];

        for (int i = 0; i < directions.size(); i++) {
            double angle = Math.acos(Math.abs(directions.get(i).dot(sourceDirection)));
            directionAngles[i] = angle;
        }

        Arrays.sort(directionAngles);

        double angularSpread = 0.0;
        for (int i = 1; i < directionAngles.length; i++) {
            angularSpread += directionAngles[i] - directionAngles[i-1];
        }

        double meanAngularSpread = angularSpread / (directionAngles.length - 1);
        return Math.min(1.0, meanAngularSpread / (Math.PI / 4.0));
    }

    private static double calculateSchroederRatio(List<Double> times, List<Double> amps, double schroederTime) {
        if (times.isEmpty()) return 0.0;

        double totalEnergy = 0.0;
        double schroederEnergy = 0.0;

        for (int i = 0; i < times.size(); i++) {
            double energy = Math.pow(amps.get(i), 2);
            totalEnergy += energy;

            if (times.get(i) <= schroederTime) {
                schroederEnergy += energy;
            }
        }

        if (totalEnergy < 1e-10) return 0.0;

        return schroederEnergy / totalEnergy;
    }

    private static double calculateEarlyDecayTime(List<Double> times, List<Double> amps, double schroederTime) {
        if (times.isEmpty()) return 0.0;

        List<Double> earlyTimes = new ArrayList<>();
        List<Double> earlyEnergies = new ArrayList<>();

        for (int i = 0; i < times.size(); i++) {
            if (times.get(i) <= schroederTime) {
                earlyTimes.add(times.get(i));
                earlyEnergies.add(Math.log(Math.pow(amps.get(i), 2)));
            }
        }

        if (earlyTimes.size() < 2) return 0.0;

        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumX2 = 0.0;
        int n = earlyTimes.size();

        for (int i = 0; i < n; i++) {
            double x = earlyTimes.get(i);
            double y = earlyEnergies.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return -60.0 / slope;
    }

    private static double calculateClarityIndex(double earlyEnergy, double lateEnergy) {
        if (lateEnergy < 1e-10) return 100.0;
        return 10.0 * Math.log10(earlyEnergy / lateEnergy);
    }

    private static double calculateDefinition(double earlyEnergy, double totalEnergy, double earlyLimit) {
        if (totalEnergy < 1e-10) return 0.0;
        return earlyEnergy / totalEnergy;
    }

    private static double calculateImpulseResponseEnergy(double previousEnergy, double segmentLength,
                                                         double absorption, double frequency) {
        double geometricAttenuation = 1.0 / (1.0 + segmentLength * 0.1);
        double materialAttenuation = Math.exp(-absorption * segmentLength * 0.5);
        double frequencyAttenuation = Math.exp(-AIR_ABSORPTION_COEFFICIENT * segmentLength *
                Math.pow(frequency / 1000.0, 1.7));

        return previousEnergy * geometricAttenuation * materialAttenuation * frequencyAttenuation;
    }

    private static EchoAnalysis createEmptyAnalysis() {
        return new EchoAnalysis(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                0.0, 0.0, 0.0, false, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0
        );
    }

    private static class PathState {
        final Vec3 position;
        final ReflectiveSurface surface;
        final List<ReflectiveSurface> surfaces;
        final int depth;
        final double totalLength;
        final double totalReflectivity;
        final double totalAbsorption;
        final double frequencyResponse;
        final double airAbsorption;
        final Vec3 rayDirection;
        final double phaseShift;
        final double energy;
        final double impulseEnergy;

        PathState(Vec3 pos, ReflectiveSurface surf, List<ReflectiveSurface> surfs,
                  int d, double length, double refl, double abs, double freqResp,
                  double airAbs, double phase, double en, double impulseEn) {
            this.position = pos;
            this.surface = surf;
            this.surfaces = surfs;
            this.depth = d;
            this.totalLength = length;
            this.totalReflectivity = refl;
            this.totalAbsorption = abs;
            this.frequencyResponse = freqResp;
            this.airAbsorption = airAbs;
            this.rayDirection = surf != null ? pos.subtract(surf.position).normalize() : new Vec3(0, 0, 0);
            this.phaseShift = phase;
            this.energy = en;
            this.impulseEnergy = impulseEn;
        }
    }

    public static EchoAnalysis quickEchoDetection(Level level, Vec3 source, Vec3 listener) {
        List<Double> echoTimes = new ArrayList<>();
        List<Double> echoAmplitudes = new ArrayList<>();
        List<Vec3> echoDirections = new ArrayList<>();
        List<String> echoMaterials = new ArrayList<>();

        Vec3[] directions = generateFibonacciSphereDirections(12);

        double totalReflectivity = 0.0;
        double totalAbsorption = 0.0;
        int surfaceCount = 0;

        for (Vec3 dir : directions) {
            BlockPos hit = traceRayToSurface(level, source, dir, 25);
            if (hit != null) {
                BlockState state = level.getBlockState(hit);
                if (!state.isAir()) {
                    Vec3 hitPoint = Vec3.atCenterOf(hit);
                    double sourceToWall = source.distanceTo(hitPoint);
                    double wallToListener = hitPoint.distanceTo(listener);
                    double totalDistance = sourceToWall + wallToListener;
                    double echoTime = totalDistance / SPEED_OF_SOUND;

                    if (echoTime > 0.005 && echoTime < 0.5) {
                        double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
                        double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
                        String material = BlockPhysicsUtil.getMaterialType(state);

                        totalReflectivity += reflectivity;
                        totalAbsorption += absorption;
                        surfaceCount++;

                        double incidenceAngle = Math.acos(Math.abs(dir.dot(calculateSurfaceNormal(level, hit, dir))));
                        double distanceAttenuation = 1.0 / (1.0 + totalDistance * 0.15);
                        double angleAttenuation = Math.cos(incidenceAngle);
                        double airAbsorption = Math.exp(-AIR_ABSORPTION_COEFFICIENT * totalDistance * 1.2);

                        double amplitude = reflectivity * (1.0 - absorption) *
                                distanceAttenuation * angleAttenuation * airAbsorption;

                        if (amplitude > 0.02) {
                            echoTimes.add(echoTime);
                            echoAmplitudes.add(amplitude);
                            echoDirections.add(dir);
                            echoMaterials.add(material);
                        }
                    }
                }
            }
        }

        double roomVolume = calculateRoomVolume(level, listener);
        double density = calculateEchoDensity(echoTimes, roomVolume);
        double clarity = calculateEchoClarity(echoTimes, echoAmplitudes, 0.08);
        double spaciousness = calculateEchoSpaciousness(echoDirections, listener, source);

        boolean hasClearEcho = false;
        double primaryDelay = 0.0;
        double primaryGain = 0.0;

        for (int i = 0; i < echoTimes.size(); i++) {
            if (echoTimes.get(i) > 0.02 && echoTimes.get(i) < 0.3 &&
                    echoAmplitudes.get(i) > 0.06) {
                hasClearEcho = true;
                if (echoAmplitudes.get(i) > primaryGain) {
                    primaryGain = echoAmplitudes.get(i);
                    primaryDelay = echoTimes.get(i);
                }
            }
        }

        double avgRefl = surfaceCount > 0 ? totalReflectivity / surfaceCount : 0.0;
        double avgAbs = surfaceCount > 0 ? totalAbsorption / surfaceCount : 0.0;
        double schroederTime = calculateSchroederTime(roomVolume);
        double schroederRatio = calculateSchroederRatio(echoTimes, echoAmplitudes, schroederTime);
        double earlyDecayTime = calculateEarlyDecayTime(echoTimes, echoAmplitudes, schroederTime);

        double earlyEnergy = 0.0;
        double lateEnergy = 0.0;
        double totalEnergy = 0.0;

        for (int i = 0; i < echoTimes.size(); i++) {
            double energy = Math.pow(echoAmplitudes.get(i), 2);
            totalEnergy += energy;
            if (echoTimes.get(i) <= 0.05) {
                earlyEnergy += energy;
            } else if (echoTimes.get(i) > 0.08) {
                lateEnergy += energy;
            }
        }

        double clarityIndex = calculateClarityIndex(earlyEnergy, lateEnergy);
        double definition = calculateDefinition(earlyEnergy, totalEnergy, 0.05);

        return new EchoAnalysis(echoTimes, echoAmplitudes, echoDirections, echoMaterials,
                density, clarity, spaciousness, hasClearEcho,
                primaryDelay, primaryGain, avgRefl, avgAbs,
                schroederRatio, earlyDecayTime, clarityIndex, definition);
    }
}