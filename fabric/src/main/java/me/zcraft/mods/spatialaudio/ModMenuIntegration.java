package me.zcraft.mods.spatialaudio;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.zcraft.mods.spatialaudio.config.ResoundingConfig;
import me.shedaniel.autoconfig.AutoConfig;




public class ModMenuIntegration implements ModMenuApi
{

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> AutoConfig.getConfigScreen(ResoundingConfig.class, screen).get();
    }

}