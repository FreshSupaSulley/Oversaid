package io.github.freshsupasulley.taboo_trickler;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

public class TSPunishment extends TTPunishment<ServerPlayer> {
	
	public TSPunishment(TricklerCategory category, Predicate<ServerPlayer> punishment)
	{
		super(category, punishment);
	}
	
	@Override
	void addPunishment(TricklerCategory category)
	{
		category.addServerPunishment(this);
	}
	
	@Override
	void displayClientMessage(ServerPlayer player, Component message)
	{
		player.displayClientMessage(message, false);
	}
}
