package dev.thedocruby.resounding.raycast;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public class SPHitResult extends HitResult {
	private final Direction side;
	private final BlockPos blockPos;
	private final boolean missed;
	private final BlockState blockState;
	public final LevelChunk chunk;

	@Contract("_, _, _, _ -> new")
	public static @NotNull SPHitResult createMissed(Vec3 pos, Direction side, BlockPos blockPos, LevelChunk c) {
		return new SPHitResult(true, pos, side, blockPos, null, c);
	}

	public SPHitResult(@NotNull BlockHitResult blockHitResult, BlockState bs, LevelChunk c) {
		super(blockHitResult.getLocation());
		this.missed = false;
		this.side = blockHitResult.getDirection();
		this.blockPos = blockHitResult.getBlockPos();
		this.blockState = bs;
		this.chunk = c;
	}

	public SPHitResult(boolean missed, Vec3 pos, Direction side, BlockPos blockPos, BlockState bs, LevelChunk c) {
		super(pos);
		this.missed = missed;
		this.side = side;
		this.blockPos = blockPos;
		this.blockState = bs;
		this.chunk = c;
	}

	public static SPHitResult get(BlockHitResult bhr, BlockState bs, LevelChunk c) {
		if (bhr == null) return null;
		return new SPHitResult(bhr, bs, c);
	}

	public BlockPos getBlockPos() {
		return this.blockPos;
	}

	public Direction getSide() {
		return this.side;
	}

	@Deprecated
	public @NotNull Type getType() {
		return this.missed ? Type.MISS : Type.BLOCK;
	}

	public boolean isMissed() {
		return this.missed;
	}

	public BlockState getBlockState() {
		return blockState;
	}

	public @NotNull Vec3 getLocation() {
		return super.getLocation();
	}
}