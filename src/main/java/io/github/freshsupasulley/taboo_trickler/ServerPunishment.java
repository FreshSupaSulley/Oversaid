package io.github.freshsupasulley.taboo_trickler;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

public class ServerPunishment extends SidedPunishment<ServerPlayer> {
	
	public ServerPunishment(TricklerCategory category, Predicate<ServerPlayer> punishment)
	{
		super(true, category, punishment);
	}
	
	@Override
	void displayClientMessage(ServerPlayer context, Component message)
	{
		context.displayClientMessage(message, false);
	}
}
