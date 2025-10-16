package io.github.freshsupasulley.oversaid.forge;

import io.github.freshsupasulley.oversaid.OversaidCategory;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Config {
	
	private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
	public static final ForgeConfigSpec.IntValue MIN_REPETITIONS = BUILDER.comment("Minimum amount of times a word has to be said before its considered a taboo").defineInRange("min_repetitions", 3, 1, Integer.MAX_VALUE);
	public static final ForgeConfigSpec.IntValue MAX_WORDS = BUILDER.comment("Maximum amount of words to store as taboos").defineInRange("max_words", 10, 1, Integer.MAX_VALUE);
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORED_WORDS = BUILDER.comment("Taboos to not include if it's said").defineList("ignored_words", List.of(), (entry) -> true);
	public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> WEIGHTS = BUILDER.comment("Weights of each punishment type. The higher the number, the more likely that punishment category will be called.", "Goes in order, left to right as follows: " + Stream.of(OversaidCategory.values()).map(OversaidCategory::getFancyName).collect(Collectors.joining(", "))).defineList("weights", Arrays.stream(OversaidCategory.values()).map(OversaidCategory::getWeight).toList(), o -> ((Integer) o) >= 0);
	
	static final ForgeConfigSpec SPEC = BUILDER.build();
}
