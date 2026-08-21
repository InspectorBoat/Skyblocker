package de.hysky.skyblocker.skyblock.teleport;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.config.configs.UIAndVisualsConfig;
import de.hysky.skyblocker.utils.ItemUtils;
import de.hysky.skyblocker.utils.render.LevelRenderExtractionCallback;
import de.hysky.skyblocker.utils.render.primitive.PrimitiveCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.stream.Collectors;

import static de.hysky.skyblocker.utils.Utils.sendMessageToBypassEvents;
import static de.hysky.skyblocker.utils.Utils.vec2toString;

public class TeleportLogger {
	public static final Minecraft CLIENT = Minecraft.getInstance();

	public record RightClick(
			Vec3 startPos,
			Vec2 rot,
			Vec3[] steps,
			BlockPos finalPos,
			long timeoutTime
	) {}

	@Nullable
	public static Vec3 lastSentPos = null;
	@Nullable
	public static Vec2 lastSentRot = null;

	/// A teleport is in flight when we've sent a UseItem/UseItemOn packet that will trigger a teleport,
	/// but haven't yet recieved a PlayerPositionPacket packet as confirmation
	/// This queue is LIFO
	private static final ArrayDeque<RightClick> queuedTeleports = new ArrayDeque<>();
	///  This is separate from queuedTeleports because we need to track when a teleport will lock rotation.
	/// queuedTeleports is cleared when there are no teleports in flight, so it is not suitable for this purpose.
	public static long conservativeLastTeleportSentTime = 0;

	///  DEBUG FIELDS
	public static Queue<Packet<?>> queue = new LinkedList<>();

	@Init
	public static void init() {
		LevelRenderExtractionCallback.EVENT.register(TeleportLogger::extractRendering);
		LevelRenderExtractionCallback.EVENT.register(_ -> timeoutOldTeleports());
	}

	/**
	 * When a player receives a teleport packet finish a teleport
	 */

	public static void onTeleport(ClientboundPlayerPositionPacket packet, Vec3 before, Vec3 after) {
		if (queuedTeleports.isEmpty()) return;
		assert CLIENT.player != null;

		RightClick teleport = queuedTeleports.removeFirst();
		if (Mth.degreesDifferenceAbs(CLIENT.player.getYRot(), teleport.rot.y) > 0.1 || Mth.degreesDifferenceAbs(CLIENT.player.getXRot(), teleport.rot.x) > 0.1) {
			say("BAD TELEPORT: %s %s expected, got %s %s".formatted(teleport.rot.x, teleport.rot.y, CLIENT.player.getXRot(), CLIENT.player.getYRot()));
			return;
		}

		if (BlockPos.containing(after.add(0, 1.5, 0)).equals(teleport.finalPos)) {
			MoveUtils.teleportAtEnd += 1;
		}

		say("TELEPORT");
		logAote(teleport.steps, after, teleport.rot);
	}

	public static void onClick(float xRotInput, float yRotInput) {
		if (CLIENT.player == null || CLIENT.level == null) return;
		// Predictive algorithm must be selected

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
		conservativeLastTeleportSentTime = System.currentTimeMillis();
		if (teleport instanceof TeleportUtils.TeleportType.Etherwarp) return;
		// Calculate start position and direction vector

		final Vec2 rot = new Vec2(xRotInput, yRotInput);
		final Vec3 startPos = lastSentPos != null ? lastSentPos : new Vec3(CLIENT.player.xLast, CLIENT.player.yLast, CLIENT.player.zLast);

		final Vec3 startEyePos = startPos.add(0, CLIENT.player.getEyeHeight(CLIENT.player.getPose()), 0);

		final Vec3 direction = CLIENT.player.calculateViewVector(rot.x, rot.y);

		final BlockPos unobstructedFinalPos = BlockPos.containing(
				startPos.add(direction.scale(teleport.distance()))
		);

		// If initiating a new teleport, we use player rotation and position; otherwise use the end position and rotation of last teleport
		queuedTeleports.addLast(new RightClick(
				startEyePos,
				rot,
				PredictiveSmoothAOTE.steps.toArray(new Vec3[0]),
				unobstructedFinalPos,
				System.currentTimeMillis() + PredictiveSmoothAOTE.ping + 400
		));
	}

	public static void say(String string) {
		sendMessageToBypassEvents(Component.nullToEmpty(string));
	}

	private static boolean last = false;
	public static int rotLock = -1;

	private static void extractRendering(PrimitiveCollector collector) {
		if (CLIENT.hasAltDown() && !last) {
			rotLock = rotLock == -1 ? 180 : -1;
		}

		last = CLIENT.hasAltDown();

		PredictiveSmoothAOTE.steps.stream().map(BlockPos::containing).distinct().forEach(pos -> collector.submitOutlinedBox(pos, red, 1, true));
		PredictiveSmoothAOTE.steps.stream().skip(1).forEach(v -> collector.submitFilledBox(AABB.ofSize(v, 0.2, 0.2, 0.2), red, 1, false));
	}

	private static void timeoutOldTeleports() {
		for (Iterator<RightClick> iterator = queuedTeleports.iterator(); iterator.hasNext(); ) {
			RightClick click = iterator.next();
			if (click.timeoutTime < System.currentTimeMillis()) {
				logAote(click.steps, null, click.rot);
				say("TIMEOUT");
				iterator.remove();
			}
		}
	}

	public static void logAote(Vec3[] steps, @Nullable Vec3 dest, Vec2 rot) {
		String toSay = "rot: %s dest: %s steps: %s".formatted(
				vec2toString(rot),
				dest,
				Arrays.stream(steps).map(Vec3::toString).collect(Collectors.joining(" ")));
		appendToFile(toSay, "/home/inspectorboat/scratch/aotelogs");
	}

	public static void appendToFile(String text, String filePath) {
		try {
			Files.writeString(
					Path.of(filePath),
					text + System.lineSeparator(),
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND
			);
		} catch (IOException _) {}
	}

	private static final float[] red = new float[]{0.7f, 0.2f, 0.2f};
	private static final float[] green = new float[]{0.2f, 0.7f, 0.2f};
	private static final float[] purple = new float[]{0.8f, 0.2f, 0.8f};

}
