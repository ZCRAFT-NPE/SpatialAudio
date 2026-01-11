package dev.thedocruby.resounding.config.presets;

import dev.thedocruby.resounding.Engine;
import dev.thedocruby.resounding.config.PrecomputedConfig;
import dev.thedocruby.resounding.config.ResoundingConfig;
import dev.thedocruby.resounding.toolbox.MaterialData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ConfigChanger {
    private ConfigChanger() {}

    public static void changeConfig(ResoundingConfig config, @Nullable Boolean enabled,
                                    @Nullable Double attenuationFactor, @Nullable Double globalReverbGain, @Nullable Double globalReverbBrightness, @Nullable Double globalBlockAbsorption, @Nullable Double globalBlockReflectance, @Nullable Integer soundSimulationDistance, @Nullable Double airAbsorption, @Nullable Double humidityAbsorption, @Nullable Double rainAbsorption, @Nullable Double underwaterFilter,
                                    @Nullable Boolean skipRainOcclusionTracing, @Nullable Integer environmentEvaluationRays, @Nullable Integer environmentEvaluationRayBounces, @Nullable Boolean simplerSharedAirspaceSimulation,
                                    @Nullable Map<String, MaterialData> materialProperties,
                                    @Nullable Integer continuousRefreshRate, @Nullable Double maxDirectOcclusionFromBlocks, @Nullable Boolean _9RayDirectOcclusion, @Nullable Boolean soundDirectionEvaluation, @Nullable Double directRaysDirEvalMultiplier, @Nullable Boolean notOccludedNoRedirect
    ) {
        if (enabled != null) config.enabled = enabled;

        if(Engine.env == EnvType.SERVER) return;

        setMaterialProperties(config.materials, materialProperties);
        config.preset = ConfigPresets.LOAD_SUCCESS;
    }

    @Environment(EnvType.CLIENT)
    public static void setMaterialProperties(ResoundingConfig.Materials materials, @Nullable Map<String, MaterialData> materialProperties) {
        if (materials.materialProperties == null || materials.materialProperties.isEmpty()) {
            materials.materialProperties = PrecomputedConfig.materialDefaults();
        }

        if (materialProperties != null) {
            for (Map.Entry<String, MaterialData> entry : materialProperties.entrySet()) {
                String key = entry.getKey();
                MaterialData newData = entry.getValue();
                MaterialData existing = materials.materialProperties.get(key);

                if (existing == null) {
                    materials.materialProperties.put(key, new MaterialData(
                            key,
                            newData.reflectivity == -1 ? 0.5 : newData.reflectivity,
                            newData.absorption == -1 ? 0.5 : newData.absorption
                    ));
                } else {
                    materials.materialProperties.put(key, new MaterialData(
                            existing.example == null ? key : existing.example,
                            newData.reflectivity == -1 ? existing.reflectivity : newData.reflectivity,
                            newData.absorption == -1 ? existing.absorption : newData.absorption
                    ));
                }
            }
        }
    }
}