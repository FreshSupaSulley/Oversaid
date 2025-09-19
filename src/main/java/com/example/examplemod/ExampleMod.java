package com.example.examplemod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ExampleMod.MODID)
public final class ExampleMod {
	
	public static final String MODID = "examplemod";
	public static final Logger LOGGER = LogUtils.getLogger();
	
	public ExampleMod(FMLJavaModLoadingContext context)
	{
		LOGGER.info("Hello {}!", MODID);
	}
}
