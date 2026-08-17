package de.hysky.skyblocker.skyblock.teleport;

import java.awt.Color;

import de.hysky.skyblocker.config.configs.UIAndVisualsConfig;
import de.hysky.skyblocker.skyblock.teleport.TeleportUtils.TeleportType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.utils.ItemUtils;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.render.LevelRenderExtractionCallback;
import de.hysky.skyblocker.utils.render.primitive.PrimitiveCollector;

public class TeleportOverlay {
	private static final Minecraft client = Minecraft.getInstance();
	private static float[] colorComponents;

	@Init
	public static void init() {
		configCallback(SkyblockerConfigManager.get().uiAndVisuals.teleportOverlay.teleportOverlayColor); // Initialize colorComponents from the config value
		LevelRenderExtractionCallback.EVENT.register(TeleportOverlay::extractRendering);
	}

	private static void extractRendering(PrimitiveCollector collector) {
		if (!Utils.isOnSkyblock() || !SkyblockerConfigManager.get().uiAndVisuals.teleportOverlay.enableTeleportOverlays || client.player == null || client.level == null) {
			return;
		}

		ItemStack heldItem = client.player.getMainHandItem();

		UIAndVisualsConfig.TeleportOverlay config = SkyblockerConfigManager.get().uiAndVisuals.teleportOverlay;
		TeleportType teleport = TeleportType.get(
				heldItem.getSkyblockId(),
				ItemUtils.getCustomData(heldItem),
				Minecraft.getInstance().options.keyShift.isDown(),
				config.enableWeirdTransmission,
				config.enableInstantTransmission,
				config.enableEtherTransmission,
				config.enableSinrecallTransmission,
				config.enableWitherImpact
		);
		if (teleport == null) return;

		// Compute direction vector and start position
		Vec3 look = client.player.calculateViewVector(client.player.getXRot(), client.player.getYRot());
		Vec3 startPos = client.player.position().add(0, client.player.getEyeHeight(client.player.getPose()), 0);

		BlockPos target = teleport.raycast(client.level, look, startPos);
		if (target == null) return;

		if (teleport instanceof TeleportType.Transmission transmission && !transmission.isValid(client.player.position() , startPos, target)) return;

		collector.submitFilledBox(target, colorComponents, colorComponents[3], false);
	}

	public static void configCallback(Color color) {
		colorComponents = color.getRGBComponents(null);
	}
}
