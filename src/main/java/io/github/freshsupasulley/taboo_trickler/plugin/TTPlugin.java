package io.github.freshsupasulley.taboo_trickler.plugin;

import io.github.freshsupasulley.censorcraft.api.CensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.CensorCraftServerAPI;
import io.github.freshsupasulley.censorcraft.api.ForgeCensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.events.PluginRegistration;
import io.github.freshsupasulley.censorcraft.api.events.server.ChatTabooEvent;
import io.github.freshsupasulley.censorcraft.api.events.server.ReceiveTranscription;
import io.github.freshsupasulley.censorcraft.api.events.server.ServerConfigEvent;
import io.github.freshsupasulley.taboo_trickler.forge.Config;
import io.github.freshsupasulley.taboo_trickler.forge.TabooTrickler;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ForgeCensorCraftPlugin
@Mod.EventBusSubscriber(modid = TabooTrickler.MODID)
public class TTPlugin implements CensorCraftPlugin {
	
	// Stuff used to add most said words to taboos
	private static final int MIN_WORD_REPETITIONS = 5;
	private static final long REPETITION_UPDATE_TIME = 2000 * 60; // recheck every 2 minutes
	private static final Map<String, Integer> WORD_COUNTS = new HashMap<>();
	private static long LAST_WORD_COUNT_CHECK = System.currentTimeMillis();
	
	// Adding from a set list
	public static CensorCraftServerAPI serverAPI;
	private static long LAST_ADDITION = System.currentTimeMillis();
	private static int LAST_ADDITION_INDEX;
	
	@Override
	public void register(PluginRegistration registration)
	{
		// Store the server config somewhere
		registration.registerEvent(ServerConfigEvent.class, (event) -> serverAPI = event.getAPI());
		
		// Track the most said words
		registration.registerEvent(ReceiveTranscription.class, (event) ->
		{
			// Break it up into words
			var pattern = Pattern.compile("\\b\\w+\\b");
			var matcher = pattern.matcher(event.getText().toLowerCase());
			
			while(matcher.find())
			{
				String match = matcher.group();
				WORD_COUNTS.merge(match, 1, Integer::sum);
			}
		});
		
		// Add some more detail when they're punished
		registration.registerEvent(ChatTabooEvent.class, event ->
		{
			TricklerPunishment punishment = (TricklerPunishment) event.getPunishments().stream().filter(taboo -> taboo.getClass().equals(TricklerPunishment.class)).findFirst().orElse(null);
			
			if(punishment != null)
			{
				event.setText(Component.empty().append((Component) event.getText()).append(" (severity: ").append(Component.literal(punishment.getCategory().getFancyName()).withStyle(style -> style.withBold(true))).append(")"));
			}
		});
		
		// Our server
		registration.registerPunishment(TricklerPunishment.class);
	}
	
	@SubscribeEvent
	public static void onLevelTick(TickEvent.PlayerTickEvent.Post event)
	{
		if(event.side != LogicalSide.SERVER)
			return;
		
		List<String> taboos = serverAPI.getServerConfig().get("global_taboos");
		long now = System.currentTimeMillis();
		
		// Repetition
		if(now - LAST_WORD_COUNT_CHECK > REPETITION_UPDATE_TIME)
		{
			// Find the next
			for(Map.Entry<String, Integer> entry : WORD_COUNTS.entrySet())
			{
				// If we don't already have this taboo AND there's enough repetitions
				if(!taboos.contains(entry.getKey()) && entry.getValue() >= MIN_WORD_REPETITIONS)
				{
					TabooTrickler.LOGGER.info("Banning {} for saying it too much", entry.getKey());
					
					// Announce what's being banned
					event.player.level().players().forEach(player -> player.displayClientMessage(Component.literal("Banned \"").append(Component.literal(entry.getKey()).withStyle(style -> style.withBold(true))), false));
					
					// Ban it
					taboos.add(entry.getKey());
					serverAPI.getServerConfig().set("global_taboos", taboos);
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
					serverAPI.getServerConfig().set("global_taboos", taboos);
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
