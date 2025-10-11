package io.github.freshsupasulley.taboo_trickler;

import io.github.freshsupasulley.taboo_trickler.forge.TabooTrickler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AnvilPunishment extends ServerPunishment {
	
	private List<Entity> anvils = new ArrayList<>();
	
	public AnvilPunishment(TricklerCategory category)
	{
		super(category, "Heads up", TimeUnit.SECONDS, 3);
	}
	
	@Override
	protected boolean internalPunish(ServerPlayer player)
	{
		// Constants
		final var level = player.level();
		final int size = 4, yOffsetAboveFeet = 15; // should be generous enough to give them time to run out of the way
		
		BlockPos center = player.blockPosition().above(yOffsetAboveFeet);
		int half = size / 2;
		int anvils = 0;
		
		for(int dx = -half; dx <= half; dx++)
		{
			for(int dz = -half; dz <= half; dz++)
			{
				BlockPos targetPos = center.offset(dx, 0, dz);
				
				if(level.isEmptyBlock(targetPos))
				{
					anvils++;
					var entity = FallingBlockEntity.fall(level, targetPos, Blocks.ANVIL.defaultBlockState());
					// ripped from the protected method
					entity.setHurtsEntities(2.0F, 40);
					
					// Add to list so we can remove them later
					this.anvils.add(entity);
				}
			}
		}
		
		return anvils > 0;
	}
	
	@Override
	protected void internalReset(ServerPlayer player)
	{
		var level = player.level();
		
		TabooTrickler.LOGGER.info("{} anvils will be deleted", anvils.size());
		
		this.anvils.stream().forEach(entity ->
		{
			var pos = entity.blockPosition();
			
			TabooTrickler.LOGGER.info("Deletion status on {}: {}", pos, level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
			
			// Also spawn a pretty ass particle cloud
			level.sendParticles(ParticleTypes.CLOUD, entity.getX(), pos.above().getY(), entity.getZ(), 10, // particle count (0 does individual spawning?)
					0.1, 0.1, 0.1, 0.05 // velocity
			);
			
			// Just in case but probably doesn't do anything
			entity.discard();
		});
		
		// IMPORTANT
		// Clear the anvils after we reset
		anvils.clear();
	}
}
