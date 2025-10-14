package io.github.freshsupasulley.taboo_trickler.plugin;

import io.github.freshsupasulley.censorcraft.api.CensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.CensorCraftServerAPI;
import io.github.freshsupasulley.censorcraft.api.ForgeCensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.events.PluginRegistration;
import io.github.freshsupasulley.censorcraft.api.events.server.ChatTabooEvent;
import io.github.freshsupasulley.censorcraft.api.events.server.ReceiveTranscription;
import io.github.freshsupasulley.censorcraft.api.events.server.ServerConfigEvent;
import io.github.freshsupasulley.censorcraft.api.punishments.Punishment;
import io.github.freshsupasulley.taboo_trickler.forge.Config;
import io.github.freshsupasulley.taboo_trickler.forge.Oversaid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ForgeCensorCraftPlugin
@Mod.EventBusSubscriber(modid = Oversaid.MODID)
public class OversaidPlugin implements CensorCraftPlugin {
	
	// Stuff used to add most said words to taboos
	private static final long TICK_TIME = 1000 * 1; // recheck every second ig
	private static final Map<String, Integer> WORD_COUNTS = new HashMap<>();
	private static long LAST_TICK = System.currentTimeMillis();
	
	private static CensorCraftServerAPI serverAPI;
	
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
			// we assume there's only one tricklerpunishment in here
			OversaidPunishment punishment = (OversaidPunishment) event.getPunishments().stream().filter(taboo -> taboo.getClass().equals(OversaidPunishment.class)).findAny().orElse(null);
			
			if(punishment != null)
			{
				event.setText(Component.empty().append((Component) event.getText()).append(" (severity: ").append(Component.literal(punishment.getCategory().getFancyName()).withStyle(style -> style.withBold(true))).append(")"));
			}
		});
		
		// Our server
		registration.registerPunishment(OversaidPunishment.class);
	}
	
	public static @Nullable Punishment getOversaidPunishment()
	{
		// Get the first one. We don't care about duplicates ig
		return serverAPI.getConfigPunishments().stream().filter(p -> p.getId().equals(OversaidPunishment.PUNISHMENT_NAME)).findFirst().orElse(null);
	}
	
	@SubscribeEvent
	public static void onLevelTick(TickEvent.ServerTickEvent.Post event)
	{
		if(event.side != LogicalSide.SERVER)
			return;
		
		// Don't tick every game tick
		long now = System.currentTimeMillis();
		if(now - LAST_TICK < TICK_TIME)
			return;
		LAST_TICK = now;
		
		// Make sure we have a punishment
		var punishment = getOversaidPunishment();
		if(punishment == null)
			return;
		
		// Update taboos
		List<String> taboos = punishment.getTaboos();
		
		// Find the top X most popular
		final int minRepetitions = Config.MIN_REPETITIONS.get();
		var newTaboos = WORD_COUNTS.entrySet().stream().filter(entry -> entry.getValue() >= minRepetitions).sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(Config.MAX_WORDS.get()).map(Map.Entry::getKey).toList();
		
		// Get all new taboos
		List<String> toBan = newTaboos.stream().filter(sample -> !taboos.contains(sample)).toList();
		
		// If we're adding things
		if(!toBan.isEmpty())
		{
			// Assemble into a Component
			Component component = toBan.stream().map(string -> Component.literal("\"" + string + "\"").withStyle(style -> style.withBold(true))).reduce(Component.empty(), (og, sample) -> og.append(sample).append(Component.literal(", ")));
			var parts = component.getSiblings();
			
			if(!parts.isEmpty())
			{
				// The last component is always just a comma
				parts.removeLast();
				
				if(parts.size() > 1)
				{
					// Remove the last 2 elements and readd the very last one after we've added ", and"
					var readd = parts.removeLast();
					parts.removeLast();
					
					parts.add(Component.literal(", and "));
					parts.add(readd);
				}
			}
			
			// Announce what's being banned
			event.getServer().getPlayerList().getPlayers().forEach(player ->
			{
				// Reset the player's audio buffer
				serverAPI.punish(player, Map.of());
				// Tell them what got banned
				player.displayClientMessage(Component.literal("Banned ").append(component), false);
			});
		}
		
		// Update taboos
		taboos.clear();
		taboos.addAll(newTaboos);
		punishment.config.set("taboo", taboos);
		serverAPI.getServerConfig().save();
		
		// Now handle the scoreboard
		Scoreboard scoreboard = event.getServer().getScoreboard();
		
		// Only use the objective if the server admin wants one
		var objective = scoreboard.getObjective(Oversaid.MODID);
		
		if(objective == null)
		{
			return;
		}
		
		// Remove old entries
		objective.getScoreboard().getTrackedPlayers().forEach(holder ->
		{
			scoreboard.resetAllPlayerScores(holder);
			scoreboard.removePlayerFromTeam(holder.getScoreboardName());
		});
		
		// We got the green light, fill the scoreboard
		for(String sample : taboos)
		{
			int count = WORD_COUNTS.getOrDefault(sample, 0);
			if(count == 0) continue;
			
			// Display the number of times it was said
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(sample), objective).set(count);
		}
	}
	
	@Override
	public String getPluginId()
	{
		return Oversaid.MODID;
	}
}
