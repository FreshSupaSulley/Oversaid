package io.github.freshsupasulley.taboo_trickler;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

public class TCPunishment extends TTPunishment<Minecraft> {
	
	public TCPunishment(TricklerCategory category, Predicate<Minecraft> punishment)
	{
		super(category, punishment);
	}
	
	@Override
	void addPunishment(TricklerCategory category)
	{
		category.addClientPunishment(this);
	}
	
	@Override
	void displayClientMessage(Minecraft mc, Component message)
	{
		mc.getChatListener().handleSystemMessage(message, false);
	}
}
