package io.github.freshsupasulley.oversaid.punishments;

import io.github.freshsupasulley.oversaid.OversaidCategory;
import io.github.freshsupasulley.oversaid.ServerPunishment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.TimeUnit;

public class WardenPunishment extends ServerPunishment {
	
	private Entity entity;
	
	public WardenPunishment(OversaidCategory category)
	{
		// 10 seconds should be enough to fall to the bottom?? Right???
		super(category, "good luck", TimeUnit.MINUTES, 3);
	}
	
	@Override
	protected boolean internalPunish(ServerPlayer player)
	{
		var level = player.level();
		
		var result = SpawnUtil.trySpawnMob(EntityType.WARDEN, EntitySpawnReason.TRIGGERED, level, player.blockPosition(), 20, 5, 6, net.minecraft.util.SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false);
		if(result.isEmpty()) return false;
		
		entity = result.get();
		return true;
	}
	
	@Override
	protected void internalReset(ServerPlayer player)
	{
		entity.kill(player.level());
		super.displayClientMessage(player, Component.literal("The warden lies slain..."));
	}
}
