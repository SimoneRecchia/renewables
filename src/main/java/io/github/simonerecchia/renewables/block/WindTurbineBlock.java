package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wind turbine of any model: the machine that sits on top of a stack of tower blocks.
 *
 * STUB: owner W (wind.md): tower-height checks, the panel, redstone, the 3D model.
 */
public class WindTurbineBlock extends MachineBlock {
	/** The facing wind_turbine.obj is modelled at. */
	public static final Direction AUTHORED = Direction.SOUTH;

	private final TurbineSpec spec;

	public WindTurbineBlock(Properties properties, TurbineSpec spec) {
		super(properties);
		this.spec = spec;
	}

	public TurbineSpec spec() {
		return spec;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.WIND_TURBINE.get().create(pos, state);
	}
}
