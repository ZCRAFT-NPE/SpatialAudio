package me.zcraft.mods.spatialaudio.raycast;

import me.zcraft.mods.spatialaudio.toolbox.Shapes;
import me.zcraft.mods.spatialaudio.toolbox.WorldChunkAccess;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static me.zcraft.mods.spatialaudio.config.PrecomputedConfig.pC;


public class Patch {

	private Patch() {}
	public static long lastUpd = 0;
	public static Map<Long, Shapes> shapeCache = new ConcurrentHashMap<>(2048);
	// reset every tick, usually up to 2200
	// {pos, (block state, block, fluid) }

	public static int maxY;
	public static int minY;
	public static int maxX;
	public static int minX;
	public static int maxZ;
	public static int minZ;

	private static final VoxelShape EMPTY = Shapes.empty();
	private static final VoxelShape CUBE = Shapes.block();

	private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE_BLOCK_POS =
			ThreadLocal.withInitial(() -> new BlockPos.MutableBlockPos(0, 0, 0));
	private static final ThreadLocal<double[]> TEMP_DOUBLES =
			ThreadLocal.withInitial(() -> new double[12]);

	public static SPHitResult fixedRaycast(@NotNull Vec3 start, Vec3 end, Level world, @Nullable BlockPos ignore, @Nullable LevelChunk chunk) {
		LiquidStorage currentNotAirStorage = chunk == null ? null : ((WorldChunkAccess)chunk).getNotAirLiquidStorage();
		int currentX = chunk == null ? ((int) Math.floor(start.x)) >> 4 : chunk.getPos().x;
		int currentZ = chunk == null ? ((int) Math.floor(start.z)) >> 4 : chunk.getPos().z;
		boolean[] currentSlice = currentNotAirStorage == null ? null : currentNotAirStorage.getSection((int)Math.floor(start.y));
		int currentY = (int) start.y;

		if (start.x > maxX || start.y > maxY || start.z > maxZ || start.x < minX || start.y < minY || start.z < minZ) {
			return SPHitResult.createMissed(start, null, BlockPos.containing(start), chunk);
		}

		Vec3 delta = end.subtract(start);
		double dx = delta.x;
		double dy = delta.y;
		double dz = delta.z;

		if (!(end.x <= maxX && end.y <= maxY && end.z <= maxZ && end.x >= minX && end.y >= minY && end.z >= minZ)) {
			double fx = dx == 0 ? Double.MAX_VALUE : (((dx > 0 ? maxX : minX) - start.x) / dx);
			double fy = dy == 0 ? Double.MAX_VALUE : (((dy > 0 ? maxY : minY) - start.y) / dy);
			double fz = dz == 0 ? Double.MAX_VALUE : (((dz > 0 ? maxZ : minZ) - start.z) / dz);
			double factor = Math.min(Math.min(fx, fy), fz);
			delta = delta.scale(factor);
			end = start.add(delta);
			dx = delta.x;
			dy = delta.y;
			dz = delta.z;
		}

		if (start.equals(end)) {
			return SPHitResult.createMissed(end, null, BlockPos.containing(end), chunk);
		} else {
			double xe1 = Mth.lerp(-1.0E-7D, end.x, start.x);
			double ye1 = Mth.lerp(-1.0E-7D, end.y, start.y);
			double ze1 = Mth.lerp(-1.0E-7D, end.z, start.z);
			double xs1 = Mth.lerp(-1.0E-7D, start.x, end.x);
			double ys1 = Mth.lerp(-1.0E-7D, start.y, end.y);
			double zs1 = Mth.lerp(-1.0E-7D, start.z, end.z);
			int xbs = Mth.floor(xs1);
			int ybs = Mth.floor(ys1);
			int zbs = Mth.floor(zs1);

			BlockPos.MutableBlockPos blockPosStart1 = MUTABLE_BLOCK_POS.get();
			blockPosStart1.set(xbs, ybs, zbs);

			int xx = xbs >> 4;
			int zz = zbs >> 4;
			if (currentX != xx || currentZ != zz) {
				if (currentNotAirStorage != null) {
					int ddx = currentX - xx;
					int ddz = currentZ - zz;
					if (ddz == 0) {
						if (ddx == -1 && currentNotAirStorage.xm != null) {
							chunk = currentNotAirStorage.xm.getChunk();
						} else if (ddx == 1 && currentNotAirStorage.xp != null) {
							chunk = currentNotAirStorage.xp.getChunk();
						} else {
							chunk = (LevelChunk) world.getChunk(xx, zz, ChunkStatus.FULL, false);
						}
					} else if (ddx == 0) {
						if (ddz == -1 && currentNotAirStorage.zm != null) {
							chunk = currentNotAirStorage.zm.getChunk();
						} else if (ddz == 1 && currentNotAirStorage.zp != null) {
							chunk = currentNotAirStorage.zp.getChunk();
						} else {
							chunk = (LevelChunk) world.getChunk(xx, zz, ChunkStatus.FULL, false);
						}
					} else {
						chunk = (LevelChunk) world.getChunk(xx, zz, ChunkStatus.FULL, false);
					}
				} else {
					chunk = (LevelChunk) world.getChunk(xx, zz, ChunkStatus.FULL, false);
				}
				currentX = xx;
				currentZ = zz;
				currentY = ybs;
				currentNotAirStorage = chunk == null ? null : ((WorldChunkAccess)chunk).getNotAirLiquidStorage();
				currentSlice = currentNotAirStorage == null ? null : currentNotAirStorage.getSection(ybs);
			} else if (ybs != currentY) {
				currentSlice = currentNotAirStorage == null ? null : currentNotAirStorage.getSection(ybs);
				currentY = ybs;
			}

			final SPHitResult hitResultStart;
			if (currentSlice == null || !currentSlice[(xbs & 15) + ((zbs & 15) << 4)] || blockPosStart1.equals(ignore))
				hitResultStart = null;
			else {
				BlockState bs1 = chunk.getBlockState(blockPosStart1);
				if (bs1.isAir() || bs1.getBlock().equals(Blocks.MOVING_PISTON)) hitResultStart = null;
				else hitResultStart = finalRaycast(world, bs1, blockPosStart1, start, end, chunk, (short)4);
			}

			if (hitResultStart != null) {
				return hitResultStart;
			} else {
				double dxx = xe1 - xs1;
				double dyy = ye1 - ys1;
				double dzz = ze1 - zs1;
				int dirx = Mth.sign(dxx);
				int diry = Mth.sign(dyy);
				int dirz = Mth.sign(dzz);
				double rdx = dirx == 0 ? 1.7976931348623157E300D : (double)dirx / dxx;
				double rdy = diry == 0 ? 1.7976931348623157E300D : (double)diry / dyy;
				double rdz = dirz == 0 ? 1.7976931348623157E300D : (double)dirz / dzz;
				double tx = rdx * (dirx > 0 ? 1.0D - Mth.frac(xs1) : Mth.frac(xs1));
				double ty = rdy * (diry > 0 ? 1.0D - Mth.frac(ys1) : Mth.frac(ys1));
				double tz = rdz * (dirz > 0 ? 1.0D - Mth.frac(zs1) : Mth.frac(zs1));

				SPHitResult object2 = null;
				do {
					if (tx > 1.0D && ty > 1.0D && tz > 1.0D) {
						return SPHitResult.createMissed(end, null, BlockPos.containing(end), chunk);
					}

					short side;

					if (tx < ty) {
						if (tx < tz) {
							xbs += dirx;
							tx += rdx;
							side = 1;
						} else {
							zbs += dirz;
							tz += rdz;
							side = 3;
						}
					} else if (ty < tz) {
						ybs += diry;
						ty += rdy;
						side = 2;
					} else {
						zbs += dirz;
						tz += rdz;
						side = 3;
					}

					int x = xbs >> 4;
					int z = zbs >> 4;
					if (currentX != x || currentZ != z) {
						if (currentNotAirStorage != null) {
							int ddx = currentX - x;
							int ddz = currentZ - z;
							if (ddz == 0) {
								if (ddx == -1 && currentNotAirStorage.xm != null) {
									chunk = currentNotAirStorage.xm.getChunk();
								} else if (ddx == 1 && currentNotAirStorage.xp != null) {
									chunk = currentNotAirStorage.xp.getChunk();
								} else {
									chunk = (LevelChunk) world.getChunk(x, z, ChunkStatus.FULL, false);
								}
							} else if (ddx == 0) {
								if (ddz == -1 && currentNotAirStorage.zm != null) {
									chunk = currentNotAirStorage.zm.getChunk();
								} else if (ddz == 1 && currentNotAirStorage.zp != null) {
									chunk = currentNotAirStorage.zp.getChunk();
								} else {
									chunk = (LevelChunk) world.getChunk(x, z, ChunkStatus.FULL, false);
								}
							} else {
								chunk = (LevelChunk) world.getChunk(x, z, ChunkStatus.FULL, false);
							}
						} else {
							chunk = (LevelChunk) world.getChunk(x, z, ChunkStatus.FULL, false);
						}
						currentX = x;
						currentZ = z;
						currentY = ybs;
						currentNotAirStorage = chunk == null ? null : ((WorldChunkAccess)chunk).getNotAirLiquidStorage();
						currentSlice = currentNotAirStorage == null ? null : currentNotAirStorage.getSection(ybs);
					} else if (ybs != currentY) {
						currentSlice = currentNotAirStorage == null ? null : currentNotAirStorage.getSection(ybs);
						currentY = ybs;
					}

					blockPosStart1.set(xbs, ybs, zbs);

					if (currentSlice != null) {
						if (currentSlice[(xbs & 15) + ((zbs & 15) << 4)] && !blockPosStart1.equals(ignore)) {
							BlockState bs = chunk.getBlockState(blockPosStart1);

							if (!bs.isAir() && !bs.getBlock().equals(Blocks.MOVING_PISTON)) {
								Vec3 start1;
								double f;
								if (side == 1) {
									f = (((-dirx * 0.499 + xbs + 0.5) - start.x) * rdx * dirx);
								} else if (side == 2) {
									f = (((-diry * 0.499 + ybs + 0.5) - start.y) * rdy * diry);
								} else {
									f = (((-dirz * 0.499 + zbs + 0.5) - start.z) * rdz * dirz);
								}

								start1 = new Vec3(start.x + dx * f, start.y + dy * f, start.z + dz * f);

								double[] temp = TEMP_DOUBLES.get();
								temp[0] = (((dirx * 0.5001 + xbs + 0.5) - start.x) * rdx * dirx);
								temp[1] = (((diry * 0.5001 + ybs + 0.5) - start.y) * rdy * diry);
								temp[2] = (((dirz * 0.5001 + zbs + 0.5) - start.z) * rdz * dirz);
								double minFactor = Math.min(Math.min(temp[0], temp[1]), temp[2]);
								Vec3 end1 = new Vec3(start.x + dx * minFactor, start.y + dy * minFactor, start.z + dz * minFactor);

								object2 = finalRaycast(world, bs, blockPosStart1, start1, end1, chunk, side);
							}
						}
					} else {
						int dx1 = ((dirx * 15 + (x << 5) + 15) >> 1) - xbs;
						int dz1 = ((dirz * 15 + (z << 5) + 15) >> 1) - zbs;
						double dtx1 = dx1 * rdx * dirx;
						double dtz1 = dz1 * rdz * dirz;
						if (currentNotAirStorage == null || currentNotAirStorage.isEmpty()
								|| (diry == 1 && ybs > currentNotAirStorage.top) || (diry == -1 && ybs < currentNotAirStorage.bottom)
								|| (dtx1 + tx < ty && dtz1 + tz < ty)) {

							if (dtx1 > dtz1) {
								zbs += dz1;
								tz += dtz1;
							} else {
								xbs += dx1;
								tx += dtx1;
							}
						} else {
							while (dx1 > 0 && dz1 > 0) {
								if (tx < ty) {
									if (tx < tz) {
										xbs += dirx;
										tx += rdx;
										dx1 -= dirx;
									} else {
										zbs += dirz;
										tz += rdz;
										dz1 -= dirz;
									}
								} else if (ty < tz) {
									break;
								} else {
									zbs += dirz;
									tz += rdz;
									dz1 -= dirz;
								}
							}
						}
					}
				} while(object2 == null);

				return object2;
			}
		}
	}

	private static @Nullable SPHitResult finalRaycast(Level world, BlockState bs, @NotNull BlockPos pos, Vec3 start, Vec3 end, LevelChunk c, Short side) {
		long posl = pos.asLong();
		Shapes shapes;
		shapes = shapeCache.get(posl);
		if (shapes == null) {
			if (pC.dRays) world.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, false, pos.getX() + 0.5d, pos.getY() + 1d, pos.getZ() + 0.5d, 0, 0, 0);
			VoxelShape collisionShape = bs.getCollisionShape(world, pos);
			shapes = new Shapes(collisionShape == EMPTY ? null : collisionShape, null);
			shapeCache.put(posl, shapes);
		}

		VoxelShape voxelShape = shapes.getSolid();
		if (voxelShape == CUBE) {
			Direction direction =
					side == 1 ? Direction.EAST :
							side == 2 ? Direction.UP :
									side == 3 ? Direction.NORTH :
											Direction.getNearest(start.x - pos.getX() - 0.5, start.y - pos.getY() - 0.5, start.z - pos.getZ() - 0.5);
			return new SPHitResult(false, start, direction, pos, bs, c);
		}
		return voxelShape == null ? null : SPHitResult.get(voxelShape.clip(start, end, pos), bs, c);
	}
}