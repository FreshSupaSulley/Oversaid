package io.github.freshsupasulley.oversaid;

import io.github.freshsupasulley.oversaid.forge.Oversaid;
import net.minecraft.network.chat.Component;

import java.util.concurrent.TimeUnit;

public abstract class SidedPunishment<T> {
	
	private final boolean isServer;
	private OversaidCategory category;
	private final String message;
	private final TimeUnit unit;
	private final long duration;
	
	public SidedPunishment(boolean isServer, OversaidCategory category, String message, TimeUnit unit, long duration)
	{
		this.isServer = isServer;
		this.category = category;
		this.message = message;
		this.unit = unit;
		this.duration = duration;
		
		Oversaid.punishments.add(this);
	}
	
	public SidedPunishment(boolean isServer, OversaidCategory category, String message)
	{
		this(isServer, category, message, null, 0);
	}
	
	public final boolean hasReset()
	{
		return unit != null;
	}
	
	public final OversaidCategory getCategory()
	{
		return category;
	}
	
	public final boolean isServerSide()
	{
		return isServer;
	}
	
	public abstract void displayClientMessage(T context, Component message);
	
	public final boolean punish(T context)
	{
		// If this punishment passed
		try
		{
			if(internalPunish(context))
			{
				if(message != null)
				{
					// Make it red to draw more attention
					displayClientMessage(context, Component.literal(message).withColor(0xFF0000));
				}
				
				return true;
			}
		} catch(Exception e)
		{
			Oversaid.LOGGER.warn("An error occurred executing trickler punishment", e);
		}
		
		return false;
	}
	
	protected abstract boolean internalPunish(T context);
	
	public final void fireReset(T context)
	{
		if(!hasReset())
			throw new IllegalStateException("This punishment doesn't have a reset");
		
		try
		{
			internalReset(context);
		} catch(Exception e)
		{
			Oversaid.LOGGER.error("Failed to execute punishment reset", e);
		}
	}
	
	protected void internalReset(T context)
	{
	}
	
	public final long calculateResetTime()
	{
		if(!hasReset())
			throw new IllegalStateException("This punishment doesn't have a reset");
		return System.currentTimeMillis() + unit.toMillis(duration);
	}
}
