package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * The electric cabin: a prefabricated substation hut, nine cells (three in plan, three high down its middle),
 * one input and one output insulator on the roof.
 *
 * Its door is on the -z side as authored, so a cabin placed facing the player shows its door to them.  No use of
 * its own: a reel strings it, the wrench reads it.
 */
public class ElectricCabinBlock extends MachineBlock {
	/** The facing cab.obj is modelled at. */
	public static final Direction AUTHORED = Direction.NORTH;

	// ---- 9 cell(s), printed by tools/check_hitboxes.py --java ----
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.block()),
			new Cell(0, 0, -1, Shapes.or(Block.box(0.00, 0.00, 4.88, 16.00, 2.56, 16.00),
					Block.box(0.00, 2.27, 4.51, 16.00, 16.00, 16.00))),
			new Cell(0, 0, 1, Block.box(0.00, 0.00, 0.00, 16.00, 16.00, 11.12)),
			new Cell(0, 1, 0, Shapes.block()),
			new Cell(0, 1, -1, Block.box(0.00, 0.00, 4.51, 16.00, 16.00, 16.00)),
			new Cell(0, 1, 1, Block.box(0.00, 0.00, 0.00, 16.00, 16.00, 11.10)),
			new Cell(0, 2, 0, Block.box(0.00, 0.00, 0.00, 16.00, 12.50, 16.00)),
			new Cell(0, 2, -1, Shapes.or(Block.box(0.00, 0.00, 4.51, 16.00, 8.80, 16.00),
					Block.box(0.00, 8.35, 4.64, 16.00, 12.50, 16.00),
					Block.box(6.67, 12.51, 11.15, 9.33, 14.58, 13.81))),
			new Cell(0, 2, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 16.00, 8.80, 11.10),
					Block.box(0.00, 8.35, 0.00, 16.00, 12.50, 11.36),
					Block.box(2.37, 11.62, 7.49, 4.03, 16.00, 9.31),
					Block.box(6.67, 12.51, 2.19, 9.33, 14.58, 4.85),
					Block.box(7.17, 11.62, 7.49, 8.83, 16.00, 9.31),
					Block.box(11.97, 11.62, 7.49, 13.63, 16.00, 9.31))));

	public ElectricCabinBlock(Properties properties) {
		super(properties);
	}

	@Override
	public List<Cell> shellCells(BlockState state) {
		return CELLS;
	}

	@Override
	public Direction shellAuthored() {
		return AUTHORED;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.ELECTRIC_CABIN.get().create(pos, state);
	}
}
