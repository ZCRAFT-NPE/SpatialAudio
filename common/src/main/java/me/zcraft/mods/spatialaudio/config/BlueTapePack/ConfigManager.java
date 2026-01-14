package me.zcraft.mods.spatialaudio.config.BlueTapePack;

import me.zcraft.mods.spatialaudio.Engine;
import me.zcraft.mods.spatialaudio.config.PrecomputedConfig;
import me.zcraft.mods.spatialaudio.config.ResoundingConfig;
import me.zcraft.mods.spatialaudio.config.presets.ConfigPresets;
import me.zcraft.mods.spatialaudio.toolbox.MaterialData;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ConfigManager {

    private ConfigManager() {}

    private static ConfigHolder<ResoundingConfig> holder;

    public static boolean resetOnReload;

    public static final String configVersion = "1.0.0-bc.8";

    private static volatile ResoundingConfig DEFAULT_CONFIG = null;

   
    public static ResoundingConfig getDefaultConfig() {
        if (DEFAULT_CONFIG == null) {
            synchronized (ConfigManager.class) {
                if (DEFAULT_CONFIG == null) {
                    DEFAULT_CONFIG = createDefaultConfig();
                }
            }
        }
        return DEFAULT_CONFIG;
    }

   
    private static ResoundingConfig createDefaultConfig() {
        ResoundingConfig config = new ResoundingConfig();
        Map<String, MaterialData> map = new HashMap<>();

        for (String key : Engine.nameToGroup.keySet()) {
            map.put(key, new MaterialData(key, 0.5, 0.5));
        }
        map.putIfAbsent("DEFAULT", new MaterialData("DEFAULT", 0.5, 0.5));

        config.materials.materialProperties = map;
        return config;
    }

    public static void registerAutoConfig() {
        if (holder != null) {throw new IllegalStateException("Configuration already registered");}
        holder = AutoConfig.register(ResoundingConfig.class, JanksonConfigSerializer::new);

      //  if (Engine.env == EnvType.CLIENT) {
            try {
                GuiRegistryinit.register();
            } catch (Throwable ignored) {
                Engine.LOGGER.error("Failed to register config menu unwrappers. Edit config that isn't working in the config file");
            }
     //   }

        holder.registerSaveListener((holder, config) -> onSave(config));
        holder.registerLoadListener((holder, config) -> onSave(config));
        reload(true);
    }

    public static ResoundingConfig getConfig() {
        if (holder == null) {
            return new ResoundingConfig();
        }
        return holder.getConfig();
    }

    public static void reload(boolean load) {
        if (holder == null) {return;}

        if(load) holder.load();
        holder.getConfig().preset.setConfig();
        holder.save();
    }

    public static void save() {
        if (holder == null) {
            registerAutoConfig();
        } else {
            holder.save();
        }
    }

   
    public static void handleBrokenMaterials(@NotNull ResoundingConfig c) {
        Engine.LOGGER.error("Critical materialProperties error. Resetting materialProperties");
        c.materials.materialProperties = PrecomputedConfig.materialDefaults();
        c.materials.blockWhiteList = Collections.emptyList();
    }

    public static void resetToDefault() {
        holder.resetToDefault();
        reload(false);
    }

    public static void handleUnstableConfig() {
        Engine.LOGGER.error("Error: Config file is not from a compatible version! Resetting the config...");
        resetOnReload = true;
    }

    public static InteractionResult onSave(ResoundingConfig c) {
        //if (Engine.env == EnvType.CLIENT) {
            if (c.materials.materialProperties == null || c.materials.materialProperties.get("DEFAULT") == null) {
                handleBrokenMaterials(c);
            }
            if (c.preset != ConfigPresets.LOAD_SUCCESS) {
                c.preset.configChanger.accept(c);
            }
        //}

        if ((c.version == null || !Objects.equals(c.version, configVersion)) && !resetOnReload) {
            handleUnstableConfig();
        }

        if (PrecomputedConfig.pC != null) PrecomputedConfig.pC.deactivate();
        try {
            PrecomputedConfig.pC = new PrecomputedConfig(c);
        } catch (CloneNotSupportedException e) {
            Engine.LOGGER.error("Failed to create precomputed config", e);
            return InteractionResult.FAIL;
        }

        if (!Engine.isOff) {
            Engine.updateRays();
            Engine.mc.getSoundManager().reload();
        }
        return InteractionResult.SUCCESS;
    }
}