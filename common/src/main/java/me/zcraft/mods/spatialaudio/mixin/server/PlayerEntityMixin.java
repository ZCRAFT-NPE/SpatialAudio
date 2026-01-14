package me.zcraft.mods.spatialaudio.mixin.server;

import me.zcraft.mods.spatialaudio.Engine;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public class PlayerEntityMixin {

	@Shadow @SuppressWarnings("SameReturnValue")
	public double getEyeY(){ return 0.0d; }

	@ModifyArg(method = "playSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"), index = 2)
	private double EyeHeightOffsetInjector(@Nullable Player player, double x, double y, double z, @NotNull SoundEvent sound, SoundSource category, float volume, float pitch) {
		return Engine.stepPattern.matcher(sound.getLocation().getPath()).matches() ? y : getEyeY(); // TODO: step sounds
	}
}