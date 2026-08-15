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
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CandleBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
			} else if (DungeonManager.getCurrentRoom().getName().equals("teleport-pad-room")) { // Teleport maze
				return false;
			} else if (DungeonManager.getCurrentRoom().getName().startsWith("trap")) { // Trap room
				return false;
			}
		}
		return true;
	}

	@Nullable
	public sealed interface TeleportType {
		/**
		 * @return The calculated absolute target location.
		 */
		public @Nullable BlockPos raycast(Level level, Vec3 direction, Vec3 startPos);

		/**
		 * @param target The targeted block
		 * @return the exact ending player position
		 */
		public Vec3 toPlayerPos(BlockPos target);

		public record Transmission(int distance) implements TeleportType {
			// TODO
			@Override
			public BlockPos raycast(Level level, Vec3 direction, Vec3 startPos) {
				return null;
			}

			// TODO
			@Override
			public Vec3 toPlayerPos(BlockPos target) {
				return null;
			}
		}

		public record Etherwarp(int distance) implements TeleportType {
			@Override
			public BlockPos raycast(Level level, Vec3 direction, Vec3 startPos) {
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

		public static @Nullable TeleportType get(String skyblockItemId, CompoundTag customData, boolean isSneaking, boolean weird, boolean instant, boolean ether, boolean sinrecall, boolean wither) {
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

		@Override
		/// Etherwarp treats all blocks as either completely solid or completely empty
		/// In general, a block is completely solid to etherwarp if and only if it has
		/// *any* entity collision, but there are some exceptions
		/// Thanks Nofrills guy
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
