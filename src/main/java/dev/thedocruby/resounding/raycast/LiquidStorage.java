package dev.thedocruby.resounding.raycast;

import dev.thedocruby.resounding.Engine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Environment(EnvType.CLIENT)
public class LiquidStorage {
	public interface IChunkAccess {
		LiquidStorage getNotAirLiquidStorage();
		LevelChunk getChunk();
	}

	private boolean full;
	public int bottom;
	public int top;
	private boolean[][] sections;
	private boolean[] sFull;

	@Contract(value = " -> new", pure = true)
	public static boolean @NotNull [] empty() {
		return new boolean[256];
	}

	public final LevelChunk chunk;

	public IChunkAccess xp = null;
	public IChunkAccess xm = null;
	public IChunkAccess zp = null;
	public IChunkAccess zm = null;

	public enum LIQUIDS {
		AIR(Set.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR, Blocks.SCAFFOLDING));
		final Set<Block> allowed;
		LIQUIDS(Set<Block> a) {
			allowed = a;
		}
		public boolean matches(Block b) {
			return allowed.contains(b);
		}
	}

	public boolean isEmpty() {
		return !full;
	}

	public boolean[] getSection(int y) {
		if (!full || y > top || y < bottom) return null;
		return sections[y - bottom];
	}

	public boolean[] getOrCreateSection(int y) {
		if (getSection(y) == null) return initSection(y);
		return sections[y - bottom];
	}

	public boolean getBlock(int x, int y, int z) {
		if (!full || y > top || y < bottom) return false;
		boolean[] section = sections[y - bottom];
		if (section == null) return false;
		return section[x + (z << 4)];
	}

	public boolean[] initSection(int y) {
		if (!full) {
			sFull = new boolean[]{false};
			bottom = y;
			top = y;
			full = true;
			sections = new boolean[1][];
			sections[0] = empty();
			return sections[0];
		} else if (y < bottom) {
			int diff = bottom - y;
			boolean[][] newSections = new boolean[sections.length + diff][];
			boolean[] newSFull = new boolean[sFull.length + diff];
			System.arraycopy(sections, 0, newSections, diff, sections.length);
			System.arraycopy(sFull, 0, newSFull, diff, sFull.length);
			sections = newSections;
			sFull = newSFull;
			bottom = y;
			sections[0] = empty();
			return sections[0];
		} else if (y > top) {
			int diff = y - top;
			boolean[][] newSections = new boolean[sections.length + diff][];
			boolean[] newSFull = new boolean[sFull.length + diff];
			System.arraycopy(sections, 0, newSections, 0, sections.length);
			System.arraycopy(sFull, 0, newSFull, 0, sFull.length);
			sections = newSections;
			sFull = newSFull;
			top = y;
		}
		sections[y - bottom] = empty();
		return sections[y - bottom];
	}

	public LiquidStorage(boolean @NotNull [][] s, int t, int b, boolean[] sf, LevelChunk ch) {
		int n = t - b + 1;
		if (s.length != n || sf.length != n) Engine.LOGGER.error("Top(" + t + ") to Bottom(" + b + ") != " + s.length + " or " + sf.length);
		full = true;
		sections = s;
		top = t;
		bottom = b;
		sFull = sf;
		chunk = ch;
	}

	public LiquidStorage(LevelChunk ch) {
		full = false;
		chunk = ch;
	}

	public void setBlock(int x, int y, int z, boolean block) {
		if (x >= 16 || x < 0 || z >= 16 || z < 0 || y >= 320 || y < -64) {
			Engine.LOGGER.error("Block coords [" + x + ", " + y + ", " + z + "] are out of bounds!");
		} else if (getBlock(x, y, z) != block) {
			getOrCreateSection(y)[x + (z << 4)] = block;
			if (!block) {
				sFull[y - bottom] = false;
				tryCull(y);
			} else {
				boolean hasAir = false;
				boolean[] section = sections[y - bottom];
                for (boolean b : section) {
                    if (!b) {
                        hasAir = true;
                        break;
                    }
                }
				sFull[y - bottom] = !hasAir;
			}
		}
	}

	public void tryCull(int y) {
		if (!full) return;
		boolean[] section = sections[y - bottom];
		if (section == null) return;

		boolean hasBlock = false;
        for (boolean b : section) {
            if (b) {
                hasBlock = true;
                break;
            }
        }

		if (!hasBlock) {
			sections[y - bottom] = null;
			if (y == bottom) {
				int y1 = y;
				for (boolean[] s : sections) {
					if (s == null) y1++;
					else break;
				}
				if (y1 > top) {
					unload();
				} else {
					int newLength = top - y1 + 1;
					boolean[][] newSections = new boolean[newLength][];
					boolean[] newSFull = new boolean[newLength];
					System.arraycopy(sections, y1 - bottom, newSections, 0, newLength);
					System.arraycopy(sFull, y1 - bottom, newSFull, 0, newLength);
					sections = newSections;
					sFull = newSFull;
					bottom = y1;
				}
			} else if (y == top) {
				int y1 = y;
				for (int i = sections.length - 1; i >= 0; i--) {
					if (sections[i] != null) {
						y1 = bottom + i;
						break;
					}
				}
				if (y1 == y) {
					unload();
				} else {
					int newLength = y1 - bottom + 1;
					boolean[][] newSections = new boolean[newLength][];
					boolean[] newSFull = new boolean[newLength];
					System.arraycopy(sections, 0, newSections, 0, newLength);
					System.arraycopy(sFull, 0, newSFull, 0, newLength);
					sections = newSections;
					sFull = newSFull;
					top = y1;
				}
			}
		}
	}

	public void unload() {
		full = false;
		sections = null;
		sFull = null;
		xp = null;
		xm = null;
		zp = null;
		zm = null;
	}
}