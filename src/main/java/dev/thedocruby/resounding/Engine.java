package dev.thedocruby.resounding;

import dev.thedocruby.resounding.openal.ALUtils;
import dev.thedocruby.resounding.openal.Context;
import dev.thedocruby.resounding.raycast.Patch;
import dev.thedocruby.resounding.raycast.Renderer;
import dev.thedocruby.resounding.raycast.SPHitResult;
import dev.thedocruby.resounding.toolbox.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dev.thedocruby.resounding.config.PrecomputedConfig.*;
import static java.util.Map.entry;

public class Engine {
	private Engine() { }

	public static Context root;

	public static EnvType env = null;
	public static Minecraft mc;
	public static boolean isOff = true;
	public static final Logger LOGGER = LogManager.getLogger("SpatialAudio");

	public static final Map<SoundType, SoundType> redirectMap =
			Map.ofEntries(
					entry(SoundType.MOSS_CARPET, SoundType.MOSS),
					entry(SoundType.AMETHYST_CLUSTER, SoundType.AMETHYST),
					entry(SoundType.SMALL_AMETHYST_BUD, SoundType.AMETHYST),
					entry(SoundType.MEDIUM_AMETHYST_BUD, SoundType.AMETHYST),
					entry(SoundType.LARGE_AMETHYST_BUD, SoundType.AMETHYST),
					entry(SoundType.POINTED_DRIPSTONE, SoundType.DRIPSTONE_BLOCK),
					entry(SoundType.FLOWERING_AZALEA, SoundType.AZALEA),
					entry(SoundType.DEEPSLATE_BRICKS, SoundType.POLISHED_DEEPSLATE),
					entry(SoundType.COPPER, SoundType.METAL),
					entry(SoundType.ANVIL, SoundType.METAL),
					entry(SoundType.NETHER_SPROUTS, SoundType.ROOTS),
					entry(SoundType.LILY_PAD, SoundType.WET_GRASS),
					entry(SoundType.NETHER_GOLD_ORE, SoundType.NETHERRACK),
					entry(SoundType.NETHER_ORE, SoundType.NETHERRACK),
					entry(SoundType.CALCITE, SoundType.STONE),
					entry(SoundType.GILDED_BLACKSTONE, SoundType.STONE),
					entry(SoundType.SMALL_DRIPLEAF, SoundType.CAVE_VINES),
					entry(SoundType.BIG_DRIPLEAF, SoundType.CAVE_VINES),
					entry(SoundType.SPORE_BLOSSOM, SoundType.CAVE_VINES),
					entry(SoundType.GLOW_LICHEN, SoundType.VINE),
					entry(SoundType.HANGING_ROOTS, SoundType.VINE),
					entry(SoundType.ROOTED_DIRT, SoundType.GRAVEL),
					entry(SoundType.WART_BLOCK, SoundType.NETHER_WART),
					entry(SoundType.CROP, SoundType.GRASS),
					entry(SoundType.BAMBOO_SAPLING, SoundType.GRASS),
					entry(SoundType.SWEET_BERRY_BUSH, SoundType.GRASS),
					entry(SoundType.SCAFFOLDING, SoundType.BAMBOO),
					entry(SoundType.LODESTONE, SoundType.NETHERITE_BLOCK),
					entry(SoundType.LADDER, SoundType.WOOD)
			);
	public static final Map<SoundType, String> groupToName =
			Map.ofEntries(
					entry(SoundType.CORAL_BLOCK        , "Coral"            ),
					entry(SoundType.GRAVEL            , "Gravel, Dirt"        ),
					entry(SoundType.AMETHYST        , "Amethyst"            ),
					entry(SoundType.SAND            , "Sand"                ),
					entry(SoundType.CANDLE            , "Candle Wax"        ),
					entry(SoundType.WEEPING_VINES    , "Weeping Vines"    ),
					entry(SoundType.SOUL_SAND        , "Soul Sand"        ),
					entry(SoundType.SOUL_SOIL        , "Soul Soil"        ),
					entry(SoundType.BASALT            , "Basalt"            ),
					entry(SoundType.NETHERRACK    , "Netherrack"        ),
					entry(SoundType.NETHER_BRICKS    , "Nether Brick"        ),
					entry(SoundType.HONEY_BLOCK        , "Honey"            ),
					entry(SoundType.BONE_BLOCK        , "Bone"                ),
					entry(SoundType.NETHER_WART    , "Nether Wart"        ),
					entry(SoundType.GRASS            , "Grass, Foliage"    ),
					entry(SoundType.METAL            , "Metal"            ),
					entry(SoundType.WET_GRASS        , "Aquatic Foliage"    ),
					entry(SoundType.GLASS            , "Glass, Ice"        ),
					entry(SoundType.ROOTS            , "Nether Foliage"    ),
					entry(SoundType.SHROOMLIGHT    , "Shroomlight"        ),
					entry(SoundType.CHAIN            , "Chain"            ),
					entry(SoundType.DEEPSLATE        , "Deepslate"        ),
					entry(SoundType.WOOD            , "Wood"                ),
					entry(SoundType.DEEPSLATE_TILES,"Deepslate Tiles"    ),
					entry(SoundType.STONE            , "Stone, Blackstone"),
					entry(SoundType.SLIME_BLOCK        , "Slime"            ),
					entry(SoundType.POLISHED_DEEPSLATE,"Polished Deepslate"),
					entry(SoundType.SNOW            , "Snow"                ),
					entry(SoundType.AZALEA_LEAVES    , "Azalea Leaves"    ),
					entry(SoundType.BAMBOO            , "Bamboo"            ),
					entry(SoundType.STEM            , "Mushroom Stems"    ),
					entry(SoundType.WOOL            , "Wool"                ),
					entry(SoundType.VINE            , "Dry Foliage"        ),
					entry(SoundType.AZALEA            , "Azalea Bush"        ),
					entry(SoundType.CAVE_VINES    , "Lush Cave Foliage"),
					entry(SoundType.NETHERITE_BLOCK    , "Netherite"        ),
					entry(SoundType.ANCIENT_DEBRIS, "Ancient Debris"    ),
					entry(SoundType.POWDER_SNOW    , "Powder Snow"        ),
					entry(SoundType.TUFF            , "Tuff"                ),
					entry(SoundType.MOSS            , "Moss"                ),
					entry(SoundType.NYLIUM        , "Nylium"            ),
					entry(SoundType.FUNGUS        , "Nether Mushroom"    ),
					entry(SoundType.LANTERN        , "Lanterns"            ),
					entry(SoundType.DRIPSTONE_BLOCK,"Dripstone"        ),
					entry(SoundType.SCULK_SENSOR    , "Sculk Sensor"        )
			);
	public static final Map<String, SoundType> nameToGroup = groupToName.keySet().stream().collect(Collectors.toMap(groupToName::get, k -> k));

	public static final Pattern spamPattern   = Pattern.compile(".*(rain|lava).*");
	public static final Pattern stepPattern   = Pattern.compile(".*(step|pf_).*");
	public static final Pattern gentlePattern = Pattern.compile(".*(ambient|splash|swim|note|compounded).*");
	public static final Pattern ignorePattern = Pattern.compile(".*(music|voice).*");
	public static final Pattern uiPattern     = Pattern.compile("ui.+");

	private static Set<Vec3> rays;
	private static int viewDist;
	private static SoundSource lastSoundCategory;
	private static String lastSoundName;
	private static Vec3 playerPos;
	private static Vec3 listenerPos;
	private static LevelChunk soundChunk;
	public static Vec3 soundPos;
	private static BlockPos soundBlockPos;
	private static boolean auxOnly;
	private static boolean isSpam;
	private static long timeT;
	private static int sourceID;

	private static final Map<Long, Double> BLOCK_REFLECTIVITY_CACHE = new ConcurrentHashMap<>(4096);
	private static final ThreadLocal<double[]> MATH_ARRAYS = ThreadLocal.withInitial(() -> new double[16]);
	private static final ThreadLocal<Vec3[]> VEC3_POOL = ThreadLocal.withInitial(() -> new Vec3[8]);

	private static final Set<Integer> activeSourceIDs = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private static int totalSoundRequests = 0;
	private static int skippedSounds = 0;

	public static void setRoot(Context context) {root=context;}

	public static <T> double logBase(T x, T b) {
		return Math.log((Double) x) / Math.log((Double) b);
	}

	@Contract("_, _ -> new")
	private static @NotNull Vec3 pseudoReflect(Vec3 ray, @NotNull Vec3i plane) {
		final Vec3 planeD = new Vec3(
				plane.getX(),
				plane.getY(),
				plane.getZ()
		);
		return ray.subtract(
				planeD.scale
						( 2
								* ray.dot(planeD)
						)
		);
	}

	@Environment(EnvType.CLIENT)
	public static void updateRays() {
		final double gRatio = 1.618033988;
		final double epsilon;

		{
			final int r = pC.nRays;
			if      (r >= 600000) { epsilon = 214d;  }
			else if (r >= 400000) { epsilon = 75d;   }
			else if (r >= 11000)  { epsilon = 27d;   }
			else if (r >= 890)    { epsilon = 10d;   }
			else if (r >= 177)    { epsilon = 3.33d; }
			else if (r >= 24)     { epsilon = 1.33d; }
			else                  { epsilon = 0.33d; }

			rays = IntStream.range(0, r).parallel().unordered().mapToObj(i -> {
				final double theta = 2d * Math.PI * i / gRatio;
				final double phi = Math.acos(1d - 2d*(i + epsilon) / (r - 1d + 2d*epsilon));

				{
					final double sP = Math.sin(phi);

					return new Vec3(
							Math.cos(theta) * sP,
							Math.sin(theta) * sP,
							Math.cos(phi)
					);
				}
			}).collect(Collectors.toSet());
		}
	}

	@Environment(EnvType.CLIENT)
	public static void updateYeetedSoundInfo(SoundInstance sound) {
		lastSoundCategory = sound.getSource();
		lastSoundName = sound.getLocation().getPath();
		if (mc.player != null) {
			listenerPos = mc.player.getEyePosition();
		}
	}

	@Contract("_ -> new")
	@Environment(EnvType.CLIENT)
	public static void svc_playSound(Context context, double posX, double posY, double posZ, int sourceIDIn, boolean auxOnlyIn) {
		lastSoundName = "voice-chat";
		playSound(context, posX, posY, posZ, sourceIDIn, auxOnlyIn);
	}

	@Environment(EnvType.CLIENT)
	public static void playSound(Context context, double posX, double posY, double posZ, int sourceIDIn, boolean auxOnlyIn) {
		if (Engine.isOff) return;

		if (!ALUtils.isValidSource(sourceIDIn)) {
			if (pC.dLog) LOGGER.debug("Invalid source ID {} for sound {}, skipping", sourceIDIn, lastSoundName);
			return;
		}

		totalSoundRequests++;

		if (activeSourceIDs.size() >= pC.maxSoundSources) {
			skippedSounds++;
			if (pC.dLog && skippedSounds % 100 == 0) {
				LOGGER.warn("Sound source limit reached: {}/{} active sources. Skipped {} sounds total.",
						activeSourceIDs.size(), pC.maxSoundSources, skippedSounds);
			}
			return;
		}

		if (!activeSourceIDs.add(sourceIDIn)) {
			if (pC.dLog) LOGGER.debug("Sound source {} is already active, skipping duplicate", sourceIDIn);
			return;
		}

		long startTime = 0;
		if (pC.pLog) startTime = System.nanoTime();
		long endTime;
		auxOnly = auxOnlyIn;
		sourceID = sourceIDIn;

		if (mc.player == null || mc.level == null || uiPattern.matcher(lastSoundName).matches() || ignorePattern.matcher(lastSoundName).matches()) {
			activeSourceIDs.remove(sourceIDIn);
			if (pC.dLog) LOGGER.info("Skipped playing sound \"{}\": Not a world sound.", lastSoundName);
			return;
		}

		if (lastSoundCategory == SoundSource.RECORDS){posX+=0.5;posY+=0.5;posZ+=0.5;}
		if (stepPattern.matcher(lastSoundName).matches()) {posY+=0.2;}
		{
			Vec3 playerPosOld = mc.player.position();
			playerPos = new Vec3(playerPosOld.x, playerPosOld.y + mc.player.getEyeHeight(), playerPosOld.z);
		}
		listenerPos = mc.player.getEyePosition();
		final int bottom = mc.level.getMinBuildHeight();
		final int top = mc.level.getMaxBuildHeight();
		isSpam = spamPattern.matcher(lastSoundName).matches();
		soundPos = new Vec3(posX, posY, posZ);
		viewDist = mc.options.getEffectiveRenderDistance();
		double maxDist = Math.min(
				Math.min(
						Math.min(
								mc.options.simulationDistance().get(), viewDist),
						pC.soundSimulationDistance) * 16,
				pC.maxTraceDist / 2);
		soundChunk = mc.level.getChunk(((int)Math.floor(soundPos.x))>>4,((int)Math.floor(soundPos.z))>>4);
		soundBlockPos = new BlockPos((int)Math.floor(soundPos.x), (int)Math.floor(soundPos.y), (int)Math.floor(soundPos.z));
		timeT = mc.level.getGameTime();
		boolean isGentle = gentlePattern.matcher(lastSoundName).matches();

		String message = "";
		if (
				posY          <= bottom || posY          >= top ||
						playerPos.y   <= bottom || playerPos.y   >= top ||
						listenerPos.y <= bottom || listenerPos.y >= top
		) {
			message = String.format("Skipped playing sound \"{}\": Cannot trace sounds outside the block grid.", lastSoundName);
		} else
		if (Math.max(playerPos.distanceTo(soundPos), listenerPos.distanceTo(soundPos)) > maxDist) {
			message = String.format("Skipped environment sampling for sound \"{}\": Sound is outside the maximum traceable distance with the current settings.", lastSoundName);
		} else
		if (pC.recordsDisable && lastSoundCategory == SoundSource.RECORDS){
			message = String.format("Skipped environment sampling for sound \"{}\": Disabled sound.", lastSoundName);
		}
		if (!message.isEmpty()) {
			if (pC.dLog) LOGGER.info(message);
			try {
				setEnv(context, processEnv(new EnvData(Collections.emptySet(), Collections.emptySet())), isGentle);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} finally {
				activeSourceIDs.remove(sourceIDIn);
			}
			return;
		}

		if (pC.dLog) LOGGER.info(message);

		try {
			setEnv(context, processEnv(evalEnv()), isGentle);
		} catch (Exception e) {
			LOGGER.error("Error processing sound {}: {}", lastSoundName, e.getMessage());
		} finally {
			activeSourceIDs.remove(sourceIDIn);
		}

		if (pC.pLog) {
			endTime = System.nanoTime();
			LOGGER.info("Total calculation time for sound {}: {} milliseconds", lastSoundName, (double)(endTime - startTime)/(double)1000000);
		}
	}

	@Environment(EnvType.CLIENT)
	public static void cleanupSource(int sourceID) {
		activeSourceIDs.remove(sourceID);
		if (root != null && root.dopplerEffect != null) {
			root.dopplerEffect.cleanup();
		}
	}

	@Environment(EnvType.CLIENT)
	public static int getActiveSourceCount() {
		return activeSourceIDs.size();
	}

	@Environment(EnvType.CLIENT)
	public static int getTotalSoundRequests() {
		return totalSoundRequests;
	}

	@Environment(EnvType.CLIENT)
	public static int getSkippedSounds() {
		return skippedSounds;
	}

	@Environment(EnvType.CLIENT)
	private static double getBlockReflectivity(final @NotNull BlockState blockState) {
		long key = BlockPos.asLong(blockState.hashCode(), 0, 0);
		Double cached = BLOCK_REFLECTIVITY_CACHE.get(key);
		if (cached != null) return cached;

		double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(blockState);
		double baseValue = pC.reflMap.getOrDefault(
				blockState.getBlock().getDescriptionId(),
				pC.reflMap.getOrDefault(
						groupToName.getOrDefault(
								redirectMap.getOrDefault(
										blockState.getSoundType(),
										blockState.getSoundType()),
								"DEFAULT"),
						pC.defaultRefl)
		);

		double value = baseValue * (1.0 - absorption);

		BLOCK_REFLECTIVITY_CACHE.put(key, value);
		return value;
	}

	@Environment(EnvType.CLIENT)
	private static @NotNull ReflectedRayData throwReflRay(@NotNull Vec3 dir) {
		SPHitResult rayHit = Patch.fixedRaycast(
				soundPos,
				new Vec3(soundPos.x + dir.x * pC.maxTraceDist, soundPos.y + dir.y * pC.maxTraceDist, soundPos.z + dir.z * pC.maxTraceDist),
				mc.level,
				soundBlockPos,
				soundChunk
		);

		if (pC.dRays) Renderer.addSoundBounceRay(soundPos, rayHit.getLocation(), ChatFormatting.GREEN.getColor());

		if (rayHit.isMissed()) {
			double[] empty = new double[pC.nRayBounces];
			Arrays.fill(empty, 0);
			return new ReflectedRayData(
					0,
					1,
					0,
					1,
					empty,
					empty,
					empty,
					empty,
					empty,
					empty
			);
		}

		BlockPos lastHitBlock = rayHit.getBlockPos();
		Vec3 lastHitPos = rayHit.getLocation();
		Vec3i lastHitNormal = rayHit.getSide().getNormal();
		Vec3 lastRayDir = dir;
		BlockState lastHitState = rayHit.getBlockState();
		double lastBlockReflectivity = getBlockReflectivity(lastHitState);

		int size = 0;
		double missed = 0;
		double totalDistance = soundPos.distanceTo(lastHitPos);
		double totalReflectivity = lastBlockReflectivity;

		double[] shared = new double[pC.nRayBounces];
		double[] distToPlayer = new double[pC.nRayBounces];
		double[] bounceDistance = new double[pC.nRayBounces];
		double[] totalBounceDistance = new double[pC.nRayBounces];
		double[] bounceReflectivity = new double[pC.nRayBounces];
		double[] totalBounceEnergy = new double[pC.nRayBounces];

		bounceReflectivity[0] = lastBlockReflectivity;
		totalBounceEnergy[0] = lastBlockReflectivity;

		bounceDistance[0] = totalDistance;
		totalBounceDistance[0] = totalDistance;
		distToPlayer[0] = lastHitPos.distanceTo(listenerPos);

		SPHitResult finalRayHit = Patch.fixedRaycast(lastHitPos, listenerPos, mc.level, lastHitBlock, rayHit.chunk);

		int color = ChatFormatting.GRAY.getColor();
		if (finalRayHit.isMissed()) {
			color = ChatFormatting.WHITE.getColor();
			shared[0] = 1;
		}
		if (pC.dRays) Renderer.addSoundBounceRay(lastHitPos, finalRayHit.getLocation(), color);

		for (int i = 1; i < pC.nRayBounces; i++) {
			double absorption = BlockPhysicsUtil.getAbsorptionCoefficient(lastHitState);
			double roughness = BlockPhysicsUtil.getRoughnessCoefficient(lastHitState);
			double density = BlockPhysicsUtil.getDensityMultiplier(lastHitState);

			if (absorption > 0.7 && Math.random() < absorption * 0.9) {
				break;
			}

			if (density < 0.3) {
				roughness = Math.min(1.0, roughness + (1.0 - density) * 0.5);
			}

			Vec3 reflectedDir;
			if (roughness > 0.3) {
				Vec3 normalVec = new Vec3(lastHitNormal.getX(), lastHitNormal.getY(), lastHitNormal.getZ());
				reflectedDir = BlockPhysicsUtil.getScatteredDirection(lastRayDir, normalVec, roughness);
			} else {
				reflectedDir = pseudoReflect(lastRayDir, lastHitNormal);
			}

			rayHit = Patch.fixedRaycast(lastHitPos,
					new Vec3(lastHitPos.x + reflectedDir.x * (pC.maxTraceDist - totalDistance),
							lastHitPos.y + reflectedDir.y * (pC.maxTraceDist - totalDistance),
							lastHitPos.z + reflectedDir.z * (pC.maxTraceDist - totalDistance)),
					mc.level, lastHitBlock, rayHit.chunk);

			if (rayHit.isMissed()) {
				if (pC.dRays) Renderer.addSoundBounceRay(lastHitPos, rayHit.getLocation(), ChatFormatting.DARK_RED.getColor());
				missed = Math.pow(totalReflectivity, pC.globalReflRcp);
				break;
			}

			final Vec3 newRayHitPos = rayHit.getLocation();
			double segmentDistance = lastHitPos.distanceTo(newRayHitPos);
			bounceDistance[i] = segmentDistance;
			totalDistance += segmentDistance;

			if (absorption > 0.7) {
				double maxRemainingDistance = pC.maxTraceDist * (1.0 - absorption * 0.7);
				if (totalDistance > maxRemainingDistance) {
					if (pC.dRays) Renderer.addSoundBounceRay(lastHitPos, newRayHitPos, ChatFormatting.DARK_PURPLE.getColor());
					missed = Math.pow(totalReflectivity, pC.globalReflRcp);
					break;
				}
			}

			if (pC.maxTraceDist - totalDistance < newRayHitPos.distanceTo(listenerPos)) {
				if (pC.dRays) Renderer.addSoundBounceRay(lastHitPos, newRayHitPos, ChatFormatting.DARK_PURPLE.getColor());
				missed = Math.pow(totalReflectivity, pC.globalReflRcp);
				break;
			}

			final double newBlockReflectivity = getBlockReflectivity(rayHit.getBlockState());
			totalReflectivity *= newBlockReflectivity;
			if (totalReflectivity < minEnergy){
				if (pC.dRays) Renderer.addSoundBounceRay(lastHitPos, newRayHitPos, ChatFormatting.DARK_PURPLE.getColor());
				break;
			}

			if (pC.dRays) Renderer.addSoundBounceRay(lastHitPos, newRayHitPos, ChatFormatting.BLUE.getColor());

			lastHitState = rayHit.getBlockState();
			lastBlockReflectivity = newBlockReflectivity;
			lastHitPos = newRayHitPos;
			lastHitNormal = rayHit.getSide().getNormal();
			lastRayDir = reflectedDir;
			lastHitBlock = rayHit.getBlockPos();

			size = i;
			totalBounceDistance[i] = totalDistance;
			distToPlayer[i] = lastHitPos.distanceTo(listenerPos);
			bounceReflectivity[i] = lastBlockReflectivity;
			totalBounceEnergy[i] = totalReflectivity;

			finalRayHit = Patch.fixedRaycast(lastHitPos, listenerPos, mc.level, lastHitBlock, rayHit.chunk);

			color = ChatFormatting.GRAY.getColor();
			if (finalRayHit.isMissed()) {
				color = ChatFormatting.WHITE.getColor();
				shared[i] = 1;
			}
			if (pC.dRays) Renderer.addSoundBounceRay(lastHitPos, finalRayHit.getLocation(), color);
		}

		return new ReflectedRayData(
				++size, missed, totalDistance,
				totalReflectivity, shared,
				distToPlayer, bounceDistance, totalBounceDistance,
				bounceReflectivity, totalBounceEnergy);
	}

	@Environment(EnvType.CLIENT)
	private static @NotNull Set<OccludedRayData> throwOcclRay() {
		if (Engine.isOff) throw new IllegalStateException("ResoundingEngine must be started first! ");
		return Collections.emptySet();
	}

	@Environment(EnvType.CLIENT)
	private static @NotNull EnvData evalEnv() {
		if (Patch.lastUpd != timeT) {
			Patch.shapeCache.clear();
			BLOCK_REFLECTIVITY_CACHE.clear();
			Patch.lastUpd = timeT;
		}

		Patch.maxY = Objects.requireNonNull(mc.level).getMaxBuildHeight();
		Patch.minY = mc.level.getMinBuildHeight();
		Patch.maxX = (int) (playerPos.x + (viewDist * 16));
		Patch.minX = (int) (playerPos.x - (viewDist * 16));
		Patch.maxZ = (int) (playerPos.z + (viewDist * 16));
		Patch.minZ = (int) (playerPos.z - (viewDist * 16));

		Set<ReflectedRayData> reflRays;
		if (isSpam) {
			if (pC.dLog || pC.eLog) LOGGER.info("Skipped ray tracing for sound: {}", lastSoundName);
			reflRays = Collections.emptySet();
		} else {
			if (pC.dLog || pC.eLog) LOGGER.info("Sampling environment with {} seed rays...", pC.nRays);
			reflRays = rays.parallelStream().map(Engine::throwReflRay).collect(Collectors.toSet());
			if (pC.dLog) LOGGER.info("Environment sampled!");
		}

		Set<OccludedRayData> occlRays = throwOcclRay();

		EnvData data = new EnvData(reflRays, occlRays);
		if (pC.eLog) LOGGER.info("Raw Environment data:\n{}", data);
		return data;
	}

	@Contract("_ -> new")
	@Environment(EnvType.CLIENT)
	private static @NotNull SoundProfile processEnv(final EnvData data) {
		boolean inWater = mc.player != null && mc.player.isUnderWater();
		final double airAbsorptionHF = 1.0;
		double directGain = (auxOnly ? 0 : inWater ? pC.waterFilt : 1) * Math.pow(airAbsorptionHF, listenerPos.distanceTo(soundPos));

		if (data.reflRays().isEmpty()) {
			return new SoundProfile(sourceID, directGain, Math.pow(directGain, pC.globalAbsHFRcp), new double[pC.resolution + 1], new double[pC.resolution + 1]);
		}

		double bounceCount = 0.0D;
		double missedSum = 0.0D;
		for (ReflectedRayData reflRay : data.reflRays()) {
			bounceCount += reflRay.size();
			missedSum += reflRay.missed();
		}
		missedSum *= pC.rcpNRays;

		double sharedSum = 0.0D;
		final double[] sendGain = new double[pC.resolution + 1];

		double rcpBounceCount = pC.resolution / bounceCount * pC.globalRvrbGain;

		RoomAcousticsAnalyzer.RoomAnalysis room = null;
		if (mc != null && mc.level != null) {
			room = RoomAcousticsAnalyzer.analyzeRoom(mc.level, soundPos);

			if (pC.dLog) {
				Engine.LOGGER.info("Room analysis: Type={}, Volume={:.1f}, Absorption={:.3f}",
						room.roomType, room.volume, room.averageAbsorption);
			}
		}

		EchoDetector.EchoAnalysis echoAnalysis = null;
		double estimatedFrequency = estimateSoundFrequencyInternal();
		if (mc != null && mc.level != null) {
			echoAnalysis = EchoDetector.detectEchoes(mc.level, soundPos, listenerPos, estimatedFrequency);
		}

		for (ReflectedRayData reflRay : data.reflRays()) {
			if (reflRay.missed() == 1.0D) continue;

			final int size = reflRay.size();
			final double[] smoothSharedEnergy = pC.fastShared ? null : new double[pC.nRayBounces];
			final double[] smoothSharedDistance = pC.fastShared ? null : new double[pC.nRayBounces];

			if (!pC.fastShared) {
				for (int i = 0; i < size; i++) {
					if (reflRay.shared()[i] == 1) {
						smoothSharedEnergy[i] = 1;
						smoothSharedDistance[i] = reflRay.distToPlayer()[i];
					} else {
						int up; double traceUpRefl = 1; double traceUpDistance = 0;
						for (up = i + 1; up <= size; up++) {
							traceUpRefl *= up == size ? 0 : reflRay.bounceReflectivity()[up];
							if (up != size) traceUpDistance += reflRay.bounceDistance()[up];
							if (up != size && reflRay.shared()[up] == 1) {
								traceUpDistance += reflRay.distToPlayer()[up];
								break;
							}
						}

						int dn; double traceDownRefl = 1; double traceDownDistance = 0;
						for (dn = i - 1; dn >= -1; dn--) {
							traceDownRefl *= dn == -1 ? 0 : reflRay.bounceReflectivity()[dn];
							if (dn != -1) traceDownDistance += reflRay.bounceDistance()[dn + 1];
							if (dn != -1 && reflRay.shared()[dn] == 1) {
								traceDownDistance += reflRay.distToPlayer()[dn];
								break;
							}
						}

						if (Math.max(traceDownRefl, traceUpRefl) == traceUpRefl){
							smoothSharedEnergy[i] = traceUpRefl;
							smoothSharedDistance[i] = traceUpDistance;
						} else {
							smoothSharedEnergy[i] = traceDownRefl;
							smoothSharedDistance[i] = traceDownDistance;
						}
					}
				}
			}

			for (int i = 0; i < size; i++) {
				sharedSum += reflRay.shared()[i];

				double baseEnergy = reflRay.totalBounceEnergy()[i];
				double totalDistance = reflRay.totalBounceDistance()[i] +
						(pC.fastShared ? reflRay.distToPlayer()[i] : smoothSharedDistance[i]);

				double highFreqAttenuation = Math.pow(0.85, totalDistance / 10.0);
				double materialAttenuation = 1.0;

				if (room != null) {
					materialAttenuation *= (1.0 - room.averageAbsorption * 0.5);

					if (room.volume > 1000) {
						double roomSizeFactor = Math.sqrt(room.volume / 1000.0);
						highFreqAttenuation = Math.pow(0.85, totalDistance * roomSizeFactor / 10.0);
					}
				}

				double playerEnergy = baseEnergy *
						(pC.fastShared ? 1 : smoothSharedEnergy[i]) *
						Math.pow(airAbsorptionHF, totalDistance) /
						Math.pow(totalDistance, 2.0 * missedSum) *
						highFreqAttenuation *
						materialAttenuation;

				double bounceTime = reflRay.totalBounceDistance()[i] / speedOfSound;
				double roomTimeFactor = 1.0;

				if (room != null) {
					if (room.roomType == RoomAcousticsAnalyzer.RoomType.LARGE ||
							room.roomType == RoomAcousticsAnalyzer.RoomType.HUGE ||
							room.roomType == RoomAcousticsAnalyzer.RoomType.CATHEDRAL) {
						roomTimeFactor = 1.5;
					}

					if (room.averageAbsorption > 0.5) {
						roomTimeFactor *= (1.0 - room.averageAbsorption * 0.8);
					}
				}

				int index = Mth.clamp(
						(int) (1/logBase(
								Math.max(
										Math.pow(reflRay.totalBounceEnergy()[i],
												pC.maxDecayTime / (bounceTime * roomTimeFactor) * pC.energyFix),
										Double.MIN_VALUE
								),
								minEnergy
						) * pC.resolution),
						0,
						pC.resolution
				);

				sendGain[index] += Mth.clamp(playerEnergy, 0, 1.0 - Double.MIN_NORMAL);
			}
		}

		sharedSum /= bounceCount;
		final double[] sendCutoff = new double[pC.resolution+1];

		if (echoAnalysis != null && echoAnalysis.hasClearEcho) {
			double echoEnhancementFactor = calculateEchoEnhancementFactor(echoAnalysis);

			for (int i = 0; i < sendGain.length; i++) {
				double echoBoost = 0.0;

				for (int j = 0; j < echoAnalysis.echoTimes.size(); j++) {
					double echoTime = echoAnalysis.echoTimes.get(j);
					double echoAmp = echoAnalysis.echoAmplitudes.get(j);

					int echoSlot = (int)(echoTime * 8 * pC.resolution);
					if (echoSlot >= 0 && echoSlot < sendGain.length && echoSlot == i) {
						echoBoost += echoAmp * 0.35 * echoAnalysis.echoClarity * echoEnhancementFactor;
					}
				}

				sendGain[i] = Math.min(1.0, sendGain[i] * (1.0 + echoBoost));

				double echoCutoffBoost = echoAnalysis.averageReflectivity * 0.12 +
						echoAnalysis.echoSpaciousness * 0.08;
				sendCutoff[i] = Math.min(1.0, sendCutoff[i] * (1.0 + echoCutoffBoost));
			}

			if (pC.dLog) {
				Engine.LOGGER.info("Enhanced echo detection: {} echoes, clarity={:.2f}, spaciousness={:.2f}, avgRefl={:.2f}",
						echoAnalysis.echoTimes.size(), echoAnalysis.echoClarity,
						echoAnalysis.echoSpaciousness, echoAnalysis.averageReflectivity);
			}
		}

		for (int i = 0; i <= pC.resolution; i++) {
			sendGain[i] = Mth.clamp(
					sendGain[i] *
							(inWater ? pC.waterFilt : 1) *
							(pC.fastShared ? sharedSum : 1) *
							rcpBounceCount,
					0,
					1.0 - Double.MIN_NORMAL
			);

			double roomCutoffFactor = 1.0;
			if (room != null) {
				roomCutoffFactor = 1.0 - room.averageAbsorption * 0.4;
			}
			sendCutoff[i] = Math.pow(sendGain[i], pC.globalRvrbHFRcp * roomCutoffFactor);
		}

		double occlusion = Patch.fixedRaycast(soundPos, listenerPos, mc.level, soundBlockPos, soundChunk)
				.isMissed() ? 1 : 0;

		directGain *= Math.pow(airAbsorptionHF, listenerPos.distanceTo(soundPos))
				/ Math.pow(listenerPos.distanceTo(soundPos), 2.0 * missedSum)
				* Mth.lerp(occlusion, sharedSum, 1d);

		if (room != null) {
			if (room.isEnclosed && occlusion == 1) {
				directGain *= 1.2;
			}

			if (room.averageAbsorption > 0.3) {
				directGain *= (1.0 + room.averageAbsorption * 0.3);
			}
		}

		if (echoAnalysis != null && echoAnalysis.hasClearEcho) {
			double echoDirectBoost = echoAnalysis.primaryEchoGain * 0.15 +
					echoAnalysis.echoClarity * 0.1;
			directGain = Math.min(1.0, directGain * (1.0 + echoDirectBoost));
		}

		double directCutoff = Math.pow(directGain, pC.globalAbsHFRcp);

		SoundProfile profile = new SoundProfile(sourceID, directGain, directCutoff, sendGain, sendCutoff, null, null, echoAnalysis);

		if (pC.eLog || pC.dLog) {
			Engine.LOGGER.info("Processed sound profile in {} room:\n{}",
					room != null ? room.roomType : "unknown", profile);
		}

		return profile;
	}

	private static double calculateEchoEnhancementFactor(EchoDetector.EchoAnalysis echoAnalysis) {
		double factor = 1.0;

		factor += echoAnalysis.echoClarity * 0.4;
		factor += echoAnalysis.echoSpaciousness * 0.3;
		factor += echoAnalysis.averageReflectivity * 0.2;
		factor -= echoAnalysis.averageAbsorption * 0.15;

		if (echoAnalysis.echoDensity > 1.5) {
			factor *= 1.1;
		}

		return Math.max(0.5, Math.min(2.0, factor));
	}

	private static double estimateSoundFrequencyInternal() {
		if (lastSoundName == null) return 1000.0;

		String soundName = lastSoundName.toLowerCase();

		if (soundName.contains("bell") || soundName.contains("chime") ||
				soundName.contains("glass") || soundName.contains("crystal")) {
			return 2000.0;
		}
		if (soundName.contains("drum") || soundName.contains("bass") ||
				soundName.contains("thump") || soundName.contains("boom")) {
			return 150.0;
		}
		if (soundName.contains("click") || soundName.contains("tick") ||
				soundName.contains("snap") || soundName.contains("pop")) {
			return 3000.0;
		}
		if (soundName.contains("voice") || soundName.contains("speech") ||
				soundName.contains("talk") || soundName.contains("chat")) {
			return 500.0;
		}
		if (soundName.contains("metal") || soundName.contains("iron") ||
				soundName.contains("steel") || soundName.contains("chain")) {
			return 800.0;
		}
		if (soundName.contains("wood") || soundName.contains("plank") ||
				soundName.contains("log") || soundName.contains("door")) {
			return 400.0;
		}
		return 1000.0;
	}

	@Environment(EnvType.CLIENT)
	public static void setEnv(Context context, final @NotNull SoundProfile profile, boolean isGentle) {
		if (profile.sendGain().length != pC.resolution + 1 || profile.sendCutoff().length != pC.resolution + 1) {
			throw new IllegalArgumentException("Error: Reverb parameter count does not match reverb resolution!");
		}

		for (int i = 0; i < profile.sendGain().length; i++) {
			double gain = profile.sendGain()[i];
			double cutoff = profile.sendCutoff()[i];

			if (gain < 0 || gain > 1.0 || Double.isNaN(gain) || Double.isInfinite(gain)) {
				gain = Mth.clamp(gain, 0, 1.0);
			}

			if (cutoff < 0 || cutoff > 1.0 || Double.isNaN(cutoff) || Double.isInfinite(cutoff)) {
				cutoff = Mth.clamp(cutoff, 0, 1.0);
			}
		}

		SlotProfile finalSend = selectSlot(profile.sendGain(), profile.sendCutoff());

		if (finalSend.slot() < 0 || finalSend.slot() >= pC.resolution) {
			finalSend = new SlotProfile(0, 0, 0);
		}

		if (finalSend.gain() < 0 || finalSend.gain() > 1.0) {
			finalSend = new SlotProfile(finalSend.slot(), Mth.clamp(finalSend.gain(), 0, 1.0), finalSend.cutoff());
		}

		if (finalSend.cutoff() < 0 || finalSend.cutoff() > 1.0) {
			finalSend = new SlotProfile(finalSend.slot(), finalSend.gain(), Mth.clamp(finalSend.cutoff(), 0, 1.0));
		}

		if (pC.eLog || pC.dLog) {
			LOGGER.info("Final reverb settings:\n{}", finalSend);
		}

		try {
			context.update(finalSend, profile, isGentle);
			if (context.dopplerActive && context.dopplerEffect != null) {
				context.dopplerEffect.applyToSource(profile.sourceID(), profile);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to set environment for sound {}: {}", lastSoundName, e.getMessage());
			// Even if it fails, the sound will still be played, just without the reverberation effect
		}
	}


	@Contract("_, _ -> new")
	@Environment(EnvType.CLIENT)
	public static @NotNull SlotProfile selectSlot(double[] sendGain, double[] sendCutoff) {
		if (pC.fastPick) {
			double max = 0;
			int imax = 0;
			for (int i = 1; i <= pC.resolution; i++) {
				if (sendGain[i] > max) {
					max = sendGain[i];
					imax = i;
				}
			}

			int iavg = imax;

			if (iavg > 0){ return new SlotProfile(iavg-1, sendGain[iavg], sendCutoff[iavg]); }
			return new SlotProfile(0, 0, 0);
		}
		return new SlotProfile(0, 0, 0);
	}
}