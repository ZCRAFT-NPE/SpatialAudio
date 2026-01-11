package dev.thedocruby.resounding.toolbox;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class BlockPhysicsUtil {

    public static double getAbsorptionCoefficient(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.WHITE_WOOL || block == Blocks.ORANGE_WOOL ||
                block == Blocks.MAGENTA_WOOL || block == Blocks.LIGHT_BLUE_WOOL ||
                block == Blocks.YELLOW_WOOL || block == Blocks.LIME_WOOL ||
                block == Blocks.PINK_WOOL || block == Blocks.GRAY_WOOL ||
                block == Blocks.LIGHT_GRAY_WOOL || block == Blocks.CYAN_WOOL ||
                block == Blocks.PURPLE_WOOL || block == Blocks.BLUE_WOOL ||
                block == Blocks.BROWN_WOOL || block == Blocks.GREEN_WOOL ||
                block == Blocks.RED_WOOL || block == Blocks.BLACK_WOOL) {
            return 0.95;
        }

        if (block == Blocks.CYAN_CARPET || block == Blocks.PURPLE_CARPET || block == Blocks.GREEN_CARPET || block == Blocks.BLUE_CARPET || block == Blocks.RED_CARPET || block == Blocks.BLACK_CARPET || block == Blocks.WHITE_CARPET || block == Blocks.ORANGE_CARPET || block == Blocks.MAGENTA_CARPET || block == Blocks.YELLOW_CARPET || block == Blocks.LIGHT_GRAY_CARPET || block == Blocks.LIGHT_BLUE_CARPET || block == Blocks.GRAY_CARPET || block == Blocks.PINK_CARPET || block == Blocks.BROWN_CARPET|| block == Blocks.MOSS_CARPET || block == Blocks.LIME_CARPET) {
            return 0.92;
        }

        if (block == Blocks.HAY_BLOCK) {
            return 0.88;
        }

        if (block == Blocks.SPONGE || block == Blocks.WET_SPONGE) {
            return 0.90;
        }

        if (block == Blocks.ACACIA_LEAVES || block == Blocks.AZALEA_LEAVES ||
                block == Blocks.FLOWERING_AZALEA_LEAVES || block == Blocks.BIRCH_LEAVES || block == Blocks.OAK_LEAVES || block == Blocks.JUNGLE_LEAVES || block == Blocks.DARK_OAK_LEAVES) {
            return 0.60;
        }

        return 0.10;
    }

    public static double getRoughnessCoefficient(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.WHITE_WOOL || block == Blocks.ORANGE_WOOL ||
                block == Blocks.MAGENTA_WOOL || block == Blocks.LIGHT_BLUE_WOOL ||
                block == Blocks.YELLOW_WOOL || block == Blocks.LIME_WOOL ||
                block == Blocks.PINK_WOOL || block == Blocks.GRAY_WOOL ||
                block == Blocks.LIGHT_GRAY_WOOL || block == Blocks.CYAN_WOOL ||
                block == Blocks.PURPLE_WOOL || block == Blocks.BLUE_WOOL ||
                block == Blocks.BROWN_WOOL || block == Blocks.GREEN_WOOL ||
                block == Blocks.RED_WOOL || block == Blocks.BLACK_WOOL) {
            return 0.85;
        }

        if (block == Blocks.SPONGE || block == Blocks.WET_SPONGE) {
            return 0.80;
        }

        return 0.20;
    }

    public static double getDensityMultiplier(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.WHITE_WOOL || block == Blocks.ORANGE_WOOL ||
                block == Blocks.MAGENTA_WOOL || block == Blocks.LIGHT_BLUE_WOOL ||
                block == Blocks.YELLOW_WOOL || block == Blocks.LIME_WOOL ||
                block == Blocks.PINK_WOOL || block == Blocks.GRAY_WOOL ||
                block == Blocks.LIGHT_GRAY_WOOL || block == Blocks.CYAN_WOOL ||
                block == Blocks.PURPLE_WOOL || block == Blocks.BLUE_WOOL ||
                block == Blocks.BROWN_WOOL || block == Blocks.GREEN_WOOL ||
                block == Blocks.RED_WOOL || block == Blocks.BLACK_WOOL) {
            return 0.15;
        }

        return 1.0;
    }

    public static Vec3 getScatteredDirection(Vec3 incident, Vec3 normal, double roughness) {
        Vec3 reflection = pseudoReflect(incident, normal);

        if (roughness > 0.0) {
            double randomAngle = roughness * Math.PI * 0.5 * (Math.random() - 0.5);
            double randomAzimuth = 2.0 * Math.PI * Math.random();

            double sinTheta = Math.sin(randomAngle);
            double cosTheta = Math.cos(randomAngle);

            Vec3 tangent = normal.cross(new Vec3(1, 0, 0));
            if (tangent.lengthSqr() < 0.001) {
                tangent = normal.cross(new Vec3(0, 0, 1));
            }
            tangent = tangent.normalize();

            Vec3 bitangent = normal.cross(tangent).normalize();

            Vec3 scatter = new Vec3(
                    sinTheta * Math.cos(randomAzimuth),
                    sinTheta * Math.sin(randomAzimuth),
                    cosTheta
            );

            Vec3 rotated = tangent.scale(scatter.x)
                    .add(bitangent.scale(scatter.y))
                    .add(normal.scale(scatter.z));

            return rotated.normalize();
        }

        return reflection;
    }

    private static Vec3 pseudoReflect(Vec3 ray, Vec3 normal) {
        return ray.subtract(normal.scale(2.0 * ray.dot(normal)));
    }
}