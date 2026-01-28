package me.zcraft.mods.spatialaudio.platform;

import me.zcraft.mods.spatialaudio.SpatialAudioCommon;
import me.zcraft.mods.spatialaudio.platform.services.IPlatformHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isClientEnvironment() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public String getModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(SpatialAudioCommon.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("Unknown");
    }
}