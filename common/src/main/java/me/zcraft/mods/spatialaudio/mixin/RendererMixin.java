package me.zcraft.mods.spatialaudio.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.zcraft.mods.spatialaudio.Engine;
import me.zcraft.mods.spatialaudio.raycast.Renderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class RendererMixin {
	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void onRenderLevel(DeltaTracker pDeltaTracker, boolean pRenderBlockOutline, Camera camera, GameRenderer pGameRenderer, LightTexture pLightTexture, Matrix4f pFrustumMatrix, Matrix4f pProjectionMatrix, CallbackInfo ci) {
		if (Engine.isOff) return;
		Renderer.renderRays(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z, Minecraft.getInstance().level);
	}
}