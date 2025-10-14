package io.github.freshsupasulley.taboo_trickler.forge;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class Config {
	
	private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
	public static final ForgeConfigSpec.IntValue MIN_REPETITIONS = BUILDER.comment("Minimum amount of times a word has to be said before its considered a taboo").defineInRange("min_repetitions", 3, 1, Integer.MAX_VALUE);
	public static final ForgeConfigSpec.IntValue MAX_WORDS = BUILDER.comment("Maximum amount of words to store as taboos").defineInRange("max_words", 10, 1, Integer.MAX_VALUE);
	
	static final ForgeConfigSpec SPEC = BUILDER.build();
}