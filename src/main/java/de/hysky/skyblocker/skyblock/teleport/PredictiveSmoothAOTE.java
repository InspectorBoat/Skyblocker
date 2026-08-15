package de.hysky.skyblocker.skyblock.teleport;

import de.hysky.skyblocker.SkyblockerMod;
import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.config.configs.UIAndVisualsConfig;
import de.hysky.skyblocker.skyblock.StatusBarTracker;
import de.hysky.skyblocker.skyblock.entity.MobGlow;
import de.hysky.skyblocker.skyblock.slayers.SlayerManager;
import de.hysky.skyblocker.skyblock.slayers.SlayerType;
import de.hysky.skyblocker.utils.ItemAbility;
import de.hysky.skyblocker.utils.ItemUtils;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.render.LevelRenderExtractionCallback;
import de.hysky.skyblocker.utils.render.primitive.PrimitiveCollector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class PredictiveSmoothAOTE {
	public record InFlightTeleport(Vec3 startPos, Vec3 endPos, Vec2 endRot, long startTimeNanos) {}

	public static final Identifier SMOOTH_AOTE_BEFORE_PHASE = SkyblockerMod.id("smooth_aote");
	public static final Minecraft CLIENT = Minecraft.getInstance();

	private static final long TELEPORT_TIMEOUT = 2500; //2.5 seconds
	public static int logPackets = 0;

	public static Vec3 lastSentPos = null;

	/// Refers to *player position*, not camera position!
	@Nullable private static Vec3 teleportStartPos;
	@Nullable private static Vec3 teleportEndPos;
	@Nullable private static Vec2 teleportEndRot;
	private static long teleportStartTime;

	/// A teleport is in flight when we've sent a UseItem/UseItemOn packet that will trigger a teleport but haven't yet recieved a PlayerPositionPacket packet as confirmation
	private static int teleportsInFlight;

	public static ArrayList<BadPrediction> misses = new ArrayList<>();
	public static Queue<Packet<?>> queue = new LinkedList<>();

	@Init
	public static void init() {
		ClientTickEvents.END_LEVEL_TICK.register(player -> {logPackets -= 1;});
		LevelRenderExtractionCallback.EVENT.register(PredictiveSmoothAOTE::extractRendering);
	}

	/**
	 * When a player receives a teleport packet finish a teleport
	 */
	public static void onTeleport() {
		//the player has been teleported so 1 less teleport ahead
		if (teleportsInFlight == 0) {
			say("RECIEVED TELEPORT BUT NO PENDING");
		}
		teleportsInFlight = Math.max(0, teleportsInFlight - 1);
		if (teleportEndPos != null) {
			if (teleportEndPos.distanceToSqr(CLIENT.player.position()) > 1e-8) {
				say("BAD PREDICTION: delta=%s".formatted(teleportEndPos.subtract(CLIENT.player.position())));
				misses.clear();
				misses.add(new BadPrediction(teleportStartPos, teleportEndPos, CLIENT.player.position()));
			}
		}
//		teleportStartPos = teleportEndPos = null;
	}

	private static boolean isShovel(ItemStack itemStack) {
		return itemStack.is(Items.WOODEN_SHOVEL) ||
				itemStack.is(Items.STONE_SHOVEL) ||
				itemStack.is(Items.IRON_SHOVEL) ||
				itemStack.is(Items.GOLDEN_SHOVEL) ||
				itemStack.is(Items.DIAMOND_SHOVEL);
	}

	/**
	 * Checks if the block is one that the shovel can turn into a path (e.g., grass or dirt)
	 *
	 * @param block block to check
	 * @return if block can be turned into path
	 */
	private static boolean canShovelActOnBlock(Block block) {
		return block == Blocks.GRASS_BLOCK ||
				block == Blocks.DIRT ||
				block == Blocks.COARSE_DIRT ||
				block == Blocks.PODZOL;
	}

	/**
	 * Finds if a player uses a teleport and then saves the start position and time. then works out final position and saves that too
	 *
	 * @param hand what the player is holding
	 */

	public static void calculateTeleportUse(InteractionHand hand, @Nullable HitResult hitResult, float xRot, float yRot) {
		if (CLIENT.player == null || CLIENT.level == null) return;

		// Predictive algorithm must be selected
		if (!SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE.predictive) return;
		if (CLIENT.options.getCameraType() != CameraType.FIRST_PERSON && !SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE.thirdPerson) return;

		//make sure the player is in an area where teleporting is allowed
		if (!TeleportUtils.canTeleportInLocation()) return;

		if (SlayerManager.isFightingOwnedSlayer() && SlayerManager.isFightingSlayerType(SlayerType.TARANTULA) && SlayerManager.getBossFight().slayerTier.ordinal() >= 2) return;

		if (hitResult instanceof BlockHitResult blockHitResult && TeleportUtils.consumesClick(CLIENT.level.getBlockState(blockHitResult.getBlockPos()).getBlock(), Utils.getLocation())) {
			return;
		}

		// Work out the type of teleport
		ItemStack heldItem = CLIENT.player.getMainHandItem();
		UIAndVisualsConfig.SmoothAOTE config = SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE;
		TeleportUtils.TeleportType teleport = TeleportUtils.TeleportType.get(
				heldItem.getSkyblockId(),
				ItemUtils.getCustomData(heldItem),
				CLIENT.player.getLastSentInput().shift(),
				config.enableWeirdTransmission,
				config.enableInstantTransmission,
				config.enableEtherTransmission,
				config.enableSinrecallTransmission,
				config.enableWitherImpact
		);
		if (teleport == null) return;

		// Make sure the player has enough mana to do the teleport
		if (!hasEnoughMana(heldItem, teleport)) return;

		// Calculate start position and direction vector
		/// There is a strange interaction here:
		/// When you right click in the air with a shovel, normally the packet order looks like this:
		/// UseItem (main hand) -> MovePlayer
		/// (Irrelevant packets in between omitted for clarity)
		/// MovePlayer (and its subclasses) are responsible for relaying player rotation to the server. Since UseItem
		/// gets sent *before* MovePlayer, does this mean the server uses the *last* tick's rotation for the AOTV teleport?
		/// No. Actually, UseItem contains fields yRot & xRot, which sends the player's most recent rotation.
		/// HOWEVER, what happens when you right click on a *block*? The packet order looks like this:
		/// UseItemOn (main hand) -> UseItem (main hand) -> MovePlayer
		/// Now, it appears that Hypixel *also* triggers the teleport when receiving the UseItemOn packet.
		/// (Apparently, they then ignore the subsequent UseItem packet.)
		/// But UseItemOn *doesn't* contain the client's rotation! So the server will use an outdated rotation.
		///
		/// In summary, when you right click in the air, the server uses your up-to-date rotation to perform the teleport.
		/// But if you right click on a block, the server uses the last tick's rotation.
		final Vec3 look;
		final Vec3 startPos;
		if (teleportsInFlight == 0) {
			look = CLIENT.player.calculateViewVector(xRot, yRot);
			startPos = lastSentPos != null ? lastSentPos : new Vec3(CLIENT.player.xLast, CLIENT.player.yLast, CLIENT.player.zLast);
		} else {
			look = CLIENT.player.calculateViewVector(teleportEndRot.x, teleportEndRot.y);
			startPos = teleportEndPos;
		}

		final Vec3 startEyePos = startPos.add(0, CLIENT.player.getEyeHeight(CLIENT.player.getPose()), 0);

		if (isTargetingNPC(CLIENT.player, 4, startEyePos, look)) return;

		BlockPos targetPos = teleport.raycast(CLIENT.level, look, startEyePos);
		if (targetPos == null) return;

		logPackets = 4;

		teleportStartTime = System.currentTimeMillis();
		teleportStartPos = CLIENT.player.position();
		teleportEndPos = teleport.toPlayerPos(targetPos);
		/// If initiating a new teleport, we use current rotation; otherwise use the stored one as we can't rotation mid flight
		if (teleportsInFlight == 0) teleportEndRot = new Vec2(xRot, yRot);
		teleportsInFlight += 1;
	}

	private static boolean hasEnoughMana(ItemStack heldItem, TeleportUtils.TeleportType teleport) {
		List<ItemAbility> abilities = heldItem.skyblocker$getAbilities();
		if (!abilities.isEmpty() && abilities.getFirst().manaCost().isPresent()) {
			int manaCost = abilities.getFirst().manaCost().getAsInt();
			int predictedMana = StatusBarTracker.getMana().value() + StatusBarTracker.getMana().overflow();
			if (predictedMana < manaCost) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the player is targeting an entity and then checks if it has a CLICK tag suggesting it has an interaction that will block the teleport
	 *
	 * @param player      player
	 * @param maxDistance max distance this is needed
	 * @param startPos    player starting location
	 * @param look        players looking direction
	 * @return if an NPC is targeted
	 */
	private static boolean isTargetingNPC(Player player, double maxDistance, Vec3 startPos, Vec3 look) {
		Entity entity = CLIENT.crosshairPickEntity;
		if (entity == null) return false;

		// Check for armor stand with "CLICK" nametag, signifying an NPC
		return MobGlow.getArmorStands(entity)
				.stream()
				.anyMatch(armorStand -> armorStand.getName().getString().equals("CLICK"));
	}

	/**
	 * Custom raycast for teleporting checks for blocks for each 1 block forward in teleport. (very similar to Hypixel's method)
	 * TODO: Fix this
	 *
	 * @param distance maximum distance
	 * @return teleport offset vector
	 */
	protected static @Nullable Vec3 raycastTransmission(int distance, Vec3 direction, Vec3 startPos) {
		if (CLIENT.level == null || direction == null || startPos == null) {
			return null;
		}

		//based on which way the ray is going get the needed vector for checking diagonals
		BlockPos xDiagonalOffset = direction.x() > 0 ? new BlockPos(1, 0, 0) : new BlockPos(-1, 0, 0);
		BlockPos zDiagonalOffset = direction.z() > 0 ? new BlockPos(0, 0, 1) : new BlockPos(0, 0, -1);


		//initialise the closest floor value outside of possible values
		int closeFloorY = Integer.MAX_VALUE;

		//loop though each block of a teleport checking each block if there are blocks in the way
		for (double offset = 0; offset <= distance; offset++) {
			Vec3 pos = startPos.add(direction.scale(offset));

			BlockPos checkPos = BlockPos.containing(pos);

			//check if there is a block at the check location
			if (!canTeleportThroughWithTransmission(checkPos)) {
				if (offset == 0) {
					// no teleport can happen
					return null;
				}
				return direction.scale(offset - 1);
			}

			//check if the block at head height is free
			if (!canTeleportThroughWithTransmission(checkPos.above())) {
				if (offset == 0) {
					//cancel the check if starting height is too low
					Vec3 justAhead = startPos.add(direction.scale(0.2));
					if ((justAhead.y() - Math.floor(justAhead.y())) <= 0.495) {
						continue;
					}
					// no teleport can happen
					return null;
				}
				return direction.scale(offset - 1);
			}

			//check for diagonal walls for some reason this check is directional, and you can go through from some directions. This seems to emulate this as best as possible
			if (offset != 0 && direction.x() < 0 && (isBlockFloor(checkPos.east())) && (isBlockFloor(BlockPos.containing(pos.subtract(direction)).offset(zDiagonalOffset)))) {
				return direction.scale(offset - 1);
			}
			if (offset != 0 && direction.z() < 0 && direction.x() < 0 && (isBlockFloor(checkPos.south())) && (isBlockFloor(BlockPos.containing(pos.subtract(direction)).offset(xDiagonalOffset)))) {
				return direction.scale(offset - 1);
			}

			//if the player is close to the floor (including diagonally) save Y and when player goes bellow this y finish teleport
			if ((isBlockFloor(checkPos.below()) || (isBlockFloor(checkPos.below().offset(xDiagonalOffset)) && isBlockFloor(checkPos.below().offset(zDiagonalOffset)))) && (pos.y() - Math.floor(pos.y())) < 0.31) {
				closeFloorY = checkPos.getY() - 1;
			}

			//if the checking Y is same as closeY finish
			if (closeFloorY == checkPos.getY()) {
				return direction.scale(offset - 1);
			}
		}

		//return full distance if no collision found
		//Hypixel has started moving this a block down so do that if it's not solid
		if (!isBlockFloor(BlockPos.containing(startPos.add(direction.scale(distance)).subtract(0, 1, 0)))) {
			return direction.scale(distance).subtract(0, 1, 0);
		}

		return direction.scale(distance);
	}

	/**
	 * Checks to see if a block is in the allowed list to teleport though.
	 * Air, non-collidable blocks, carpets, pots, 3 or less snow layers.
	 * This is probably different from etherwarp.
	 *
	 * @param blockPos block location
	 * @return if a block location can be teleported though
	 */
	private static Boolean canTeleportThroughWithTransmission(BlockPos blockPos) {
		if (CLIENT.level == null) {
			return false;
		}

		BlockState blockState = CLIENT.level.getBlockState(blockPos);
		if (blockState.isAir()) {
			return true;
		}
		Block block = blockState.getBlock();
		VoxelShape shape = blockState.getCollisionShape(CLIENT.level, blockPos);

		return shape.isEmpty() || block instanceof CarpetBlock || block instanceof FlowerPotBlock || block instanceof WebBlock || (block.equals(Blocks.SNOW) && blockState.getValue(BlockStateProperties.LAYERS) <= 3);
	}

	/**
	 * Checks to see if a block goes to the top if so class it as a floor
	 *
	 * @param blockPos block location
	 * @return if it's a floor block
	 */
	private static Boolean isBlockFloor(BlockPos blockPos) {
		if (CLIENT.level == null) {
			return false;
		}

		BlockState blockState = CLIENT.level.getBlockState(blockPos);
		VoxelShape shape = blockState.getCollisionShape(CLIENT.level, blockPos);
		if (shape.isEmpty()) {
			return false;
		}
		return shape.bounds().maxY >= 1 || blockState.getBlock() == Blocks.MUD; //every thing 1 or above counts but there is some added extras like mud
	}

	/**
	 * works out where they player should be based on how far though the predicted teleport time.
	 *
	 * @return the camera position for the interpolated pos
	 */
	@Nullable
	public static Vec3 getInterpolatedPos(Vec3 originalPos, double eyeHeight) {
		if (CLIENT.player == null || teleportStartPos == null || teleportEndPos == null) {
			return null;
		}
		long currentTime = System.currentTimeMillis();
		long gap = currentTime - teleportStartTime;

		double teleportDuration = 100;
		double percentage = Math.clamp((double) (gap) / teleportDuration, 0, 1);

		//if the animation is done and the player has finished the teleport server side finish the teleport
		if (teleportsInFlight == 0) {
			if (gap >= teleportDuration) {
				//reset when player has reached the end of the teleports
				teleportStartPos = null;
				teleportEndPos = null;
				return null;
			} else {
				teleportEndPos = originalPos;
			}
		}

		return teleportEndPos.add(0, eyeHeight, 0);
//		return teleportStartPos.add(teleportEndPos.subtract(teleportStartPos).scale(easeInOutQuad(percentage))).add(0, eyeHeight, 0);
	}

	public static double easeInOutQuad(double t) {
		if (t < 0.5) {
			return 2.0 * t * t;
		} else {
			return 1.0 - 2.0 * (1.0 - t) * (1.0 - t);
		}
	}

	/**
	 * Get the difference between the camara and the actual player position. Then adds this to interpolated camara position
	 *
	 * @return Interpolated player position
	 */
	@Nullable
	public static Vec3 getInterpolatedPlayerPos() {
		return null;
	}

	public static void say(String string) {
		CLIENT.player.sendSystemMessage(Component.nullToEmpty(string));
	}

	private record BadPrediction(Vec3 start, Vec3 end, Vec3 actual) {}

	private static float[] red = new float[]{0.7f, 0.2f, 0.2f};
	private static float[] green = new float[]{0.2f, 0.7f, 0.2f};
	private static float[] purple = new float[]{0.8f, 0.2f, 0.8f};

	private static void extractRendering(PrimitiveCollector collector) {
		misses.forEach(miss -> {
			collector.submitFilledBox(AABB.ofSize(miss.start.add(0, 1.5 / 2.0, 0), 0.6, 1.5, 0.6), purple, 0.3f, false);
			collector.submitFilledBox(AABB.ofSize(miss.end.add(0, 1.5 / 2.0, 0), 0.6, 1.5, 0.6), red, 0.3f, false);
			collector.submitFilledBox(AABB.ofSize(miss.actual.add(0, 1.5 / 2.0, 0), 0.6, 1.5, 0.6), green, 0.3f, false);
			collector.submitLinesFromPoints(new Vec3[]{miss.start.add(0, 1.27, 0), miss.end.add(0, 1.27, 0)}, red, 1, 1, false);
			collector.submitLinesFromPoints(new Vec3[]{miss.start.add(0, 1.27, 0), miss.actual.add(0, 1.27, 0)}, green, 1, 1, false);
		});

	}
}
