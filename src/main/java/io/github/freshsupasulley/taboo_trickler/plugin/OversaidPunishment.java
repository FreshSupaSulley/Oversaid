package io.github.freshsupasulley.taboo_trickler.plugin;

import io.github.freshsupasulley.censorcraft.api.punishments.Punishment;
import io.github.freshsupasulley.taboo_trickler.SidedPunishment;
import io.github.freshsupasulley.taboo_trickler.OversaidCategory;
import io.github.freshsupasulley.taboo_trickler.forge.Oversaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import java.util.function.Function;

public class OversaidPunishment extends Punishment {
	
	public static final String PUNISHMENT_NAME = "trickler";
	
	private OversaidCategory category;
	private int punishmentIndex;
	private boolean abandonOnFail;
	
	// Required
	public OversaidPunishment()
	{
		// Pick a random punishment out of a random category
		this.category = OversaidCategory.random();
		this.punishmentIndex = (int) (Math.random() * category.punishments.size());
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
	
	public OversaidCategory getCategory()
	{
		return category;
	}
	
	@Override
	public String getId()
	{
		return PUNISHMENT_NAME;
	}
	
	@Override
	protected void buildConfig()
	{
	}
	
	@Override
	public void punish(Object uncasted)
	{
		// If we picked a server-side punishment
		if(category.punishments.get(punishmentIndex).isServerSide())
		{
			ServerPlayer player = (ServerPlayer) uncasted;
			punishInternal(player, Entity::getUUID);
		}
	}
	
	private <T> void punishInternal(T context, Function<T, UUID> uuidGetter)
	{
		// The first punishment selected on instantiation indicates the sidedness
		boolean isServerSide = category.punishments.get(punishmentIndex).isServerSide();
		String sideLog = (isServerSide ? "server" : "client") + "-side";
		final int maxAttempts = 10;
		boolean punished = false;
		
		// Only go for this many attempts to avoid an endless loop
		for(int i = 0; i < maxAttempts; i++)
		{
			// If no punishment was found (see bottom of loop)
			if(punishmentIndex != -1)
			{
				var punishment = category.punishments.get(punishmentIndex);
				
				Oversaid.LOGGER.debug("Attempting {} to punish from category '{}'", sideLog, category);
				
				//noinspection unchecked
				if(((SidedPunishment<T>) punishment).punish(context))
				{
					if(punishment.hasReset())
					{
						UUID uuid = uuidGetter.apply(context);
						Oversaid.addReset(uuid, punishmentIndex, punishment);
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
			var list = category.punishments.stream().filter(p -> p.isServerSide() == isServerSide).toList();
			
			if(list.isEmpty())
			{
				// We couldn't find a punishment in this category, so count it as an attempt with another iteration
				punishmentIndex = -1;
			}
			else
			{
				punishmentIndex = (int) (Math.random() * list.size());
			}
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
