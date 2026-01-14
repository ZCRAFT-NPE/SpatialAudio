package me.zcraft.mods.spatialaudio.openal;

public class ALset {
	public long old = -1;
	public long self = 0;
	public int direct = 0;
	public int echoSlot = 0;
	public int echoEffect = 0;
	public int dopplerSource = 0;
	public int[] slots = new int[0];
	public int[] effects = new int[0];
	public int[] filters = new int[0];

	public void clear() {
		slots = new int[0];
		effects = new int[0];
		filters = new int[0];
		direct = 0;
		echoSlot = 0;
		echoEffect = 0;
		dopplerSource = 0;
		old = -1;
		self = 0;
	}

	public boolean hasObjects() {
		return slots.length > 0 || effects.length > 0 ||
				filters.length > 0 || direct != 0 ||
				echoSlot != 0 || echoEffect != 0 ||
				dopplerSource != 0;
	}
}