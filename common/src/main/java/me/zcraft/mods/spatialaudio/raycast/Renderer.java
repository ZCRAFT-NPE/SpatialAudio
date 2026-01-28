package me.zcraft.mods.spatialaudio.raycast;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import static me.zcraft.mods.spatialaudio.config.PrecomputedConfig.pC;

public class Renderer {
	private static final Deque<RaySegment> raySegments = new ArrayDeque<>();
	private static final Object lock = new Object();
	private static long lastRenderTime = System.currentTimeMillis();
	private static final int MAX_RAYS = 500;

	public static void renderRays(double cameraX, double cameraY, double cameraZ, Level world) {
		if (!pC.dRays || world == null) return;

		long currentTime = System.currentTimeMillis();
		float deltaTime = Math.min(0.1f, (currentTime - lastRenderTime) / 1000.0f);
		lastRenderTime = currentTime;

		synchronized (lock) {
			if (raySegments.isEmpty()) return;

			Iterator<RaySegment> iterator = raySegments.iterator();
			while (iterator.hasNext()) {
				RaySegment ray = iterator.next();
				ray.lifetime -= deltaTime;
				if (ray.lifetime <= 0) {
					iterator.remove();
				}
			}

			if (raySegments.isEmpty()) return;
		}

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();

		RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
		RenderSystem.lineWidth(2.0f);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		Tesselator tesselator = Tesselator.getInstance();

		BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);

		int renderedCount = 0;
		synchronized (lock) {
			for (RaySegment ray : raySegments) {
				if (renderedCount >= MAX_RAYS) break;

				float startX = (float)(ray.start.x - cameraX);
				float startY = (float)(ray.start.y - cameraY);
				float startZ = (float)(ray.start.z - cameraZ);
				float endX = (float)(ray.end.x - cameraX);
				float endY = (float)(ray.end.y - cameraY);
				float endZ = (float)(ray.end.z - cameraZ);

				float ageRatio = ray.lifetime / ray.maxLifetime;
				int alpha = (int)(ageRatio * 200 + 55);

				int r = 255;
				int g = (int)(220 + 35 * ageRatio);
				int b = (int)(255 * ageRatio);

				// 添加两个顶点来构成一条线
				buffer.addVertex(startX, startY, startZ)
						.setColor(r, g, b, alpha);
				buffer.addVertex(endX, endY, endZ)
						.setColor(r, g, b, alpha);

				renderedCount++;
			}
		}

		BufferUploader.drawWithShader(buffer.buildOrThrow());

		RenderSystem.lineWidth(1.0f);
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
	}

	public static void addSoundBounceRay(Vec3 start, Vec3 end) {
		if (!pC.dRays) return;
		addRaySegment(start, end);
	}

	public static void addOcclusionRay(Vec3 start, Vec3 end) {
		if (!pC.dRays) return;
		addRaySegment(start, end);
	}

	public static void addRaySegment(Vec3 start, Vec3 end) {
		if (!pC.dRays) return;

		synchronized (lock) {
			if (raySegments.size() >= 1000) {
				raySegments.removeFirst();
			}

			for (RaySegment existing : raySegments) {
				if (existing.start.distanceToSqr(start) < 0.0001 &&
						existing.end.distanceToSqr(end) < 0.0001) {
					existing.lifetime = existing.maxLifetime;
					return;
				}
			}

			raySegments.add(new RaySegment(start, end));
		}
	}

	public static void clearAllRays() {
		synchronized (lock) {
			raySegments.clear();
		}
	}

	public static int getActiveRayCount() {
		synchronized (lock) {
			return raySegments.size();
		}
	}

	private static class RaySegment {
		final Vec3 start;
		final Vec3 end;
		float lifetime;
		final float maxLifetime;

		RaySegment(Vec3 start, Vec3 end) {
			this.start = start;
			this.end = end;
			this.maxLifetime = 2.0f;
			this.lifetime = maxLifetime;
		}
	}
}