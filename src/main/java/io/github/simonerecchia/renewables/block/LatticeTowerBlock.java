package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.api.power.TowerSpec;
import io.github.simonerecchia.renewables.grid.WireLedger;

import io.github.simonerecchia.renewables.Renewables;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lattice transmission tower, in one of three duties.
 *
 * The duty is what makes them three objects rather than one: a suspension tower only holds a conductor
 * up, a tension tower takes the difference between the pulls either side of it, and a terminal tower
 * takes the whole pull of the line on one side, standing on the widest foot of the three to do it. So a
 * line is a run of suspension towers with a tension tower every few kilometres and a terminal tower at each
 * end, which is what a player builds here too.
 *
 * Eleven and a half blocks tall and seven across its lower crossarm, so it is a {@link MachineShell}: its
 * collision is a table of cells printed by {@code tools/gen_tower_models.py --java}.
 */
public class LatticeTowerBlock extends Block implements EntityBlock, MachineShell {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** The facing the three lattice_*.obj files are modelled at: the crossarms run east-west. */
	public static final Direction AUTHORED = Direction.NORTH;

	/**
	 * Where the three towers collide, one table a duty.
	 *
	 * Printed by {@code tools/gen_tower_models.py --java}, which authors it from the legs, the floors of the
	 * crossarms, the peak, the footings and the insulator strings rather than cutting it from every group the model
	 * draws - on a lattice the diagonals sweep almost every cell of the bounding volume, and between the braces is
	 * air.  The duties are three towers: a tension tower stands on a wider foot than a suspension tower and a
	 * terminal tower on a wider one still, so they share no cell.
	 */
	/** ---- 51 cell(s), printed by tools/gen_tower_models.py --java ---- */
	private static final List<Cell> SUSPENSION_CELLS = List.of(
			new Cell(-1, 0, -1, Shapes.or(Block.box(5.43, 0.00, 5.43, 9.86, 1.53, 9.86),
					Block.box(6.80, 0.00, 6.80, 10.00, 16.00, 10.00))),
			new Cell(-1, 0, 1, Shapes.or(Block.box(5.43, 0.00, 6.14, 9.86, 1.53, 10.57),
					Block.box(6.80, 0.00, 6.00, 10.00, 16.00, 9.20))),
			new Cell(1, 0, -1, Shapes.or(Block.box(6.00, 0.00, 6.80, 9.20, 16.00, 10.00),
					Block.box(6.14, 0.00, 5.43, 10.57, 1.53, 9.86))),
			new Cell(1, 0, 1, Shapes.or(Block.box(6.00, 0.00, 6.00, 9.20, 16.00, 9.20),
					Block.box(6.14, 0.00, 6.14, 10.57, 1.53, 10.57))),
			new Cell(-1, 1, -1, Block.box(8.30, 0.00, 8.30, 11.50, 16.00, 11.50)),
			new Cell(-1, 1, 1, Block.box(8.30, 0.00, 4.50, 11.50, 16.00, 7.70)),
			new Cell(1, 1, -1, Block.box(4.50, 0.00, 8.30, 7.70, 16.00, 11.50)),
			new Cell(1, 1, 1, Block.box(4.50, 0.00, 4.50, 7.70, 16.00, 7.70)),
			new Cell(-1, 2, -1, Block.box(9.81, 0.00, 9.81, 13.01, 16.00, 13.01)),
			new Cell(-1, 2, 1, Block.box(9.81, 0.00, 2.99, 13.01, 16.00, 6.19)),
			new Cell(1, 2, -1, Block.box(2.99, 0.00, 9.81, 6.19, 16.00, 13.01)),
			new Cell(1, 2, 1, Block.box(2.99, 0.00, 2.99, 6.19, 16.00, 6.19)),
			new Cell(-1, 3, -1, Block.box(11.32, 0.00, 11.32, 14.52, 16.00, 14.52)),
			new Cell(-1, 3, 1, Block.box(11.32, 0.00, 1.48, 14.52, 16.00, 4.68)),
			new Cell(1, 3, -1, Block.box(1.48, 0.00, 11.32, 4.68, 16.00, 14.52)),
			new Cell(1, 3, 1, Block.box(1.48, 0.00, 1.48, 4.68, 16.00, 4.68)),
			new Cell(-1, 4, -1, Block.box(12.82, 0.00, 12.82, 16.00, 16.00, 16.00)),
			new Cell(-1, 4, 1, Block.box(12.82, 0.00, 0.00, 16.00, 16.00, 3.18)),
			new Cell(1, 4, -1, Block.box(0.00, 0.00, 12.82, 3.18, 16.00, 16.00)),
			new Cell(1, 4, 1, Block.box(0.00, 0.00, 0.00, 3.18, 16.00, 3.18)),
			new Cell(-1, 5, -1, Block.box(14.33, 0.00, 14.33, 16.00, 16.00, 16.00)),
			new Cell(-1, 5, 0, Shapes.or(Block.box(14.33, 0.00, 0.00, 16.00, 16.00, 1.53),
					Block.box(14.33, 0.00, 14.47, 16.00, 16.00, 16.00))),
			new Cell(-1, 5, 1, Block.box(14.33, 0.00, 0.00, 16.00, 16.00, 1.67)),
			new Cell(0, 5, -1, Shapes.or(Block.box(0.00, 0.00, 14.33, 1.53, 16.00, 16.00),
					Block.box(14.47, 0.00, 14.33, 16.00, 16.00, 16.00))),
			new Cell(0, 5, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.53, 16.00, 1.53),
					Block.box(0.00, 0.00, 14.47, 1.53, 16.00, 16.00),
					Block.box(14.47, 0.00, 0.00, 16.00, 16.00, 1.53),
					Block.box(14.47, 0.00, 14.47, 16.00, 16.00, 16.00))),
			new Cell(0, 5, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.53, 16.00, 1.67),
					Block.box(14.47, 0.00, 0.00, 16.00, 16.00, 1.67))),
			new Cell(1, 5, -1, Block.box(0.00, 0.00, 14.33, 1.67, 16.00, 16.00)),
			new Cell(1, 5, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.67, 16.00, 1.53),
					Block.box(0.00, 0.00, 14.47, 1.67, 16.00, 16.00))),
			new Cell(1, 5, 1, Block.box(0.00, 0.00, 0.00, 1.67, 16.00, 1.67)),
			new Cell(-3, 6, 0, Block.box(0.35, 3.84, 7.28, 2.61, 16.00, 8.72)),
			new Cell(-2, 6, 0, Block.box(11.61, 3.84, 7.28, 13.87, 16.00, 8.72)),
			new Cell(0, 6, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 3.03, 16.00, 3.03),
					Block.box(0.00, 0.00, 12.97, 3.03, 16.00, 16.00),
					Block.box(12.97, 0.00, 0.00, 16.00, 16.00, 3.03),
					Block.box(12.97, 0.00, 12.97, 16.00, 16.00, 16.00))),
			new Cell(2, 6, 0, Block.box(2.13, 3.84, 7.28, 4.39, 16.00, 8.72)),
			new Cell(3, 6, 0, Block.box(13.39, 3.84, 7.28, 15.65, 16.00, 8.72)),
			new Cell(-3, 7, 0, Shapes.or(Block.box(0.35, 0.00, 7.28, 2.61, 1.86, 8.72),
					Block.box(1.48, 2.73, 4.07, 16.00, 4.97, 11.93))),
			new Cell(-2, 7, 0, Shapes.or(Block.box(0.00, 2.73, 3.01, 16.00, 4.97, 12.99),
					Block.box(11.61, 0.00, 7.28, 13.87, 1.86, 8.72))),
			new Cell(-1, 7, 0, Block.box(0.00, 2.73, 2.18, 16.00, 4.97, 13.82)),
			new Cell(0, 7, 0, Shapes.or(Block.box(0.00, 2.73, 2.18, 2.55, 4.97, 13.82),
					Block.box(1.07, 0.00, 1.07, 4.27, 16.00, 4.27),
					Block.box(1.07, 0.00, 11.73, 4.27, 16.00, 14.93),
					Block.box(11.73, 0.00, 1.07, 14.93, 16.00, 4.27),
					Block.box(11.73, 0.00, 11.73, 14.93, 16.00, 14.93),
					Block.box(13.45, 2.73, 2.18, 16.00, 4.97, 13.82))),
			new Cell(1, 7, 0, Block.box(0.00, 2.73, 2.18, 16.00, 4.97, 13.82)),
			new Cell(2, 7, 0, Shapes.or(Block.box(0.00, 2.73, 3.01, 16.00, 4.97, 12.99),
					Block.box(2.13, 0.00, 7.28, 4.39, 1.86, 8.72))),
			new Cell(3, 7, 0, Shapes.or(Block.box(0.00, 2.73, 4.07, 14.52, 4.97, 11.93),
					Block.box(13.39, 0.00, 7.28, 15.65, 1.86, 8.72))),
			new Cell(-2, 8, 0, Block.box(1.39, 7.61, 7.28, 3.65, 16.00, 8.72)),
			new Cell(0, 8, 0, Shapes.or(Block.box(1.56, 0.00, 1.56, 4.76, 16.00, 4.76),
					Block.box(1.56, 0.00, 11.24, 4.76, 16.00, 14.44),
					Block.box(11.24, 0.00, 1.56, 14.44, 16.00, 4.76),
					Block.box(11.24, 0.00, 11.24, 14.44, 16.00, 14.44))),
			new Cell(2, 8, 0, Block.box(12.35, 7.61, 7.28, 14.61, 16.00, 8.72)),
			new Cell(-2, 9, 0, Shapes.or(Block.box(1.39, 0.00, 7.28, 3.65, 5.64, 8.72),
					Block.box(2.52, 6.51, 4.81, 16.00, 8.75, 11.19))),
			new Cell(-1, 9, 0, Block.box(0.00, 6.51, 3.26, 16.00, 8.75, 12.74)),
			new Cell(0, 9, 0, Shapes.or(Block.box(0.00, 6.51, 3.26, 3.63, 8.75, 12.74),
					Block.box(2.04, 0.00, 2.04, 5.24, 16.00, 5.24),
					Block.box(2.04, 0.00, 10.76, 5.24, 16.00, 13.96),
					Block.box(10.76, 0.00, 2.04, 13.96, 16.00, 5.24),
					Block.box(10.76, 0.00, 10.76, 13.96, 16.00, 13.96),
					Block.box(12.37, 6.51, 3.26, 16.00, 8.75, 12.74))),
			new Cell(1, 9, 0, Block.box(0.00, 6.51, 3.26, 16.00, 8.75, 12.74)),
			new Cell(2, 9, 0, Shapes.or(Block.box(0.00, 6.51, 4.81, 13.48, 8.75, 11.19),
					Block.box(12.35, 0.00, 7.28, 14.61, 5.64, 8.72))),
			new Cell(0, 10, 0, Block.box(3.35, 0.00, 3.35, 12.65, 16.00, 12.65)),
			new Cell(0, 11, 0, Block.box(4.95, 0.00, 4.95, 11.05, 8.00, 11.05)));
	/** ---- 60 cell(s), printed by tools/gen_tower_models.py --java ---- */
	private static final List<Cell> TENSION_CELLS = List.of(
			new Cell(-1, 0, -1, Shapes.or(Block.box(0.66, 0.00, 0.66, 5.09, 1.53, 5.09),
					Block.box(2.26, 0.00, 2.26, 5.46, 16.00, 5.46))),
			new Cell(-1, 0, 1, Shapes.or(Block.box(0.66, 0.00, 10.91, 5.09, 1.53, 15.34),
					Block.box(2.26, 0.00, 10.54, 5.46, 16.00, 13.74))),
			new Cell(1, 0, -1, Shapes.or(Block.box(10.54, 0.00, 2.26, 13.74, 16.00, 5.46),
					Block.box(10.91, 0.00, 0.66, 15.34, 1.53, 5.09))),
			new Cell(1, 0, 1, Shapes.or(Block.box(10.54, 0.00, 10.54, 13.74, 16.00, 13.74),
					Block.box(10.91, 0.00, 10.91, 15.34, 1.53, 15.34))),
			new Cell(-1, 1, -1, Block.box(4.24, 0.00, 4.24, 7.44, 16.00, 7.44)),
			new Cell(-1, 1, 1, Block.box(4.24, 0.00, 8.56, 7.44, 16.00, 11.76)),
			new Cell(1, 1, -1, Block.box(8.56, 0.00, 4.24, 11.76, 16.00, 7.44)),
			new Cell(1, 1, 1, Block.box(8.56, 0.00, 8.56, 11.76, 16.00, 11.76)),
			new Cell(-1, 2, -1, Block.box(6.22, 0.00, 6.22, 9.42, 16.00, 9.42)),
			new Cell(-1, 2, 1, Block.box(6.22, 0.00, 6.58, 9.42, 16.00, 9.78)),
			new Cell(1, 2, -1, Block.box(6.58, 0.00, 6.22, 9.78, 16.00, 9.42)),
			new Cell(1, 2, 1, Block.box(6.58, 0.00, 6.58, 9.78, 16.00, 9.78)),
			new Cell(-1, 3, -1, Block.box(8.19, 0.00, 8.19, 11.39, 16.00, 11.39)),
			new Cell(-1, 3, 1, Block.box(8.19, 0.00, 4.61, 11.39, 16.00, 7.81)),
			new Cell(1, 3, -1, Block.box(4.61, 0.00, 8.19, 7.81, 16.00, 11.39)),
			new Cell(1, 3, 1, Block.box(4.61, 0.00, 4.61, 7.81, 16.00, 7.81)),
			new Cell(-1, 4, -1, Block.box(10.17, 0.00, 10.17, 13.37, 16.00, 13.37)),
			new Cell(-1, 4, 1, Block.box(10.17, 0.00, 2.63, 13.37, 16.00, 5.83)),
			new Cell(1, 4, -1, Block.box(2.63, 0.00, 10.17, 5.83, 16.00, 13.37)),
			new Cell(1, 4, 1, Block.box(2.63, 0.00, 2.63, 5.83, 16.00, 5.83)),
			new Cell(-1, 5, -1, Block.box(12.14, 0.00, 12.14, 15.34, 16.00, 15.34)),
			new Cell(-1, 5, 1, Block.box(12.14, 0.00, 0.66, 15.34, 16.00, 3.86)),
			new Cell(1, 5, -1, Block.box(0.66, 0.00, 12.14, 3.86, 16.00, 15.34)),
			new Cell(1, 5, 1, Block.box(0.66, 0.00, 0.66, 3.86, 16.00, 3.86)),
			new Cell(-1, 6, -1, Block.box(14.12, 0.00, 14.12, 16.00, 16.00, 16.00)),
			new Cell(-1, 6, 0, Shapes.or(Block.box(14.12, 0.00, 0.00, 16.00, 16.00, 1.32),
					Block.box(14.12, 0.00, 14.68, 16.00, 16.00, 16.00))),
			new Cell(-1, 6, 1, Block.box(14.12, 0.00, 0.00, 16.00, 16.00, 1.88)),
			new Cell(0, 6, -1, Shapes.or(Block.box(0.00, 0.00, 14.12, 1.32, 16.00, 16.00),
					Block.box(14.68, 0.00, 14.12, 16.00, 16.00, 16.00))),
			new Cell(0, 6, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.32, 16.00, 1.32),
					Block.box(0.00, 0.00, 14.68, 1.32, 16.00, 16.00),
					Block.box(14.68, 0.00, 0.00, 16.00, 16.00, 1.32),
					Block.box(14.68, 0.00, 14.68, 16.00, 16.00, 16.00))),
			new Cell(0, 6, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.32, 16.00, 1.88),
					Block.box(14.68, 0.00, 0.00, 16.00, 16.00, 1.88))),
			new Cell(1, 6, -1, Block.box(0.00, 0.00, 14.12, 1.88, 16.00, 16.00)),
			new Cell(1, 6, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.88, 16.00, 1.32),
					Block.box(0.00, 0.00, 14.68, 1.88, 16.00, 16.00))),
			new Cell(1, 6, 1, Block.box(0.00, 0.00, 0.00, 1.88, 16.00, 1.88)),
			new Cell(-3, 7, -1, Block.box(0.00, 1.51, 5.20, 3.40, 3.76, 16.00)),
			new Cell(-3, 7, 0, Shapes.or(Block.box(0.00, 1.51, 0.00, 3.40, 3.76, 4.93),
					Block.box(0.00, 1.51, 11.07, 3.40, 3.76, 16.00),
					Block.box(1.48, 2.73, 3.18, 16.00, 4.97, 12.82))),
			new Cell(-3, 7, 1, Block.box(0.00, 1.51, 0.00, 3.40, 3.76, 10.80)),
			new Cell(-2, 7, -1, Block.box(10.82, 1.51, 1.98, 14.66, 3.76, 16.00)),
			new Cell(-2, 7, 0, Shapes.or(Block.box(0.00, 2.73, 1.73, 16.00, 4.97, 14.27),
					Block.box(10.82, 1.51, 0.00, 14.66, 3.76, 1.72),
					Block.box(10.82, 1.51, 14.28, 14.66, 3.76, 16.00))),
			new Cell(-2, 7, 1, Block.box(10.82, 1.51, 0.00, 14.66, 3.76, 14.02)),
			new Cell(-1, 7, 0, Block.box(0.00, 2.73, 0.77, 16.00, 4.97, 15.23)),
			new Cell(0, 7, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 2.94, 16.00, 2.94),
					Block.box(0.00, 0.00, 13.06, 2.94, 16.00, 16.00),
					Block.box(0.00, 2.73, 0.77, 1.19, 4.97, 15.23),
					Block.box(13.06, 0.00, 0.00, 16.00, 16.00, 2.94),
					Block.box(13.06, 0.00, 13.06, 16.00, 16.00, 16.00),
					Block.box(14.81, 2.73, 0.77, 16.00, 4.97, 15.23))),
			new Cell(1, 7, 0, Block.box(0.00, 2.73, 0.77, 16.00, 4.97, 15.23)),
			new Cell(2, 7, -1, Block.box(1.34, 1.51, 1.98, 5.18, 3.76, 16.00)),
			new Cell(2, 7, 0, Shapes.or(Block.box(0.00, 2.73, 1.73, 16.00, 4.97, 14.27),
					Block.box(1.34, 1.51, 0.00, 5.18, 3.76, 1.72),
					Block.box(1.34, 1.51, 14.28, 5.18, 3.76, 16.00))),
			new Cell(2, 7, 1, Block.box(1.34, 1.51, 0.00, 5.18, 3.76, 14.02)),
			new Cell(3, 7, -1, Block.box(12.60, 1.51, 5.20, 16.00, 3.76, 16.00)),
			new Cell(3, 7, 0, Shapes.or(Block.box(0.00, 2.73, 3.18, 14.52, 4.97, 12.82),
					Block.box(12.60, 1.51, 0.00, 16.00, 3.76, 4.93),
					Block.box(12.60, 1.51, 11.07, 16.00, 3.76, 16.00))),
			new Cell(3, 7, 1, Block.box(12.60, 1.51, 0.00, 16.00, 3.76, 10.80)),
			new Cell(0, 8, 0, Shapes.or(Block.box(0.35, 0.00, 0.35, 3.55, 16.00, 3.55),
					Block.box(0.35, 0.00, 12.45, 3.55, 16.00, 15.65),
					Block.box(12.45, 0.00, 0.35, 15.65, 16.00, 3.55),
					Block.box(12.45, 0.00, 12.45, 15.65, 16.00, 15.65))),
			new Cell(-2, 9, -1, Block.box(0.60, 5.29, 5.20, 4.44, 7.54, 16.00)),
			new Cell(-2, 9, 0, Shapes.or(Block.box(0.60, 5.29, 0.00, 4.44, 7.54, 4.93),
					Block.box(0.60, 5.29, 11.07, 4.44, 7.54, 16.00),
					Block.box(2.52, 6.51, 4.12, 16.00, 8.75, 11.88))),
			new Cell(-2, 9, 1, Block.box(0.60, 5.29, 0.00, 4.44, 7.54, 10.80)),
			new Cell(-1, 9, 0, Block.box(0.00, 6.51, 2.12, 16.00, 8.75, 13.88)),
			new Cell(0, 9, 0, Shapes.or(Block.box(0.00, 6.51, 2.12, 2.54, 8.75, 13.88),
					Block.box(0.95, 0.00, 0.95, 4.15, 16.00, 4.15),
					Block.box(0.95, 0.00, 11.85, 4.15, 16.00, 15.05),
					Block.box(11.85, 0.00, 0.95, 15.05, 16.00, 4.15),
					Block.box(11.85, 0.00, 11.85, 15.05, 16.00, 15.05),
					Block.box(13.46, 6.51, 2.12, 16.00, 8.75, 13.88))),
			new Cell(1, 9, 0, Block.box(0.00, 6.51, 2.12, 16.00, 8.75, 13.88)),
			new Cell(2, 9, -1, Block.box(11.56, 5.29, 5.20, 15.40, 7.54, 16.00)),
			new Cell(2, 9, 0, Shapes.or(Block.box(0.00, 6.51, 4.12, 13.48, 8.75, 11.88),
					Block.box(11.56, 5.29, 0.00, 15.40, 7.54, 4.93),
					Block.box(11.56, 5.29, 11.07, 15.40, 7.54, 16.00))),
			new Cell(2, 9, 1, Block.box(11.56, 5.29, 0.00, 15.40, 7.54, 10.80)),
			new Cell(0, 10, 0, Shapes.or(Block.box(2.62, 0.00, 2.62, 5.82, 16.00, 5.82),
					Block.box(2.62, 0.00, 10.18, 5.82, 16.00, 13.38),
					Block.box(6.01, 12.36, 6.01, 9.99, 16.00, 9.99),
					Block.box(10.18, 0.00, 2.62, 13.38, 16.00, 5.82),
					Block.box(10.18, 0.00, 10.18, 13.38, 16.00, 13.38))),
			new Cell(0, 11, 0, Block.box(4.66, 0.00, 4.66, 11.34, 8.00, 11.34)));
	/** ---- 75 cell(s), printed by tools/gen_tower_models.py --java ---- */
	private static final List<Cell> TERMINAL_CELLS = List.of(
			new Cell(-2, 0, -2, Shapes.or(Block.box(13.25, 0.00, 13.25, 16.00, 1.53, 16.00),
					Block.box(15.02, 0.00, 15.02, 16.00, 16.00, 16.00))),
			new Cell(-2, 0, -1, Shapes.or(Block.box(13.25, 0.00, 0.00, 16.00, 1.53, 1.68),
					Block.box(15.02, 0.00, 0.00, 16.00, 16.00, 2.22))),
			new Cell(-2, 0, 1, Shapes.or(Block.box(13.25, 0.00, 14.32, 16.00, 1.53, 16.00),
					Block.box(15.02, 0.00, 13.78, 16.00, 16.00, 16.00))),
			new Cell(-2, 0, 2, Shapes.or(Block.box(13.25, 0.00, 0.00, 16.00, 1.53, 2.75),
					Block.box(15.02, 0.00, 0.00, 16.00, 16.00, 0.98))),
			new Cell(-1, 0, -2, Shapes.or(Block.box(0.00, 0.00, 13.25, 1.68, 1.53, 16.00),
					Block.box(0.00, 0.00, 15.02, 2.22, 16.00, 16.00))),
			new Cell(-1, 0, -1, Block.box(0.00, 0.00, 0.00, 2.22, 16.00, 2.22)),
			new Cell(-1, 0, 1, Block.box(0.00, 0.00, 13.78, 2.22, 16.00, 16.00)),
			new Cell(-1, 0, 2, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.68, 1.53, 2.75),
					Block.box(0.00, 0.00, 0.00, 2.22, 16.00, 0.98))),
			new Cell(1, 0, -2, Shapes.or(Block.box(13.78, 0.00, 15.02, 16.00, 16.00, 16.00),
					Block.box(14.32, 0.00, 13.25, 16.00, 1.53, 16.00))),
			new Cell(1, 0, -1, Block.box(13.78, 0.00, 0.00, 16.00, 16.00, 2.22)),
			new Cell(1, 0, 1, Block.box(13.78, 0.00, 13.78, 16.00, 16.00, 16.00)),
			new Cell(1, 0, 2, Shapes.or(Block.box(13.78, 0.00, 0.00, 16.00, 16.00, 0.98),
					Block.box(14.32, 0.00, 0.00, 16.00, 1.53, 2.75))),
			new Cell(2, 0, -2, Shapes.or(Block.box(0.00, 0.00, 13.25, 2.75, 1.53, 16.00),
					Block.box(0.00, 0.00, 15.02, 0.98, 16.00, 16.00))),
			new Cell(2, 0, -1, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.98, 16.00, 2.22),
					Block.box(0.00, 0.00, 0.00, 2.75, 1.53, 1.68))),
			new Cell(2, 0, 1, Shapes.or(Block.box(0.00, 0.00, 13.78, 0.98, 16.00, 16.00),
					Block.box(0.00, 0.00, 14.32, 2.75, 1.53, 16.00))),
			new Cell(2, 0, 2, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.98, 16.00, 0.98),
					Block.box(0.00, 0.00, 0.00, 2.75, 1.53, 2.75))),
			new Cell(-1, 1, -1, Block.box(1.33, 0.00, 1.33, 4.53, 16.00, 4.53)),
			new Cell(-1, 1, 1, Block.box(1.33, 0.00, 11.47, 4.53, 16.00, 14.67)),
			new Cell(1, 1, -1, Block.box(11.47, 0.00, 1.33, 14.67, 16.00, 4.53)),
			new Cell(1, 1, 1, Block.box(11.47, 0.00, 11.47, 14.67, 16.00, 14.67)),
			new Cell(-1, 2, -1, Block.box(3.63, 0.00, 3.63, 6.83, 16.00, 6.83)),
			new Cell(-1, 2, 1, Block.box(3.63, 0.00, 9.17, 6.83, 16.00, 12.37)),
			new Cell(1, 2, -1, Block.box(9.17, 0.00, 3.63, 12.37, 16.00, 6.83)),
			new Cell(1, 2, 1, Block.box(9.17, 0.00, 9.17, 12.37, 16.00, 12.37)),
			new Cell(-1, 3, -1, Block.box(5.94, 0.00, 5.94, 9.14, 16.00, 9.14)),
			new Cell(-1, 3, 1, Block.box(5.94, 0.00, 6.86, 9.14, 16.00, 10.06)),
			new Cell(1, 3, -1, Block.box(6.86, 0.00, 5.94, 10.06, 16.00, 9.14)),
			new Cell(1, 3, 1, Block.box(6.86, 0.00, 6.86, 10.06, 16.00, 10.06)),
			new Cell(-1, 4, -1, Block.box(8.24, 0.00, 8.24, 11.44, 16.00, 11.44)),
			new Cell(-1, 4, 1, Block.box(8.24, 0.00, 4.56, 11.44, 16.00, 7.76)),
			new Cell(1, 4, -1, Block.box(4.56, 0.00, 8.24, 7.76, 16.00, 11.44)),
			new Cell(1, 4, 1, Block.box(4.56, 0.00, 4.56, 7.76, 16.00, 7.76)),
			new Cell(-1, 5, -1, Block.box(10.55, 0.00, 10.55, 13.75, 16.00, 13.75)),
			new Cell(-1, 5, 1, Block.box(10.55, 0.00, 2.25, 13.75, 16.00, 5.45)),
			new Cell(1, 5, -1, Block.box(2.25, 0.00, 10.55, 5.45, 16.00, 13.75)),
			new Cell(1, 5, 1, Block.box(2.25, 0.00, 2.25, 5.45, 16.00, 5.45)),
			new Cell(-1, 6, -1, Block.box(12.85, 0.00, 12.85, 16.00, 16.00, 16.00)),
			new Cell(-1, 6, 1, Block.box(12.85, 0.00, 0.00, 16.00, 16.00, 3.15)),
			new Cell(1, 6, -1, Block.box(0.00, 0.00, 12.85, 3.15, 16.00, 16.00)),
			new Cell(1, 6, 1, Block.box(0.00, 0.00, 0.00, 3.15, 16.00, 3.15)),
			new Cell(-3, 7, -1, Block.box(0.00, 1.48, 5.20, 3.40, 3.73, 16.00)),
			new Cell(-3, 7, 0, Shapes.or(Block.box(0.00, 1.48, 0.00, 3.40, 3.73, 4.93),
					Block.box(1.48, 2.73, 2.48, 16.00, 4.97, 13.52))),
			new Cell(-2, 7, -1, Block.box(10.82, 1.48, 1.32, 14.66, 3.73, 16.00)),
			new Cell(-2, 7, 0, Shapes.or(Block.box(0.00, 2.73, 0.73, 16.00, 4.97, 15.27),
					Block.box(10.82, 1.48, 0.00, 14.66, 3.73, 1.05))),
			new Cell(-1, 7, -1, Block.box(14.75, 0.00, 14.75, 16.00, 16.00, 16.00)),
			new Cell(-1, 7, 0, Shapes.or(Block.box(0.00, 2.73, 0.00, 16.00, 4.97, 16.00),
					Block.box(14.75, 0.00, 0.00, 16.00, 16.00, 1.95),
					Block.box(14.75, 0.00, 14.05, 16.00, 16.00, 16.00))),
			new Cell(-1, 7, 1, Block.box(14.75, 0.00, 0.00, 16.00, 16.00, 1.25)),
			new Cell(0, 7, -1, Shapes.or(Block.box(0.00, 0.00, 14.75, 1.95, 16.00, 16.00),
					Block.box(14.05, 0.00, 14.75, 16.00, 16.00, 16.00))),
			new Cell(0, 7, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.95, 16.00, 1.95),
					Block.box(0.00, 0.00, 14.05, 1.95, 16.00, 16.00),
					Block.box(14.05, 0.00, 0.00, 16.00, 16.00, 1.95),
					Block.box(14.05, 0.00, 14.05, 16.00, 16.00, 16.00))),
			new Cell(0, 7, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.95, 16.00, 1.25),
					Block.box(14.05, 0.00, 0.00, 16.00, 16.00, 1.25))),
			new Cell(1, 7, -1, Block.box(0.00, 0.00, 14.75, 1.25, 16.00, 16.00)),
			new Cell(1, 7, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 1.25, 16.00, 1.95),
					Block.box(0.00, 0.00, 14.05, 1.25, 16.00, 16.00),
					Block.box(0.00, 2.73, 0.00, 16.00, 4.97, 16.00))),
			new Cell(1, 7, 1, Block.box(0.00, 0.00, 0.00, 1.25, 16.00, 1.25)),
			new Cell(2, 7, -1, Block.box(1.34, 1.48, 1.32, 5.18, 3.73, 16.00)),
			new Cell(2, 7, 0, Shapes.or(Block.box(0.00, 2.73, 0.73, 16.00, 4.97, 15.27),
					Block.box(1.34, 1.48, 0.00, 5.18, 3.73, 1.05))),
			new Cell(3, 7, -1, Block.box(12.60, 1.48, 5.20, 16.00, 3.73, 16.00)),
			new Cell(3, 7, 0, Shapes.or(Block.box(0.00, 2.73, 2.48, 14.52, 4.97, 13.52),
					Block.box(12.60, 1.48, 0.00, 16.00, 3.73, 4.93))),
			new Cell(-1, 8, -1, Block.box(15.48, 0.00, 15.48, 16.00, 16.00, 16.00)),
			new Cell(-1, 8, 0, Shapes.or(Block.box(15.48, 0.00, 0.00, 16.00, 16.00, 2.68),
					Block.box(15.48, 0.00, 13.32, 16.00, 16.00, 16.00))),
			new Cell(-1, 8, 1, Block.box(15.48, 0.00, 0.00, 16.00, 16.00, 0.52)),
			new Cell(0, 8, -1, Shapes.or(Block.box(0.00, 0.00, 15.48, 2.68, 16.00, 16.00),
					Block.box(13.32, 0.00, 15.48, 16.00, 16.00, 16.00))),
			new Cell(0, 8, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 2.68, 16.00, 2.68),
					Block.box(0.00, 0.00, 13.32, 2.68, 16.00, 16.00),
					Block.box(13.32, 0.00, 0.00, 16.00, 16.00, 2.68),
					Block.box(13.32, 0.00, 13.32, 16.00, 16.00, 16.00))),
			new Cell(0, 8, 1, Shapes.or(Block.box(0.00, 0.00, 0.00, 2.68, 16.00, 0.52),
					Block.box(13.32, 0.00, 0.00, 16.00, 16.00, 0.52))),
			new Cell(1, 8, -1, Block.box(0.00, 0.00, 15.48, 0.52, 16.00, 16.00)),
			new Cell(1, 8, 0, Shapes.or(Block.box(0.00, 0.00, 0.00, 0.52, 16.00, 2.68),
					Block.box(0.00, 0.00, 13.32, 0.52, 16.00, 16.00))),
			new Cell(1, 8, 1, Block.box(0.00, 0.00, 0.00, 0.52, 16.00, 0.52)),
			new Cell(-2, 9, -1, Block.box(0.60, 5.25, 5.20, 4.44, 7.51, 16.00)),
			new Cell(-2, 9, 0, Shapes.or(Block.box(0.60, 5.25, 0.00, 4.44, 7.51, 4.93),
					Block.box(2.52, 6.51, 3.63, 16.00, 8.75, 12.37))),
			new Cell(-1, 9, 0, Block.box(0.00, 6.51, 1.34, 16.00, 8.75, 14.66)),
			new Cell(0, 9, 0, Shapes.or(Block.box(0.00, 6.51, 1.34, 1.79, 8.75, 14.66),
					Block.box(0.21, 0.00, 0.21, 3.41, 16.00, 3.41),
					Block.box(0.21, 0.00, 12.59, 3.41, 16.00, 15.79),
					Block.box(12.59, 0.00, 0.21, 15.79, 16.00, 3.41),
					Block.box(12.59, 0.00, 12.59, 15.79, 16.00, 15.79),
					Block.box(14.21, 6.51, 1.34, 16.00, 8.75, 14.66))),
			new Cell(1, 9, 0, Block.box(0.00, 6.51, 1.34, 16.00, 8.75, 14.66)),
			new Cell(2, 9, -1, Block.box(11.56, 5.25, 5.20, 15.40, 7.51, 16.00)),
			new Cell(2, 9, 0, Shapes.or(Block.box(0.00, 6.51, 3.63, 13.48, 8.75, 12.37),
					Block.box(11.56, 5.25, 0.00, 15.40, 7.51, 4.93))),
			new Cell(0, 10, 0, Shapes.or(Block.box(2.17, 0.00, 2.17, 5.37, 16.00, 5.37),
					Block.box(2.17, 0.00, 10.63, 5.37, 16.00, 13.83),
					Block.box(6.01, 12.36, 6.01, 9.99, 16.00, 9.99),
					Block.box(10.63, 0.00, 2.17, 13.83, 16.00, 5.37),
					Block.box(10.63, 0.00, 10.63, 13.83, 16.00, 13.83))),
			new Cell(0, 11, 0, Block.box(4.56, 0.00, 4.56, 11.44, 8.00, 11.44)));

	private final TowerSpec spec;

	public LatticeTowerBlock(Properties properties, TowerSpec spec) {
		super(properties.sound(SoundType.METAL));
		this.spec = spec;
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	public TowerSpec spec() {
		return spec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shellShape(state);
	}

	/** A structure or a schematic placed turned turns the machine with it, as MachineBlock does. */
	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public List<Cell> shellCells(BlockState state) {
		return switch (spec.duty()) {
			case SUSPENSION -> SUSPENSION_CELLS;
			case TENSION -> TENSION_CELLS;
			case TERMINAL -> TERMINAL_CELLS;
		};
	}

	@Override
	public Direction shellFacing(BlockState state) {
		return state.getValue(FACING);
	}

	@Override
	public Direction shellAuthored() {
		return AUTHORED;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Nullable @Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LatticeTowerBlockEntity(pos, state);
	}

	/** Nothing of its own to tick, but its cells are worth mending: a tower is a lot of them. */
	@Nullable @Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, blockState, blockEntity) -> MachineShell.heal(lvl, pos, blockState);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		MachineShell.place(level, pos, state);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			MachineShell.clear(level, pos, state);
			if (!level.isClientSide && level instanceof ServerLevel serverLevel
					&& level.getBlockEntity(pos) instanceof LatticeTowerBlockEntity tower) {
				WireLedger.removeAttachedTo(serverLevel, tower.getInsulatorIds());
			}
		}

		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
