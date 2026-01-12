package dev.thedocruby.resounding.toolbox;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public record SoundProfile(
		int sourceID,
		double directGain,
		double directCutoff,
		double[] sendGain,
		double[] sendCutoff,
		@Nullable Vec3 position,
		@Nullable Vec3 velocity,
		@Nullable EchoDetector.EchoAnalysis echoAnalysis
) {

	public SoundProfile(
			int sourceID,
			double directGain,
			double directCutoff,
			double[] sendGain,
			double[] sendCutoff
	) {
		this(sourceID, directGain, directCutoff, sendGain, sendCutoff, null, null, null);
	}

	public SoundProfile(
			int sourceID,
			double directGain,
			double directCutoff,
			double[] sendGain,
			double[] sendCutoff,
			@Nullable Vec3 position,
			@Nullable Vec3 velocity
	) {
		this(sourceID, directGain, directCutoff, sendGain, sendCutoff, position, velocity, null);
	}

	public SoundProfile withPosition(@Nullable Vec3 position) {
		return new SoundProfile(
				sourceID, directGain, directCutoff,
				sendGain, sendCutoff, position, velocity, echoAnalysis
		);
	}

	public SoundProfile withVelocity(@Nullable Vec3 velocity) {
		return new SoundProfile(
				sourceID, directGain, directCutoff,
				sendGain, sendCutoff, position, velocity, echoAnalysis
		);
	}

	public SoundProfile withEchoAnalysis(@Nullable EchoDetector.EchoAnalysis echoAnalysis) {
		return new SoundProfile(
				sourceID, directGain, directCutoff,
				sendGain, sendCutoff, position, velocity, echoAnalysis
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SoundProfile profile = (SoundProfile) o;
		return               sourceID     == profile.sourceID
				&&               directGain   == profile.directGain
				&&               directCutoff == profile.directCutoff
				&& Arrays.equals(sendGain,       profile.sendGain     )
				&& Arrays.equals(sendCutoff,     profile.sendCutoff   )
				&& Objects.equals(position,      profile.position     )
				&& Objects.equals(velocity,      profile.velocity     )
				&& Objects.equals(echoAnalysis,  profile.echoAnalysis );
	}

	@Override
	public int hashCode() {
		int hash = Objects.hash(sourceID, directGain, directCutoff, position, velocity, echoAnalysis);
		hash = 31 * hash + Arrays.hashCode(sendGain);
		hash = 31 * hash + Arrays.hashCode(sendCutoff);
		return hash;
	}

	@Override
	public @NotNull String toString() {
		return "    SoundProfile {\n"   +
				"        sourceID = "     +                 sourceID       +
				";\n        directGain = "   +                 directGain     +
				";\n        directCutoff = " +                 directCutoff   +
				";\n        sendGain = "     + Arrays.toString(sendGain     ) +
				";\n        sendCutoff = "   + Arrays.toString(sendCutoff   ) +
				";\n        position = "     + (position != null ? position : "null") +
				";\n        velocity = "     + (velocity != null ? velocity : "null") +
				";\n        echoAnalysis = " + (echoAnalysis != null ? "EchoAnalysis{hasClearEcho=" + echoAnalysis.hasClearEcho + "}" : "null") +
				";\n    }";
	}
}