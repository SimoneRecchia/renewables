package io.github.simonerecchia.renewables.client.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * How a renderer poses a machine's groups on top of the block's frame, told to the picker so that what is pointed at is
 * what is drawn: an inverter drawn at 0.62 of its model, a turbine scaled to its size and its tower stretched to its
 * height, a bare tower that draws only the tube.
 *
 * A renderer registers one at init for its block entity type and builds its own drawing from the same function; a type
 * with none is picked as authored.  Moving parts that cannot be aimed at (a turning rotor) may answer null.
 */
@OnlyIn(Dist.CLIENT)
public final class GroupPoses {
	/** The pose of a group that is drawn as authored. */
	public static final Matrix4fc IDENTITY = new Matrix4f();
	/** Every group as authored. */
	static final Posed AS_AUTHORED = group -> IDENTITY;

	/** One machine's answer: the matrix of a group in the model's frame, or null when the group is not drawn. */
	@FunctionalInterface
	public interface Posed {
		@Nullable
		Matrix4fc of(String group);
	}

	/** A renderer's answer for a machine of its type. */
	@FunctionalInterface
	public interface Source<T extends BlockEntity> {
		Posed of(T machine, MeshModel model);
	}

	private static final Map<Class<?>, Source<?>> SOURCES = new ConcurrentHashMap<>();

	private GroupPoses() {
	}

	public static <T extends BlockEntity> void register(Class<T> type, Source<T> source) {
		SOURCES.put(type, source);
	}

	/** How the machine's groups are drawn; as authored for a type no renderer has registered. */
	@SuppressWarnings("unchecked")
	static Posed of(@Nullable BlockEntity machine, MeshModel model) {
		if (machine == null) return AS_AUTHORED;

		Source<BlockEntity> source = (Source<BlockEntity>) SOURCES.get(machine.getClass());
		return source == null ? AS_AUTHORED : source.of(machine, model);
	}
}
