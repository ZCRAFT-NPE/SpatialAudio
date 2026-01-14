package me.zcraft.mods.spatialaudio.toolbox;



import net.minecraft.world.phys.shapes.VoxelShape;



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
