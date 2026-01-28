package me.zcraft.mods.spatialaudio.toolbox;

import com.google.common.util.concurrent.AtomicDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RoomAcousticsAnalyzer {
    private static final int MAX_SEARCH_VOLUME = 131072;
    private static final int MAX_ITERATIONS = 3000;
    private static final int TARGET_FRAME_TIME_MS = 8;
    private static final double MIN_ANALYSIS_QUALITY = 0.4;

    private static final Map<Long, CacheEntry> spatialCache = new ConcurrentHashMap<>(512, 0.75f, 4);
    private static final Map<Long, PredictiveEntry> predictiveCache = new ConcurrentHashMap<>(256);
    private static BloomFilter bloomFilter = new BloomFilter(8192);
    private static final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

    private static final ForkJoinPool computePool = new ForkJoinPool(
            Runtime.getRuntime().availableProcessors(),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true
    );

    private static final ExecutorService ioBoundExecutor = Executors.newFixedThreadPool(2);
    private static final ScheduledExecutorService maintenanceExecutor = Executors.newScheduledThreadPool(1);

    private static final AtomicInteger activeAnalyses = new AtomicInteger(0);
    private static final AtomicLong totalProcessingTime = new AtomicLong(0);
    private static final AtomicDouble performanceScaleFactor = new AtomicDouble(1.0);

    static {
        maintenanceExecutor.scheduleAtFixedRate(() -> {
            performCacheMaintenance();
            adjustPerformanceScaling();
        }, 5, 5, TimeUnit.SECONDS);
    }

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
        public final double analysisQuality;
        public final long computationTimeMs;
        public final boolean fromPrediction;

        public RoomAnalysis(AABB bounds, Vec3 dimensions, double volume, double surfaceArea,
                            double totalAbsorptionArea, double totalReflectionArea,
                            double averageAbsorption, double averageReflectivity,
                            double avgWallDistance, boolean isEnclosed, RoomType roomType,
                            double schroederFrequency, double meanFreePath, double criticalDistance,
                            double modalDensity, double[] absorptionByFrequency,
                            double analysisQuality, long computationTimeMs, boolean fromPrediction) {
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
            this.analysisQuality = analysisQuality;
            this.computationTimeMs = computationTimeMs;
            this.fromPrediction = fromPrediction;
        }
    }

    public enum RoomType {
        TINY(0, 100, 16),
        SMALL(100, 1000, 24),
        MEDIUM(1000, 8000, 32),
        LARGE(8000, 27000, 48),
        HUGE(27000, 64000, 64),
        CATHEDRAL(64000, Double.MAX_VALUE, 96);

        public final double minVolume;
        public final double maxVolume;
        public final int searchRadius;

        RoomType(double minVolume, double maxVolume, int searchRadius) {
            this.minVolume = minVolume;
            this.maxVolume = maxVolume;
            this.searchRadius = searchRadius;
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

    private static class CacheEntry {
        final RoomAnalysis analysis;
        final long timestamp;
        final long accessCount;
        final int hitScore;

        CacheEntry(RoomAnalysis analysis, long timestamp, long accessCount, int hitScore) {
            this.analysis = analysis;
            this.timestamp = timestamp;
            this.accessCount = accessCount;
            this.hitScore = hitScore;
        }
    }

    private static class PredictiveEntry {
        final double predictedAbsorption;
        final double predictedReflectivity;
        final RoomType predictedType;
        final long lastUpdated;
        final int confidence;

        PredictiveEntry(double absorption, double reflectivity, RoomType type, long timestamp, int confidence) {
            this.predictedAbsorption = absorption;
            this.predictedReflectivity = reflectivity;
            this.predictedType = type;
            this.lastUpdated = timestamp;
            this.confidence = confidence;
        }
    }

    private static class BloomFilter {
        private final long[] bits;
        private final int size;

        BloomFilter(int size) {
            this.size = size;
            this.bits = new long[(size + 63) / 64];
        }

        void add(long key) {
            int hash = hash(key);
            bits[hash >>> 6] |= 1L << (hash & 63);
        }

        boolean mightContain(long key) {
            int hash = hash(key);
            return (bits[hash >>> 6] & (1L << (hash & 63))) != 0;
        }

        private int hash(long key) {
            key = (~key) + (key << 21);
            key = key ^ (key >>> 24);
            key = (key + (key << 3)) + (key << 8);
            key = key ^ (key >>> 14);
            key = (key + (key << 2)) + (key << 4);
            key = key ^ (key >>> 28);
            key = key + (key << 31);
            return Math.abs((int) (key % size));
        }
    }

    public static RoomAnalysis analyzeRoom(Level level, Vec3 center) {
        long startTime = System.nanoTime();
        activeAnalyses.incrementAndGet();

        try {
            if (activeAnalyses.get() > 8) {
                return performQuickAnalysis(level, center);
            }

            BlockPos centerPos = BlockPos.containing(center);
            long spatialKey = computeSpatialKey(centerPos);
            long chunkKey = computeChunkKey(centerPos);

            cacheLock.readLock().lock();
            try {
                if (bloomFilter.mightContain(spatialKey)) {
                    CacheEntry cached = spatialCache.get(spatialKey);
                    if (cached != null && System.currentTimeMillis() - cached.timestamp < 10000) {
                        if (cached.hitScore > 2) {
                            return cached.analysis;
                        }
                    }
                }

                PredictiveEntry prediction = predictiveCache.get(chunkKey);
                if (prediction != null && prediction.confidence > 70) {
                    if (System.currentTimeMillis() - prediction.lastUpdated < 30000) {
                        return createPredictedAnalysis(center, prediction);
                    }
                }
            } finally {
                cacheLock.readLock().unlock();
            }

            CompletableFuture<RoomAnalysis> future = analyzeRoomAsyncInternal(level, center);
            RoomAnalysis result = future.get(100, TimeUnit.MILLISECONDS);

            cacheLock.writeLock().lock();
            try {
                bloomFilter.add(spatialKey);
                spatialCache.put(spatialKey, new CacheEntry(result, System.currentTimeMillis(), 1, 1));

                PredictiveEntry existing = predictiveCache.get(chunkKey);
                if (existing == null || existing.confidence < 85) {
                    predictiveCache.put(chunkKey, new PredictiveEntry(
                            result.averageAbsorption,
                            result.averageReflectivity,
                            result.roomType,
                            System.currentTimeMillis(),
                            Math.min(100, existing != null ? existing.confidence + 15 : 75)
                    ));
                }
            } finally {
                cacheLock.writeLock().unlock();
            }

            return result;
        } catch (Exception e) {
            return performFallbackAnalysis(level, center);
        } finally {
            long endTime = System.nanoTime();
            totalProcessingTime.addAndGet((endTime - startTime) / 1_000_000);
            activeAnalyses.decrementAndGet();
        }
    }

    private static CompletableFuture<RoomAnalysis> analyzeRoomAsyncInternal(Level level, Vec3 center) {
        return CompletableFuture.supplyAsync(() -> {
            BlockPos centerPos = BlockPos.containing(center);

            AdaptiveSampler sampler = new AdaptiveSampler(level, centerPos);
            sampler.performInitialSampling();

            if (sampler.shouldUseQuickMode()) {
                return performOptimizedAnalysis(level, centerPos);
            }

            RoomBoundsFinder boundsFinder = new RoomBoundsFinder(level, centerPos);
            AABB bounds = boundsFinder.findBounds();

            if (bounds == null || bounds.getSize() > 1_000_000) {
                return performOptimizedAnalysis(level, centerPos);
            }

            RoomDataCollector collector = new RoomDataCollector(level, bounds, sampler);
            RoomDataCollector.RoomData data = collector.collectData();

            double volume = calculateVolume(bounds);
            RoomType roomType = RoomType.fromVolume(volume);
            double quality = calculateDynamicQuality(bounds, volume, sampler.getSampleCount());

            if (quality < MIN_ANALYSIS_QUALITY) {
                return createLowQualityAnalysis(level, center, bounds, volume, roomType, quality);
            }

            double schroederFrequency = calculateSchroederFrequency(volume);
            double meanFreePath = 4.0 * volume / data.surfaceArea;
            double criticalDistance = calculateCriticalDistance(volume, data.averageAbsorption);
            double modalDensity = calculateModalDensity(volume, schroederFrequency);
            double[] absorptionByFrequency = calculateAbsorptionByFrequency(data.materialAbsorptionMap);

            Vec3 dimensions = new Vec3(
                    bounds.maxX - bounds.minX,
                    bounds.maxY - bounds.minY,
                    bounds.maxZ - bounds.minZ
            );

            return new RoomAnalysis(
                    bounds, dimensions, volume, data.surfaceArea,
                    data.totalAbsorptionArea, data.totalReflectionArea,
                    data.averageAbsorption, data.averageReflectivity,
                    data.avgWallDistance, data.isEnclosed, roomType,
                    schroederFrequency, meanFreePath, criticalDistance,
                    modalDensity, absorptionByFrequency, quality,
                    System.currentTimeMillis(), false
            );
        }, computePool);
    }

    private static class AdaptiveSampler {
        private final Level level;
        private final BlockPos center;
        private int sampleCount;

        private static final int SAMPLES_PER_AXIS = 8;

        AdaptiveSampler(Level level, BlockPos center) {
            this.level = level;
            this.center = center;
        }

        void performInitialSampling() {
            int radius = 16;
            int solidCount = 0;
            int totalSamples = 0;

            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    for (int dy = -8; dy <= 8; dy += 4) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (level.isLoaded(pos)) {
                            BlockState state = level.getBlockState(pos);
                            if (!state.isAir() && state.blocksMotion()) {
                                solidCount++;
                            }
                            totalSamples++;
                        }
                    }
                }
            }

            boolean highDensity = totalSamples > 0 && (solidCount * 100 / totalSamples) > 60;
            sampleCount = highDensity ? SAMPLES_PER_AXIS * 2 : SAMPLES_PER_AXIS;
        }

        boolean shouldUseQuickMode() {
            return activeAnalyses.get() > 4 || performanceScaleFactor.get() < 0.6;
        }

        int getSampleCount() {
            return sampleCount;
        }

        int getSampleInterval(double volume) {
            if (volume > 50000) return 4;
            if (volume > 10000) return 3;
            if (volume > 1000) return 2;
            return 1;
        }
    }

    private static class RoomBoundsFinder {
        private final Level level;
        private final BlockPos start;
        private final boolean[][][] visited;
        private final Queue<BlockPos> queue;
        private final int offsetX, offsetY, offsetZ;
        private final int sizeX, sizeY, sizeZ;

        private int minX, minY, minZ;
        private int maxX, maxY, maxZ;
        private int iterations;

        RoomBoundsFinder(Level level, BlockPos start) {
            this.level = level;
            this.start = start;

            int searchRadius = RoomType.CATHEDRAL.searchRadius;
            this.sizeX = searchRadius * 2 + 1;
            this.sizeY = Math.min(64, searchRadius * 2 + 1);
            this.sizeZ = searchRadius * 2 + 1;

            this.offsetX = start.getX() - searchRadius;
            this.offsetY = start.getY() - sizeY / 2;
            this.offsetZ = start.getZ() - searchRadius;

            this.visited = new boolean[sizeX][sizeY][sizeZ];
            this.queue = new ArrayDeque<>(1024);

            this.minX = start.getX();
            this.minY = start.getY();
            this.minZ = start.getZ();
            this.maxX = start.getX();
            this.maxY = start.getY();
            this.maxZ = start.getZ();
        }

        AABB findBounds() {
            if (!initializeSearch()) return null;

            final int[] dx = {-1, 1, 0, 0, 0, 0};
            final int[] dy = {0, 0, -1, 1, 0, 0};
            final int[] dz = {0, 0, 0, 0, -1, 1};

            while (!queue.isEmpty() && iterations++ < MAX_ITERATIONS) {
                if (iterations % 100 == 0 && shouldEarlyTerminate()) {
                    break;
                }

                BlockPos current = queue.poll();
                updateBounds(current);

                for (int i = 0; i < 6; i++) {
                    processNeighbor(current, dx[i], dy[i], dz[i]);
                }
            }

            if ((maxX - minX) * (maxY - minY) * (maxZ - minZ) > MAX_SEARCH_VOLUME) {
                return null;
            }

            return new AABB(minX - 1, minY - 1, minZ - 1,
                    maxX + 2, maxY + 2, maxZ + 2);
        }

        private boolean initializeSearch() {
            int relX = start.getX() - offsetX;
            int relY = start.getY() - offsetY;
            int relZ = start.getZ() - offsetZ;

            if (relX < 0 || relX >= sizeX || relY < 0 || relY >= sizeY || relZ < 0 || relZ >= sizeZ) {
                return false;
            }

            if (!isPassable(level, start)) {
                return false;
            }

            visited[relX][relY][relZ] = true;
            queue.add(start);
            return true;
        }

        private void updateBounds(BlockPos pos) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        private void processNeighbor(BlockPos current, int dx, int dy, int dz) {
            int nx = current.getX() + dx;
            int ny = current.getY() + dy;
            int nz = current.getZ() + dz;

            int relX = nx - offsetX;
            int relY = ny - offsetY;
            int relZ = nz - offsetZ;

            if (relX < 0 || relX >= sizeX || relY < 0 || relY >= sizeY || relZ < 0 || relZ >= sizeZ) {
                return;
            }

            if (!visited[relX][relY][relZ]) {
                BlockPos neighbor = new BlockPos(nx, ny, nz);
                if (isPassable(level, neighbor)) {
                    visited[relX][relY][relZ] = true;
                    queue.add(neighbor);
                }
            }
        }

        private boolean shouldEarlyTerminate() {
            int volume = (maxX - minX) * (maxY - minY) * (maxZ - minZ);
            return volume > 10000 && iterations > 500;
        }
    }

    private static class RoomDataCollector {
        private final Level level;
        private final AABB bounds;
        private final AdaptiveSampler sampler;

        static class RoomData {
            final double surfaceArea;
            final double totalAbsorptionArea;
            final double totalReflectionArea;
            final double averageAbsorption;
            final double averageReflectivity;
            final double avgWallDistance;
            final boolean isEnclosed;
            final Map<String, Double> materialAbsorptionMap;

            RoomData(double surfaceArea, double totalAbsorptionArea,
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

        RoomDataCollector(Level level, AABB bounds, AdaptiveSampler sampler) {
            this.level = level;
            this.bounds = bounds;
            this.sampler = sampler;
        }

        RoomData collectData() {
            int minX = (int) Math.floor(bounds.minX);
            int minY = (int) Math.floor(bounds.minY);
            int minZ = (int) Math.floor(bounds.minZ);
            int maxX = (int) Math.ceil(bounds.maxX);
            int maxY = (int) Math.ceil(bounds.maxY);
            int maxZ = (int) Math.ceil(bounds.maxZ);

            int sampleInterval = sampler.getSampleInterval(
                    (maxX - minX) * (maxY - minY) * (maxZ - minZ)
            );

            List<CompletableFuture<PartialResult>> futures = new ArrayList<>();
            int chunkSize = 16;

            for (int cx = minX; cx <= maxX; cx += chunkSize) {
                for (int cz = minZ; cz <= maxZ; cz += chunkSize) {
                    final int startX = cx;
                    final int startZ = cz;
                    final int endX = Math.min(cx + chunkSize - 1, maxX);
                    final int endZ = Math.min(cz + chunkSize - 1, maxZ);

                    futures.add(CompletableFuture.supplyAsync(() ->
                                    processChunk(startX, endX, minY, maxY, startZ, endZ, sampleInterval),
                            computePool
                    ));
                }
            }

            double totalSurfaceArea = 0;
            double totalAbsorptionArea = 0;
            double totalReflectionArea = 0;
            int totalSurfaceCount = 0;
            Map<String, Double> materialMap = new ConcurrentHashMap<>();

            for (CompletableFuture<PartialResult> future : futures) {
                try {
                    PartialResult result = future.get();
                    totalSurfaceArea += result.surfaceArea;
                    totalAbsorptionArea += result.absorptionArea;
                    totalReflectionArea += result.reflectionArea;
                    totalSurfaceCount += result.surfaceCount;

                    result.materialContributions.forEach((material, absorption) ->
                            materialMap.merge(material, absorption, Double::sum)
                    );
                } catch (Exception ignored) {
                }
            }

            boolean isEnclosed = checkEnclosure();
            double avgAbsorption = totalSurfaceCount > 0 ? totalAbsorptionArea / totalSurfaceCount : 0.1;
            double avgReflectivity = totalSurfaceCount > 0 ? totalReflectionArea / totalSurfaceCount : 0.9;
            double avgWallDistance = calculateAverageWallDistance(bounds);

            int finalTotalSurfaceCount = totalSurfaceCount;
            materialMap.replaceAll((k, v) -> v / finalTotalSurfaceCount);

            return new RoomData(
                    totalSurfaceArea, totalAbsorptionArea, totalReflectionArea,
                    avgAbsorption, avgReflectivity, avgWallDistance,
                    isEnclosed, materialMap
            );
        }

        private PartialResult processChunk(int startX, int endX, int minY, int maxY,
                                           int startZ, int endZ, int sampleInterval) {
            double surfaceArea = 0;
            double absorptionArea = 0;
            double reflectionArea = 0;
            int surfaceCount = 0;
            Map<String, Double> materialMap = new HashMap<>();

            for (int x = startX; x <= endX; x += sampleInterval) {
                for (int z = startZ; z <= endZ; z += sampleInterval) {
                    for (int y = minY; y <= maxY; y += sampleInterval) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.isLoaded(pos)) continue;

                        level.getBlockState(pos);
                        if (isPassable(level, pos)) {
                            surfaceArea += processAirBlock(x, y, z, materialMap);
                            surfaceCount++;
                        }
                    }
                }
            }

            return new PartialResult(surfaceArea, absorptionArea, reflectionArea, surfaceCount, materialMap);
        }

        private double processAirBlock(int x, int y, int z, Map<String, Double> materialMap) {
            double area = 0;

            area += checkWall(x + 1, y, z, materialMap);
            area += checkWall(x - 1, y, z, materialMap);
            area += checkWall(x, y + 1, z, materialMap);
            area += checkWall(x, y - 1, z, materialMap);
            area += checkWall(x, y, z + 1, materialMap);
            area += checkWall(x, y, z - 1, materialMap);

            return area;
        }

        private double checkWall(int wx, int wy, int wz, Map<String, Double> materialMap) {
            BlockPos wallPos = new BlockPos(wx, wy, wz);
            if (!level.isLoaded(wallPos)) return 0;

            BlockState state = level.getBlockState(wallPos);
            if (!isPassable(level, wallPos)) {
                double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(state);
                BlockPhysicsUtil.getReflectivityCoefficient(state);

                String material = BlockPhysicsUtil.getMaterialType(state);
                materialMap.merge(material, absorption, Double::sum);

                return 1.0;
            }
            return 0;
        }

        private boolean checkEnclosure() {
            return true;
        }

        private static class PartialResult {
            final double surfaceArea;
            final double absorptionArea;
            final double reflectionArea;
            final int surfaceCount;
            final Map<String, Double> materialContributions;

            PartialResult(double surfaceArea, double absorptionArea, double reflectionArea,
                          int surfaceCount, Map<String, Double> materialContributions) {
                this.surfaceArea = surfaceArea;
                this.absorptionArea = absorptionArea;
                this.reflectionArea = reflectionArea;
                this.surfaceCount = surfaceCount;
                this.materialContributions = materialContributions;
            }
        }
    }

    private static RoomAnalysis performQuickAnalysis(Level level, Vec3 center) {
        BlockPos centerPos = BlockPos.containing(center);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = centerPos.offset(dx * 2, dy * 2, dz * 2);
                    if (level.isLoaded(pos)) {
                        BlockState state = level.getBlockState(pos);
                        if (!state.isAir() && state.blocksMotion()) {
                            BlockPhysicsUtil.getAbsorptionCoefficient(state);
                        }
                    }
                }
            }
        }

        AABB bounds = createQuickBounds(centerPos, 8);
        double volume = calculateVolume(bounds);
        RoomType roomType = RoomType.fromVolume(volume);

        return createLowQualityAnalysis(level, center, bounds, volume, roomType, 0.5);
    }

    private static RoomAnalysis performOptimizedAnalysis(Level level, BlockPos center) {
        int radius = Math.min(24, RoomType.MEDIUM.searchRadius);
        AABB bounds = createQuickBounds(center, radius);
        double volume = calculateVolume(bounds);
        RoomType roomType = RoomType.fromVolume(volume);
        double quality = 0.6 * performanceScaleFactor.get();

        return createLowQualityAnalysis(level, new Vec3(center.getX(), center.getY(), center.getZ()),
                bounds, volume, roomType, quality);
    }

    private static RoomAnalysis performFallbackAnalysis(Level level, Vec3 center) {
        BlockPos centerPos = BlockPos.containing(center);
        AABB bounds = createQuickBounds(centerPos, 16);
        double volume = calculateVolume(bounds);
        RoomType roomType = RoomType.fromVolume(volume);

        return createLowQualityAnalysis(level, center, bounds, volume, roomType, 0.3);
    }

    private static RoomAnalysis createPredictedAnalysis(Vec3 center, PredictiveEntry prediction) {
        AABB bounds = createQuickBounds(BlockPos.containing(center), prediction.predictedType.searchRadius);
        double volume = calculateVolume(bounds);
        double quality = prediction.confidence / 100.0 * 0.8;

        double[] absorptionByFrequency = new double[8];
        Arrays.fill(absorptionByFrequency, prediction.predictedAbsorption);

        Vec3 dimensions = new Vec3(
                bounds.maxX - bounds.minX,
                bounds.maxY - bounds.minY,
                bounds.maxZ - bounds.minZ
        );

        double schroederFrequency = calculateSchroederFrequency(volume);
        double surfaceArea = estimateSurfaceArea(volume);
        double meanFreePath = 4.0 * volume / surfaceArea;
        double criticalDistance = calculateCriticalDistance(volume, prediction.predictedAbsorption);
        double modalDensity = calculateModalDensity(volume, schroederFrequency);
        double avgWallDistance = calculateAverageWallDistance(bounds);

        return new RoomAnalysis(
                bounds, dimensions, volume, surfaceArea,
                surfaceArea * prediction.predictedAbsorption,
                surfaceArea * prediction.predictedReflectivity,
                prediction.predictedAbsorption, prediction.predictedReflectivity,
                avgWallDistance, true, prediction.predictedType,
                schroederFrequency, meanFreePath, criticalDistance,
                modalDensity, absorptionByFrequency, quality,
                System.currentTimeMillis(), true
        );
    }

    private static RoomAnalysis createLowQualityAnalysis(Level level, Vec3 center, AABB bounds,
                                                         double volume, RoomType roomType, double quality) {
        double estimatedAbsorption = estimateMaterialProperty(level, BlockPos.containing(center));
        double estimatedReflectivity = 1.0 - estimatedAbsorption * 0.8;

        Vec3 dimensions = new Vec3(
                bounds.maxX - bounds.minX,
                bounds.maxY - bounds.minY,
                bounds.maxZ - bounds.minZ
        );

        double surfaceArea = estimateSurfaceArea(volume);
        double schroederFrequency = calculateSchroederFrequency(volume);
        double meanFreePath = 4.0 * volume / surfaceArea;
        double criticalDistance = calculateCriticalDistance(volume, estimatedAbsorption);
        double modalDensity = calculateModalDensity(volume, schroederFrequency);
        double[] absorptionByFrequency = createEstimatedAbsorptionArray(estimatedAbsorption);
        double avgWallDistance = calculateAverageWallDistance(bounds);

        return new RoomAnalysis(
                bounds, dimensions, volume, surfaceArea,
                surfaceArea * estimatedAbsorption,
                surfaceArea * estimatedReflectivity,
                estimatedAbsorption, estimatedReflectivity,
                avgWallDistance, true, roomType,
                schroederFrequency, meanFreePath, criticalDistance,
                modalDensity, absorptionByFrequency, quality,
                System.currentTimeMillis(), false
        );
    }

    private static long computeSpatialKey(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());

        return ((long) chunkX << 48) | ((long) chunkZ << 32) |
                ((long) sectionY << 16) | (localX << 8) | localZ;
    }

    private static long computeChunkKey(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static void performCacheMaintenance() {
        cacheLock.writeLock().lock();
        try {
            long currentTime = System.currentTimeMillis();

            spatialCache.entrySet().removeIf(entry -> currentTime - entry.getValue().timestamp > 30000);

            predictiveCache.entrySet().removeIf(entry -> currentTime - entry.getValue().lastUpdated > 120000);

            if (spatialCache.size() > 1000) {
                spatialCache.clear();
                bloomFilter = new BloomFilter(8192);
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private static void adjustPerformanceScaling() {
        long avgTime = totalProcessingTime.get() / Math.max(1, activeAnalyses.get());
        if (avgTime > TARGET_FRAME_TIME_MS * 2) {
            performanceScaleFactor.set(Math.max(0.3, performanceScaleFactor.get() * 0.8));
        } else if (avgTime < TARGET_FRAME_TIME_MS / 2) {
            performanceScaleFactor.set(Math.min(1.0, performanceScaleFactor.get() * 1.2));
        }
        totalProcessingTime.set(0);
    }

    private static boolean isPassable(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() ||
                state.getBlock() == Blocks.MOVING_PISTON ||
                !state.blocksMotion();
    }

    private static AABB createQuickBounds(BlockPos center, int radius) {
        int verticalRadius = Math.min(radius, 16);
        return new AABB(
                center.getX() - radius, center.getY() - verticalRadius, center.getZ() - radius,
                center.getX() + radius, center.getY() + verticalRadius, center.getZ() + radius
        );
    }

    private static double calculateDynamicQuality(AABB bounds, double volume, int sampleCount) {
        double aspectRatio = Math.max(
                bounds.maxX - bounds.minX,
                Math.max(bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ)
        ) / Math.min(
                bounds.maxX - bounds.minX,
                Math.min(bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ)
        );

        double quality = 1.0;
        if (volume > 50000) quality *= 0.6;
        else if (volume > 10000) quality *= 0.8;

        if (aspectRatio > 8.0) quality *= 0.7;
        quality *= Math.min(1.0, sampleCount / 8.0);
        quality *= performanceScaleFactor.get();

        return Math.max(MIN_ANALYSIS_QUALITY, quality);
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

    private static double estimateSurfaceArea(double volume) {
        return 6.0 * Math.pow(volume, 2.0/3.0);
    }

    private static double estimateMaterialProperty(Level level, BlockPos center) {
        int samples = 0;
        double total = 0;

        for (int dx = -12; dx <= 12; dx += 4) {
            for (int dz = -12; dz <= 12; dz += 4) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (level.isLoaded(pos)) {
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        total += BlockPhysicsUtil.getAbsorptionCoefficient(state);
                        samples++;
                    }
                }
            }
        }

        return samples > 0 ? total / samples : 0.15;
    }

    private static double[] createEstimatedAbsorptionArray(double baseAbsorption) {
        double[] absorption = new double[8];
        for (int i = 0; i < absorption.length; i++) {
            absorption[i] = baseAbsorption * (0.9 + 0.1 * Math.random());
        }
        return absorption;
    }

    private static double calculateSchroederFrequency(double volume) {
        return 2000.0 * Math.sqrt(0.05 / Math.max(volume, 0.1));
    }

    private static double calculateCriticalDistance(double volume, double averageAbsorption) {
        double roomConstant = volume * averageAbsorption / Math.max(1.0 - averageAbsorption, 0.01);
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

                totalAbsorption += baseAbsorption * Math.max(0.0, Math.min(1.0, frequencyFactor));
                count++;
            }

            absorptionByFrequency[i] = count > 0 ?
                    Math.max(0.05, Math.min(1.0, totalAbsorption / count)) : 0.1;
        }

        return absorptionByFrequency;
    }

    public static void cleanup() {
        computePool.shutdown();
        ioBoundExecutor.shutdown();
        maintenanceExecutor.shutdown();

        try {
            if (!computePool.awaitTermination(1, TimeUnit.SECONDS)) {
                computePool.shutdownNow();
            }
            if (!ioBoundExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                ioBoundExecutor.shutdownNow();
            }
            if (!maintenanceExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                maintenanceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        spatialCache.clear();
        predictiveCache.clear();
        bloomFilter = new BloomFilter(8192);
        activeAnalyses.set(0);
        totalProcessingTime.set(0);
        performanceScaleFactor.set(1.0);
    }
}