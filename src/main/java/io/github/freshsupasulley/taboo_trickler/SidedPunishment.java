package io.github.freshsupasulley.taboo_trickler;

import io.github.freshsupasulley.taboo_trickler.forge.Oversaid;
import net.minecraft.network.chat.Component;

import java.util.concurrent.TimeUnit;

public abstract class SidedPunishment<T> {
	
	private final boolean isServer;
	private final String message;
	private final TimeUnit unit;
	private final long duration;
	
	public SidedPunishment(boolean isServer, OversaidCategory category, String message, TimeUnit unit, long duration)
	{
		this.isServer = isServer;
		this.message = message;
		this.unit = unit;
		this.duration = duration;
		
		category.punishments.add(this);
	}
	
	public SidedPunishment(boolean isServer, OversaidCategory category, String message)
	{
		this(isServer, category, message, null, 0);
	}
	
	public final boolean hasReset()
	{
		return unit != null;
	}
	
	public final boolean isServerSide()
	{
		return isServer;
	}
	
	abstract void displayClientMessage(T context, Component message);
	
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
		if(!hasReset()) throw new IllegalStateException("This punishment doesn't have a reset");
		
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
		if(!hasReset()) throw new IllegalStateException("This punishment doesn't have a reset");
		return System.currentTimeMillis() + unit.toMillis(duration);
	}
}
