package de.hysky.skyblocker.skyblock.dungeon;

import de.hysky.skyblocker.SkyblockerMod;
import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.events.ServerTickCallback;
import de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonManager;
import de.hysky.skyblocker.utils.chat.ChatFilterResult;
import de.hysky.skyblocker.utils.chat.ChatMessageListener;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;

public class FireFreezeStaffTimer {
	private static final Identifier FIRE_FREEZE_STAFF_TIMER = SkyblockerMod.id("fire_freeze_staff_timer");
	private static long fireFreezeTimer;
	private static boolean timerActive = false;

	@Init
	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, FIRE_FREEZE_STAFF_TIMER, FireFreezeStaffTimer::extractRenderState);
		ChatMessageListener.EVENT.register(FireFreezeStaffTimer::onChatMessage);
		ClientPlayConnectionEvents.JOIN.register((_, _, _) -> FireFreezeStaffTimer.reset());
		ServerTickCallback.EVENT.register(FireFreezeStaffTimer::onServerTick);
	}

	private static void onServerTick() {
		if (timerActive) fireFreezeTimer -= 1;
	}

	private static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();

		if (client.gui.screen() != null) return;

		if (SkyblockerConfigManager.get().dungeons.theProfessor.fireFreezeStaffTimer && fireFreezeTimer != 0) {
			if (fireFreezeTimer <= -100) {
				reset();
				return;
			}

			Component message;
			if (fireFreezeTimer > 0) {
				message = Component.literal("in ").append(Component.literal(String.format("%.2f", (float) (fireFreezeTimer) / 20) + "s").withStyle(ChatFormatting.YELLOW));
			} else {
				message = Component.literal("NOW").withStyle(ChatFormatting.RED);
			}

			Font renderer = client.font;
			int width = client.getWindow().getGuiScaledWidth() / 2;
			int height = client.getWindow().getGuiScaledHeight() / 2;

			graphics.centeredText(renderer, Component.literal("Fire Freeze ").append(message), width, height, CommonColors.WHITE);
		}
	}

	private static void reset() {
		fireFreezeTimer = 0;
		timerActive = false;
	}

	private static ChatFilterResult onChatMessage(Component message, String messageText) {
		if (DungeonManager.getBoss() == DungeonBoss.PROFESSOR && SkyblockerConfigManager.get().dungeons.theProfessor.fireFreezeStaffTimer && messageText.equals("[BOSS] The Professor: Oh? You found my Guardians' one weakness?")) {
			fireFreezeTimer = 114L;
			timerActive = true;
		}

		return ChatFilterResult.PASS;
	}
}
