package dev.thedocruby.resounding.mixin.server;

import dev.thedocruby.resounding.Engine;
import net.fabricmc.api.EnvType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

@Mixin(ServerLevel.class)
public class ServerWorldMixin {
	@Shadow @Final
	private MinecraftServer server;

	@ModifyArg(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"),
			index = 4)
	private double SoundDistanceModifierInjectorPlayerSound(double distance) {
		if ((Engine.env == EnvType.CLIENT && Engine.isOff) || (Engine.env == EnvType.SERVER && !pC.enabled)) return distance;
		return Math.min(distance * pC.soundSimulationDistance, 16 * Math.min(this.server.getPlayerList().getViewDistance(), this.server.getPlayerList().getSimulationDistance()));
	}

	@ModifyArg(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"),
			index = 4)
	private double SoundDistanceModifierInjectorEntitySound(double distance) {
		if ((Engine.env == EnvType.CLIENT && Engine.isOff) || (Engine.env == EnvType.SERVER && !pC.enabled)) return distance;
		return Math.min(distance * pC.soundSimulationDistance, 16 * Math.min(this.server.getPlayerList().getViewDistance(), this.server.getPlayerList().getSimulationDistance()));
	}
}