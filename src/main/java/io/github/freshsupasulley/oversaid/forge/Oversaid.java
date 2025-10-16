package io.github.freshsupasulley.oversaid.forge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.freshsupasulley.oversaid.*;
import io.github.freshsupasulley.oversaid.plugin.OversaidPlugin;
import io.github.freshsupasulley.oversaid.punishments.AbyssPunishment;
import io.github.freshsupasulley.oversaid.punishments.AnvilPunishment;
import io.github.freshsupasulley.oversaid.punishments.WardenPunishment;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

@Mod(Oversaid.MODID)
@Mod.EventBusSubscriber(modid = Oversaid.MODID)
public final class Oversaid {
	
	public static final List<SidedPunishment<?>> punishments = new ArrayList<>();
	
	public static final String MODID = "oversaid";
	public static final Logger LOGGER = LogUtils.getLogger();
	public static final Map<UUID, Map<Integer, ResetFunction>> RESETS = new HashMap<>();
	
	// Keeps track of the server punishment builders so we can instantiate them after all punishments are defined
	private static final List<SPBuilder> builders = new ArrayList<>();
	
	public Oversaid(FMLJavaModLoadingContext context)
	{
		context.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
		
		// Register our punishments
		// The only good punishment (very rare)
		register(OversaidCategory.GOOD, (player, level) ->
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
		
		// Now register them by instantiation
		builders.forEach(SPBuilder::create);
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
		dispatcher.register(Commands.literal("oversaid").requires(source -> source.hasPermission(4))
				// existing category/index branch
				// new "add" subcommand that accepts a string (allows spaces)
				.then(Commands.literal("taboos").executes(ctx ->
				{
					Component toSend;
					
					if(OversaidPlugin.TABOOS.isEmpty())
					{
						toSend = Component.literal("No taboos");
					}
					else
					{
						toSend = Component.literal(String.join(", ", OversaidPlugin.TABOOS));
					}
					
					ctx.getSource().sendSuccess(() -> toSend, false);
					return 0;
				})).then(Commands.literal("reset").executes(ctx ->
				{
					var player = ctx.getSource().getPlayerOrException();
					var resets = Oversaid.RESETS.get(player.getUUID()).entrySet();
					final int totalResets = resets.size();
					var iterator = resets.iterator();
					
					while(iterator.hasNext())
					{
						try
						{
							//noinspection unchecked
							((SidedPunishment<ServerPlayer>) iterator.next().getValue().punishment()).fireReset(player);
						} catch(Exception e)
						{
							ctx.getSource().sendFailure(Component.literal("Failed to execute reset"));
							return 0;
						}
						
						iterator.remove();
					}
					
					ctx.getSource().sendSuccess(() -> Component.literal("Fired " + totalResets + " reset(s)"), false);
					return 0;
				}))
				//				.then(Commands.literal("add").then(Commands.argument("taboo", StringArgumentType.greedyString()).executes(ctx ->
				//				{
				//					String text = StringArgumentType.getString(ctx, "taboo").trim();
				//
				//					// Add to list
				//					var punishment = OversaidPlugin.getOversaidPunishment();
				//					List<String> taboos = punishment.getTaboos();
				//					taboos.add(text);
				//					punishment.config.set("taboo", taboos);
				//					OversaidPlugin.serverAPI.getServerConfig().save();
				//
				//					ctx.getSource().sendSuccess(() -> Component.literal("Added ").append(Component.literal(text).withStyle(style -> style.withBold(true))).append(Component.literal(" to the trickler taboos")), true);
				//					return 0;
				//				})))
				.then(Commands.literal("punish").then(Commands.argument("category", StringArgumentType.word()).suggests((ctx, builder) ->
				{
					for(var cat : OversaidCategory.values())
					{
						builder.suggest(cat.name().toLowerCase());
					}
					return builder.buildFuture();
				}).then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(ctx ->
				{
					String categoryName = StringArgumentType.getString(ctx, "category").toUpperCase();
					int index = IntegerArgumentType.getInteger(ctx, "index");
					
					// Get the category
					OversaidCategory category;
					
					try
					{
						category = OversaidCategory.valueOf(categoryName);
					} catch(IllegalArgumentException e)
					{
						ctx.getSource().sendFailure(Component.literal("Invalid category: " + categoryName));
						return 1;
					}
					
					// Get the punishment
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					
					if(index < 0 || index >= category.getPunishments().size())
					{
						ctx.getSource().sendFailure(Component.literal("Invalid index (must be within [0-" + (category.getPunishments().size() - 1) + "]"));
						return 1;
					}
					
					try
					{
						new OversaidPunishment(category, category.getPunishments().get(index)).punish(player);
					} catch(RuntimeException e)
					{
						ctx.getSource().sendFailure(Component.literal("Failed executing server punishment"));
						return 1;
					}
					
					ctx.getSource().sendSuccess(() -> Component.literal("Executed server punishment"), false);
					return 0;
				})))));
	}
	
	public static void addReset(UUID player, OversaidPunishment punishment, SidedPunishment<?> selected)
	{
		// If a reset for this punishment was already due, this effectively just delays when the reset will happen
		LOGGER.info("Putting reset for punishment in category {}", punishment.getCategory());
		RESETS.computeIfAbsent(player, uuid -> new HashMap<>()).put(punishment.getPunishmentIndex(), new ResetFunction(selected, selected.calculateResetTime()));
	}
	
	// SERVER
	private static SPBuilder register(OversaidCategory category, BiPredicate<ServerPlayer, ServerLevel> execution)
	{
		var builder = new SPBuilder(category, (player) -> execution.test(player, player.level()));
		builders.add(builder);
		return builder;
	}
	
	// Convenience method to a run a command from the player's position but as op
	private static void runCommand(ServerPlayer player, String command)
	{
		var server = player.getServer();
		var stack = server.createCommandSourceStack().withPermission(2).withPosition(player.blockPosition().getCenter()).withRotation(player.getRotationVector()).withSuppressedOutput();
		
		server.getCommands().performPrefixedCommand(stack, command);
	}
	
	private static void registerBad()
	{
		// Poison and weakness II for 20
		register(OversaidCategory.BAD, (player, level) ->
		{
			final int seconds = 20;
			player.addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20, 1));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, seconds * 20, 1));
			return true;
		});
		
		// Slowness + blindness
		register(OversaidCategory.BAD, (player, level) ->
		{
			final int seconds = 20;
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, seconds * 20, 4));
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, seconds * 20, 0));
			return true;
		});
		
		// Mining fatigue III
		register(OversaidCategory.BAD, (player, level) ->
		{
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60 * 20, 2));
			return true;
		});
		
		// Hunger 50 for 15s
		register(OversaidCategory.BAD, (player, level) ->
		{
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 15 * 20, 49));
			return true;
		});
		
		// Shrink the player
		register(OversaidCategory.BAD, (player, level) ->
		{
			runCommand(player, "attribute @p minecraft:scale base set 0.1");
			return true;
		}).addReset(TimeUnit.MINUTES, 5, player ->
		{
			runCommand(player, "attribute @p minecraft:scale base set 1");
		});
		
		// Grow the player
		register(OversaidCategory.BAD, (player, level) ->
		{
			runCommand(player, "attribute @p minecraft:scale base set 3");
			return true;
		}).addReset(TimeUnit.MINUTES, 2, player -> runCommand(player, "attribute @p minecraft:scale base set 1"));
		
		// Reduce durability of held item
		register(OversaidCategory.BAD, (player, level) ->
		{
			// Get the item in the main hand
			ItemStack heldItem = player.getMainHandItem();
			
			// Abandon if the item isn't damageable
			if(!heldItem.isDamageableItem())
				return false;
			
			// Half it I guess?
			final int factor = 2;
			
			// Don't break it, leave it at most 1 tap away
			heldItem.setDamageValue(Math.min(heldItem.getMaxDamage() - 1, heldItem.getMaxDamage() - (heldItem.getMaxDamage() - heldItem.getDamageValue()) / factor));
			return true;
		}).setMessage("No more durability for you");
		
		// Replace all stone and cobblestone in a radius with their infested counterpart
		register(OversaidCategory.BAD, (player, level) ->
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
		
		// Silverfish with weaving
		register(OversaidCategory.BAD, (player, level) ->
		{
			for(int i = 0; i < 3; i++)
			{
				runCommand(player, "summon silverfish ~ ~ ~ {active_effects:[{id:\"minecraft:weaving\",duration:20000000,show_icon:0b}]}");
			}
			
			return true;
		});
		
		// Spawn one creeper
		register(OversaidCategory.BAD, (player, level) ->
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
		register(OversaidCategory.BAD, (player, level) ->
		{
			for(int i = 0; i < 2; i++)
			{
				runCommand(player, "summon skeleton ~ ~ ~");
			}
			
			return true;
		});
		
		// Curse of vanishing on their armor / held armor
		register(OversaidCategory.BAD, (player, level) ->
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
		register(OversaidCategory.BAD, (player, level) ->
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
		
		// Shitty item spam (this even sets their armor slots lmao)
		register(OversaidCategory.BAD, (player, level) ->
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
					// Fill with a full stack of garbage
					ItemLike[] items = {Items.LEAF_LITTER, Items.TALL_GRASS, Items.SPORE_BLOSSOM, Items.MOSS_CARPET, Items.DEAD_BUSH, Items.LARGE_FERN};
					ItemStack kelpStack = new ItemStack(items[(int) (Math.random() * items.length)], 64);
					inventory.setItem(slot, kelpStack);
				}
			}
			
			return filled;
		});
		
		// Remove all item entities in a 10 block radius
		register(OversaidCategory.BAD, (player, level) ->
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
		
		// Replace all water and lava buckets in inventory with milk buckets
		register(OversaidCategory.BAD, (player, level) ->
		{
			boolean replacedAny = false;
			
			for(int i = 0; i < player.getInventory().getContainerSize(); i++)
			{
				ItemStack stack = player.getInventory().getItem(i);
				
				// Check if the item is a water or lava bucket
				if(stack.getItem() == Items.WATER_BUCKET || stack.getItem() == Items.LAVA_BUCKET)
				{
					// Replace with a milk bucket, maintaining the original count (should always be 1, but safe check)
					player.getInventory().setItem(i, new ItemStack(Items.MILK_BUCKET, stack.getCount()));
					replacedAny = true;
				}
			}
			
			return replacedAny;
		}).setMessage("Your buckets have been purified with milk");
	}
	
	private static void registerVeryBad()
	{
		// Cobweb trap
		register(OversaidCategory.BAD, (player, level) ->
		{
			BlockPos center = player.blockPosition();
			int radius = 1;
			AtomicInteger count = new AtomicInteger();
			
			BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius)).forEach(pos ->
			{
				if(level.getBlockState(pos).isAir())
				{
					count.incrementAndGet();
					level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
				}
			});
			
			return count.get() > 0;
		});
		
		// Invisible creeper
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			Creeper entity = EntityType.CREEPER.create(level, EntitySpawnReason.COMMAND);
			if(entity == null)
				return false;
			
			entity.setPos(player.getX(), player.getY(), player.getZ());
			entity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 1));
			level.addFreshEntity(entity);
			return true;
		});
		
		// Summon lightning bolt
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			runCommand(player, "execute at " + player.getName().getString() + " run summon lightning_bolt ~ ~ ~");
			return true;
		});
		
		// Summon 10 angry bees
		register(OversaidCategory.VERY_BAD, (player, level) ->
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
		
		// Insane levitation for 3s
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 3 * 20, 9));
			return true;
		}).setMessage("Up we go!!");
		
		// Summon 3 killer bunnies
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 3; i++)
			{
				runCommand(player, "summon minecraft:rabbit ~ ~ ~ {RabbitType:99}");
			}
			
			return true;
		});
		
		// Spawn 10 pufferfish
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 10; i++)
			{
				runCommand(player, "summon minecraft:pufferfish ~ ~ ~");
			}
			
			return true;
		});
		
		// Spawn 2 invisible enderman that are agrod on you
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 2; i++)
			{
				// Spawn near player
				EnderMan enderman = EntityType.ENDERMAN.create(level, EntitySpawnReason.COMMAND);
				if(enderman == null)
					return false;
				
				enderman.setPos(player.getX(), player.getY(), player.getZ());
				
				// Set the player as target
				enderman.setTarget(player);
				
				// (Optional) Force it to become aggressive immediately
				enderman.setPersistentAngerTarget(player.getUUID());
				enderman.setRemainingPersistentAngerTime(600); // 30 seconds
				
				// Give them invisibility
				enderman.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30 * 20, 1));
				
				// Add to world
				level.addFreshEntity(enderman);
			}
			
			return true;
		});
		
		// Launch in random XZ direction with some Y lift
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			final int strength = 20;
			player.setDeltaMovement(new Vec3((Math.random() - 0.5) * strength, 2, (Math.random() - 0.5) * strength));
			player.hurtMarked = true;
			return true;
		});
		
		// Force player to MLG clutch
		register(OversaidCategory.VERY_BAD, (player, level) ->
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
		}).setMessage("You have a water bucket. Lets see this clutch asf MLG");
		
		// this is bad
		// Delete held item unless it's an eye of ender
//		register(OversaidCategory.VERY_BAD, (player, level) ->
//		{
//			ItemStack held = player.getMainHandItem();
//			if(!held.isEmpty() && !held.is(Items.ENDER_EYE))
//			{
//				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
//				player.displayClientMessage(Component.literal("your ").append(held.getItemName()).append(" is mine now"), false);
//				return true;
//			}
//
//			return false;
//		});
		
		// Spawn charged creeper
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			runCommand(player, "summon creeper ~ ~ ~ {powered:1b}");
			return true;
		});
		
		// Spawn 5 phantoms (needs loop)
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 5; i++)
			{
				runCommand(player, "summon phantom ~ ~5 ~");
			}
			
			return true;
		});
		
		// Spawn 3 invisible baby zombies
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			for(int i = 0; i < 3; i++)
			{
				runCommand(player, "summon zombie ~ ~ ~ {IsBaby:1b,active_effects:[{id:\"minecraft:invisibility\",duration:20000000,show_icon:0b}]}");
			}
			
			return true;
		}).setMessage("RELEASE THE BABY!!");
		
		// Summon 1 invisible ghast
		register(OversaidCategory.VERY_BAD, (player, level) ->
		{
			runCommand(player, "summon ghast ~ ~5 ~ {active_effects:[{id:\"minecraft:invisibility\",duration:20000000,show_icon:0b}]}");
			return true;
		});
	}
	
	private static void registerCrushing()
	{
		// Summon anvils above their head, then immediately make them disappear
		// This is all we have to do
		new AnvilPunishment(OversaidCategory.CRUSHING);
		
		// Dig them a giant fucking hole underneath their feet, let them fall for 3s, then reset the blocks
		// This effectively traps them inside the earth
		new AbyssPunishment(OversaidCategory.CRUSHING);
		
		// Spawn the warden, TEMPORARILY. This kills it after 2 mins
		new WardenPunishment(OversaidCategory.CRUSHING);
		
		// Rig them to explode (give them time to prepare)
		register(OversaidCategory.CRUSHING, (player, level) -> true).setMessage("You are rigged to explode in 15 seconds").addReset(TimeUnit.SECONDS, 15, player ->
		{
			// Ig they win
			if(player.isDeadOrDying())
				return;
			
			Vec3 pos = player.position();
			player.level().explode(null, player.level().damageSources().generic(), new ExplosionDamageCalculator(), pos.x, pos.y, pos.z, 5, true, Level.ExplosionInteraction.BLOCK);
		});
		
		// Lava block under player
		// maybe a bit too deadly
//		register(OversaidCategory.CRUSHING, (player, level) ->
//		{
//			runCommand(player, "setblock ~ ~ ~ lava");
//			return true;
//		});
		
		// Replace helmet with pumpkin with Curse of Binding
		register(OversaidCategory.CRUSHING, (player, level) ->
		{
			ItemStack helmet = player.getInventory().getEquipment().get(EquipmentSlot.HEAD);
			
			// Break early if this punishment already happened to them (or if they just so happen to be wearing a pumpkin, ig they get a pass)
			if(helmet.is(Items.CARVED_PUMPKIN))
				return false;
			
			runCommand(player, "item replace entity @p armor.head with carved_pumpkin[minecraft:enchantments={\"minecraft:binding_curse\": 1}]");
			return true;
		}).setMessage("PUMPKIN!!!!");
		
		// Set max HP to a few hearts less than normal
		register(OversaidCategory.CRUSHING, (player, level) ->
		{
			final float factor = 0.8f;
			final float baseHealth = (float) player.getAttribute(Attributes.MAX_HEALTH).getValue();
			
			final float newHealth = baseHealth * factor;
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newHealth);
			
			if(player.getHealth() > newHealth)
			{
				player.setHealth(newHealth);
			}
			
			return true;
		}).addReset(TimeUnit.MINUTES, 5, player ->
		{
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ServerPlayer.MAX_HEALTH);
			player.displayClientMessage(Component.literal("Reset your max hearts to normal"), false);
		}).setMessage("Reduced your max hearts for 5 minutes");
	}
}
