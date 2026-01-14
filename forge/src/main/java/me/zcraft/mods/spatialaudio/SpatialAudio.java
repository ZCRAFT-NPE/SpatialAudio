package me.zcraft.mods.spatialaudio;

import me.shedaniel.autoconfig.AutoConfig;
import me.zcraft.mods.spatialaudio.config.ResoundingConfig;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SpatialAudioCommon.MOD_ID)
public class SpatialAudio {
    public SpatialAudio() {
        SpatialAudioCommon.init();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSideSetup);
    }
    public void onClientSideSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(SpatialAudioCommon.MOD_ID).ifPresent(container -> ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) ->
                        AutoConfig.getConfigScreen(ResoundingConfig.class,parent).get())
        ));
    }
}
