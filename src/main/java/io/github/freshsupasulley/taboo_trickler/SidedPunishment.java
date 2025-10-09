package io.github.freshsupasulley.taboo_trickler;

import net.minecraft.network.chat.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class SidedPunishment<T> {
	
	private final boolean isServer;
	
	private final Predicate<T> execute;
	private Consumer<T> resetter;
	private String message;
	
	// If punishments require resetting their effects at a later time
	private TimeUnit unit;
	private long duration;
	
	public SidedPunishment(boolean isServer, TricklerCategory category, Predicate<T> punishment)
	{
		this.isServer = isServer;
		this.execute = punishment;
		category.punishments.add(this);
	}
	
	public final boolean isServerSide()
	{
		return isServer;
	}
	
	abstract void displayClientMessage(T context, Component message);
	
	public boolean punish(T context)
	{
		// If this punishment passed
		if(execute.test(context))
		{
			if(message != null)
			{
				displayClientMessage(context, Component.literal(message));
			}
			
			return true;
		}
		
		return false;
	}
	
	public boolean hasReset()
	{
		return resetter != null;
	}
	
	public void fireReset(T context)
	{
		resetter.accept(context);
	}
	
	public long calculateResetTime()
	{
		if(!hasReset()) throw new IllegalStateException("This punishment doesn't have a reset function");
		return System.currentTimeMillis() + unit.toMillis(duration);
	}
	
	public SidedPunishment<?> setMessage(String message)
	{
		this.message = message;
		return this;
	}
	
	public SidedPunishment<?> addReset(TimeUnit unit, long time, Consumer<T> resetter)
	{
		this.unit = unit;
		this.duration = time;
		this.resetter = resetter;
		return this;
	}
}
