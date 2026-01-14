package me.zcraft.mods.spatialaudio.config;

import me.zcraft.mods.spatialaudio.Engine;
import me.zcraft.mods.spatialaudio.toolbox.MaterialData;


import net.minecraft.util.Mth;

import java.util.*;

import static me.zcraft.mods.spatialaudio.Engine.nameToGroup;

public class PrecomputedConfig {
   
    public static final float globalVolumeMultiplier = 4f;
   
    public static final double speedOfSound = 343.3;
    public static final double minEnergy = Math.exp(-9.21);

   
    private static final Map<String, MaterialData> MATERIAL_DEFAULTS = createMaterialDefaults();

   
    public boolean enableEcho = true;

   
    public float echoThreshold = 0.3f;

   
    public int maxEchoSlots = 2;

   
    public int maxSoundSources = 64;

   
    public static Map<String, MaterialData> materialDefaults() {
        return MATERIAL_DEFAULTS;
    }

    private static Map<String, MaterialData> createMaterialDefaults() {
        Map<String, MaterialData> map = new LinkedHashMap<>(40);
        map.put("Coral",              new MaterialData(null, 0.350, 0.250));
        map.put("Gravel, Dirt",       new MaterialData(null, 0.500, 0.650));
        map.put("Amethyst",           new MaterialData(null, 0.850, 0.400));
        map.put("Sand",               new MaterialData(null, 0.400, 0.600));
        map.put("Candle Wax",         new MaterialData(null, 0.350, 0.400));
        map.put("Weeping Vines",      new MaterialData(null, 0.300, 0.300));
        map.put("Soul Sand",          new MaterialData(null, 0.050, 0.850));
        map.put("Soul Soil",          new MaterialData(null, 0.100, 0.900));
        map.put("Basalt",             new MaterialData(null, 0.800, 0.375));
        map.put("Netherrack",         new MaterialData(null, 0.750, 0.450));
        map.put("Nether Brick",       new MaterialData(null, 0.880, 0.400));
        map.put("Honey",              new MaterialData(null, 0.120, 0.350));
        map.put("Bone",               new MaterialData(null, 0.900, 0.300));
        map.put("Nether Wart",        new MaterialData(null, 0.200, 0.800));
        map.put("Grass, Foliage",     new MaterialData(null, 0.240, 0.240));
        map.put("Metal",              new MaterialData(null, 0.950, 0.400));
        map.put("Aquatic Foliage",    new MaterialData(null, 0.550, 0.650));
        map.put("Glass, Ice",         new MaterialData(null, 0.900, 0.320));
        map.put("Nether Foliage",     new MaterialData(null, 0.150, 0.500));
        map.put("Shroomlight",        new MaterialData(null, 0.850, 0.300));
        map.put("Chain",              new MaterialData(null, 0.800, 0.550));
        map.put("Deepslate",          new MaterialData(null, 0.940, 0.600));
        map.put("Wood",               new MaterialData(null, 0.675, 0.400));
        map.put("Deepslate Tiles",    new MaterialData(null, 0.975, 0.525));
        map.put("Stone, Blackstone",  new MaterialData(null, 0.900, 0.500));
        map.put("Slime",              new MaterialData(null, 0.880, 0.620));
        map.put("Polished Deepslate", new MaterialData(null, 0.975, 0.600));
        map.put("Snow",               new MaterialData(null, 0.250, 0.420));
        map.put("Azalea Leaves",      new MaterialData(null, 0.300, 0.350));
        map.put("Bamboo",             new MaterialData(null, 0.600, 0.300));
        map.put("Mushroom Stems",     new MaterialData(null, 0.600, 0.650));
        map.put("Wool",               new MaterialData(null, 0.025, 0.950));
        map.put("Dry Foliage",        new MaterialData(null, 0.250, 0.150));
        map.put("Azalea Bush",        new MaterialData(null, 0.300, 0.450));
        map.put("Lush Cave Foliage",  new MaterialData(null, 0.350, 0.250));
        map.put("Netherite",          new MaterialData(null, 0.995, 0.300));
        map.put("Ancient Debris",     new MaterialData(null, 0.450, 0.800));
        map.put("Nether Fungus Stem", new MaterialData(null, 0.300, 0.650));
        map.put("Powder Snow",        new MaterialData(null, 0.180, 0.100));
        map.put("Tuff",               new MaterialData(null, 0.750, 0.400));
        map.put("Moss",               new MaterialData(null, 0.200, 0.400));
        map.put("Nylium",             new MaterialData(null, 0.400, 0.500));
        map.put("Nether Mushroom",    new MaterialData(null, 0.250, 0.750));
        map.put("Lanterns",           new MaterialData(null, 0.750, 0.350));
        map.put("Dripstone",          new MaterialData(null, 0.850, 0.320));
        map.put("Sculk Sensor",       new MaterialData(null, 0.150, 0.850));
        map.put("DEFAULT",            new MaterialData(null, 0.500, 0.500));
        return Collections.unmodifiableMap(map);
    }

   
    public double maxDecayTime = 4.142;
    public static PrecomputedConfig pC = null;

    public boolean enabled;
    public int soundSimulationDistance;

   
    public double globalRvrbGain;
   
    public double energyFix;
   
    public int resolution;
   
    public double globalRvrbHFRcp;
   
    public double rvrbDiff;
   
    public double globalAbs;
   
    public double globalAbsHFRcp;
   
    public double globalRefl;
   
    public double globalReflRcp;
   
    public float humAbs;
   
    public float rainAbs;
   
    public double waterFilt;

   
    public boolean skipRainOccl;
   
    public int nRays;
   
    public double rcpNRays;
   
    public int nRayBounces;
   
    public double rcpAllRays;
   
    public double maxTraceDist;
   
    public boolean fastShared;
   
    public boolean fastPick;

   
    public Map<String, Double> reflMap;
   
    public double defaultRefl;
   
    public Map<String, Double> absMap;
   
    public double defaultAbs;
   
    public Set<String> blockWhiteSet;

   
    public boolean recordsDisable;
   
    public int srcRefrRate;
   
    public double maxBlckOccl;
   
    public boolean nineRay;
   
    public boolean dirEval;
   
    public double dirEvalBias;
   
    public boolean notOcclRedir;

   
    public boolean dLog;
   
    public boolean oLog;
   
    public boolean eLog;
   
    public boolean pLog;
   
    public boolean dRays;

    private boolean active = true;

    public PrecomputedConfig(ResoundingConfig c) throws CloneNotSupportedException {
        if (pC != null && pC.active) throw new CloneNotSupportedException("Tried creating second instance of precomputedConfig");
        enabled = c.enabled;

        //if(Engine.env == EnvType.CLIENT) {
            long startTime = System.nanoTime();

            enableEcho = c.effects.enableEcho;
            echoThreshold = c.effects.echoThreshold;
            maxEchoSlots = c.effects.maxEchoSlots;
            maxSoundSources = c.quality.maxSoundSources;
            globalRvrbGain = Mth.clamp(c.general.globalReverbGain/100d, 0.0d, 1.0d);
            energyFix = 1 / Math.max(c.general.globalReverbStrength, Double.MIN_NORMAL);
            resolution = c.quality.reverbResolution;
            globalRvrbHFRcp = 1 / Math.max(c.general.globalReverbBrightness, Double.MIN_NORMAL);
            rvrbDiff = Mth.clamp(c.general.globalReverbSmoothness, 0.0, 1.0);
            globalAbs = c.general.globalBlockAbsorption;
            globalAbsHFRcp = 1 / Math.max(c.general.globalAbsorptionBrightness, Double.MIN_NORMAL);
            globalRefl = c.general.globalBlockReflectance;
            globalReflRcp = 1 / globalRefl;
            waterFilt = 1 - Mth.clamp(c.effects.underwaterFilter, 0.0, 1.0);
            soundSimulationDistance = c.quality.soundSimulationDistance;

            skipRainOccl = c.misc.skipRainOcclusionTracing;
            nRays = c.quality.envEvalRays;
            rcpNRays = 1d / nRays;
            nRayBounces = c.quality.envEvalRayBounces + resolution;
            rcpAllRays = rcpNRays / nRayBounces;
            maxTraceDist = Mth.clamp(c.quality.rayLength, 1.0, 16.0) * nRayBounces * 16 * Math.sqrt(2);
            //occlMode = c.quality.occlusionMode;
           // fastShared = c.quality.sharedAirspaceMode == SharedAirspaceMode.FAST;
            fastPick = true;

            defaultRefl = c.materials.materialProperties.get("DEFAULT").reflectivity;
            defaultAbs = c.materials.materialProperties.get("DEFAULT").absorption;
            blockWhiteSet = new HashSet<>(c.materials.blockWhiteList);

            Map<String, MaterialData> matProp = new HashMap<>(c.materials.materialProperties);

            List<String> wrong = new ArrayList<>();
            List<String> toRemove = new ArrayList<>();

            reflMap = new HashMap<>(matProp.size());
            absMap = new HashMap<>(matProp.size());

            for (Map.Entry<String, MaterialData> entry : matProp.entrySet()) {
                String key = entry.getKey();
                MaterialData value = entry.getValue();

                if (nameToGroup.containsKey(key) || blockWhiteSet.contains(key)) {
                    reflMap.put(key, Math.pow(value.reflectivity, globalReflRcp));
                    absMap.put(key, value.absorption);
                } else if (!"DEFAULT".equals(key)) {
                    wrong.add(key + " (" + value.example + ")");
                    toRemove.add(key);
                }
            }

            if (!wrong.isEmpty()) {
                Engine.LOGGER.error("Material Data map contains {} extra entries:\n{}\nPatching Material Data...", wrong.size(), wrong);
                for (String key : toRemove) {
                    matProp.remove(key);
                }
            }

            c.materials.materialProperties = matProp;

            recordsDisable = c.misc.recordsDisable;
            srcRefrRate = Math.max(c.quality.sourceRefreshRate, 1);
            maxBlckOccl = c.quality.maxBlockOcclusion;
            nineRay = c.quality.nineRayBlockOcclusion;
            dirEval = c.effects.soundDirectionEvaluation;
            dirEvalBias = Math.pow(c.effects.directRaysDirEvalMultiplier, 10.66);
            notOcclRedir = !c.misc.notOccludedNoRedirect;

            dLog = c.debug.debugLogging;
            oLog = c.debug.occlusionLogging;
            eLog = c.debug.environmentLogging;
            pLog = c.debug.performanceLogging;
            dRays = c.debug.raytraceParticles;

            if (pLog) {
                long endTime = System.nanoTime();
                Engine.LOGGER.info("Config preprocessing took {}ms", (endTime - startTime) / 1_000_000.0);
            }
        //} else {
          //  soundSimulationDistance = c.server.soundSimulationDistance;
        //}
    }

    public void deactivate(){ active = false;}
}