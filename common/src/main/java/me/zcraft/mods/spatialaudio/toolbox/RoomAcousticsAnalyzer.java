package me.zcraft.mods.spatialaudio.toolbox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class RoomAcousticsAnalyzer {
    private static final int MAX_ROOM_VOLUME = 100_000;
    private static final int MAX_SEARCH_DISTANCE = 64;
    private static final int MAX_ITERATIONS = 10_000;
    private static final Map<Long, RoomAnalysis> analysisCache = new WeakHashMap<>();
    private static final int CACHE_DURATION_TICKS = 20;

    public static class RoomAnalysis {
        public final AABB bounds;
        public final Vec3 dimensions;
        public final double volume;
        public final double surfaceArea;
        public final double totalAbsorptionArea;
        public final double totalReflectionArea;
        public final double averageAbsorption;
        public final double averageReflectivity;
        public final double avgWallDistance;
        public final boolean isEnclosed;
        public final RoomType roomType;
        public final double schroederFrequency;
        public final double meanFreePath;
        public final double criticalDistance;
        public final double modalDensity;
        public final double[] absorptionByFrequency;

        public RoomAnalysis(AABB bounds, Vec3 dimensions, double volume, double surfaceArea,
                            double totalAbsorptionArea, double totalReflectionArea,
                            double averageAbsorption, double averageReflectivity,
                            double avgWallDistance, boolean isEnclosed, RoomType roomType,
                            double schroederFrequency, double meanFreePath, double criticalDistance,
                            double modalDensity, double[] absorptionByFrequency) {
            this.bounds = bounds;
            this.dimensions = dimensions;
            this.volume = volume;
            this.surfaceArea = surfaceArea;
            this.totalAbsorptionArea = totalAbsorptionArea;
            this.totalReflectionArea = totalReflectionArea;
            this.averageAbsorption = averageAbsorption;
            this.averageReflectivity = averageReflectivity;
            this.avgWallDistance = avgWallDistance;
            this.isEnclosed = isEnclosed;
            this.roomType = roomType;
            this.schroederFrequency = schroederFrequency;
            this.meanFreePath = meanFreePath;
            this.criticalDistance = criticalDistance;
            this.modalDensity = modalDensity;
            this.absorptionByFrequency = absorptionByFrequency;
        }
    }

    public enum RoomType {
        TINY(0, 100),
        SMALL(100, 1000),
        MEDIUM(1000, 8000),
        LARGE(8000, 27000),
        HUGE(27000, 64000),
        CATHEDRAL(64000, Double.MAX_VALUE);

        public final double minVolume;
        public final double maxVolume;

        RoomType(double minVolume, double maxVolume) {
            this.minVolume = minVolume;
            this.maxVolume = maxVolume;
        }

        public static RoomType fromVolume(double volume) {
            for (RoomType type : values()) {
                if (volume >= type.minVolume && volume < type.maxVolume) {
                    return type;
                }
            }
            return CATHEDRAL;
        }
    }

    public static RoomAnalysis analyzeRoom(Level level, Vec3 center) {
        BlockPos centerPos = BlockPos.containing(center);
        long cacheKey = getCacheKey(level, centerPos);
        RoomAnalysis cached = analysisCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        AABB searchBounds = new AABB(
                centerPos.getX() - MAX_SEARCH_DISTANCE,
                centerPos.getY() - MAX_SEARCH_DISTANCE,
                centerPos.getZ() - MAX_SEARCH_DISTANCE,
                centerPos.getX() + MAX_SEARCH_DISTANCE,
                centerPos.getY() + MAX_SEARCH_DISTANCE,
                centerPos.getZ() + MAX_SEARCH_DISTANCE
        );

        AABB bounds = findRoomBoundsOptimized(level, centerPos, searchBounds);
        Vec3 dimensions = new Vec3(
                bounds.maxX - bounds.minX,
                bounds.maxY - bounds.minY,
                bounds.maxZ - bounds.minZ
        );
        double volume = calculateVolume(bounds);

        if (volume > MAX_ROOM_VOLUME) {
            bounds = createBoundedAABB(centerPos, MAX_SEARCH_DISTANCE);
            dimensions = new Vec3(
                    bounds.maxX - bounds.minX,
                    bounds.maxY - bounds.minY,
                    bounds.maxZ - bounds.minZ
            );
            volume = calculateVolume(bounds);
        }

        RoomAnalysisData data = collectRoomData(level, bounds);
        RoomType roomType = RoomType.fromVolume(volume);

        double schroederFrequency = calculateSchroederFrequency(volume);
        double meanFreePath = 4.0 * volume / data.surfaceArea;
        double criticalDistance = calculateCriticalDistance(volume, data.averageAbsorption);
        double modalDensity = calculateModalDensity(volume, schroederFrequency);
        double[] absorptionByFrequency = calculateAbsorptionByFrequency(data.materialAbsorptionMap);

        RoomAnalysis analysis = new RoomAnalysis(
                bounds, dimensions, volume, data.surfaceArea,
                data.totalAbsorptionArea, data.totalReflectionArea,
                data.averageAbsorption, data.averageReflectivity,
                data.avgWallDistance, data.isEnclosed, roomType,
                schroederFrequency, meanFreePath, criticalDistance,
                modalDensity, absorptionByFrequency
        );

        analysisCache.put(cacheKey, analysis);
        return analysis;
    }

    private static long getCacheKey(Level level, BlockPos pos) {
        long tick = level.getGameTime() / CACHE_DURATION_TICKS;
        return ((long) pos.getX() << 40) |
                ((long) pos.getY() << 20) |
                pos.getZ() |
                (tick << 60);
    }

    private static AABB findRoomBoundsOptimized(Level level, BlockPos start, AABB searchBounds) {
        int sizeX = (int)(searchBounds.maxX - searchBounds.minX) + 1;
        int sizeY = (int)(searchBounds.maxY - searchBounds.minY) + 1;
        int sizeZ = (int)(searchBounds.maxZ - searchBounds.minZ) + 1;

        boolean[][][] visited = new boolean[sizeX][sizeY][sizeZ];
        int offsetX = (int) searchBounds.minX;
        int offsetY = (int) searchBounds.minY;
        int offsetZ = (int) searchBounds.minZ;

        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);

        int startRelX = start.getX() - offsetX;
        int startRelY = start.getY() - offsetY;
        int startRelZ = start.getZ() - offsetZ;
        visited[startRelX][startRelY][startRelZ] = true;

        int minX = start.getX();
        int minY = start.getY();
        int minZ = start.getZ();
        int maxX = start.getX();
        int maxY = start.getY();
        int maxZ = start.getZ();

        int iterations = 0;
        final int[] dxArray = {-1, 1, 0, 0, 0, 0};
        final int[] dyArray = {0, 0, -1, 1, 0, 0};
        final int[] dzArray = {0, 0, 0, 0, -1, 1};

        while (!queue.isEmpty() && iterations++ < MAX_ITERATIONS) {
            BlockPos current = queue.poll();
            int x = current.getX();
            int y = current.getY();
            int z = current.getZ();

            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;

            for (int i = 0; i < 6; i++) {
                int nx = x + dxArray[i];
                int ny = y + dyArray[i];
                int nz = z + dzArray[i];

                if (nx < searchBounds.minX || nx > searchBounds.maxX ||
                        ny < searchBounds.minY || ny > searchBounds.maxY ||
                        nz < searchBounds.minZ || nz > searchBounds.maxZ) {
                    continue;
                }

                int relX = nx - offsetX;
                int relY = ny - offsetY;
                int relZ = nz - offsetZ;

                if (!visited[relX][relY][relZ]) {
                    BlockPos neighbor = new BlockPos(nx, ny, nz);
                    if (isPassable(level, neighbor)) {
                        visited[relX][relY][relZ] = true;
                        queue.add(neighbor);
                    }
                }
            }
        }

        return new AABB(minX - 1, minY - 1, minZ - 1,
                maxX + 2, maxY + 2, maxZ + 2);
    }

    private static boolean isPassable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() ||
                state.getBlock() == Blocks.MOVING_PISTON ||
                !state.blocksMotion();
    }

    private static AABB createBoundedAABB(BlockPos center, int radius) {
        return new AABB(
                center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius, center.getY() + radius, center.getZ() + radius
        );
    }

    private static class RoomAnalysisData {
        final double surfaceArea;
        final double totalAbsorptionArea;
        final double totalReflectionArea;
        final double averageAbsorption;
        final double averageReflectivity;
        final double avgWallDistance;
        final boolean isEnclosed;
        final Map<String, Double> materialAbsorptionMap;

        RoomAnalysisData(double surfaceArea, double totalAbsorptionArea,
                         double totalReflectionArea, double averageAbsorption,
                         double averageReflectivity, double avgWallDistance,
                         boolean isEnclosed, Map<String, Double> materialAbsorptionMap) {
            this.surfaceArea = surfaceArea;
            this.totalAbsorptionArea = totalAbsorptionArea;
            this.totalReflectionArea = totalReflectionArea;
            this.averageAbsorption = averageAbsorption;
            this.averageReflectivity = averageReflectivity;
            this.avgWallDistance = avgWallDistance;
            this.isEnclosed = isEnclosed;
            this.materialAbsorptionMap = materialAbsorptionMap;
        }
    }

    private static RoomAnalysisData collectRoomData(Level level, AABB bounds) {
        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX);
        int maxY = (int) Math.ceil(bounds.maxY);
        int maxZ = (int) Math.ceil(bounds.maxZ);

        int sizeX = maxX - minX + 3;
        int sizeY = maxY - minY + 3;
        int sizeZ = maxZ - minZ + 3;

        boolean[][][] solidBlocks = new boolean[sizeX][sizeY][sizeZ];
        Map<String, Integer> materialCount = new HashMap<>();
        Map<String, Double> materialAbsorptionMap = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos) && !isPassable(level, pos)) {
                        solidBlocks[x - minX + 1][y - minY + 1][z - minZ + 1] = true;
                        String materialType = BlockPhysicsUtil.getMaterialType(level.getBlockState(pos));
                        materialCount.put(materialType, materialCount.getOrDefault(materialType, 0) + 1);
                    }
                }
            }
        }

        double surfaceArea = 0.0;
        double totalAbsorptionArea = 0.0;
        double totalReflectionArea = 0.0;
        int surfaceCount = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int bx = x - minX + 1;
                    int by = y - minY + 1;
                    int bz = z - minZ + 1;

                    if (!solidBlocks[bx][by][bz]) {
                        if (solidBlocks[bx + 1][by][bz]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            BlockPos wallPos = new BlockPos(x + 1, y, z);
                            BlockState state = level.getBlockState(wallPos);
                            double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
                            double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
                            totalAbsorptionArea += absorption;
                            totalReflectionArea += reflectivity;

                            String material = BlockPhysicsUtil.getMaterialType(state);
                            materialAbsorptionMap.put(material,
                                    materialAbsorptionMap.getOrDefault(material, 0.0) + absorption);
                        }
                        if (solidBlocks[bx - 1][by][bz]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            BlockPos wallPos = new BlockPos(x - 1, y, z);
                            BlockState state = level.getBlockState(wallPos);
                            double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
                            double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
                            totalAbsorptionArea += absorption;
                            totalReflectionArea += reflectivity;

                            String material = BlockPhysicsUtil.getMaterialType(state);
                            materialAbsorptionMap.put(material,
                                    materialAbsorptionMap.getOrDefault(material, 0.0) + absorption);
                        }
                        if (solidBlocks[bx][by + 1][bz]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            BlockPos wallPos = new BlockPos(x, y + 1, z);
                            BlockState state = level.getBlockState(wallPos);
                            double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
                            double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
                            totalAbsorptionArea += absorption;
                            totalReflectionArea += reflectivity;

                            String material = BlockPhysicsUtil.getMaterialType(state);
                            materialAbsorptionMap.put(material,
                                    materialAbsorptionMap.getOrDefault(material, 0.0) + absorption);
                        }
                        if (solidBlocks[bx][by - 1][bz]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            BlockPos wallPos = new BlockPos(x, y - 1, z);
                            BlockState state = level.getBlockState(wallPos);
                            double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
                            double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
                            totalAbsorptionArea += absorption;
                            totalReflectionArea += reflectivity;

                            String material = BlockPhysicsUtil.getMaterialType(state);
                            materialAbsorptionMap.put(material,
                                    materialAbsorptionMap.getOrDefault(material, 0.0) + absorption);
                        }
                        if (solidBlocks[bx][by][bz + 1]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            BlockPos wallPos = new BlockPos(x, y, z + 1);
                            BlockState state = level.getBlockState(wallPos);
                            double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
                            double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
                            totalAbsorptionArea += absorption;
                            totalReflectionArea += reflectivity;

                            String material = BlockPhysicsUtil.getMaterialType(state);
                            materialAbsorptionMap.put(material,
                                    materialAbsorptionMap.getOrDefault(material, 0.0) + absorption);
                        }
                        if (solidBlocks[bx][by][bz - 1]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            BlockPos wallPos = new BlockPos(x, y, z - 1);
                            BlockState state = level.getBlockState(wallPos);
                            double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
                            double reflectivity = BlockPhysicsUtil.getReflectivityCoefficient(state);
                            totalAbsorptionArea += absorption;
                            totalReflectionArea += reflectivity;

                            String material = BlockPhysicsUtil.getMaterialType(state);
                            materialAbsorptionMap.put(material,
                                    materialAbsorptionMap.getOrDefault(material, 0.0) + absorption);
                        }
                    }
                }
            }
        }

        boolean isEnclosed = isEnclosedSimplified(level, bounds, solidBlocks, minX, minY, minZ);
        double averageAbsorption = surfaceCount > 0 ? totalAbsorptionArea / surfaceCount : 0.1;
        double averageReflectivity = surfaceCount > 0 ? totalReflectionArea / surfaceCount : 0.9;
        double avgWallDistance = calculateAverageWallDistance(bounds);

        for (String material : materialAbsorptionMap.keySet()) {
            materialAbsorptionMap.put(material, materialAbsorptionMap.get(material) / surfaceCount);
        }

        return new RoomAnalysisData(surfaceArea, totalAbsorptionArea, totalReflectionArea,
                averageAbsorption, averageReflectivity, avgWallDistance, isEnclosed, materialAbsorptionMap);
    }

    private static boolean isEnclosedSimplified(Level level, AABB bounds,
                                                boolean[][][] solidBlocks,
                                                int minX, int minY, int minZ) {
        int sizeX = solidBlocks.length;
        int sizeY = solidBlocks[0].length;
        int sizeZ = solidBlocks[0][0].length;

        int sampleCount = Math.min(20, sizeX * sizeZ);
        for (int i = 0; i < sampleCount; i++) {
            int x = minX + (i * sizeX / sampleCount);
            int z = minZ + (i * sizeZ / sampleCount);

            if (!solidBlocks[x - minX + 1][sizeY - 1][z - minZ + 1] ||
                    !solidBlocks[x - minX + 1][0][z - minZ + 1]) {
                return false;
            }
        }

        return true;
    }

    private static double calculateVolume(AABB bounds) {
        double width = bounds.maxX - bounds.minX;
        double height = bounds.maxY - bounds.minY;
        double depth = bounds.maxZ - bounds.minZ;
        return width * height * depth;
    }

    private static double calculateAverageWallDistance(AABB bounds) {
        double width = bounds.maxX - bounds.minX;
        double height = bounds.maxY - bounds.minY;
        double depth = bounds.maxZ - bounds.minZ;
        return (width + height + depth) / 6.0;
    }

    private static double calculateSchroederFrequency(double volume) {
        return 2000.0 * Math.sqrt(0.05 / volume);
    }

    private static double calculateCriticalDistance(double volume, double averageAbsorption) {
        double roomConstant = volume * averageAbsorption / (1.0 - averageAbsorption);
        return 0.057 * Math.sqrt(volume / (Math.PI * roomConstant));
    }

    private static double calculateModalDensity(double volume, double schroederFrequency) {
        double speedOfSound = 343.0;
        return 4.0 * Math.PI * volume * Math.pow(schroederFrequency, 3) / Math.pow(speedOfSound, 3);
    }

    private static double[] calculateAbsorptionByFrequency(Map<String, Double> materialAbsorptionMap) {
        double[] absorptionByFrequency = new double[8];
        double[] frequencies = {125, 250, 500, 1000, 2000, 4000, 8000, 16000};

        for (int i = 0; i < frequencies.length; i++) {
            double freq = frequencies[i];
            double totalAbsorption = 0.0;
            int count = 0;

            for (Map.Entry<String, Double> entry : materialAbsorptionMap.entrySet()) {
                String material = entry.getKey();
                double baseAbsorption = entry.getValue();

                double frequencyFactor = switch (material) {
                    case "glass" -> 1.0 - 0.1 * Math.log10(freq / 1000.0);
                    case "metal" -> 1.0 - 0.05 * Math.log10(freq / 1000.0);
                    case "wool" -> 1.0 + 0.2 * Math.log10(freq / 1000.0);
                    case "wood" -> 1.0 - 0.08 * Math.log10(freq / 1000.0);
                    case "stone" -> 1.0 - 0.03 * Math.log10(freq / 1000.0);
                    case "porous" -> 1.0 + 0.3 * Math.log10(freq / 1000.0);
                    default -> 1.0;
                };

                totalAbsorption += baseAbsorption * frequencyFactor;
                count++;
            }

            absorptionByFrequency[i] = count > 0 ? totalAbsorption / count : 0.1;
        }

        return absorptionByFrequency;
    }

    public static double calculateReverberationTime(RoomAnalysis room) {
        if (!room.isEnclosed) return 0.1;

        double V = room.volume;
        double S = room.surfaceArea;
        double α = room.averageAbsorption;

        if (S * α == 0) return 0.1;

        double rt60Sabine = 0.161 * V / (S * α);

        double eyringCorrection = 0.0;
        if (α < 0.9) {
            eyringCorrection = -0.161 * V / (S * Math.log(1.0 - α));
        }

        double rt60 = (rt60Sabine + eyringCorrection) / 2.0;

        switch (room.roomType) {
            case TINY:
                rt60 *= 0.3;
                break;
            case SMALL:
                rt60 *= 0.6;
                break;
            case MEDIUM:
                rt60 *= 0.9;
                break;
            case LARGE:
                rt60 *= 1.2;
                break;
            case HUGE:
                rt60 *= 1.5;
                break;
            case CATHEDRAL:
                rt60 *= 2.0;
                break;
        }

        return Math.max(0.1, Math.min(12.0, rt60));
    }

    public static double calculateEarlyReflectionsDelay(RoomAnalysis room) {
        return room.meanFreePath / 343.0;
    }

    public static double calculateLateReverbDelay(RoomAnalysis room) {
        return room.criticalDistance / 343.0;
    }

    public static double calculateDensity(RoomAnalysis room) {
        double baseDensity = 0.5 + 0.5 * Math.exp(-room.volume / 10000.0);
        baseDensity *= (1.0 - room.averageAbsorption * 0.5);
        baseDensity *= Math.min(1.0, room.modalDensity / 1000.0);
        return Math.max(0.1, Math.min(1.0, baseDensity));
    }

    public static double calculateDiffusion(RoomAnalysis room) {
        double baseDiffusion = 0.7;
        double irregularity = room.surfaceArea / Math.pow(room.volume, 2.0/3.0);
        baseDiffusion += Math.min(0.3, irregularity * 0.1);
        baseDiffusion += room.averageAbsorption * 0.2;

        double aspectRatio = room.dimensions.x / Math.max(room.dimensions.y, room.dimensions.z);
        baseDiffusion *= (1.0 - Math.abs(1.0 - aspectRatio) * 0.2);

        return Math.max(0.3, Math.min(1.0, baseDiffusion));
    }

    public static double calculateHighFrequencyGain(RoomAnalysis room) {
        double baseGain = 0.94 - room.averageAbsorption * 0.6;

        if (room.absorptionByFrequency.length > 3) {
            double hfAbsorption = room.absorptionByFrequency[3];
            baseGain -= hfAbsorption * 0.3;
        }

        return Math.max(0.18, Math.min(1.0, baseGain));
    }

    public static double calculateDecayHFRatio(RoomAnalysis room) {
        double baseRatio = 1.08 - room.averageAbsorption * 0.35;

        if (room.absorptionByFrequency.length > 3) {
            double hfAbsorption = room.absorptionByFrequency[3];
            baseRatio -= hfAbsorption * 0.2;
        }

        return Math.max(0.3, Math.min(2.0, baseRatio));
    }
}