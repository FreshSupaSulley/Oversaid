package io.github.freshsupasulley.taboo_trickler.plugin;

import io.github.freshsupasulley.censorcraft.api.punishments.Punishment;
import io.github.freshsupasulley.taboo_trickler.SidedPunishment;
import io.github.freshsupasulley.taboo_trickler.TricklerCategory;
import io.github.freshsupasulley.taboo_trickler.forge.TabooTrickler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import java.util.function.Function;

public class TricklerPunishment extends Punishment {
	
	public static String NAME = "trickler";
	
	private TricklerCategory category;
	private int punishmentIndex;
	private boolean abandonOnFail;
	
	// Required
	public TricklerPunishment()
	{
		// Pick a random punishment out of this category
		var list = TricklerCategory.random().punishments;
		this.punishmentIndex = (int) (Math.random() * list.size());
	}
	
	/**
	 * Use this for manually invoking the punishment with non-random args.
	 *
	 * <p>This will <b>not</b> fallback to another punishment if failed.</p>
	 *
	 * @param punishmentIndex {@link SidedPunishment} index
	 */
	public TricklerPunishment(TricklerCategory category, int punishmentIndex)
	{
		this.abandonOnFail = true;
		this.category = category;
		this.punishmentIndex = punishmentIndex;
	}
	
	public TricklerCategory getCategory()
	{
		return category;
	}
	
	@Override
	public String getId()
	{
		return NAME;
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
				
				TabooTrickler.LOGGER.debug("Attempting {} to punish from category '{}'", sideLog, category);
				
				//noinspection unchecked
				if(((SidedPunishment<T>) punishment).punish(context))
				{
					if(punishment.hasReset())
					{
						UUID uuid = uuidGetter.apply(context);
						TabooTrickler.addReset(uuid, punishmentIndex, punishment);
					}
					
					punished = true;
					break;
				}
				else
				{
					TabooTrickler.LOGGER.info("{} punishment failed", category);
					
					if(abandonOnFail)
					{
						TabooTrickler.LOGGER.info("Not trying another punishment");
						break;
					}
				}
			}
			
			// Pick another category and punishment since this didn't work
			this.category = TricklerCategory.random();
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
				TabooTrickler.LOGGER.error("Attempted a {} punishment {} times and always failed", sideLog, maxAttempts);
			}
			
			// Pass to caller
			throw new IllegalStateException("Failed to execute punishment");
		}
	}
}
