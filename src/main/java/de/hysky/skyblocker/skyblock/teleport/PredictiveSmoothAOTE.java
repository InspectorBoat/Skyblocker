package de.hysky.skyblocker.skyblock.teleport;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.config.configs.UIAndVisualsConfig;
import de.hysky.skyblocker.skyblock.slayers.SlayerManager;
import de.hysky.skyblocker.skyblock.slayers.SlayerType;
import de.hysky.skyblocker.utils.ItemUtils;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.render.LevelRenderExtractionCallback;
import de.hysky.skyblocker.utils.render.primitive.PrimitiveCollector;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class PredictiveSmoothAOTE {
	public static final Minecraft CLIENT = Minecraft.getInstance();

	/**
	 * @param startPos             Refers to player position, not camera position
	 * @param endPos               Refers to player position, not camera position
	 * @param rot                  The rotation with which the teleport starts and ends
	 * @param startTimeMillis
	 * @param expirationTimeMillis Teleport is cancelled if this time is reached without it completing. Equal to timeoutTime + ping + 100 ms buffer. Monotonically increasing.
	 */
	public record InFlightTeleport(
			Vec3 startPos,
			Vec3 endPos,
			Vec2 rot,
			long startTimeMillis,
			long expirationTimeMillis,
			boolean willRotate,
			TeleportUtils.TeleportType teleportType,
			@Nullable Vec3[] steps
	) {}

	@Nullable
	public static Vec3 lastSentPos = null;
	@Nullable
	public static Vec2 lastSentRot = null;

	/// A teleport is in flight when we've sent a UseItem/UseItemOn packet that will trigger a teleport,
	/// but haven't yet recieved a PlayerPositionPacket packet as confirmation
	/// This queue is LIFO
	private static final ArrayDeque<InFlightTeleport> queuedTeleports = new ArrayDeque<>();
	///  This is separate from queuedTeleports because we need to track when a teleport will lock rotation.
	/// queuedTeleports is cleared when there are no teleports in flight, so it is not suitable for this purpose.
	public static long lastTeleportSentTime = 0;

	///  DEBUG FIELDS
	public static ArrayList<Vec3> steps = new ArrayList<>();
	public static Queue<Packet<?>> queue = new LinkedList<>();

	@Init
	public static void init() {
		LevelRenderExtractionCallback.EVENT.register(PredictiveSmoothAOTE::extractRendering);
	}

	/**
	 * When a player receives a teleport packet finish a teleport
	 */
	public static void onTeleport(ClientboundPlayerPositionPacket packet, Vec3 before, Vec3 after) {
		if (queuedTeleports.isEmpty()) return;
		assert CLIENT.player != null;

		InFlightTeleport teleport = queuedTeleports.removeFirst();
	}

	/**
	 * Finds if a player uses a teleport and then saves the start position and time. then works out final position and saves that too
	 */
	public static void predictTeleport(@Nullable HitResult hitResult, float xRotInput, float yRotInput, boolean clickingOnBlock) {
		if (CLIENT.player == null || CLIENT.level == null) return;
		// Predictive algorithm must be selected
		if (!SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE.predictive) return;
		if (CLIENT.options.getCameraType() != CameraType.FIRST_PERSON && !SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE.thirdPerson) return;

		//make sure the player is in an area where teleporting is allowed
		if (!TeleportUtils.canTeleportInLocation()) return;

		if (SlayerManager.getBossFight() instanceof SlayerManager.BossFight slayer) {
			if (slayer.playerBoss && slayer.slayerType == SlayerType.TARANTULA && slayer.slayerTier.ordinal() >= 2) return;
		}

		if (hitResult instanceof BlockHitResult blockHitResult && TeleportUtils.consumesClick(CLIENT.level.getBlockState(blockHitResult.getBlockPos()).getBlock(), Utils.getLocation())) {
			return;
		}

		// Work out the type of teleport
		final ItemStack heldItem = CLIENT.player.getMainHandItem();
		final UIAndVisualsConfig.SmoothAOTE config = SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE;
		final TeleportUtils.TeleportType teleport = TeleportUtils.TeleportType.get(
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
		if (!TeleportUtils.hasEnoughMana(heldItem, teleport)) return;

		// Calculate start position and direction vector
		final InFlightTeleport inFlightTeleport = queuedTeleports.peekLast();

		final Vec2 rot;
		final Vec3 startPos;
		// Server ignores all player sent position packets in between teleporting the player and recieving a confirmation from the client
		// Therefore, any rotations while a teleport is in flight are null and void
		// Except if the teleport is a non-block-clicking transmission. somehow.
		if (inFlightTeleport == null || (teleport instanceof TeleportUtils.TeleportType.Transmission && clickingOnBlock)) {
			rot = new Vec2(xRotInput, yRotInput);
			startPos = lastSentPos != null ? lastSentPos : new Vec3(CLIENT.player.xLast, CLIENT.player.yLast, CLIENT.player.zLast);
		} else {
			rot = inFlightTeleport.rot;
			startPos = inFlightTeleport.endPos;
		}

		final Vec3 startEyePos = startPos.add(0, CLIENT.player.getEyeHeight(CLIENT.player.getPose()), 0);

		if (TeleportUtils.isTargetingNPC()) return;

		BlockPos targetPos = teleport.raycast(CLIENT.level, CLIENT.player.calculateViewVector(rot.x, rot.y), startEyePos);
		if (targetPos == null) {
			return;
		}

		if (teleport instanceof TeleportUtils.TeleportType.Transmission transmission && !transmission.isValid(startPos, startEyePos, targetPos)) {
			return;
		}

		// If initiating a new teleport, we use player rotation and position; otherwise use the end position and rotation of last teleport
		long now = System.currentTimeMillis();
		queuedTeleports.addLast(new InFlightTeleport(
				startPos,
				teleport.toPlayerPos(targetPos),
				rot,
				now,
				Math.max(System.currentTimeMillis() + ping + 100, inFlightTeleport != null ? inFlightTeleport.expirationTimeMillis : 0),
				lastTeleportSentTime + 1000 < now,
				teleport,
				steps.toArray(new Vec3[0])
		));
		lastTeleportSentTime = now;
	}

	public static @Nullable Vec3 getCameraPos(double eyeHeight) {
		if (CLIENT.player == null) return null;
		long currentTime = System.currentTimeMillis();
		// If the first teleport times out, clear the rest because we probably fucked up
		if (queuedTeleports.peekFirst() instanceof InFlightTeleport teleport && currentTime > teleport.expirationTimeMillis) {
			queuedTeleports.clear();
			return null;
		}
		if (queuedTeleports.peekLast() instanceof InFlightTeleport teleport) {
			double elapsed = (double) (currentTime - teleport.startTimeMillis);

			double teleportDuration = 200;
			double progress = Math.clamp(elapsed / teleportDuration, 0, 1);
//			return teleport.endPos.add(0, eyeHeight, 0);
			return teleport.startPos
					.add(teleport.endPos.subtract(teleport.startPos).scale(Utils.easeInOutQuad(progress)))
					.add(0, eyeHeight, 0);
		} else return null;
	}

	public static @Nullable Vec2 getCameraRot() {
		if (queuedTeleports.peekLast() instanceof InFlightTeleport teleport && teleport.willRotate) {
			return teleport.rot;
		}
		return null;
	}

	public static @Nullable Vec3 getVisualPlayerPos() {
		if (CLIENT.player == null) return null;
		long currentTime = System.currentTimeMillis();
		// If the first teleport times out, clear the rest because we probably fucked up
		if (queuedTeleports.peekLast() instanceof InFlightTeleport teleport) {
			double elapsed = (double) (currentTime - teleport.startTimeMillis);

			double teleportDuration = 200;
			double progress = Math.clamp(elapsed / teleportDuration, 0, 1);
//			return teleport.endPos.add(0, eyeHeight, 0);
			return teleport.startPos.add(teleport.endPos.subtract(teleport.startPos).scale(Utils.easeInOutQuad(progress)));
		} else return null;
	}

	private static void extractRendering(PrimitiveCollector collector) {
//		steps.stream().map(BlockPos::containing).distinct().forEach(pos -> collector.submitOutlinedBox(pos, red, 1, true));
//		steps.stream().skip(1).forEach(v -> collector.submitFilledBox(AABB.ofSize(v, 0.2, 0.2, 0.2), red, 1, false));
	}

	public static void updatePing(long ping) {
		PredictiveSmoothAOTE.ping = ping;
	}

	public static long ping = 0;

	private static final float[] red = new float[]{0.7f, 0.2f, 0.2f};
	private static final float[] green = new float[]{0.2f, 0.7f, 0.2f};
	private static final float[] purple = new float[]{0.8f, 0.2f, 0.8f};

}
