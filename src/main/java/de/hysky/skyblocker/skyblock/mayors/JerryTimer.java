package de.hysky.skyblocker.skyblock.mayors;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.events.SkyblockEvents;
import de.hysky.skyblocker.utils.Constants;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.chat.ChatFilterResult;
import de.hysky.skyblocker.utils.chat.ChatMessageListener;
import de.hysky.skyblocker.utils.mayor.MayorUtils;
import de.hysky.skyblocker.utils.scheduler.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

public final class JerryTimer {
	private static boolean isJerryActive = false;

	private JerryTimer() {}

	@Init
	public static void init() {
		//Example message: "§b ☺ §eThere is a §aGreen Jerry§e!"
		//There are various formats, all of which start with the "§b ☺ " prefix and contain the word "<color> Jerry"
		ChatMessageListener.EVENT.register((message, _) -> {
			if (!isJerryActive || !SkyblockerConfigManager.get().helpers.jerry.enableJerryTimer) return ChatFilterResult.PASS;
			//This part of hypixel still uses legacy text formatting, so we can't strip formatting
			String messageAsString = message.getString();

			if (!messageAsString.startsWith("§b ☺ ") || !messageAsString.contains("Jerry")) return ChatFilterResult.PASS;
			HoverEvent hoverEvent = message.getStyle().getHoverEvent();
			if (hoverEvent == null || hoverEvent.action() != HoverEvent.Action.SHOW_TEXT) return ChatFilterResult.PASS;
			LocalPlayer player = Minecraft.getInstance().player;
			Scheduler.INSTANCE.schedule(() -> {
				if (player == null || !Utils.isOnSkyblock()) return;
				player.sendSystemMessage(Constants.PREFIX.get().append(Component.translatable("skyblocker.config.helpers.jerry.sendJerryTimerMessage")).withStyle(ChatFormatting.GREEN));
				player.playSound(SoundEvents.VILLAGER_TRADE, 100f, 1.0f);
			}, 20 * 60 * 6); // 6 minutes

			return ChatFilterResult.PASS;
		});

		SkyblockEvents.MAYOR_CHANGE.register(() -> isJerryActive = MayorUtils.getActivePerks().contains("Jerrypocalypse"));
	}
}
