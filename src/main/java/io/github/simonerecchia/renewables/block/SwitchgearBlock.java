package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.SwitchgearSpec;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A disconnector or a breaker: one cell, six insulators (line side first), an open/closed state.
 *
 * STUB: owner B (grid-blocks.md section 6): the hand and redstone switching, the refusal to open under load,
 * the trip, and the call to {@code Grid.recalcNow} when the state changes.
 */
public class SwitchgearBlock extends MachineBlock {
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

	/** The facing the mv_*.obj files are modelled at. */
	public static final Direction AUTHORED = Direction.NORTH;

	private final SwitchgearSpec spec;

	public SwitchgearBlock(Properties properties, SwitchgearSpec spec) {
		super(properties);
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(OPEN, false));
	}

	public SwitchgearSpec spec() {
		return spec;
	}

	/** Whether the switch is open, which is what the renderer swings the blade for. */
	public static boolean open(BlockState state) {
		return state.getValue(OPEN);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(OPEN);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.SWITCHGEAR.get().create(pos, state);
	}
}
