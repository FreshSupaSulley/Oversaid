package io.github.freshsupasulley.taboo_trickler;

import net.minecraft.network.chat.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class TTPunishment<T> {
	
	private Predicate<T> execute;
	private Consumer<T> resetter;
	private String message;
	
	// If punishments require resetting their effects at a later time
	private TimeUnit unit;
	private long duration;
	
	public TTPunishment(TricklerCategory category, Predicate<T> punishment)
	{
		this.execute = punishment;
		addPunishment(category);
	}
	
	abstract void addPunishment(TricklerCategory category);
	abstract void displayClientMessage(T handle, Component message);
	
	public boolean punish(T player)
	{
		// If this punishment passed
		if(execute.test(player))
		{
			if(message != null)
			{
				displayClientMessage(player, Component.literal(message));
			}
			
			return true;
		}
		
		return false;
	}
	
	public boolean hasReset()
	{
		return resetter != null;
	}
	
	public long getResetTime()
	{
		if(!hasReset()) throw new IllegalStateException("This server punishment doesn't have a reset function");
		return System.currentTimeMillis() + unit.toMillis(duration);
	}
	
	public TTPunishment setMessage(String message)
	{
		this.message = message;
		return this;
	}
	
	public TTPunishment reset(TimeUnit unit, long time, Consumer<T> resetter)
	{
		this.unit = unit;
		this.duration = time;
		this.resetter = resetter;
		return this;
	}
}
