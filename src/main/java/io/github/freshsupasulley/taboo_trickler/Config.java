package io.github.freshsupasulley.taboo_trickler;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class Config {
	
	private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
	public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TABOO_LIST = BUILDER.comment("The taboos to slowly add over time").defineList("taboos", List.of("silly goose"), object -> true);
	public static final ForgeConfigSpec.IntValue NEW_TABOO_INTERVAL = BUILDER.comment("Number of seconds between adding new taboos").defineInRange("new_taboo_interval", 300, 1, Integer.MAX_VALUE);
	public static final ForgeConfigSpec.BooleanValue SUPPRESS_COMMAND_OUTPUT = BUILDER.comment("Prints the command executed in chat if a punishment is performed with a command").define("suppress_command_output", false);
	
	static final ForgeConfigSpec SPEC = BUILDER.build();
}