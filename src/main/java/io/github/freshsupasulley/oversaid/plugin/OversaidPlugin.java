package io.github.freshsupasulley.oversaid.plugin;

import io.github.freshsupasulley.censorcraft.api.CensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.CensorCraftServerAPI;
import io.github.freshsupasulley.censorcraft.api.ForgeCensorCraftPlugin;
import io.github.freshsupasulley.censorcraft.api.events.PluginRegistration;
import io.github.freshsupasulley.censorcraft.api.events.server.ReceiveTranscription;
import io.github.freshsupasulley.censorcraft.api.events.server.ServerConfigEvent;
import io.github.freshsupasulley.censorcraft.api.punishments.Trie;
import io.github.freshsupasulley.oversaid.OversaidPunishment;
import io.github.freshsupasulley.oversaid.forge.Config;
import io.github.freshsupasulley.oversaid.forge.Oversaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@ForgeCensorCraftPlugin
@Mod.EventBusSubscriber(modid = Oversaid.MODID)
public class OversaidPlugin implements CensorCraftPlugin {
	
	// Stuff used to add most said words to taboos
	private static final long TICK_TIME = 1000 * 1; // recheck every second ig
	public static final Set<String> TABOOS = new HashSet<>();
	private static final Map<String, Integer> WORD_COUNTS = new HashMap<>();
	private static long LAST_TICK = System.currentTimeMillis();
	
	private static final Map<UUID, String> toPunish = new HashMap<>();
	
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
			
			// Handle punishments manually here because we're not registering a punishment (which would require installing this on both client and server)
			// Check if that's banned
			Trie trie = new Trie(TABOOS);
			String taboo = trie.containsAnyIsolatedIgnoreCase(event.getText());
			
			if(taboo != null)
			{
				toPunish.put(event.getPlayerUUID(), taboo);
			}
		});
		
		// Add some more detail when they're punished
		//		registration.registerEvent(ChatTabooEvent.class, event ->
		//		{
		//			// we assume there's only one tricklerpunishment in here
		//			OversaidPunishment punishment = (OversaidPunishment) event.getPunishments().stream().filter(taboo -> taboo.getClass().equals(OversaidPunishment.class)).findAny().orElse(null);
		//
		//			if(punishment != null)
		//			{
		//				event.setText(Component.empty().append((Component) event.getText()).append(" (severity: ").append(Component.literal(punishment.getCategory().getFancyName()).withStyle(style -> style.withBold(true))).append(")"));
		//			}
		//		});
		
		// Our server
		//		registration.registerPunishment(OversaidPunishment.class);
	}
	
	@SubscribeEvent
	public static void onLevelTick(TickEvent.ServerTickEvent.Post event)
	{
		if(event.side != LogicalSide.SERVER)
			return;
		
		// Run all queued punishments
		for(Map.Entry<UUID, String> sample : toPunish.entrySet())
		{
			ServerPlayer serverPlayer = event.getServer().getPlayerList().getPlayer(sample.getKey());
			
			if(serverPlayer != null)
			{
				OversaidPunishment punishment;
				
				// Forbid punishing someone if they were already punished by something that's still on a reset cooldown
				// Holy fucking shit. This is the first time I've EVER used a do while loop. And it's actually a justifiable use case. Unc status achieved
				do
				{
					punishment = new OversaidPunishment();
				}
				while(Oversaid.RESETS.containsKey(sample.getKey()) && Oversaid.RESETS.get(sample.getKey()).containsKey(punishment.getPunishmentIndex()));
				
				final var antiFinal = punishment;
				// make it yellow to pop out more from regular chat messages
				event.getServer().getPlayerList().getPlayers().forEach(player -> player.displayClientMessage(Component.empty().withColor(0xFFD700).append(Component.literal(player.getScoreboardName()).withStyle(style -> style.withBold(true))).append(" said \"").append(Component.literal(sample.getValue()).withStyle(style -> style.withBold(true))).append("\" (severity: ").append(Component.literal(antiFinal.getCategory().getFancyName()).withStyle(style -> style.withBold(true))).append(")"), false));
				punishment.punish(serverPlayer);
			}
		}
		
		toPunish.clear();
		
		// Don't tick every game tick
		long now = System.currentTimeMillis();
		if(now - LAST_TICK < TICK_TIME)
			return;
		LAST_TICK = now;
		
		// Find the top X most popular
		// Make sure to exclude the taboo if its ignored in the config
		Predicate<String> wordValidator = (word) ->
		{
			// Meets min repetitions
			boolean result = WORD_COUNTS.getOrDefault(word, 0) >= Config.MIN_REPETITIONS.get();
			// If the word isn't ignored
			result &= !Config.IGNORED_WORDS.get().contains(word);
			// Meets min length
			result &= word.length() >= Config.MIN_WORD_LENGTH.get();
			return result;
		};
		
		var neww = WORD_COUNTS.keySet().stream().filter(wordValidator).toList();
		
		// Get all new taboos
		List<String> toBan = neww.stream().filter(sample -> !TABOOS.contains(sample)).toList();
		
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
			
			// Update taboos
			TABOOS.addAll(neww);
		}
		
		// Remove all old, non-valid words
		TABOOS.removeIf(sample -> !wordValidator.test(sample));
		
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
		for(String sample : TABOOS)
		{
			int count = WORD_COUNTS.getOrDefault(sample, 0);
			if(count == 0)
				continue;
			
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
