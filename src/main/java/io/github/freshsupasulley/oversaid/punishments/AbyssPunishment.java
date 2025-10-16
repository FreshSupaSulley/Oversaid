package io.github.freshsupasulley.oversaid.punishments;

import io.github.freshsupasulley.oversaid.OversaidCategory;
import io.github.freshsupasulley.oversaid.ServerPunishment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AbyssPunishment extends ServerPunishment {
	
	private Map<BlockPos, BlockState> restore = new HashMap<>();
	
	public AbyssPunishment(OversaidCategory category)
	{
		// Stop them halfway through their fall to bury them in stone
		super(category, "The abyss doth beckon thee", TimeUnit.SECONDS, 3);
	}
	
	@Override
	protected boolean internalPunish(ServerPlayer player)
	{
		var level = player.level();
		int blocksDeleted = 0;
		final int radius = 3;
		BlockPos centerPos = player.blockPosition();
		
		// Do this at most the # of blocks tall this dimension is
		for(int dx = -radius; dx <= radius; dx++)
		{
			for(int dz = -radius; dz <= radius; dz++)
			{
				BlockPos columnPos = centerPos.offset(dx, 0, dz);
				
				// Dig downward in this column
				for(int y = columnPos.getY(); y >= level.dimensionType().minY(); y--)
				{
					BlockPos targetPos = new BlockPos(columnPos.getX(), y, columnPos.getZ());
					BlockState state = level.getBlockState(targetPos);
					
					// Skip air blocks
					if(state.isAir())
					{
						continue;
					}
					
					// Stop if block is unbreakable
					// ^ nah it gets restored anyways
//					if(state.getDestroySpeed(level, targetPos) == -1.0F)
//					{
//						break;
//					}
					
					// We need to restore this later
					restore.put(targetPos, state.getBlock().defaultBlockState());
					
					blocksDeleted++;
					level.setBlockAndUpdate(targetPos, Blocks.AIR.defaultBlockState());
				}
			}
		}
		
		return blocksDeleted > 0;
	}
	
	@Override
	protected void internalReset(ServerPlayer player)
	{
		restore.forEach((pos, state) -> player.level().setBlockAndUpdate(pos, state));
		restore.clear();
	}
}
