package de.hysky.skyblocker.skyblock.teleport;

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
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class PredictiveSmoothAOTE {
	public static final Minecraft CLIENT = Minecraft.getInstance();

	/**
	 * @param startPos             Refers to player position, not camera position
	 * @param endPos               Refers to player position, not camera position
	 * @param rot                  The rotation with which the teleport starts and ends
	 * @param startTimeMillis
	 * @param expirationTimeMillis Teleport is cancelled if this time is reached without it completing. Equal to startTimeMillis + ping + 100 ms buffer. Monotonically increasing.
	 * @param otherEndPos          DEBUG FIELD
	 * @param crouching            DEBUG FIELD
	 * @param isLastRot            DEBUG FIELD
	 */
	public record InFlightTeleport(
			Vec3 startPos,
			Vec3 endPos,
			Vec2 rot,
			long startTimeMillis,
			long expirationTimeMillis,
			@Nullable Vec3 otherEndPos,
			boolean crouching,
			boolean isLastRot,
			boolean willRotate,
			TeleportUtils.TeleportType teleportType
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
	public static ArrayList<BadPrediction> misses = new ArrayList<>();
	public static ArrayList<Vec3> boxes = new ArrayList<>();
	public static ArrayList<Pair<Vec3, Vec3>> actualTeleports = new ArrayList<>();
	public static Queue<Packet<?>> queue = new LinkedList<>();
	public static int logPackets = 0;

	@Init
	public static void init() {
		ClientTickEvents.END_LEVEL_TICK.register(_ -> logPackets -= 1);
		LevelRenderExtractionCallback.EVENT.register(PredictiveSmoothAOTE::extractRendering);
	}

	/**
	 * When a player receives a teleport packet finish a teleport
	 */
	public static void onTeleport(ClientboundPlayerPositionPacket packet, Vec3 before, Vec3 after) {
		if (queuedTeleports.isEmpty()) return;
//		say("TPED TO %s".formatted(CLIENT.player.position()));
//		say(queuedTeleports.stream().map(t -> t.endPos.toString()).collect(Collectors.joining(" | ")));

		InFlightTeleport teleport = queuedTeleports.removeFirst();

		boolean didRot = packet.change().xRot() != 0 || packet.change().yRot() != 0;
//		say("didRot: (%.2f, %.2f) %s (%s)".formatted(packet.change().xRot(), packet.change().yRot(), didRot, didRot == teleport.willRotate ? "correct" : "wrong"));
		if (teleport.endPos.distanceToSqr(after) > 1e-8) {
//			say("Bad prediction: c=%s l=%s d=%s".formatted(
//					teleport.crouching,
//					teleport.isLastRot,
//					teleport.endPos.subtract(after))
//			);
			if (teleport.otherEndPos != null && teleport.otherEndPos.distanceToSqr(after) > 1e-8) {
//				say("Other option: d=%s".formatted(teleport.otherEndPos.subtract(after)));
			} else {
//				say("OTHER OPTION MATCHES");
			}
			final Vec3 correctPos = after;

			final Vec2 wrongRot = teleport.rot;
			final Vec2 wrongRotWrapped = new Vec2(Mth.wrapDegrees(wrongRot.x), wrongRot.y);
			final Vec2 correctRot = new Vec2(CLIENT.player.getXRot(), CLIENT.player.getYRot());
			final Vec2 correctRotWrapped = new Vec2(Mth.wrapDegrees(correctRot.x), correctRot.y);

			final boolean rotMatches = wrongRotWrapped.distanceToSqr(correctRotWrapped) < 0.02 * 0.02;

//			say("Rotation %s".formatted(rotMatches ?
//					"MATCHES=(%.2f %.2f)".formatted(correctRotWrapped.x, correctRotWrapped.y) :
//					"DOESN'T MATCH: p=(%.2f %.2f) : c=(%.2f %.2f)".formatted(teleport.rot.x, teleport.rot.y, correctRotWrapped.x, correctRotWrapped.y)));


			Vec3 lastPos = new Vec3(0, 0, 0);
			Vec2 lastRot = new Vec2(0, 0);
			for (Packet<?> p : queue) {
				Vec3 packetPos;
				Vec2 packetRot;
				if (p instanceof ServerboundMovePlayerPacket p2) {
					packetPos = new Vec3(p2.getX(lastPos.x), p2.getY(lastPos.y), p2.getZ(lastPos.z));
					packetRot = new Vec2(p2.getXRot(lastRot.x), p2.getYRot(lastRot.y));
					lastPos = packetPos;
					lastRot = packetRot;
				} else if (p instanceof ServerboundUseItemPacket p2) {
					packetPos = lastPos;
					packetRot = new Vec2(p2.getXRot(), p2.getYRot());
				} else if (p instanceof ServerboundUseItemOnPacket p2) {
					packetPos = lastPos;
					packetRot = lastRot;
				} else return;
				String toSay = "(%.2f %.2f %.2f) (%.2f %.2f)".formatted(packetPos.x, packetPos.y, packetPos.z, packetRot.x, packetRot.y);
				if (Mth.abs(Mth.degreesDifference(packetRot.x, correctRot.x)) < 0.02 && packetRot.y == correctRot.y) {
					toSay += packetPos.equals(correctPos) ? " t" : " c";
				}
				if (p instanceof ServerboundUseItemPacket) toSay += " u";
				if (p instanceof ServerboundUseItemOnPacket) toSay += " b";
				// Simulate teleport from this pos
				BlockPos potentialTarget = teleport.teleportType.raycast(
						CLIENT.level,
						CLIENT.player.calculateViewVector(correctRot.x, correctRot.y),
						new Vec3(packetPos.x, packetPos.y + 1.27, packetPos.z)
				);
				if (potentialTarget != null) {
					Vec3 potentialPlayerPos = teleport.teleportType.toPlayerPos(potentialTarget);
					toSay += potentialPlayerPos.distanceToSqr(correctPos) < 0.01 * 0.01 ? " !!!!! CORRECT" : " ? : %s".formatted(potentialPlayerPos.subtract(correctPos));
				} else toSay += " ? : -";

//				say(toSay);
			}

			misses.clear();
			misses.add(new BadPrediction(teleport.startPos, teleport.endPos, CLIENT.player.position()));
		} else {
//			say("GOOD PREDICTION");
		}
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
		if (!hasEnoughMana(heldItem, teleport)) return;

		// Calculate start position and direction vector
		final InFlightTeleport inFlightTeleport = queuedTeleports.peekLast();

		final Vec2 rot;
		final Vec3 look;
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
//			say("USING INFLIGHT ROT: (%s) (%s)".formatted(inFlightTeleport.rot.y, yRotInput));
		}
		look = CLIENT.player.calculateViewVector(rot.x, rot.y);

		final Vec3 startEyePos = startPos.add(0, CLIENT.player.getEyeHeight(CLIENT.player.getPose()), 0);

		if (isTargetingNPC()) return;

		BlockPos targetPos = teleport.raycast(CLIENT.level, CLIENT.player.calculateViewVector(rot.x, rot.y), startEyePos);
		if (targetPos == null) return;
		if (teleport instanceof TeleportUtils.TeleportType.Transmission transmission && !transmission.isValid(startPos, startEyePos, targetPos)) return;

		logPackets = 4;

		final boolean crouching = CLIENT.player.hasPose(Pose.CROUCHING);
		final Vec3 otherStartEyePos = startPos.add(0, crouching ? 1.27 : 1.62, 0);
		final BlockPos otherTargetPos = teleport.raycast(CLIENT.level, look, otherStartEyePos);

		// If initiating a new teleport, we use player rotation and position; otherwise use the end position and rotation of last teleport
		long now = System.currentTimeMillis();
		queuedTeleports.addLast(new InFlightTeleport(
				startPos,
				teleport.toPlayerPos(targetPos),
				rot,
				now,
				Math.max(System.currentTimeMillis() + ping + 100, inFlightTeleport != null ? inFlightTeleport.expirationTimeMillis : 0),
				otherTargetPos != null ? teleport.toPlayerPos(otherTargetPos) : null,
				crouching,
				clickingOnBlock,
				lastTeleportSentTime + 1000 < now,
				teleport
		));
		System.nanoTime();
		lastTeleportSentTime = now;
//		say("TPING FROM %s to %s".formatted(inFlightTeleport == null ? startPos : inFlightTeleport.endPos, teleport.toPlayerPos(targetPos)));
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
	 * @return if an NPC is targeted
	 */
	private static boolean isTargetingNPC() {
		Entity entity = CLIENT.crosshairPickEntity;
		if (entity == null) return false;

		// Check for armor stand with "CLICK" nametag, signifying an NPC
		return MobGlow.getArmorStands(entity)
				.stream()
				.anyMatch(armorStand -> armorStand.getName().getString().equals("CLICK"));
	}

	/**
	 * works out where they player should be based on how far though the predicted teleport time.
	 *
	 * @return the camera position for the interpolated pos
	 */
	public static @Nullable Vec3 getCameraPos(Vec3 originalPos, double eyeHeight) {
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
					.add(teleport.endPos.subtract(teleport.startPos).scale(easeInOutQuad(progress)))
					.add(0, eyeHeight, 0);

		} else return null;
	}

	public static @Nullable Vec2 getCameraRot() {
		if (queuedTeleports.peekLast() instanceof InFlightTeleport teleport && teleport.willRotate) {
			return teleport.rot;
		}
		return null;
	}

	/**
	 * @return Interpolated player position
	 */
	@Nullable
	public static Vec3 getVisualPlayerPos() {
		if (CLIENT.player == null) return null;
		long currentTime = System.currentTimeMillis();
		// If the first teleport times out, clear the rest because we probably fucked up
		if (queuedTeleports.peekLast() instanceof InFlightTeleport teleport) {
			double elapsed = (double) (currentTime - teleport.startTimeMillis);

			double teleportDuration = 200;
			double progress = Math.clamp(elapsed / teleportDuration, 0, 1);
//			return teleport.endPos.add(0, eyeHeight, 0);
			return teleport.startPos.add(teleport.endPos.subtract(teleport.startPos).scale(easeInOutQuad(progress)));
		} else return null;
	}

	public static double easeInOutQuad(double t) {
		return t < 0.5 ?
				2.0 * t * t :
				1.0 - 2.0 * (1.0 - t) * (1.0 - t);
	}

	public static void say(String string) {
		CLIENT.player.sendSystemMessage(Component.nullToEmpty(string));
	}

	public record BadPrediction(Vec3 start, Vec3 end, Vec3 actual) {}

	private static final float[] red = new float[]{0.7f, 0.2f, 0.2f};
	private static final float[] green = new float[]{0.2f, 0.7f, 0.2f};
	private static final float[] purple = new float[]{0.8f, 0.2f, 0.8f};

	private static boolean last = false;
	private static void extractRendering(PrimitiveCollector collector) {
		boxes.forEach(box -> {
			collector.submitFilledBox(AABB.ofSize(box, 0.15, 0.15, 0.15), red, 0.4f, true);
			if (CLIENT.hasAltDown() && !last) say(box.toString());
		});
		if (CLIENT.hasAltDown() && !last && !boxes.isEmpty()) say(String.valueOf(boxes.get(0).y));
		last = CLIENT.hasAltDown();
		if (!queuedTeleports.isEmpty()) {
			collector.submitFilledBox(AABB.ofSize(CLIENT.player.position(), 0.6, 1.5, 0.6), purple, 0.5f, false);

		}
		misses.forEach(miss -> {
			if (true) return;
			collector.submitFilledBox(AABB.ofSize(miss.start.add(0, 1.5 / 2.0, 0), 0.6, 1.5, 0.6), purple, 0.3f, false);
			collector.submitFilledBox(AABB.ofSize(miss.end.add(0, 1.5 / 2.0, 0), 0.6, 1.5, 0.6), red, 0.3f, false);
			collector.submitFilledBox(AABB.ofSize(miss.actual.add(0, 1.5 / 2.0, 0), 0.6, 1.5, 0.6), green, 0.3f, false);
			collector.submitLinesFromPoints(new Vec3[]{miss.start.add(0, 1.27, 0), miss.end.add(0, 1.27, 0)}, red, 1, 1, false);
			collector.submitLinesFromPoints(new Vec3[]{miss.start.add(0, 1.27, 0), miss.actual.add(0, 1.27, 0)}, green, 1, 1, false);
		});
	}

	public static void updatePing(long ping) {
		PredictiveSmoothAOTE.ping = ping;
	}

	public static long ping = 0;
}
