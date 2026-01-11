package dev.thedocruby.resounding.toolbox;

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
        public final double volume;
        public final double surfaceArea;
        public final double averageAbsorption;
        public final double avgWallDistance;
        public final boolean isEnclosed;
        public final RoomType roomType;

        public RoomAnalysis(AABB bounds, double volume, double surfaceArea,
                            double averageAbsorption, double avgWallDistance,
                            boolean isEnclosed, RoomType roomType) {
            this.bounds = bounds;
            this.volume = volume;
            this.surfaceArea = surfaceArea;
            this.averageAbsorption = averageAbsorption;
            this.avgWallDistance = avgWallDistance;
            this.isEnclosed = isEnclosed;
            this.roomType = roomType;
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
        double volume = calculateVolume(bounds);

        if (volume > MAX_ROOM_VOLUME) {
            bounds = createBoundedAABB(centerPos, MAX_SEARCH_DISTANCE);
            volume = calculateVolume(bounds);
        }

        RoomAnalysisData data = collectRoomData(level, bounds);
        RoomType roomType = RoomType.fromVolume(volume);

        RoomAnalysis analysis = new RoomAnalysis(
                bounds, volume, data.surfaceArea,
                data.averageAbsorption, data.avgWallDistance,
                data.isEnclosed, roomType
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
        final double averageAbsorption;
        final double avgWallDistance;
        final boolean isEnclosed;

        RoomAnalysisData(double surfaceArea, double averageAbsorption,
                         double avgWallDistance, boolean isEnclosed) {
            this.surfaceArea = surfaceArea;
            this.averageAbsorption = averageAbsorption;
            this.avgWallDistance = avgWallDistance;
            this.isEnclosed = isEnclosed;
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

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos) && !isPassable(level, pos)) {
                        solidBlocks[x - minX + 1][y - minY + 1][z - minZ + 1] = true;
                    }
                }
            }
        }

        double surfaceArea = 0.0;
        double totalAbsorption = 0.0;
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
                            totalAbsorption += getAbsorption(level, new BlockPos(x + 1, y, z));
                        }
                        if (solidBlocks[bx - 1][by][bz]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            totalAbsorption += getAbsorption(level, new BlockPos(x - 1, y, z));
                        }
                        if (solidBlocks[bx][by + 1][bz]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            totalAbsorption += getAbsorption(level, new BlockPos(x, y + 1, z));
                        }
                        if (solidBlocks[bx][by - 1][bz]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            totalAbsorption += getAbsorption(level, new BlockPos(x, y - 1, z));
                        }
                        if (solidBlocks[bx][by][bz + 1]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            totalAbsorption += getAbsorption(level, new BlockPos(x, y, z + 1));
                        }
                        if (solidBlocks[bx][by][bz - 1]) {
                            surfaceArea += 1.0;
                            surfaceCount++;
                            totalAbsorption += getAbsorption(level, new BlockPos(x, y, z - 1));
                        }
                    }
                }
            }
        }

        boolean isEnclosed = isEnclosedSimplified(level, bounds, solidBlocks, minX, minY, minZ);
        double avgAbsorption = surfaceCount > 0 ? totalAbsorption / surfaceCount : 0.1;
        double avgWallDistance = calculateAverageWallDistance(bounds);

        return new RoomAnalysisData(surfaceArea, avgAbsorption, avgWallDistance, isEnclosed);
    }

    private static double getAbsorption(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return 0.1;
        BlockState state = level.getBlockState(pos);
        return BlockPhysicsUtil.getAbsorptionCoefficient(state);
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

    public static double calculateReverberationTime(RoomAnalysis room) {
        if (!room.isEnclosed) return 0.1;

        double V = room.volume;
        double A = room.surfaceArea;
        double α = room.averageAbsorption;

        if (A * α == 0) return 0.1;

        double rt60 = 0.161 * V / (A * α);

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

        return Math.max(0.1, Math.min(10.0, rt60));
    }

    public static double calculateEarlyReflectionsDelay(RoomAnalysis room) {
        double distance = room.avgWallDistance;
        return distance / 343.0;
    }

    public static double calculateLateReverbDelay(RoomAnalysis room) {
        double volumeFactor = Math.sqrt(room.volume) / 50.0;
        return Math.min(0.1, 0.01 + volumeFactor * 0.02);
    }

    public static double calculateDensity(RoomAnalysis room) {
        double baseDensity = 0.5 + 0.5 * Math.exp(-room.volume / 10000.0);
        baseDensity *= (1.0 - room.averageAbsorption * 0.5);
        return Math.max(0.1, Math.min(1.0, baseDensity));
    }

    public static double calculateDiffusion(RoomAnalysis room) {
        double baseDiffusion = 0.7;
        double irregularity = room.surfaceArea / Math.pow(room.volume, 2.0/3.0);
        baseDiffusion += Math.min(0.3, irregularity * 0.1);
        baseDiffusion += room.averageAbsorption * 0.2;
        return Math.max(0.3, Math.min(1.0, baseDiffusion));
    }
}