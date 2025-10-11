package io.github.freshsupasulley.taboo_trickler;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.TimeUnit;

public abstract class ServerPunishment extends SidedPunishment<ServerPlayer> {
	
	public ServerPunishment(TricklerCategory category, String message, TimeUnit unit, long duration)
	{
		super(true, category, message, unit, duration);
	}
	
	@Override
	void displayClientMessage(ServerPlayer context, Component message)
	{
		context.displayClientMessage(message, false);
	}
}
