package io.github.freshsupasulley.taboo_trickler;

import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SPBuilder {
	
	private final OversaidCategory category;
	private final Predicate<ServerPlayer> punishment;
	private Consumer<ServerPlayer> resetter;
	private String message;
	
	// If punishments require resetting their effects at a later time
	private TimeUnit unit;
	private long duration;
	
	public SPBuilder(OversaidCategory category, Predicate<ServerPlayer> punishment)
	{
		this.category = category;
		this.punishment = punishment;
	}
	
	public SPBuilder setMessage(String message)
	{
		this.message = message;
		return this;
	}
	
	public SPBuilder addReset(TimeUnit unit, long time, Consumer<ServerPlayer> resetter)
	{
		this.unit = unit;
		this.duration = time;
		this.resetter = resetter;
		return this;
	}
	
	public void create()
	{
		new ServerPunishment(category, message, unit, duration) {
			
			@Override
			protected boolean internalPunish(ServerPlayer context)
			{
				return punishment.test(context);
			}
			
			@Override
			protected void internalReset(ServerPlayer context)
			{
				resetter.accept(context);
			}
		};
	}
}
