package me.zcraft.mods.spatialaudio.raycast;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;


import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static me.zcraft.mods.spatialaudio.config.PrecomputedConfig.pC;


public class Renderer {

	private Renderer() {}

	private static final List<RaySegment> raySegments = new CopyOnWriteArrayList<>();

	public static void renderRays(double cameraX, double cameraY, double cameraZ, Level world) {
		if (world == null || raySegments.isEmpty()) {
			return;
		}

		long gameTime = world.getGameTime();

		// 移除过期的射线
		raySegments.removeIf(ray -> (gameTime - ray.createdAt) > ray.lifespanTicks);

		if (raySegments.isEmpty()) {
			return;
		}

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.lineWidth(1.5f);

		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder buffer = tesselator.getBuilder();

		// 开始批量渲染所有射线
		buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

		for (RaySegment ray : raySegments) {
			// 计算相对于相机的位置
			double startX = ray.start.x - cameraX;
			double startY = ray.start.y - cameraY;
			double startZ = ray.start.z - cameraZ;
			double endX = ray.end.x - cameraX;
			double endY = ray.end.y - cameraY;
			double endZ = ray.end.z - cameraZ;

			// 解析颜色
			int r = (ray.color >> 16) & 0xFF;
			int g = (ray.color >> 8) & 0xFF;
			int b = ray.color & 0xFF;
			int alpha = 200; // 半透明

			// 添加起点和终点
			buffer.vertex(startX, startY, startZ).color(r, g, b, alpha).endVertex();
			buffer.vertex(endX, endY, endZ).color(r, g, b, alpha).endVertex();
		}

		tesselator.end();

		// 恢复渲染状态
		RenderSystem.lineWidth(1.0f);
	}

	public static void addSoundBounceRay(Vec3 start, Vec3 end, int color) {
		if (!pC.dRays) {
			return;
		}
		addRaySegment(start, end, color, 40); // 2秒生命周期
	}

	public static void addOcclusionRay(Vec3 start, Vec3 end, int color) {
		if (!pC.dRays) {
			return;
		}
		addRaySegment(start, end, color, 40);
	}

	public static void addRaySegment(Vec3 start, Vec3 end, int color, int lifespanTicks) {
		raySegments.add(new RaySegment(start, end, color, lifespanTicks));
	}

	public static void clearAllRays() {
		raySegments.clear();
	}

	public static int getActiveRayCount() {
		return raySegments.size();
	}

	private static class RaySegment {
		private final Vec3 start;
		private final Vec3 end;
		private final int color;
		private final long createdAt;
		private final int lifespanTicks;

		public RaySegment(Vec3 start, Vec3 end, int color, int lifespanTicks) {
			this.start = start;
			this.end = end;
			this.color = color;
			this.createdAt = System.currentTimeMillis() / 50; // 转换为游戏刻（20刻/秒）
			this.lifespanTicks = lifespanTicks;
		}
	}
}