package io.github.freshsupasulley.taboo_trickler.forge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.freshsupasulley.taboo_trickler.ResetFunction;
import io.github.freshsupasulley.taboo_trickler.ServerPunishment;
import io.github.freshsupasulley.taboo_trickler.SidedPunishment;
import io.github.freshsupasulley.taboo_trickler.TricklerCategory;
import io.github.freshsupasulley.taboo_trickler.plugin.TricklerPunishment;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

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
			
			handleReset(true, player, value);
		});
	}
	
	@SubscribeEvent
	private static void onClientTick(TickEvent.ClientTickEvent event)
	{
		RESETS.forEach((key, value) -> handleReset(false, Minecraft.getInstance(), value));
	}
	
	private static <T> void handleReset(boolean isServer, T context, Map<Integer, ResetFunction> value)
	{
		final long time = System.currentTimeMillis();
		
		for(Iterator<Map.Entry<Integer, ResetFunction>> it = value.entrySet().iterator(); it.hasNext(); )
		{
			ResetFunction reset = it.next().getValue();
			
			// Ignore resets dedicated for the other side
			if(reset.punishment().isServerSide() != isServer)
				continue;
			
			// Check if expired
			if(time >= reset.resetTime())
			{
				LOGGER.info("Executing reset");
				
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
		RESETS.computeIfAbsent(player, uuid -> new HashMap<>()).put(index, new ResetFunction(selected, selected.calculateResetTime()));
	}
	
	// SERVER
	private static ServerPunishment register(TricklerCategory category, BiPredicate<ServerPlayer, ServerLevel> execution)
	{
		return new ServerPunishment(category, (player) -> execution.test(player, player.level()));
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
		// Poison and weakness II for 30s
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
		
		// Mining fatigue III
		register(TricklerCategory.BAD, (player, level) ->
		{
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60 * 20, 2));
			return true;
		});
		
		// Hunger 50 for 15s
		register(TricklerCategory.BAD, (player, level) ->
		{
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 15 * 20, 49));
			return true;
		});
		
		// Shrink the player
		register(TricklerCategory.BAD, (player, level) ->
		{
			runCommand(player, "attribute @p minecraft:scale base set 0.1");
			return true;
		}).addReset(TimeUnit.MINUTES, 5, player ->
		{
			runCommand(player, "attribute @p minecraft:scale base set 1");
		});
		
		// Grow the player
		register(TricklerCategory.BAD, (player, level) ->
		{
			runCommand(player, "attribute @p minecraft:scale base set 3");
			return true;
		}).addReset(TimeUnit.MINUTES, 2, player ->
		{
			runCommand(player, "attribute @p minecraft:scale base set 1");
		});
		
		// Reduce durability of held item
		register(TricklerCategory.BAD, (player, level) ->
		{
			// Get the item in the main hand
			ItemStack heldItem = player.getMainHandItem();
			
			// Abandon if the item isn't damageable
			if(!heldItem.isDamageableItem())
				return false;
			
			final int factor = 8;
			
			// Don't break it, leave it at most 1 tap away
			heldItem.setDamageValue(Math.min(heldItem.getMaxDamage() - 1, heldItem.getMaxDamage() - (heldItem.getMaxDamage() - heldItem.getDamageValue()) / factor));
			return true;
		}).setMessage("No more durability for you");
		
		// Replace all torches in your inventory and ones nearby with redstone torches
		register(TricklerCategory.BAD, (player, level) ->
		{
			BlockPos playerPos = player.blockPosition();
			int radius = 10;
			
			var list = BlockPos.betweenClosedStream(playerPos.offset(-radius, -radius, -radius), playerPos.offset(radius, radius, radius)).map(pos -> Map.entry(pos.immutable(), level.getBlockState(pos))).filter(entry ->
			{
				Block block = entry.getValue().getBlock();
				return block == Blocks.TORCH || block == Blocks.WALL_TORCH;
			}).toList();
			
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
		
		// Replace all stone and cobblestone in a radius with their infested counterpart
		register(TricklerCategory.BAD, (player, level) ->
		{
			BlockPos playerPos = player.blockPosition();
			int radius = 5;
			
			Map<Block, Block> infestables = new HashMap<>();
			infestables.put(Blocks.STONE, Blocks.INFESTED_STONE);
			infestables.put(Blocks.COBBLESTONE, Blocks.INFESTED_COBBLESTONE);
			infestables.put(Blocks.CHISELED_STONE_BRICKS, Blocks.INFESTED_CHISELED_STONE_BRICKS);
			infestables.put(Blocks.DEEPSLATE, Blocks.INFESTED_DEEPSLATE);
			infestables.put(Blocks.STONE_BRICKS, Blocks.INFESTED_STONE_BRICKS);
			infestables.put(Blocks.CRACKED_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS);
			infestables.put(Blocks.MOSSY_STONE_BRICKS, Blocks.INFESTED_MOSSY_STONE_BRICKS);
			
			var list = BlockPos.betweenClosedStream(playerPos.offset(-radius, -radius, -radius), playerPos.offset(radius, radius, radius)).map(pos -> Map.entry(pos.immutable(), level.getBlockState(pos))).filter(entry ->
			{
				Block block = entry.getValue().getBlock();
				return infestables.containsKey(block);
			}).toList();
			
			// If we didn't find any candidates nearby
			if(list.isEmpty())
				return false;
			
			list.forEach(entry ->
			{
				BlockPos pos = entry.getKey();
				BlockState state = entry.getValue();
				level.setBlockAndUpdate(pos, infestables.get(state.getBlock()).defaultBlockState());
			});
			
			return true;
		}).setMessage("They're living in your walls");
		
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
		
		// Silverfish with weaving
		register(TricklerCategory.BAD, (player, level) ->
		{
			for(int i = 0; i < 3; i++)
			{
				runCommand(player, "summon silverfish ~ ~ ~ {active_effects:[{id:\"minecraft:weaving\",duration:20000000,show_icon:0b}]}");
			}
			
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
		
		// Spawn 2 skeletons
		register(TricklerCategory.BAD, (player, level) ->
		{
			for(int i = 0; i < 2; i++)
			{
				runCommand(player, "summon skeleton ~ ~ ~");
			}
			
			return true;
		});
		
		// Spawn one elder guardian
		register(TricklerCategory.BAD, (player, level) ->
		{
			ElderGuardian guardian = EntityType.ELDER_GUARDIAN.create(level, EntitySpawnReason.COMMAND);
			if(guardian != null)
			{
				guardian.setPos(player.position());
				level.addFreshEntity(guardian);
			}
			
			return true;
		});
		
		// Curse of vanishing on their armor / held armor
		register(TricklerCategory.BAD, (player, level) ->
		{
			var equipment = player.getInventory().getEquipment();
			List<ItemStack> armor = List.of(player.getItemHeldByArm(HumanoidArm.RIGHT), player.getItemHeldByArm(HumanoidArm.LEFT), equipment.get(EquipmentSlot.HEAD), equipment.get(EquipmentSlot.CHEST), equipment.get(EquipmentSlot.LEGS), equipment.get(EquipmentSlot.FEET));
			
			var enchantables = armor.stream().filter(item -> !item.isEnchanted() && item.isEnchantable()).toList();
			
			// If there's nothing to enchant
			if(enchantables.isEmpty())
				return false;
			
			enchantables.forEach(e -> e.enchant(Enchantments.VANISHING_CURSE.getOrThrow(player), 1));
			return true;
		}).setMessage("Applied curse of vanishing on your held items and armor");
		
		// Scramble inventory
		register(TricklerCategory.BAD, (player, level) ->
		{
			// Get the inventory list (main inventory only)
			List<ItemStack> inventoryList = player.getInventory().getNonEquipmentItems();
			if(inventoryList.isEmpty())
				return false;
			
			// Copy all items into a new list and shuffle
			List<ItemStack> shuffled = new ArrayList<>(inventoryList);
			Collections.shuffle(shuffled);
			
			// Set the shuffled items back into the player's inventory
			for(int i = 0; i < inventoryList.size(); i++)
			{
				inventoryList.set(i, shuffled.get(i));
			}
			
			// Mark inventory as changed so client syncs
			player.inventoryMenu.broadcastChanges();
			return true;
		}).setMessage("Scrambled your inventory");
		
		// Raw kelp spam (this even sets their armor slots lmao)
		register(TricklerCategory.BAD, (player, level) ->
		{
			Inventory inventory = player.getInventory();
			
			boolean filled = false;
			
			// Fill each empty slot with a full stack of kelp
			for(int slot = 0; slot < inventory.getContainerSize(); slot++)
			{
				ItemStack currentStack = inventory.getItem(slot);
				if(currentStack.isEmpty())
				{
					filled = true;
					// Fill with a full stack of kelp (64)
					ItemStack kelpStack = new ItemStack(Items.LEAF_LITTER, 64);
					inventory.setItem(slot, kelpStack);
				}
			}
			
			return filled;
		});
		
		// Remove all item entities in a 10 block radius
		register(TricklerCategory.BAD, (player, level) ->
		{
			final int radius = 10;
			
			// Define an axis-aligned bounding box (cube) centered on the player
			AABB box = new AABB(player.getX() - radius, player.getY() - radius, player.getZ() - radius, player.getX() + radius, player.getY() + radius, player.getZ() + radius);
			
			// Get all ItemEntities (dropped items) in that area
			var itemEntities = level.getEntitiesOfClass(ItemEntity.class, box);
			
			// Remove them
			for(ItemEntity item : itemEntities)
			{
				item.remove(Entity.RemovalReason.DISCARDED);
			}
			
			return !itemEntities.isEmpty();
		}).setMessage("Deleted all item entities near you");
		
		register(TricklerCategory.BAD, (player, level) ->
		{
			var destLevel = player.getServer().getLevel(Level.NETHER).getLevel();
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 15 * 20, 1)); // 15 secs of slow falling
			player.teleport(new TeleportTransition(destLevel, destLevel.getSharedSpawnPos().above().getBottomCenter(), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
			return true;
		}).addReset(TimeUnit.MINUTES, 2, player ->
		{
			// Only proceed if they're still in the nether
			if(player.level().dimension() == Level.NETHER)
				return;
			// Tp them back to a good spawn point (this seems to do the trick)
			var teleporttransition = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
			player.teleport(teleporttransition);
		}).setMessage("Enjoy the nether for a short while");
		
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
		
		// Send a random, questionable message to somebody else on the server (hopefully monitor_chat is off otherwise this would double up)
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			var insults = List.of("i fucking hate you", "can we kiss", "i want to touch you");
			
			// Get a random insult
			Component content = Component.literal(insults.get((int) (Math.random() * insults.size())));
			CommandSourceStack source = player.createCommandSourceStack();
			ChatType.Bound chatType = ChatType.bind(ChatType.SAY_COMMAND, source);
			
			var players = level.players();
			if(players.isEmpty()) return false;
			
			// Send it only to the recipient (not broadcast)
			players.get((int) (Math.random() * players.size())).sendSystemMessage(chatType.decorate(content));
			return true;
		});
		
		// Summon 10 angry bees
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 10; i++)
			{
				Bee bee = new Bee(EntityType.BEE, level);
				bee.setPos(player.position());
				
				// Set the bee's target to the player
				bee.setTarget(player);
				
				// Add the bee to the world
				level.addFreshEntity(bee);
			}
			
			return true;
		});
		
		// Insane levitation for 4s
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 4 * 20, 9));
			return true;
		}).setMessage("Up we go!!");
		
		// Summon 3 killer bunnies
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 3; i++)
			{
				runCommand(player, "summon minecraft:rabbit ~ ~ ~ {RabbitType:99}");
			}
			
			return true;
		});
		
		// Spawn 10 pufferfish
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 10; i++)
			{
				runCommand(player, "summon minecraft:pufferfish ~ ~ ~");
			}
			
			return true;
		});
		
		// Launch in random XZ direction with some Y lift
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			final int strength = 20;
			player.setDeltaMovement(new Vec3((Math.random() - 0.5) * strength, 5, (Math.random() - 0.5) * strength));
			player.hurtMarked = true;
			return true;
		});
		
		register(TricklerCategory.VERY_BAD, (player, level) ->
		{
			// Nether has a bedrock roof so this would just trap them up there (although funny, its annoying)
			if(player.level().dimension() == Level.NETHER)
				return false;
			
			// Fail if they don't already have a bucket and they also have no inventory space for one
			if(!player.getInventory().hasAnyOf(Set.of(Items.WATER_BUCKET)) && !player.addItem(new ItemStack(Items.WATER_BUCKET)))
				return false;
			
			// They are ready for the clutch
			player.teleportTo(player.getX(), player.getY() + 100, player.getZ());
			return true;
		}).setMessage("You now have a water bucket. Lets see this clutch asf MLG");
		
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
		
		// Set max HP to 8 hearts (16 health)
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
	}
}
