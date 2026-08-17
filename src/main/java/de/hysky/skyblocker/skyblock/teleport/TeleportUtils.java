package de.hysky.skyblocker.skyblock.teleport;

import de.hysky.skyblocker.skyblock.dungeon.DungeonBoss;
import de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonManager;
import de.hysky.skyblocker.utils.Area;
import de.hysky.skyblocker.utils.Location;
import de.hysky.skyblocker.utils.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class TeleportUtils {
	/**
	 * @return if the player is allowed to teleport
	 */
	public static boolean canTeleportInLocation() {
		if (Utils.getMap().equals("Mineshaft")) { // Glacite Mineshafts
			return false;
		} else if (Utils.getArea() == Area.CrystalHollows.JUNGLE_TEMPLE) { // Jungle Temple
			return false;
		} else if (Utils.getLocation() == Location.PRIVATE_ISLAND && Utils.getArea() != Area.PrivateIsland.YOUR_ISLAND) { // Visiting islands
			return false;
		} else if (Utils.getLocation() == Location.GARDEN && Utils.getArea() != Area.PrivateIsland.YOUR_ISLAND) { // Visiting islands
			return false;
		} else if (Utils.getArea() == Area.CrimsonIsle.DOJO) { // Crimson isle dojo
			return false;
		} else if (Utils.isInDungeons()) { // Dungeons
			if (DungeonManager.isInBoss() && DungeonManager.getBoss() == DungeonBoss.MAXOR) { // Maxor phase
				return false;
			}

			if (!DungeonManager.isCurrentRoomMatched()) return true;
			if (DungeonManager.getCurrentRoom().getName().equals("boxes-room")) { // Boulder puzzle
				return false;
			} else // Trap room
				if (DungeonManager.getCurrentRoom().getName().equals("teleport-pad-room")) { // Teleport maze
				return false;
			} else return !DungeonManager.getCurrentRoom().getName().startsWith("trap");
		}
		return true;
	}

	@Nullable
	public sealed interface TeleportType {
		/**
		 * @return The calculated absolute target location.
		 */
		@Nullable BlockPos raycast(Level level, Vec3 direction, Vec3 startPos);

		/**
		 * @param target The targeted block
		 * @return the exact ending player position
		 */
		Vec3 toPlayerPos(BlockPos target);

		record Transmission(int distance) implements TeleportType {
			/**
			 * Custom raycast for teleporting checks for blocks for each 1 block forward in teleport. (very similar to Hypixel's method)
			 */
			public @Nullable BlockPos raycast(Level level, Vec3 direction, Vec3 startPos) {
				BlockPos pos = getBlockPos(level, direction, startPos);
				if (pos == null) return null;
				if (passable(level, pos) && passable(level, pos.below(1))) {
					pos = pos.below(1);
				}
				return pos;

			}

			public boolean isValid(Vec3 playerPos, Vec3 eyePos, BlockPos targetPos) {
				return !BlockPos.containing(playerPos).equals(targetPos) && !BlockPos.containing(eyePos).equals(targetPos);
			}

			private @Nullable BlockPos getBlockPos(Level level, Vec3 direction, Vec3 startPos) {
				PredictiveSmoothAOTE.boxes.clear();
				//based on which way the ray is going get the needed vector for checking diagonals
				BlockPos xDiagonalOffset = direction.x() > 0 ? new BlockPos(1, 0, 0) : new BlockPos(-1, 0, 0);
				BlockPos zDiagonalOffset = direction.z() > 0 ? new BlockPos(0, 0, 1) : new BlockPos(0, 0, -1);


				int closeFloorY = Integer.MAX_VALUE;

				BlockPos lastBlockPos = null;
				for (double offset = 0; offset <= distance; offset++) {
					Vec3 pos = startPos.add(direction.scale(offset));
					BlockPos blockPos = BlockPos.containing(pos);
					if (offset > 0) PredictiveSmoothAOTE.boxes.add(pos);

					// check if there is a block is occupied
					if (!passable(level, blockPos)) {
						return lastBlockPos;
					}

					// check if the block at head height is free
					if (!passable(level, blockPos.above())) {
						if (offset == 0) {
							// cancel the check if starting height is too low
							Vec3 justAhead = pos.add(direction.scale(0.2));
							if ((justAhead.y() - Math.floor(justAhead.y())) <= 0.495) {
								continue;
							}
							// no teleport can happen
							return null;
						}
						return lastBlockPos;
					}

					// Check for diagonal walls
					// For some reason this check is directional, and you can go through from some directions.
					// This seems to emulate this as best as possible
					if (offset != 0) {
						if (direction.x() < 0 && (topFaceSolid(level, blockPos.east())) && (topFaceSolid(level, lastBlockPos.offset(zDiagonalOffset)))) {
							return lastBlockPos;
						}
						if (direction.z() < 0 && direction.x() < 0 && (topFaceSolid(level, blockPos.south())) && (topFaceSolid(level, lastBlockPos.offset(xDiagonalOffset)))) {
							return lastBlockPos;
						}
					}

					//if the player is close to the floor (including diagonally) save Y and when player goes bellow this y finish teleport
					if ((topFaceSolid(level, blockPos.below()) || (topFaceSolid(level, blockPos.below().offset(xDiagonalOffset)) && topFaceSolid(level, blockPos.below().offset(zDiagonalOffset)))) && (pos.y() - Math.floor(pos.y())) < 0.31) {
						closeFloorY = blockPos.getY() - 1;
					}

					//if the checking Y is same as closeY finish
					if (closeFloorY == blockPos.getY()) return lastBlockPos;
					lastBlockPos = blockPos;
				}

				// return full distance if no collision found
				return BlockPos.containing(startPos.add(direction.scale(distance)));
			}

			@Override
			public Vec3 toPlayerPos(BlockPos target) {
				return Vec3.atBottomCenterOf(target);
			}


			/**
			 * Checks to see if a block is in the allowed list to teleport though.
			 * Air, non-collidable blocks, carpets, pots, 3 or less snow layers.
			 *
			 * @param blockPos block location
			 * @return if a block location can be teleported though
			 */
			private static boolean passable(Level level, BlockPos blockPos) {
				BlockState blockState = level.getBlockState(blockPos);
				Block block = blockState.getBlock();
				VoxelShape shape = blockState.getCollisionShape(level, blockPos);

				return switch (block) {
					case CarpetBlock _, FlowerPotBlock _, WebBlock _ -> true;
					case SnowLayerBlock _ -> blockState.getValue(BlockStateProperties.LAYERS) <= 3;
					default -> shape.isEmpty();
				};
			}

			/**
			 * Checks to see if a block's top face is solid
			 */
			private static boolean topFaceSolid(Level level, BlockPos blockPos) {
				BlockState blockState = level.getBlockState(blockPos);
				VoxelShape shape = blockState.getCollisionShape(level, blockPos);
				if (shape.isEmpty()) {
					return false;
				}
				return shape.bounds().maxY >= 1 || blockState.getBlock() == Blocks.MUD;
			}
		}

		record Etherwarp(int distance) implements TeleportType {
			@Override
			public @Nullable BlockPos raycast(Level level, Vec3 direction, Vec3 startPos) {
				BlockHitResult result = level.clip(new EtherwarpClipContext(startPos, startPos.add(direction.scale(distance))));
				if (result.getType() == HitResult.Type.MISS) return null;

				BlockPos target = result.getBlockPos();
				Block targetBlock = level.getBlockState(target).getBlock();
				//
				if (targetBlock instanceof FenceGateBlock || targetBlock instanceof FenceBlock || targetBlock instanceof WallBlock) target = target.above();

				// Require 2 blocks of clearance to fit player
				if (blocksClearance(level, target.above(1)) || blocksClearance(level, target.above(2))) return null;
				return target;
			}

			@Override
			public Vec3 toPlayerPos(BlockPos target) {
				return Vec3.atBottomCenterOf(target).add(0, 1.05, 0);
			}

			private boolean blocksClearance(Level level, BlockPos pos) {
				BlockState state = level.getBlockState(pos);
				Block block = state.getBlock();
				// Single layer snow layers block clearance, despite not having a hitbox
				// This is probably because of Hypixel's 1.7 backend, not a hardcoded exception
				if (block instanceof SnowLayerBlock || block instanceof EndPortalBlock) return true;
				return !state.getCollisionShape(level, pos, CollisionContext.positionContext(0)).isEmpty();
			}
		}

		static @Nullable TeleportType get(String skyblockItemId, CompoundTag customData, boolean isSneaking, boolean weird, boolean instant, boolean ether, boolean sinrecall, boolean wither) {
			return switch (skyblockItemId) {
				case "ASPECT_OF_THE_LEECH_1" -> weird ? new Transmission(3) : null;
				case "ASPECT_OF_THE_LEECH_2" -> weird ? new Transmission(4) : null;

				case "ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID" -> {
					if (isSneaking && customData.getBooleanOr("ethermerge", false)) {
						yield ether ? new Etherwarp(getTunerDistance(customData) + 57) : null;
					}
					yield instant ? new Transmission(getTunerDistance(customData) + 8) : null;
				}
				case "ETHERWARP_CONDUIT" -> ether ? new Etherwarp(getTunerDistance(customData) + 57) : null;
				case "SINSEEKER_SCYTHE" -> sinrecall ? new Transmission(getTunerDistance(customData) + 4) : null;
				case "NECRON_BLADE", "ASTRAEA", "HYPERION", "SCYLLA", "VALKYRIE" -> wither ? new Transmission(10) : null;
				default -> null;
			};
		}
	}

	public static boolean consumesClick(Block block, Location location) {
		return switch (block) {
			case TrapDoorBlock trapDoor -> {
				if (!trapDoor.type.canOpenByHand()) yield false;
				if (location == Location.PRIVATE_ISLAND || location == Location.GARDEN) yield true;
				yield trapDoor.type == BlockSetType.OAK;
			}
			case DoorBlock door -> door.type().canOpenByHand();

			case LayeredCauldronBlock layeredCauldron -> layeredCauldron.precipitationType == Biome.Precipitation.RAIN;

			case DispenserBlock _, LeverBlock _, FenceGateBlock _,
				 BrewingStandBlock _, HopperBlock _, CraftingTableBlock _,
				 ChestBlock _, EnderChestBlock _, AnvilBlock _,
				 EnchantingTableBlock _, CauldronBlock _, FurnaceBlock _,
				 AzaleaBlock _ -> true;
			default -> false;
		};
	}

	private static int getTunerDistance(CompoundTag customData) {
		return customData.getIntOr("tuned_transmission", 0);
	}

	public static class EtherwarpClipContext extends ClipContext {
		public EtherwarpClipContext(Vec3 from, Vec3 to) {
			super(from, to, null, null, CollisionContext.positionContext(0));
		}

		/// Etherwarp treats all blocks as either completely solid or completely empty
		/// In general, a block is completely solid to etherwarp if and only if it has
		/// *any* entity collision, but there are some exceptions
		public VoxelShape getBlockShape(final BlockState blockState, final BlockGetter level, final BlockPos pos) {
			return switch (blockState.getBlock()) {
				case LadderBlock _, AbstractSkullBlock _, CocoaBlock _,
					 FlowerPotBlock _, DiodeBlock _, CandleBlock _ -> Shapes.empty();

				case SignBlock _, ScaffoldingBlock _, SnowLayerBlock _,
					 BasePressurePlateBlock _, AbstractBannerBlock _, TrapDoorBlock _,
					 TripWireHookBlock _, FenceGateBlock _ -> Shapes.block();

				default -> blockState.getCollisionShape(level, pos, this.collisionContext).isEmpty() ? Shapes.empty() : Shapes.block();
			};
		}

		/// Etherwarp always goes through fluids
		public VoxelShape getFluidShape(final FluidState fluidState, final BlockGetter level, final BlockPos pos) {
			return Shapes.empty();
		}
	}
}
