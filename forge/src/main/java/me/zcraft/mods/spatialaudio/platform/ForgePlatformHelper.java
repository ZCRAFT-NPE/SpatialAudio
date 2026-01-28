package me.zcraft.mods.spatialaudio.platform;

import me.zcraft.mods.spatialaudio.SpatialAudioCommon;
import me.zcraft.mods.spatialaudio.platform.services.IPlatformHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        if (modList == null) {
            return false;
        }
        return modList.isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public boolean isClientEnvironment() {
        return FMLEnvironment.dist.isClient();
    }

    @Override
    public String getModVersion() {
        return ModList.get()
                .getModContainerById(SpatialAudioCommon.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("Unknown");
    }
}