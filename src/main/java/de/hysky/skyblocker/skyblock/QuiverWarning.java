package de.hysky.skyblocker.skyblock;

import de.hysky.skyblocker.events.DungeonEvents;
import de.hysky.skyblocker.utils.chat.ChatFilterResult;
import de.hysky.skyblocker.utils.chat.ChatMessageListener;
import org.jspecify.annotations.Nullable;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.scheduler.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;

public class QuiverWarning {
	private static @Nullable ArrowsLeft queuedWarning = null;

	@Init
	public static void init() {

		ChatMessageListener.EVENT.register(QuiverWarning::onChatMessage);
		DungeonEvents.DUNGEON_ENDED.register(QuiverWarning::update);
		Scheduler.INSTANCE.scheduleCyclic(QuiverWarning::update, 10);
	}

	public static ChatFilterResult onChatMessage(Component message, String messageText) {
		if (SkyblockerConfigManager.get().general.quiverWarning.enableQuiverWarning && messageText.startsWith("QUIVER!")) {
			if (messageText.startsWith("QUIVER! You only have 50")) {
				showWarning(ArrowsLeft.FIFTY_LEFT);
			} else if (messageText.startsWith("QUIVER! You only have 10")) {
				showWarning(ArrowsLeft.TEN_LEFT);
			} else if (messageText.startsWith("QUIVER! You have run out of")) {
				showWarning(ArrowsLeft.EMPTY);
			}
		}
		return ChatFilterResult.PASS;
	}

	private static void showWarning(ArrowsLeft warning) {
		Minecraft.getInstance().gui.hud.resetTitleTimes();
		if (!Utils.isInDungeons()) {
			Minecraft.getInstance().gui.hud.setTitle(Component.translatable(warning.key).withStyle(ChatFormatting.RED));
		} else if (SkyblockerConfigManager.get().general.quiverWarning.enableQuiverWarningInDungeons) {
			Minecraft.getInstance().gui.hud.setTitle(Component.translatable(warning.key).withStyle(ChatFormatting.RED));
			QuiverWarning.queuedWarning = warning;
		}
	}

	public static void update() {
		if (queuedWarning != null && SkyblockerConfigManager.get().general.quiverWarning.enableQuiverWarning && SkyblockerConfigManager.get().general.quiverWarning.enableQuiverWarningAfterDungeon && !Utils.isInDungeons()) {
			Hud hud = Minecraft.getInstance().gui.hud;
			hud.resetTitleTimes();
			hud.setTitle(Component.translatable(queuedWarning.key).withStyle(ChatFormatting.RED));
			queuedWarning = null;
		}
	}

	private enum ArrowsLeft {
		NONE(""),
		FIFTY_LEFT("50Left"),
		TEN_LEFT("10Left"),
		EMPTY("empty");
		private final String key;

		ArrowsLeft(String key) {
			this.key = "skyblocker.quiverWarning." + key;
		}
	}
}
