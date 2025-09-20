package io.github.freshsupasulley.taboo_trickler;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import io.github.freshsupasulley.censorcraft.api.CensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.ForgeCensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.events.PluginRegistration;
import io.github.freshsupasulley.censorcraft.api.events.server.ServerConfigEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@ForgeCensorCraftPlugin
@Mod.EventBusSubscriber(modid = TabooTrickler.MODID)
public class TTPlugin implements CensorCraftPlugin {
	
	private static CommentedFileConfig config;
	private static long LAST_ADDITION = System.currentTimeMillis();
	private static int LAST_ADDITION_INDEX;
	
	@Override
	public void register(PluginRegistration registration)
	{
		registration.registerEvent(ServerConfigEvent.class, this::onServerConfig);
		// To register punishments
	}
	
	public void onServerConfig(ServerConfigEvent event)
	{
		config = event.getAPI().getServerConfig();
	}
	
	@SubscribeEvent
	public static void onLevelTick(TickEvent.PlayerTickEvent.Post event)
	{
		// Check if it's time to drop a new taboo
		if(System.currentTimeMillis() - LAST_ADDITION > Config.NEW_TABOO_INTERVAL.get() * 1000) // convert to ms
		{
			LAST_ADDITION = System.currentTimeMillis();
			
			TabooTrickler.LOGGER.info("Attempting to add new taboo");
			// Append, don't overwrite
			List<String> taboos = config.get("global_taboos");
			
			var list = Config.TABOO_LIST.get();
			
			if(LAST_ADDITION_INDEX == list.size() - 1)
			{
				TabooTrickler.LOGGER.info("Ran out of taboos to add!");
				return;
			}
			
			// This should stop once we add all taboos from the list
			for(int i = LAST_ADDITION_INDEX; i < list.size(); i++)
			{
				String sample = list.get(i);
				
				if(!taboos.contains(sample))
				{
					taboos.add(sample);
					LAST_ADDITION_INDEX = i;
					
					// Notify the world
					event.player.level().players().forEach(player -> player.displayClientMessage(Component.literal("Banned a word (" + sample.length() + " character(s) long)"), false));
					config.set("global_taboos", taboos);
					break;
				}
			}
		}
	}
	
	@Override
	public String getPluginId()
	{
		return TabooTrickler.MODID;
	}
}
