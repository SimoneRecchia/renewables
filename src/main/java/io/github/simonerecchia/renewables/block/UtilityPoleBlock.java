package io.github.simonerecchia.renewables.block;

import io.github.simonerecchia.renewables.item.ClientHooks;
import io.github.simonerecchia.renewables.item.Tools;
import io.github.simonerecchia.renewables.registry.ModBlockEntities;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * The utility pole: a concrete shaft seven cells tall with two crossarms, eight pin insulators, and a
 * configuration screen for how the arms are set.
 *
 * It fills fifteen cells, which is a {@link MachineShell}: a column of seven, and five wide at the height of the
 * arms.  The table is cut from the model by {@code tools/check_hitboxes.py --java} and follows it a pixel at a
 * time - the shaft tapers as the cone does, and under each arm there is the brace and not a wall.  It does not
 * follow the arms' offset, yaw and pitch (grid-blocks.md section 3.1): those move what is drawn and where the
 * wires end, and a pole leaning a quarter turn collides where it stands.
 */
public class UtilityPoleBlock extends MachineBlock {
	/** The facing utility_pole.obj is modelled at: the crossarms run east-west. */
	public static final Direction AUTHORED = Direction.NORTH;

	// ---- 13 cell(s), printed by tools/check_hitboxes.py --java ----
	private static final List<Cell> CELLS = List.of(
			new Cell(0, 0, 0, Shapes.or(Block.box(3.73, 0.00, 3.73, 12.27, 2.00, 12.27),
					Block.box(4.11, 2.00, 4.11, 11.89, 16.00, 11.89),
					Block.box(7.58, 1.60, 10.46, 8.42, 16.00, 12.04))),
			new Cell(0, 1, 0, Shapes.or(Block.box(2.88, 11.19, 7.62, 4.64, 11.85, 8.38),
					Block.box(4.11, 0.00, 4.11, 11.89, 4.00, 11.89),
					Block.box(4.37, 4.00, 4.37, 11.63, 16.00, 11.63),
					Block.box(6.32, 6.72, 4.25, 9.68, 10.88, 4.51),
					Block.box(7.58, 0.00, 10.46, 8.42, 16.00, 12.04),
					Block.box(11.50, 1.27, 7.62, 13.26, 1.93, 8.38))),
			new Cell(0, 2, 0, Shapes.or(Block.box(3.17, 15.03, 7.62, 4.93, 15.69, 8.38),
					Block.box(4.37, 0.00, 4.37, 11.63, 6.00, 11.63),
					Block.box(4.63, 6.00, 4.63, 11.37, 16.00, 11.37),
					Block.box(7.58, 0.00, 10.46, 8.42, 16.00, 12.04),
					Block.box(11.21, 5.11, 7.62, 12.97, 5.77, 8.38))),
			new Cell(0, 3, 0, Shapes.or(Block.box(4.63, 0.00, 4.63, 11.37, 8.00, 11.37),
					Block.box(4.90, 8.00, 4.90, 11.10, 16.00, 11.10),
					Block.box(7.58, 0.00, 10.46, 8.42, 16.00, 12.04),
					Block.box(10.93, 8.95, 7.62, 12.69, 9.61, 8.38))),
			new Cell(0, 4, 0, Shapes.or(Block.box(0.00, 8.00, 7.65, 16.00, 11.00, 8.35),
					Block.box(0.00, 11.00, 6.25, 16.00, 14.99, 9.75),
					Block.box(0.72, 7.00, 7.65, 15.28, 8.00, 8.35),
					Block.box(1.96, 6.00, 7.65, 14.04, 7.00, 8.35),
					Block.box(3.20, 5.00, 7.65, 12.80, 6.00, 8.35),
					Block.box(3.46, 2.87, 7.62, 5.22, 3.53, 8.38),
					Block.box(4.45, 4.28, 7.65, 11.55, 5.00, 8.35),
					Block.box(4.90, 0.00, 4.90, 11.10, 10.00, 11.10),
					Block.box(5.16, 10.00, 5.16, 10.84, 16.00, 10.84),
					Block.box(7.58, 0.00, 10.46, 8.42, 16.00, 12.04))),
			new Cell(-1, 4, 0, Shapes.or(Block.box(0.00, 14.61, 6.67, 0.37, 16.00, 9.33),
					Block.box(0.00, 11.00, 6.25, 16.00, 14.99, 9.75),
					Block.box(10.03, 14.61, 6.67, 12.69, 16.00, 9.33),
					Block.box(12.99, 10.00, 7.65, 16.00, 11.00, 8.35),
					Block.box(14.23, 9.00, 7.65, 16.00, 10.00, 8.35),
					Block.box(15.48, 8.00, 7.65, 16.00, 9.00, 8.35))),
			new Cell(1, 4, 0, Shapes.or(Block.box(0.00, 8.00, 7.65, 0.52, 9.00, 8.35),
					Block.box(0.00, 9.00, 7.65, 1.77, 10.00, 8.35),
					Block.box(0.00, 10.00, 7.65, 3.01, 11.00, 8.35),
					Block.box(0.00, 11.00, 6.25, 16.00, 14.99, 9.75),
					Block.box(3.31, 14.61, 6.67, 5.97, 16.00, 9.33),
					Block.box(15.63, 14.61, 6.67, 16.00, 16.00, 9.33))),
			new Cell(-2, 4, 0, Shapes.or(Block.box(12.64, 11.00, 6.25, 16.00, 14.99, 9.75),
					Block.box(13.71, 14.61, 6.67, 16.00, 16.00, 9.33))),
			new Cell(2, 4, 0, Shapes.or(Block.box(0.00, 14.61, 6.67, 2.29, 16.00, 9.33),
					Block.box(0.00, 11.00, 6.25, 3.36, 14.99, 9.75))),
			new Cell(0, 5, 0, Shapes.or(Block.box(0.00, 9.00, 7.65, 16.00, 10.00, 8.35),
					Block.box(0.00, 10.00, 6.25, 16.00, 12.00, 9.75),
					Block.box(0.00, 12.00, 6.56, 16.00, 13.00, 9.44),
					Block.box(0.00, 13.00, 6.25, 16.00, 14.00, 9.75),
					Block.box(0.37, 8.00, 7.65, 15.63, 9.00, 8.35),
					Block.box(1.31, 7.00, 7.65, 14.69, 8.00, 8.35),
					Block.box(2.25, 6.00, 7.65, 13.75, 7.00, 8.35),
					Block.box(3.19, 5.00, 7.65, 12.81, 6.00, 8.35),
					Block.box(4.13, 4.00, 7.65, 11.87, 5.00, 8.35),
					Block.box(5.07, 3.50, 7.65, 10.93, 4.00, 8.35),
					Block.box(5.16, 0.00, 5.16, 10.84, 12.00, 10.84),
					Block.box(5.42, 12.00, 5.42, 10.58, 16.00, 10.58),
					Block.box(7.58, 0.00, 10.46, 8.42, 16.00, 12.04))),
			new Cell(-1, 5, 0, Shapes.or(Block.box(0.00, 0.00, 6.67, 0.37, 1.38, 9.33),
					Block.box(2.56, 10.00, 6.25, 16.00, 12.00, 9.75),
					Block.box(2.56, 12.00, 6.56, 16.00, 13.00, 9.44),
					Block.box(2.56, 13.00, 6.25, 16.00, 14.00, 9.75),
					Block.box(3.63, 13.81, 6.67, 6.29, 16.00, 9.33),
					Block.box(10.03, 0.00, 6.67, 12.69, 1.38, 9.33),
					Block.box(12.83, 13.81, 6.67, 15.49, 16.00, 9.33),
					Block.box(15.43, 9.00, 7.65, 16.00, 10.00, 8.35))),
			new Cell(1, 5, 0, Shapes.or(Block.box(0.00, 9.00, 7.65, 0.57, 10.00, 8.35),
					Block.box(0.00, 10.00, 6.25, 13.44, 12.00, 9.75),
					Block.box(0.00, 12.00, 6.56, 13.44, 13.00, 9.44),
					Block.box(0.00, 13.00, 6.25, 13.44, 14.00, 9.75),
					Block.box(0.51, 13.81, 6.67, 3.17, 16.00, 9.33),
					Block.box(3.31, 0.00, 6.67, 5.97, 1.38, 9.33),
					Block.box(9.71, 13.81, 6.67, 12.37, 16.00, 9.33),
					Block.box(15.63, 0.00, 6.67, 16.00, 1.38, 9.33))),
			new Cell(0, 6, 0, Shapes.or(Block.box(5.42, 0.00, 5.42, 10.58, 4.00, 10.58),
					Block.box(7.58, 0.00, 10.46, 8.42, 0.80, 12.04))));

	public UtilityPoleBlock(Properties properties) {
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

	/**
	 * An empty hand opens the arms' configuration; the screen is the client's, and the server does nothing
	 * about it until the screen sends its numbers back.
	 *
	 * Anything in the hand, or a tool of the mod in the other one, passes the click on to the item: a reel strings a
	 * wire, a block is built against it, the wrench reads it.  A shell forwards its clicks here, so the same holds on
	 * any cell of the pole.
	 */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!Tools.emptyHanded(player, hand)) return InteractionResult.PASS;
		// moving the arms moves the wires: a change to the world, which adventure and spectator may not make
		if (!level.isClientSide || !player.mayBuild()) return InteractionResult.PASS;

		ClientHooks.openPoleConfiguration(pos);
		return InteractionResult.SUCCESS;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return ModBlockEntities.UTILITY_POLE.get().create(pos, state);
	}
}
