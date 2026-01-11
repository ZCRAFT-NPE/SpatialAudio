package dev.thedocruby.resounding.raycast;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.thedocruby.resounding.config.PrecomputedConfig.pC;

@Environment(EnvType.CLIENT)
public class Renderer {

	private Renderer() {}

	private static final List<Ray> rays = new CopyOnWriteArrayList<>();

	public static void renderRays(double x, double y, double z, Level world) {
		if (world == null) {
			return;
		}
		long gameTime = world.getGameTime();
		for (Ray ray : rays) {
			if (ray.tickCreated == -1) ray.tickCreated = gameTime;
			renderRay(ray, x, y, z);
		}
		rays.removeIf(ray -> (gameTime - ray.tickCreated) > ray.lifespan || (gameTime - ray.tickCreated) < 0L);
	}

	public static void addSoundBounceRay(Vec3 start, Vec3 end, int color) {
		if (!pC.dRays) {
			return;
		}
		addRay(start, end, color, false);
	}

	public static void addOcclusionRay(Vec3 start, Vec3 end, int color) {
		if (!pC.dRays) {
			return;
		}
		addRay(start, end, color, true);
	}

	public static void addRay(Vec3 start, Vec3 end, int color, boolean throughWalls) {
		rays.add(new Ray(start, end, color, throughWalls));
	}

	public static void renderRay(@NotNull Ray ray, double x, double y, double z) {
		int red = getRed(ray.color);
		int green = getGreen(ray.color);
		int blue = getBlue(ray.color);

		if (!ray.throughWalls) {
			RenderSystem.enableDepthTest();
		}
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder bufferBuilder = tesselator.getBuilder();
		RenderSystem.disableBlend();
		RenderSystem.lineWidth(ray.throughWalls ? 3F : 0.25F);

		bufferBuilder.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

		bufferBuilder.vertex(ray.start.x - x, ray.start.y - y, ray.start.z - z).color(red, green, blue, 255).endVertex();
		bufferBuilder.vertex(ray.end.x - x, ray.end.y - y, ray.end.z - z).color(red, green, blue, 255).endVertex();

		tesselator.end();
		RenderSystem.lineWidth(1F);
		RenderSystem.enableBlend();
	}

	private static int getRed(int argb) {
		return (argb >> 16) & 0xFF;
	}

	private static int getGreen(int argb) {
		return (argb >> 8) & 0xFF;
	}

	private static int getBlue(int argb) {
		return argb & 0xFF;
	}

	private static class Ray {
		private final Vec3 start;
		private final Vec3 end;
		private final int color;
		private long tickCreated;
		private final long lifespan;
		private final boolean throughWalls;

		public Ray(Vec3 start, Vec3 end, int color, boolean throughWalls) {
			this.start = start;
			this.end = end;
			this.color = color;
			this.throughWalls = throughWalls;
			this.tickCreated = -1;
			this.lifespan = 20 * 2;
		}
	}
}