package me.zcraft.mods.spatialaudio.mixin;

import me.zcraft.mods.spatialaudio.raycast.LiquidStorage;
import me.zcraft.mods.spatialaudio.toolbox.WorldChunkAccess;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


@Mixin(LevelChunk.class)
public abstract class WorldChunkMixin extends ChunkAccess implements WorldChunkAccess, LiquidStorage.IChunkAccess {
	private LiquidStorage notAirLiquidStorage = null;

	@Override
	public LiquidStorage getNotAirLiquidStorage() {
		return notAirLiquidStorage;
	}

	@Override
	public LevelChunk getChunk() {
		return (LevelChunk) (Object) this;
	}

	@Shadow @Final Level level;

	public WorldChunkMixin(ChunkPos pos, UpgradeData upgradeData, LevelHeightAccessor heightLimitView, Registry<Biome> biome, long inhabitedTime, @Nullable LevelChunkSection[] sectionArrayInitializer, @Nullable BlendingData blendingData) {
		super(pos, upgradeData, heightLimitView, biome, inhabitedTime, sectionArrayInitializer, blendingData);
	}

	@Inject(method = "replaceWithPacketData", at = @At("RETURN"))
	private void load(FriendlyByteBuf buf, CompoundTag nbt, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer, CallbackInfo ci) {
		initStorage();
	}

	@Inject(
			method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
			at = @At("RETURN")
	)
	private void create(Level world, ChunkPos pos, UpgradeData upgradeData,
						LevelChunkTicks<net.minecraft.world.level.block.Block> blockTickScheduler,
						LevelChunkTicks<net.minecraft.world.level.material.Fluid> fluidTickScheduler,
						long inhabitedTime, LevelChunkSection[] sectionArrayInitializer,
						LevelChunk.PostLoadProcessor entityLoader, BlendingData blendingData,
						CallbackInfo ci) {
		if (sectionArrayInitializer != null) initStorage();
	}

	private void initStorage() {
		if (level == null || !level.isClientSide) return;
		LevelChunkSection[] chunkSections = getSections();
		boolean[][] notAirSections = new boolean[512][];
		AtomicInteger bottomNotAir = new AtomicInteger(-600);
		AtomicInteger topNotAir = new AtomicInteger(-600);
		boolean[] notAirFull = new boolean[512];

		int minBuildHeight = this.getMinBuildHeight();

		for (int sectionIndex = 0; sectionIndex < chunkSections.length; sectionIndex++) {
			LevelChunkSection chunkSection = chunkSections[sectionIndex];
			if (chunkSection == null || chunkSection.hasOnlyAir()) continue;

			int sectionBottomY = minBuildHeight + sectionIndex * 16;

			for (int y = sectionBottomY, l = y + 16; y < l; y++) {
				boolean[] notAirSlice = LiquidStorage.empty();
				int notAirCount = 0;
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						Block block = chunkSection.getBlockState(x, y & 15, z).getBlock();
						if (!LiquidStorage.LIQUIDS.AIR.matches(block)) {
							notAirSlice[x + (z << 4)] = true;
							notAirCount++;
						}
					}
				}
				if (notAirCount != 0) {
					synchronized (notAirSections) {
						notAirSections[y + 64] = notAirSlice;
					}
					int Y = y;
					bottomNotAir.getAndUpdate((v) -> v == -600 ? Y : Math.min(v, Y));
					topNotAir.getAndUpdate((v) -> v == -600 ? Y : Math.max(v, Y));
					synchronized (notAirFull) {
						notAirFull[y + 64] = (notAirCount == 16 * 16);
					}
				}
			}
		}

		if (topNotAir.get() != -600) {
			notAirLiquidStorage = new LiquidStorage(
					ArrayUtils.subarray(notAirSections, bottomNotAir.get() + 64, topNotAir.get() + 64 + 1),
					topNotAir.get(),
					bottomNotAir.get(),
					ArrayUtils.subarray(notAirFull, bottomNotAir.get() + 64, topNotAir.get() + 64 + 1),
					(LevelChunk) (Object) this
			);
		} else {
			notAirLiquidStorage = new LiquidStorage((LevelChunk) (Object) this);
		}

		setupAdjacentChunks();
	}

	private void setupAdjacentChunks() {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				if (Math.abs(dx) + Math.abs(dz) > 1) continue;

				LevelChunk adjacentChunk = (LevelChunk) level.getChunk(
						super.getPos().x + dx,
						super.getPos().z + dz,
						ChunkStatus.FULL,
						false
				);

				if (adjacentChunk != null && adjacentChunk instanceof LiquidStorage.IChunkAccess adjacentAccess) {
					LiquidStorage adjacentStorage = adjacentAccess.getNotAirLiquidStorage();
					if (adjacentStorage != null) {
						if (dx == -1) {
							notAirLiquidStorage.xm = adjacentAccess;
							adjacentStorage.xp = this;
						} else if (dx == 1) {
							notAirLiquidStorage.xp = adjacentAccess;
							adjacentStorage.xm = this;
						} else if (dz == -1) {
							notAirLiquidStorage.zm = adjacentAccess;
							adjacentStorage.zp = this;
						} else if (dz == 1) {
							notAirLiquidStorage.zp = adjacentAccess;
							adjacentStorage.zm = this;
						}
					}
				}
			}
		}
	}

	@Shadow
	public @Nullable ChunkStatus getStatus() {
		return null;
	}

	@Inject(method = "setBlockState", at = @At("HEAD"))
	private void setBlock(BlockPos pos, BlockState state, boolean moved, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<BlockState> cir) {
		if (!level.isClientSide) return;
		Block block = state.getBlock();
		notAirLiquidStorage.setBlock(pos.getX() & 15, pos.getY(), pos.getZ() & 15, !LiquidStorage.LIQUIDS.AIR.matches(block));
	}

}