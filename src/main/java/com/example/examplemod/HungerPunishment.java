package com.example.examplemod;

import io.github.freshsupasulley.censorcraft.api.punishments.Punishment;
import io.github.freshsupasulley.censorcraft.api.punishments.Trie;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Demo punishment that depletes the user's hunger bar.
 * 
 * <p>
 * Only works in survival and when not in peaceful mode.
 * </p>
 */
public class HungerPunishment extends Punishment {
	
	@Override
	public String getName()
	{
		// This overrides the default name that appears in the server config file
		return "hunger";
	}
	
	// Make this punishment enabled when first written to the config file
	@Override
	protected boolean initEnable()
	{
		return true;
	}
	
	@Override
	protected void buildConfig()
	{
		// Don't use floats, at all. Use doubles (the config system doesn't like floats). Ints are fine too.
		// This is because the config system we use only recognizes doubles and will fail validation for floats
		define("exhaustion", 500, "The amount of food exhaustion to inflict onto the player");
	}
	
	// You can add custom control over the taboos here, so they can change at your whim
	@Override
	public String getTaboo(String sample, boolean isolateWords)
	{
		List<String> taboos = config.get("taboo");
		// Always add "corn" to the taboos along with whatever the server owner put down
		taboos.add("corn");
		// Continue with parent's trie checking logic
		Trie trie = new Trie(taboos);
		return isolateWords ? trie.containsAnyIsolatedIgnoreCase(sample) : trie.containsAnyIgnoreCase(sample);
	}
	
	@Override
	public void punish(Object serverPlayer)
	{
		// When retrieving data from the server config file, casting to the Number class is the best way to get values (for now)
		((ServerPlayer) serverPlayer).causeFoodExhaustion(((Number) config.get("exhaustion")).floatValue());
	}
}
