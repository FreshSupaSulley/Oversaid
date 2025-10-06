package io.github.freshsupasulley.taboo_trickler;

import io.github.freshsupasulley.censorcraft.api.punishments.Punishment;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class TricklerPunishment extends Punishment {
	
	public static String NAME = "trickler";
	
	private TricklerCategory category;
	private boolean isClientSide;
	private int punishment;
	
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
		// Determine if this will be a server or client punishment based off some random category and punishment selection
		this.category = TricklerCategory.random();
		
		// Pick a random punishment out of this category
		int size = category.server.size() + category.client.size();
		this.punishment = (int) (Math.random() * size);
		
		// If we picked a server-side punishment
		if(punishment < category.server.size())
		{
			ServerPlayer player = (ServerPlayer) uncasted;
			punishInternal(true, player, Entity::getUUID);
		}
		else
		{
			// Defer to client
			// The client will start with the punishment and category we just picked
			// Ideally that doesn't fail but if it does it'll look for others
			isClientSide = true;
		}
	}
	
	@Override
	public void punishClientSide()
	{
		if(!isClientSide)
			return;
		
		// This will attempt to use the same punishment the server punish code tried to use
		punishInternal(false, Minecraft.getInstance(), (minecraft) -> minecraft.player.getUUID());
	}
	
	private <T> void punishInternal(boolean serverSide, T context, Function<T, UUID> uuidGetter)
	{
		String sideLog = (serverSide ? "server" : "client") + "-side";
		final int maxAttempts = 10;
		boolean punished = false;
		
		// Only go for this many attempts to avoid an endless loop
		for(int i = 0; i < maxAttempts && !punished; i++)
		{
			List<? extends TTPunishment> punishments = serverSide ? category.server : category.client;
			
			// If there's no punishments on this side
			if(!punishments.isEmpty())
			{
				var selected = punishments.get(punishment);
				
				TabooTrickler.LOGGER.info("Attempting {} to punish from category '{}'", sideLog, category);
				
				if(selected.punish(context))
				{
					if(selected.hasReset())
					{
						UUID uuid = uuidGetter.apply(context);
						TabooTrickler.addReset(uuid, punishment, selected);
					}
					
					punished = true;
					break;
				}
				else
				{
					TabooTrickler.LOGGER.info("{} punishment failed", category);
				}
			}
			
			// Repick another category and punishment since this didn't work
			this.category = TricklerCategory.random();
			this.punishment = (int) (Math.random() * punishments.size());
		}
		
		if(!punished)
		{
			TabooTrickler.LOGGER.error("Attempted a {} punishment {} times but failed", sideLog, maxAttempts);
		}
	}
}
