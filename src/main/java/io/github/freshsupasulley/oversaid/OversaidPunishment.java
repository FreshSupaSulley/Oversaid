package io.github.freshsupasulley.oversaid;

import io.github.freshsupasulley.oversaid.forge.Oversaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.*;
import java.util.function.Function;

public class OversaidPunishment {
	
	private OversaidCategory category;
	private int punishmentIndex;
	private boolean abandonOnFail;
	
	// Required
	public OversaidPunishment()
	{
		// Pick a random category based on configurable weights
		this.category = OversaidCategory.random();
		this.punishmentIndex = findRandomPunishment(category);
	}
	
	/**
	 * Use this for manually invoking the punishment with non-random args.
	 *
	 * <p>This will <b>not</b> fallback to another punishment if failed.</p>
	 *
	 * @param punishmentIndex {@link SidedPunishment} index
	 */
	public OversaidPunishment(OversaidCategory category, int punishmentIndex)
	{
		this.abandonOnFail = true;
		this.category = category;
		this.punishmentIndex = punishmentIndex;
	}
	
	private int findRandomPunishment(OversaidCategory category)
	{
		// Now pick a random punishment within that category
		List<Integer> candidates = new ArrayList<>();
		
		for(int i = 0; i < Oversaid.punishments.size(); i++)
		{
			var sample = Oversaid.punishments.get(i);
			
			if(sample.getCategory() == category)
			{
				candidates.add(i);
			}
		}
		
		if(candidates.isEmpty())
			throw new IllegalStateException("No punishments for category " + category);
		
		return candidates.get((int) (Math.random() * candidates.size()));
	}
	
	public int getPunishmentIndex()
	{
		return punishmentIndex;
	}
	
	public OversaidCategory getCategory()
	{
		return category;
	}
	
	public void punish(ServerPlayer player)
	{
		// If we picked a server-side punishment
		if(Oversaid.punishments.get(punishmentIndex).isServerSide())
		{
			punishInternal(player, Entity::getUUID);
		}
	}
	
	private <T> void punishInternal(T context, Function<T, UUID> uuidGetter)
	{
		// The first punishment selected on instantiation indicates the sidedness
		boolean isServerSide = Oversaid.punishments.get(punishmentIndex).isServerSide();
		String sideLog = (isServerSide ? "server" : "client") + "-side";
		final int maxAttempts = 10;
		boolean punished = false;
		
		// Only go for this many attempts to avoid an endless loop
		for(int i = 0; i < maxAttempts; i++)
		{
			// If no punishment was found (see bottom of loop)
			if(punishmentIndex != -1)
			{
				var punishment = Oversaid.punishments.get(punishmentIndex);
				
				Oversaid.LOGGER.debug("Attempting {} to punish from category '{}'", sideLog, category);
				
				//noinspection unchecked
				if(((SidedPunishment<T>) punishment).punish(context))
				{
					if(punishment.hasReset())
					{
						UUID uuid = uuidGetter.apply(context);
						Oversaid.addReset(uuid, this, punishment);
					}
					
					punished = true;
					break;
				}
				else
				{
					Oversaid.LOGGER.info("{} punishment failed", category);
					
					if(abandonOnFail)
					{
						Oversaid.LOGGER.info("Not trying another punishment");
						break;
					}
				}
			}
			
			// Pick another category and punishment since this didn't work
			this.category = OversaidCategory.random();
			// if we add client punishments back we need to ensure this picks the proper side too
			this.punishmentIndex = findRandomPunishment(category);
		}
		
		if(!punished)
		{
			if(!abandonOnFail)
			{
				Oversaid.LOGGER.error("Attempted a {} punishment {} times and always failed", sideLog, maxAttempts);
			}
			
			// Pass to caller
			throw new IllegalStateException("Failed to execute punishment");
		}
	}
}
