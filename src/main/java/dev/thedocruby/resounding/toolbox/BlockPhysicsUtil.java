package dev.thedocruby.resounding.toolbox;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class BlockPhysicsUtil {

    private static final Map<String, MaterialProfile> MATERIAL_PROFILES = new HashMap<>();

    static {
        initializeMaterialProfiles();
    }

    private static void initializeMaterialProfiles() {
        MATERIAL_PROFILES.put("minecraft:stone", new MaterialProfile(0.012, 0.988, 0.082, 0.021, "stone", 2750));
        MATERIAL_PROFILES.put("minecraft:granite", new MaterialProfile(0.015, 0.985, 0.092, 0.028, "stone", 2720));
        MATERIAL_PROFILES.put("minecraft:diorite", new MaterialProfile(0.013, 0.987, 0.085, 0.024, "stone", 2680));
        MATERIAL_PROFILES.put("minecraft:andesite", new MaterialProfile(0.014, 0.986, 0.089, 0.026, "stone", 2700));
        MATERIAL_PROFILES.put("minecraft:cobblestone", new MaterialProfile(0.025, 0.975, 0.125, 0.048, "stone", 2450));
        MATERIAL_PROFILES.put("minecraft:mossy_cobblestone", new MaterialProfile(0.032, 0.968, 0.142, 0.062, "stone", 2300));

        MATERIAL_PROFILES.put("minecraft:oak_planks", new MaterialProfile(0.085, 0.915, 0.152, 0.096, "wood", 620));
        MATERIAL_PROFILES.put("minecraft:spruce_planks", new MaterialProfile(0.088, 0.912, 0.162, 0.102, "wood", 580));
        MATERIAL_PROFILES.put("minecraft:birch_planks", new MaterialProfile(0.082, 0.918, 0.148, 0.094, "wood", 590));
        MATERIAL_PROFILES.put("minecraft:jungle_planks", new MaterialProfile(0.084, 0.916, 0.155, 0.099, "wood", 600));
        MATERIAL_PROFILES.put("minecraft:acacia_planks", new MaterialProfile(0.089, 0.911, 0.165, 0.104, "wood", 610));
        MATERIAL_PROFILES.put("minecraft:dark_oak_planks", new MaterialProfile(0.092, 0.908, 0.172, 0.108, "wood", 630));
        MATERIAL_PROFILES.put("minecraft:oak_log", new MaterialProfile(0.078, 0.922, 0.142, 0.085, "wood", 680));

        MATERIAL_PROFILES.put("minecraft:glass", new MaterialProfile(0.028, 0.972, 0.035, 0.018, "glass", 2550));
        MATERIAL_PROFILES.put("minecraft:glass_pane", new MaterialProfile(0.030, 0.970, 0.038, 0.022, "glass", 2500));
        MATERIAL_PROFILES.put("minecraft:tinted_glass", new MaterialProfile(0.032, 0.968, 0.041, 0.025, "glass", 2600));

        MATERIAL_PROFILES.put("minecraft:iron_block", new MaterialProfile(0.008, 0.992, 0.015, 0.008, "metal", 7874));
        MATERIAL_PROFILES.put("minecraft:gold_block", new MaterialProfile(0.006, 0.994, 0.012, 0.006, "metal", 19320));
        MATERIAL_PROFILES.put("minecraft:copper_block", new MaterialProfile(0.010, 0.990, 0.018, 0.010, "metal", 8960));
        MATERIAL_PROFILES.put("minecraft:diamond_block", new MaterialProfile(0.007, 0.993, 0.014, 0.007, "metal", 3510));

        MATERIAL_PROFILES.put("minecraft:white_wool", new MaterialProfile(0.625, 0.375, 0.825, 0.715, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:orange_wool", new MaterialProfile(0.630, 0.370, 0.820, 0.710, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:magenta_wool", new MaterialProfile(0.618, 0.382, 0.830, 0.720, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:light_blue_wool", new MaterialProfile(0.635, 0.365, 0.815, 0.705, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:yellow_wool", new MaterialProfile(0.640, 0.360, 0.810, 0.700, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:lime_wool", new MaterialProfile(0.628, 0.372, 0.822, 0.712, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:pink_wool", new MaterialProfile(0.615, 0.385, 0.835, 0.725, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:gray_wool", new MaterialProfile(0.605, 0.395, 0.845, 0.735, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:light_gray_wool", new MaterialProfile(0.620, 0.380, 0.828, 0.718, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:cyan_wool", new MaterialProfile(0.622, 0.378, 0.826, 0.716, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:purple_wool", new MaterialProfile(0.628, 0.372, 0.822, 0.712, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:blue_wool", new MaterialProfile(0.632, 0.368, 0.818, 0.708, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:brown_wool", new MaterialProfile(0.610, 0.390, 0.840, 0.730, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:green_wool", new MaterialProfile(0.618, 0.382, 0.832, 0.722, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:red_wool", new MaterialProfile(0.625, 0.375, 0.825, 0.715, "wool", 185));
        MATERIAL_PROFILES.put("minecraft:black_wool", new MaterialProfile(0.600, 0.400, 0.850, 0.740, "wool", 185));

        MATERIAL_PROFILES.put("minecraft:water", new MaterialProfile(0.218, 0.782, 0.285, 0.142, "liquid", 998));
        MATERIAL_PROFILES.put("minecraft:lava", new MaterialProfile(0.385, 0.615, 0.452, 0.262, "liquid", 2810));

        MATERIAL_PROFILES.put("minecraft:air", new MaterialProfile(0.0012, 0.9988, 0.0015, 0.0008, "air", 1.225));
        MATERIAL_PROFILES.put("minecraft:cave_air", new MaterialProfile(0.0015, 0.9985, 0.0018, 0.0010, "air", 1.250));
        MATERIAL_PROFILES.put("minecraft:void_air", new MaterialProfile(0.0000, 1.0000, 0.0000, 0.0000, "air", 0.001));

        MATERIAL_PROFILES.put("minecraft:ice", new MaterialProfile(0.035, 0.965, 0.055, 0.032, "ice", 917));
        MATERIAL_PROFILES.put("minecraft:packed_ice", new MaterialProfile(0.030, 0.970, 0.048, 0.028, "ice", 950));
        MATERIAL_PROFILES.put("minecraft:blue_ice", new MaterialProfile(0.025, 0.975, 0.042, 0.025, "ice", 970));

        MATERIAL_PROFILES.put("minecraft:obsidian", new MaterialProfile(0.011, 0.989, 0.028, 0.015, "stone", 2980));
        MATERIAL_PROFILES.put("minecraft:bedrock", new MaterialProfile(0.005, 0.995, 0.012, 0.006, "stone", 3100));
        MATERIAL_PROFILES.put("minecraft:bricks", new MaterialProfile(0.018, 0.982, 0.065, 0.032, "stone", 2100));
        MATERIAL_PROFILES.put("minecraft:clay", new MaterialProfile(0.022, 0.978, 0.075, 0.041, "stone", 1850));
        MATERIAL_PROFILES.put("minecraft:terracotta", new MaterialProfile(0.020, 0.980, 0.072, 0.038, "stone", 1900));

        MATERIAL_PROFILES.put("minecraft:sand", new MaterialProfile(0.095, 0.905, 0.182, 0.115, "sand", 1600));
        MATERIAL_PROFILES.put("minecraft:red_sand", new MaterialProfile(0.098, 0.902, 0.188, 0.120, "sand", 1620));
        MATERIAL_PROFILES.put("minecraft:gravel", new MaterialProfile(0.082, 0.918, 0.165, 0.098, "sand", 1800));
        MATERIAL_PROFILES.put("minecraft:dirt", new MaterialProfile(0.075, 0.925, 0.152, 0.092, "soil", 1450));
        MATERIAL_PROFILES.put("minecraft:grass_block", new MaterialProfile(0.068, 0.932, 0.142, 0.085, "soil", 1380));
        MATERIAL_PROFILES.put("minecraft:mycelium", new MaterialProfile(0.072, 0.928, 0.148, 0.089, "soil", 1400));
        MATERIAL_PROFILES.put("minecraft:podzol", new MaterialProfile(0.070, 0.930, 0.145, 0.087, "soil", 1420));

        MATERIAL_PROFILES.put("minecraft:netherrack", new MaterialProfile(0.038, 0.962, 0.112, 0.065, "stone", 2450));
        MATERIAL_PROFILES.put("minecraft:soul_sand", new MaterialProfile(0.125, 0.875, 0.215, 0.152, "sand", 1750));
        MATERIAL_PROFILES.put("minecraft:soul_soil", new MaterialProfile(0.118, 0.882, 0.202, 0.142, "soil", 1600));
        MATERIAL_PROFILES.put("minecraft:basalt", new MaterialProfile(0.016, 0.984, 0.085, 0.035, "stone", 2850));
        MATERIAL_PROFILES.put("minecraft:polished_basalt", new MaterialProfile(0.014, 0.986, 0.075, 0.028, "stone", 2850));
        MATERIAL_PROFILES.put("minecraft:blackstone", new MaterialProfile(0.017, 0.983, 0.088, 0.038, "stone", 2800));

        MATERIAL_PROFILES.put("minecraft:glowstone", new MaterialProfile(0.032, 0.968, 0.085, 0.045, "glass", 2650));
        MATERIAL_PROFILES.put("minecraft:sea_lantern", new MaterialProfile(0.030, 0.970, 0.082, 0.042, "glass", 2600));
        MATERIAL_PROFILES.put("minecraft:redstone_lamp", new MaterialProfile(0.028, 0.972, 0.078, 0.040, "glass", 2550));

        MATERIAL_PROFILES.put("minecraft:sponge", new MaterialProfile(0.785, 0.215, 0.852, 0.805, "porous", 380));
        MATERIAL_PROFILES.put("minecraft:wet_sponge", new MaterialProfile(0.815, 0.185, 0.872, 0.825, "porous", 820));

        MATERIAL_PROFILES.put("minecraft:hay_block", new MaterialProfile(0.682, 0.318, 0.785, 0.725, "porous", 420));
        MATERIAL_PROFILES.put("minecraft:dried_kelp_block", new MaterialProfile(0.695, 0.305, 0.795, 0.735, "porous", 450));

        MATERIAL_PROFILES.put("minecraft:slime_block", new MaterialProfile(0.185, 0.815, 0.255, 0.192, "elastic", 1150));
        MATERIAL_PROFILES.put("minecraft:honey_block", new MaterialProfile(0.212, 0.788, 0.282, 0.218, "viscous", 1420));

        MATERIAL_PROFILES.put("minecraft:magma_block", new MaterialProfile(0.042, 0.958, 0.118, 0.072, "stone", 2850));
        MATERIAL_PROFILES.put("minecraft:nether_wart_block", new MaterialProfile(0.625, 0.375, 0.725, 0.665, "organic", 680));
        MATERIAL_PROFILES.put("minecraft:warped_wart_block", new MaterialProfile(0.618, 0.382, 0.718, 0.658, "organic", 670));

        MATERIAL_PROFILES.put("minecraft:bone_block", new MaterialProfile(0.055, 0.945, 0.132, 0.078, "bone", 1850));
        MATERIAL_PROFILES.put("minecraft:netherite_block", new MaterialProfile(0.004, 0.996, 0.010, 0.005, "metal", 8750));
    }

    private record MaterialProfile(double absorption, double reflectivity, double scattering, double roughness,
                                   String materialType, double density) {
            private MaterialProfile(double absorption, double reflectivity, double scattering, double roughness, String materialType, double density) {
                this.absorption = Math.max(0.0, Math.min(1.0, absorption));
                this.reflectivity = Math.max(0.0, Math.min(1.0, reflectivity));
                this.scattering = Math.max(0.0, Math.min(1.0, scattering));
                this.roughness = Math.max(0.0, Math.min(1.0, roughness));
                this.materialType = materialType;
                this.density = Math.max(0.1, density);
            }
        }

    public static double getAbsorptionCoefficient(BlockState state) {
        String blockId = state.getBlock().getDescriptionId();
        MaterialProfile profile = MATERIAL_PROFILES.get(blockId);
        if (profile != null) {
            return profile.absorption;
        }

        Block block = state.getBlock();
        if (block.defaultBlockState().isAir()) {
            return 0.0012;
        }

        String materialType = getMaterialType(state);
        return switch (materialType) {
            case "glass" -> 0.030;
            case "metal" -> 0.009;
            case "wool" -> 0.620;
            case "wood", "sand" -> 0.085;
            case "stone" -> 0.015;
            case "soil" -> 0.072;
            case "porous" -> 0.750;
            case "elastic" -> 0.185;
            case "viscous" -> 0.212;
            case "organic" -> 0.622;
            case "bone" -> 0.055;
            case "liquid" -> 0.218;
            default -> 0.025;
        };
    }

    public static double getReflectivityCoefficient(BlockState state) {
        String blockId = state.getBlock().getDescriptionId();
        MaterialProfile profile = MATERIAL_PROFILES.get(blockId);
        if (profile != null) {
            return profile.reflectivity;
        }

        Block block = state.getBlock();
        if (block.defaultBlockState().isAir()) {
            return 0.9988;
        }

        String materialType = getMaterialType(state);
        return switch (materialType) {
            case "glass" -> 0.970;
            case "metal" -> 0.991;
            case "wool" -> 0.380;
            case "wood", "sand" -> 0.915;
            case "stone" -> 0.985;
            case "soil" -> 0.928;
            case "porous" -> 0.250;
            case "elastic" -> 0.815;
            case "viscous" -> 0.788;
            case "organic" -> 0.378;
            case "bone" -> 0.945;
            case "liquid" -> 0.782;
            default -> 0.975;
        };
    }

    public static double getScatteringCoefficient(BlockState state) {
        String blockId = state.getBlock().getDescriptionId();
        MaterialProfile profile = MATERIAL_PROFILES.get(blockId);
        if (profile != null) {
            return profile.scattering;
        }

        Block block = state.getBlock();
        if (block.defaultBlockState().isAir()) {
            return 0.0015;
        }

        String materialType = getMaterialType(state);
        return switch (materialType) {
            case "glass" -> 0.038;
            case "metal" -> 0.015;
            case "wool" -> 0.825;
            case "wood" -> 0.152;
            case "sand" -> 0.182;
            case "soil" -> 0.148;
            case "porous" -> 0.850;
            case "elastic" -> 0.255;
            case "viscous" -> 0.282;
            case "organic" -> 0.718;
            case "bone" -> 0.132;
            case "liquid" -> 0.285;
            default -> 0.085;
        };
    }

    public static double getRoughnessCoefficient(BlockState state) {
        String blockId = state.getBlock().getDescriptionId();
        MaterialProfile profile = MATERIAL_PROFILES.get(blockId);
        if (profile != null) {
            return profile.roughness;
        }

        Block block = state.getBlock();
        if (block.defaultBlockState().isAir()) {
            return 0.0008;
        }

        String materialType = getMaterialType(state);
        return switch (materialType) {
            case "glass" -> 0.022;
            case "metal" -> 0.008;
            case "wool" -> 0.715;
            case "wood" -> 0.096;
            case "stone" -> 0.028;
            case "sand" -> 0.115;
            case "soil" -> 0.089;
            case "porous" -> 0.805;
            case "elastic" -> 0.192;
            case "viscous" -> 0.218;
            case "organic" -> 0.658;
            case "bone" -> 0.078;
            case "liquid" -> 0.142;
            default -> 0.032;
        };
    }

    public static double getDensityMultiplier(BlockState state) {
        String blockId = state.getBlock().getDescriptionId();
        MaterialProfile profile = MATERIAL_PROFILES.get(blockId);
        if (profile != null) {
            return profile.density / 1000.0;
        }

        Block block = state.getBlock();
        if (block.defaultBlockState().isAir()) {
            return 0.001225;
        }

        String materialType = getMaterialType(state);
        return switch (materialType) {
            case "glass" -> 2.550;
            case "metal" -> 7.874;
            case "wool" -> 0.185;
            case "wood" -> 0.620;
            case "stone" -> 2.750;
            case "sand" -> 1.600;
            case "soil" -> 1.450;
            case "porous" -> 0.380;
            case "elastic" -> 1.150;
            case "viscous" -> 1.420;
            case "organic" -> 0.680;
            case "bone" -> 1.850;
            case "liquid" -> 0.998;
            default -> 2.000;
        };
    }

    public static String getMaterialType(BlockState state) {
        String blockId = state.getBlock().getDescriptionId();
        MaterialProfile profile = MATERIAL_PROFILES.get(blockId);
        if (profile != null) {
            return profile.materialType;
        }

        Block block = state.getBlock();
        if (block == Blocks.GLASS || block == Blocks.GLASS_PANE || block == Blocks.TINTED_GLASS ||
                block == Blocks.GLOWSTONE || block == Blocks.SEA_LANTERN || block == Blocks.REDSTONE_LAMP) {
            return "glass";
        }
        if (block == Blocks.IRON_BLOCK || block == Blocks.GOLD_BLOCK || block == Blocks.COPPER_BLOCK ||
                block == Blocks.DIAMOND_BLOCK || block == Blocks.NETHERITE_BLOCK) {
            return "metal";
        }
        if (block == Blocks.WHITE_WOOL || block == Blocks.ORANGE_WOOL || block == Blocks.MAGENTA_WOOL ||
                block == Blocks.LIGHT_BLUE_WOOL || block == Blocks.YELLOW_WOOL || block == Blocks.LIME_WOOL ||
                block == Blocks.PINK_WOOL || block == Blocks.GRAY_WOOL || block == Blocks.LIGHT_GRAY_WOOL ||
                block == Blocks.CYAN_WOOL || block == Blocks.PURPLE_WOOL || block == Blocks.BLUE_WOOL ||
                block == Blocks.BROWN_WOOL || block == Blocks.GREEN_WOOL || block == Blocks.RED_WOOL ||
                block == Blocks.BLACK_WOOL) {
            return "wool";
        }
        if (block == Blocks.OAK_PLANKS || block == Blocks.SPRUCE_PLANKS || block == Blocks.BIRCH_PLANKS ||
                block == Blocks.JUNGLE_PLANKS || block == Blocks.ACACIA_PLANKS || block == Blocks.DARK_OAK_PLANKS ||
                block == Blocks.OAK_LOG || block == Blocks.SPRUCE_LOG || block == Blocks.BIRCH_LOG ||
                block == Blocks.JUNGLE_LOG || block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG) {
            return "wood";
        }
        if (block == Blocks.STONE || block == Blocks.GRANITE || block == Blocks.DIORITE || block == Blocks.ANDESITE ||
                block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE || block == Blocks.STONE_BRICKS ||
                block == Blocks.MOSSY_STONE_BRICKS || block == Blocks.BRICKS || block == Blocks.OBSIDIAN ||
                block == Blocks.BEDROCK || block == Blocks.NETHERRACK || block == Blocks.BASALT ||
                block == Blocks.POLISHED_BASALT || block == Blocks.BLACKSTONE) {
            return "stone";
        }
        if (block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.GRAVEL || block == Blocks.SOUL_SAND) {
            return "sand";
        }
        if (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL ||
                block == Blocks.SOUL_SOIL) {
            return "soil";
        }
        if (block == Blocks.SPONGE || block == Blocks.WET_SPONGE || block == Blocks.HAY_BLOCK ||
                block == Blocks.DRIED_KELP_BLOCK) {
            return "porous";
        }
        if (block == Blocks.SLIME_BLOCK) {
            return "elastic";
        }
        if (block == Blocks.HONEY_BLOCK) {
            return "viscous";
        }
        if (block == Blocks.NETHER_WART_BLOCK || block == Blocks.WARPED_WART_BLOCK) {
            return "organic";
        }
        if (block == Blocks.BONE_BLOCK) {
            return "bone";
        }
        if (block == Blocks.WATER || block == Blocks.LAVA) {
            return "liquid";
        }
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) {
            return "ice";
        }
        return "stone";
    }

    public static double getSpeedOfSoundMultiplier(BlockState state) {
        double density = getDensityMultiplier(state);
        double elasticity = getReflectivityCoefficient(state);
        double poissonRatio = 0.25;
        double youngsModulus = elasticity * 200e9;

        double longitudinalWaveSpeed = Math.sqrt(youngsModulus * (1 - poissonRatio) /
                (density * 1000 * (1 + poissonRatio) * (1 - 2 * poissonRatio)));

        double baseSpeed = 343.0;
        return Math.min(5.0, Math.max(0.2, longitudinalWaveSpeed / baseSpeed));
    }

    public static double getFrequencyResponse(BlockState state, double frequency) {
        double absorption = getAbsorptionCoefficient(state);
        double scattering = getScatteringCoefficient(state);
        double roughness = getRoughnessCoefficient(state);

        double frequencyNormalized = frequency / 1000.0;
        double alpha = 0.05;

        double absorptionResponse = 1.0 - absorption * (1.0 - Math.exp(-alpha * frequencyNormalized));
        double scatteringResponse = 1.0 - scattering * (1.0 - Math.exp(-0.1 * frequencyNormalized));
        double roughnessResponse = 1.0 - roughness * (1.0 - Math.exp(-0.08 * frequencyNormalized));

        double combinedResponse = absorptionResponse * scatteringResponse * roughnessResponse;

        String materialType = getMaterialType(state);
        double materialFactor = switch (materialType) {
            case "glass" -> 1.15 - 0.25 * Math.min(1.0, frequencyNormalized * 0.5);
            case "metal" -> 1.12 - 0.18 * Math.min(1.0, frequencyNormalized * 0.4);
            case "wool" -> 0.82 - 0.35 * Math.min(1.0, frequencyNormalized * 0.8);
            case "wood" -> 0.95 - 0.22 * Math.min(1.0, frequencyNormalized * 0.6);
            case "porous" -> 0.78 - 0.42 * Math.min(1.0, frequencyNormalized * 0.9);
            default -> 1.0 - 0.15 * Math.min(1.0, frequencyNormalized * 0.3);
        };

        return Math.max(0.2, Math.min(1.5, combinedResponse * materialFactor));
    }

    public static Vec3 getScatteredDirection(Vec3 incident, Vec3 normal, double roughness) {
        double theta = Math.acos(Math.abs(incident.dot(normal)));

        double sigma = roughness * 0.5;
        double gaussianRandom = Math.sqrt(-2.0 * Math.log(Math.random())) * Math.cos(2.0 * Math.PI * Math.random());

        double deltaTheta = sigma * gaussianRandom;
        double deltaPhi = 2.0 * Math.PI * Math.random();

        double newTheta = Math.max(0.0, Math.min(Math.PI / 2.0, theta + deltaTheta));

        Vec3 tangent = normal.cross(new Vec3(1, 0, 0));
        if (tangent.lengthSqr() < 0.001) {
            tangent = normal.cross(new Vec3(0, 0, 1));
        }
        tangent = tangent.normalize();

        Vec3 bitangent = normal.cross(tangent).normalize();

        Vec3 scattered = new Vec3(
                Math.sin(newTheta) * Math.cos(deltaPhi),
                Math.sin(newTheta) * Math.sin(deltaPhi),
                Math.cos(newTheta)
        );

        Vec3 rotated = tangent.scale(scattered.x)
                .add(bitangent.scale(scattered.y))
                .add(normal.scale(scattered.z));

        return rotated.normalize();
    }

    public static double getAcousticImpedance(BlockState state) {
        double density = getDensityMultiplier(state) * 1000.0;
        double speedOfSound = 343.0 * getSpeedOfSoundMultiplier(state);
        return density * speedOfSound;
    }

    public static double getTransmissionCoefficient(BlockState state1, BlockState state2) {
        double z1 = getAcousticImpedance(state1);
        double z2 = getAcousticImpedance(state2);

        if (z1 + z2 < 1e-10) return 0.0;

        return 4.0 * z1 * z2 / Math.pow(z1 + z2, 2);
    }

    public static double getReflectionCoefficient(BlockState state1, BlockState state2) {
        double z1 = getAcousticImpedance(state1);
        double z2 = getAcousticImpedance(state2);

        if (z1 + z2 < 1e-10) return 1.0;

        return Math.pow((z2 - z1) / (z2 + z1), 2);
    }
}