package io.github.freshsupasulley.taboo_trickler.forge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.freshsupasulley.taboo_trickler.*;
import io.github.freshsupasulley.taboo_trickler.plugin.TricklerPunishment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Mod(TabooTrickler.MODID)
@Mod.EventBusSubscriber(modid = TabooTrickler.MODID)
public final class TabooTrickler {
	
	public static final String MODID = "taboo_trickler";
	public static final Logger LOGGER = LogUtils.getLogger();
	public static final Map<UUID, Map<Integer, ResetFunction>> RESETS = new HashMap<>();
	
	public TabooTrickler(FMLJavaModLoadingContext context)
	{
		context.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
		
		// Register our punishments
		// The only good punishment (very rare)
		register(TricklerCategory.GOOD, (player, level) ->
		{
			BlockPos pos = player.blockPosition();
			
			// Check if the block below is solid before placing cake
			if(level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))
			{
				level.setBlock(pos, Blocks.CAKE.defaultBlockState(), 3);
				return true;
			}
			
			// unsuccessful, pick another command / category
			return false;
		}).setMessage("Enjoy your cake lil bro!!");
		
		// Defer to functions
		registerBad();
		registerVeryBad();
		registerCrushing();
	}
	
	@SubscribeEvent
	private static void onServerTick(TickEvent.ServerTickEvent event)
	{
		RESETS.forEach((key, value) ->
		{
			ServerPlayer player = event.getServer().getPlayerList().getPlayer(key);
			handleReset(player, value);
		});
	}
	
	@SubscribeEvent
	private static void onClientTick(TickEvent.ClientTickEvent event)
	{
		RESETS.forEach((key, value) -> handleReset(Minecraft.getInstance(), value));
	}
	
	private static <T> void handleReset(T context, Map<Integer, ResetFunction> value)
	{
		final long time = System.currentTimeMillis();
		
		for(Iterator<Map.Entry<Integer, ResetFunction>> it = value.entrySet().iterator(); it.hasNext();)
		{
			ResetFunction reset = it.next().getValue();
			
			// Check if expired
			if(time >= reset.resetTime())
			{
				// Run the reset and remove from the iterator
				// This guaranteed to be a server punishment here
				//noinspection unchecked
				((SidedPunishment<T>) reset.punishment()).fireReset(context);
				it.remove();
			}
		}
	}
	
	@SubscribeEvent
	private static void registerCommands(RegisterCommandsEvent event)
	{
		var dispatcher = event.getDispatcher();
		
		// For testing our punishments
		dispatcher.register(Commands.literal("trickler").requires(source -> source.hasPermission(4)).then(Commands.argument("category", StringArgumentType.word()).suggests((ctx, builder) ->
		{
			for(var cat : TricklerCategory.values())
			{
				builder.suggest(cat.name().toLowerCase());
			}
			return builder.buildFuture();
		}).then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(ctx ->
		{
			String categoryName = StringArgumentType.getString(ctx, "category").toUpperCase();
			int index = IntegerArgumentType.getInteger(ctx, "index");
			
			// Get the category
			TricklerCategory category;
			
			try
			{
				category = TricklerCategory.valueOf(categoryName);
			} catch(IllegalArgumentException e)
			{
				ctx.getSource().sendFailure(Component.literal("Invalid category: " + categoryName));
				return 0;
			}
			
			// Get the punishment
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			
			if(index < 0 || index >= category.punishments.size())
			{
				ctx.getSource().sendFailure(Component.literal("Invalid index (must be within [0-" + (category.punishments.size() - 1) + "]"));
				return 0;
			}
			
			if(category.punishments.get(index).isServerSide())
			{
				try
				{
					new TricklerPunishment(category, index).punish(player);
				} catch(RuntimeException e)
				{
					ctx.getSource().sendFailure(Component.literal("Failed executing server punishment"));
					return 0;
				}
				
				ctx.getSource().sendSuccess(() -> Component.literal("Executed server punishment"), false);
				return 1;
			}
			else
			{
				try
				{
					new TricklerPunishment(category, index).punishClientSide();
				} catch(RuntimeException e)
				{
					ctx.getSource().sendFailure(Component.literal("Failed executing client punishment"));
					return 0;
				}
				
				ctx.getSource().sendSuccess(() -> Component.literal("Executed client punishment"), false);
				return 1;
			}
		}))));
	}
	
	public static void addReset(UUID player, int index, SidedPunishment<?> selected)
	{
		// If a reset for this punishment was already due, this effectively just delays when the reset will happen
		LOGGER.info("Putting reset for punishment #{}", index);
		RESETS.computeIfAbsent(player, uuid -> new HashMap<>(Map.of(index, new ResetFunction(selected, selected.calculateResetTime()))));
	}
	
	// SERVER
	private static ServerPunishment register(TricklerCategory category, BiPredicate<ServerPlayer, ServerLevel> execution)
	{
		return new ServerPunishment(category, (player) -> execution.test(player, player.level()));
	}
	
	// CLIENT
	private static ClientPunishment register(TricklerCategory category, Predicate<Minecraft> punishment)
	{
		return new ClientPunishment(category, punishment);
	}
	
	// Convenience method to a run a command from the player's position but as op
	private static void runCommand(ServerPlayer player, String command)
	{
		var server = player.getServer();
		var stack = server.createCommandSourceStack().withPermission(2).withPosition(player.blockPosition().getCenter()).withRotation(player.getRotationVector());
		
		// If the user wants to silence the commands
		if(Config.SUPPRESS_COMMAND_OUTPUT.get())
		{
			stack = stack.withSuppressedOutput();
		}
		
		server.getCommands().performPrefixedCommand(stack, command);
	}
	
	private static void registerBad()
	{
		// BAD (1-8): Poison II and Weakness II for 30s
		register(TricklerCategory.BAD, (player, level) ->
		{
			player.addEffect(new MobEffectInstance(MobEffects.POISON, 30 * 20, 1));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30 * 20, 1));
			return true;
		});
		
		// Slowness + blindness
		register(TricklerCategory.BAD, (player, level) ->
		{
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30 * 20, 4));
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30 * 20, 0));
			return true;
		});
		
		// Replace all torches in your inventory and ones nearby with redstone torches
		register(TricklerCategory.BAD, (player, level) ->
		{
			BlockPos playerPos = player.blockPosition();
			int radius = 10;
			
			var list = BlockPos.betweenClosedStream(playerPos.offset(-radius, -radius, -radius), playerPos.offset(radius, radius, radius)).map(pos -> Map.entry(pos.immutable(), level.getBlockState(pos))).filter(entry ->
			{
				Block block = entry.getValue().getBlock();
				return block == Blocks.TORCH || block == Blocks.WALL_TORCH;
			}).collect(Collectors.toUnmodifiableList());
			
			boolean replacedAny = false;
			
			for(int i = 0; i < player.getInventory().getContainerSize(); i++)
			{
				ItemStack stack = player.getInventory().getItem(i);
				if(stack.is(Items.TORCH))
				{
					ItemStack redstone = new ItemStack(Items.REDSTONE_TORCH, stack.getCount());
					player.getInventory().setItem(i, redstone);
					replacedAny = true;
				}
			}
			
			// If we didn't find any nearby torches AND the player doesn't have any
			if(list.isEmpty() && !replacedAny)
				return false;
			
			list.forEach(entry ->
			{
				BlockPos pos = entry.getKey();
				BlockState state = entry.getValue();
				Block block = state.getBlock();
				
				if(block == Blocks.TORCH)
				{
					level.setBlockAndUpdate(pos, Blocks.REDSTONE_TORCH.defaultBlockState());
				}
				else if(block == Blocks.WALL_TORCH)
				{
					Direction facing = state.getValue(WallTorchBlock.FACING);
					BlockPos attached = pos.relative(facing.getOpposite());
					
					if(level.getBlockState(attached).isSolid())
					{
						BlockState redWall = Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
						level.setBlockAndUpdate(pos, redWall);
					}
				}
			});
			
			return true;
		}).setMessage("May all your torches be redstone ones");
		
		// Cobweb trap
		register(TricklerCategory.BAD, (player, level) ->
		{
			BlockPos center = player.blockPosition();
			int radius = 2;
			BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius)).forEach(pos ->
			{
				if(level.getBlockState(pos).isAir())
				{
					level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
				}
			});
			
			return true;
		});
		
		// Spawn one creeper
		register(TricklerCategory.BAD, (player, level) ->
		{
			Creeper creeper = EntityType.CREEPER.create(level, EntitySpawnReason.COMMAND);
			if(creeper != null)
			{
				creeper.setPos(player.position());
				level.addFreshEntity(creeper);
			}
			
			return true;
		});
		
		// BAD (26-29): Set FOV to 30 and brightness to 0 — CLIENT
		//		register(TTPunishmentCategory.BAD, (minecraft) -> {
		//			minecraft.options.fov = 30.0F;
		//			minecraft.options.gamma = 0.0F;
		//		});
	}
	
	private static void registerVeryBad()
	{
		// Summon lightning bolt
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			runCommand(player, "execute at " + player.getName().getString() + " run summon lightning_bolt ~ ~ ~");
			return true;
		});
		
		// Spawn live TNT with 3 second fuse
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			runCommand(player, "summon tnt ~ ~ ~ {Fuse:60}");
			return true;
		}).setMessage("RUN");
		
		// Spawn 3 baby zombies (needs loop)
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 3; i++)
			{
				runCommand(player, "summon zombie ~ ~ ~ {IsBaby:1b}");
			}
			
			return true;
		});
		
		// Delete held item unless it's an eye of ender
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			ItemStack held = player.getMainHandItem();
			if(!held.isEmpty() && !held.is(Items.ENDER_EYE))
			{
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				player.displayClientMessage(Component.literal("your " + held.getDisplayName().getString() + " is mine now"), false);
				return true;
			}
			
			return false;
		});
		
		// VERY BAD (65-69): Spawn charged creeper
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			runCommand(player, "summon creeper ~ ~ ~ {powered:1b}");
			return true;
		});
		
		// VERY BAD (70-74): Spawn 5 phantoms (needs loop)
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 5; i++)
			{
				runCommand(player, "summon phantom ~ ~5 ~");
			}
			
			return true;
		});
		
		// VERY BAD (75-79): Spawn 3 invisible skeletons (needs loop + potion effect)
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 3; i++)
			{
				runCommand(player, "summon skeleton ~ ~ ~ {active_effects:[{id:\"minecraft:invisibility\",duration:20000000,show_icon:0b}]}");
			}
			
			return true;
		});
	}
	
	private static void registerCrushing()
	{
		// Spawn 3 invisible baby zombies
		register(TricklerCategory.CRUSHING, (player, level) ->
		{
			for(int i = 0; i < 3; i++)
			{
				runCommand(player, "summon zombie ~ ~ ~ {IsBaby:1b,active_effects:[{id:\"minecraft:invisibility\",duration:20000000,show_icon:0b}]}");
			}
			
			return true;
		}).setMessage("RELEASE THE BABY!!");
		
		// Spawn a zombie with full diamond gear
		register(TricklerCategory.CRUSHING, (player, level) ->
		{
			runCommand(player, "summon zombie ~ ~ ~ {equipment:{mainhand:{count:1,id:\"minecraft:diamond_sword\"},feet:{id: \"minecraft:diamond_boots\",count:1},legs:{id: \"minecraft:diamond_leggings\",count:1},chest:{id: \"minecraft:diamond_chestplate\",count:1},head:{id:\"minecraft:diamond_helmet\",count:1}}}");
			return true;
		});
		
		// Lava block under player
		register(TricklerCategory.CRUSHING, (player, level) ->
		{
			runCommand(player, "setblock ~ ~ ~ lava");
			return true;
		});
		
		// Spawn one Warden
		register(TricklerCategory.CRUSHING, (player, level) ->
		{
			runCommand(player, "summon warden ~ ~ ~");
			return true;
		}).setMessage("good luck");
		
		// Replace helmet with pumpkin with Curse of Binding
		register(TricklerCategory.CRUSHING, (player, level) ->
		{
			ItemStack helmet = player.getInventory().getEquipment().get(EquipmentSlot.HEAD);
			
			// Break early if this punishment already happened to them (or if they just so happen to be wearing a pumpkin, ig they get a pass)
			if(helmet.is(Items.CARVED_PUMPKIN))
				return false;
			
			runCommand(player, "item replace entity @p armor.head with carved_pumpkin[minecraft:enchantments={\"minecraft:binding_curse\": 1}]");
			return true;
		}).setMessage("PUMPKIN!!!!");
		
		// Summon 1 invisible ghast
		register(TricklerCategory.CRUSHING, (player, level) ->
		{
			runCommand(player, "summon ghast ~ ~5 ~ {active_effects:[{id:\"minecraft:invisibility\",duration:20000000,show_icon:0b}]}");
			return true;
		});
		
		// CRUSHING (98): Set max HP to 8 hearts (16 health) (no command)
		register(TricklerCategory.CRUSHING, (player, level) ->
		{
			final int newHealth = 8;
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newHealth);
			
			if(player.getHealth() > newHealth)
			{
				player.setHealth(newHealth);
			}
			
			return true;
		}).addReset(TimeUnit.SECONDS, 10, player ->
		{
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ServerPlayer.MAX_HEALTH);
			player.displayClientMessage(Component.literal("Reset your max hearts to normal"), false);
		}).setMessage("Reduced your max hearts for a bit");
		
		// Rickroll
		register(TricklerCategory.CRUSHING, (minecraft) ->
		{
			Util.getPlatform().openUri("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
			return true;
		});
	}
}
