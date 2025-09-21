package io.github.freshsupasulley.taboo_trickler;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import io.github.freshsupasulley.censorcraft.api.CensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.ForgeCensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.events.PluginRegistration;
import io.github.freshsupasulley.censorcraft.api.events.server.ReceiveTranscription;
import io.github.freshsupasulley.censorcraft.api.events.server.ServerConfigEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.regex.Pattern;

@ForgeCensorCraftPlugin
@Mod.EventBusSubscriber(modid = TabooTrickler.MODID)
public class TTPlugin implements CensorCraftPlugin {
	
	// Functionality to add most said words to taboos
	private static final int MIN_WORD_REPETITIONS = 5;
	private static final long REPETITION_UPDATE_TIME = 2000 * 60; // recheck every 2 minutes
	private static final Map<String, Integer> WORD_COUNTS = new HashMap<>();
	private static long LAST_WORD_COUNT_CHECK = System.currentTimeMillis();
	
	// Adding from a set list
	private static CommentedFileConfig config;
	private static long LAST_ADDITION = System.currentTimeMillis();
	private static int LAST_ADDITION_INDEX;
	
	@Override
	public void register(PluginRegistration registration)
	{
		registration.registerEvent(ServerConfigEvent.class, this::onServerConfig);
		registration.registerEvent(ReceiveTranscription.class, this::onReceiveTranscription);
		// To register punishments
	}
	
	public void onServerConfig(ServerConfigEvent event)
	{
		config = event.getAPI().getServerConfig();
	}
	
	public void onReceiveTranscription(ReceiveTranscription event)
	{
		// Break it up into words
		var pattern = Pattern.compile("\\b\\w+\\b");
		var matcher = pattern.matcher(event.getText().toLowerCase());
		
		while(matcher.find())
		{
			String match = matcher.group();
			int merged = WORD_COUNTS.merge(match, 1, Integer::sum);
		}
	}
	
	@SubscribeEvent
	public static void onLevelTick(TickEvent.PlayerTickEvent.Post event)
	{
		List<String> taboos = config.get("global_taboos");
		long now = System.currentTimeMillis();
		
		// Repetition
		if(now - LAST_WORD_COUNT_CHECK > REPETITION_UPDATE_TIME)
		{
			// Find the next
			Iterator<Map.Entry<String, Integer>> iterator = WORD_COUNTS.entrySet().iterator();
			
			while(iterator.hasNext())
			{
				var entry = iterator.next();
				
				// If we don't already have this taboo AND there's enough repetitions
				if(!taboos.contains(entry.getKey()) && entry.getValue() >= MIN_WORD_REPETITIONS)
				{
					TabooTrickler.LOGGER.info("Banning {} for saying it too much", entry.getKey());
					
					// Announce what's being banned banned
					event.player.level().players().forEach(player -> player.displayClientMessage(Component.literal("Banned \"").append(Component.literal(entry.getKey()).withStyle(style -> style.withBold(true))).append("\" for saying it too much"), false));
					
					// Ban it
					taboos.add(entry.getKey());
					config.set("global_taboos", taboos);
					break;
				}
			}
			
			LAST_WORD_COUNT_CHECK = now;
		}
		
		// Check if it's time to drop a new taboo
		if(now - LAST_ADDITION > Config.NEW_TABOO_INTERVAL.get() * 1000) // convert to ms
		{
			LAST_ADDITION = now;
			
			TabooTrickler.LOGGER.info("Attempting to add new taboo");
			
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
					event.player.level().players().forEach(player -> player.displayClientMessage(Component.literal("Banned a word (" + sample.length() + " character" + (sample.length() == 1 ? "" : "s") + " long)"), false));
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
