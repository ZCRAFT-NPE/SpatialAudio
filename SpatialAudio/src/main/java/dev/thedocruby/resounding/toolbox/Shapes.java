package dev.thedocruby.resounding.toolbox;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.shapes.VoxelShape;


@Environment(EnvType.CLIENT)
public record Shapes(VoxelShape solid, VoxelShape liquid) {
	public static VoxelShape empty() {
		return net.minecraft.world.phys.shapes.Shapes.empty();
	}

	public static VoxelShape block() {
		return net.minecraft.world.phys.shapes.Shapes.block();
	}
	public VoxelShape getSolid() {
		return this.solid;
	}
	public VoxelShape getLiquid() {
		return this.liquid;
	}
}
