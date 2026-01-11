package dev.thedocruby.resounding.config.presets;

import dev.thedocruby.resounding.config.BlueTapePack.ConfigManager;
import dev.thedocruby.resounding.config.PrecomputedConfig;
import dev.thedocruby.resounding.config.ResoundingConfig;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

@SuppressWarnings({"unused"})
public enum ConfigPresets {
    LOAD_SUCCESS("Choose", null),

    RESET_MATERIALS("Reset Materials", new ResetMaterialsConsumer());

    public final Consumer<ResoundingConfig> configChanger;
    public final String text;

    public void setConfig() {
        if (configChanger != null) {
            configChanger.accept(ConfigManager.getConfig());
            ConfigManager.save();
        }
    }

    ConfigPresets(String text, @Nullable Consumer<ResoundingConfig> c) {
        this.configChanger = c;
        this.text = text;
    }

    @Override
    public String toString() {
        return this.text;
    }

    private static class ResetMaterialsConsumer implements Consumer<ResoundingConfig> {
        @Override
        public void accept(ResoundingConfig config) {
            ConfigChanger.changeConfig(config, true,
                    null, null, null, null,
                    null, null, null, null,

                    null, null, null, null, null, null,
                    PrecomputedConfig.materialDefaults(),
                    null, null, null, null,null, null
            );
        }
    }
}