package io.github.freshsupasulley.taboo_trickler;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

public class ClientPunishment extends SidedPunishment<Minecraft> {
	
	public ClientPunishment(TricklerCategory category, Predicate<Minecraft> punishment)
	{
		super(false, category, punishment);
	}
	
	@Override
	void displayClientMessage(Minecraft context, Component message)
	{
		context.getChatListener().handleSystemMessage(message, false);
	}
}
