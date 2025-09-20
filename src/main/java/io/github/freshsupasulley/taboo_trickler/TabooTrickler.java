package io.github.freshsupasulley.taboo_trickler;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TabooTrickler.MODID)
public final class TabooTrickler {
	
	public static final String MODID = "taboo_trickler";
	public static final Logger LOGGER = LogUtils.getLogger();
	
	public TabooTrickler(FMLJavaModLoadingContext context)
	{
		LOGGER.info("Hello from {}!", MODID);
		context.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
	}
}
