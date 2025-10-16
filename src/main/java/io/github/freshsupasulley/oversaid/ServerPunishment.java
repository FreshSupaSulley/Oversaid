package io.github.freshsupasulley.oversaid;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.TimeUnit;

public abstract class ServerPunishment extends SidedPunishment<ServerPlayer> {
	
	public ServerPunishment(OversaidCategory category, String message, TimeUnit unit, long duration)
	{
		super(true, category, message, unit, duration);
	}
	
	@Override
	public void displayClientMessage(ServerPlayer context, Component message)
	{
		context.displayClientMessage(message, false);
	}
}
