package me.zcraft.mods.spatialaudio;

import me.shedaniel.autoconfig.AutoConfig;
import me.zcraft.mods.spatialaudio.config.ResoundingConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(SpatialAudioCommon.MOD_ID)
public class SpatialAudio {

    public SpatialAudio(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        SpatialAudioCommon.init();
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ModLoadingContext.get().registerExtensionPoint(
                IConfigScreenFactory.class,
                () -> (mc, screen) -> AutoConfig.getConfigScreen(ResoundingConfig.class, screen).get()
        ));
    }
}