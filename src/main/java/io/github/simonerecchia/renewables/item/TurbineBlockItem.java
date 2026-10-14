package io.github.simonerecchia.renewables.item;

import io.github.simonerecchia.renewables.api.Nameplate;
import io.github.simonerecchia.renewables.api.power.TurbineSpec;
import io.github.simonerecchia.renewables.block.MachineBlock;
import io.github.simonerecchia.renewables.block.ModelFacing;
import io.github.simonerecchia.renewables.block.RotorClearance;
import io.github.simonerecchia.renewables.block.TurbineTowerBlock;
import io.github.simonerecchia.renewables.block.WindTurbineBlock;
import io.github.simonerecchia.renewables.catalog.TurbineCatalog;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A turbine in the hand, showing its nameplate the way a datasheet header reads. */
public class TurbineBlockItem extends BlockItem {
	private final TurbineSpec spec;

	public TurbineBlockItem(Block block, Properties properties, TurbineSpec spec) {
		super(block, properties);
		this.spec = spec;
	}

	public TurbineSpec spec() {
		return spec;
	}

	/**
	 * Refuses to mount on a tower that is not a height this model is built for, or where its rotor would not be out in
	 * the air - something over the nacelle, or its hub inside a block or under water - and says why.
	 *
	 * The refusal consumes nothing.  The message is sent from the server only: the client runs this too, and would
	 * otherwise show it twice.
	 */
	@Override
	public InteractionResult place(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		int segments = TurbineTowerBlock.countBelow(level, pos);

		if (!spec.acceptsTowerHeight(segments)) {
			return refuse(context, Component.translatable("message.renewables.turbine.tower_height",
					spec.displayName(), spec.minTowerSegments(), spec.maxTowerSegments(), segments));
		}

		BlockState state = getBlock().getStateForPlacement(context);
		if (state != null && state.hasProperty(MachineBlock.FACING)) {
			double modelYaw = ModelFacing.degrees(WindTurbineBlock.AUTHORED, state.getValue(MachineBlock.FACING));
			RotorClearance clearance = RotorClearance.survey(level, pos, segments, spec, modelYaw);
			if (clearance.covered()) return refuse(context, Component.translatable("message.renewables.turbine.covered", spec.displayName()));
			if (clearance.jammed()) return refuse(context, Component.translatable("message.renewables.turbine.jammed", spec.displayName()));
		}

		return super.place(context);
	}

	private static InteractionResult refuse(BlockPlaceContext context, Component why) {
		Player player = context.getPlayer();
		if (player != null && !context.getLevel().isClientSide()) {
			player.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
		}

		return InteractionResult.FAIL;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal(TurbineCatalog.fullName(spec)).withStyle(ChatFormatting.WHITE));

		if (!spec.iecClass().isEmpty()) {
			tooltip.add(Component.translatable("tooltip.renewables.turbine.class", spec.iecClass()).withStyle(ChatFormatting.DARK_GRAY));
		}

		tooltip.add(Component.translatable("tooltip.renewables.turbine.nameplate",
				Nameplate.rating(spec.ratedPowerKw()),
				Nameplate.fmt("%.0f", spec.rotorDiameterM())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.turbine.wind",
				Nameplate.fmt("%.1f", spec.cutInSpeed()),
				Nameplate.fmt("%.1f", spec.ratedSpeed()),
				Nameplate.fmt("%.1f", spec.cutOutSpeed())).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.renewables.turbine.tower",
				spec.minTowerSegments(),
				spec.maxTowerSegments(),
				Nameplate.fmt("%.0f", spec.hubHeightM(spec.minTowerSegments())),
				Nameplate.fmt("%.0f", spec.hubHeightM(spec.maxTowerSegments()))).withStyle(ChatFormatting.DARK_GRAY));
	}


}
