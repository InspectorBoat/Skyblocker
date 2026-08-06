package de.hysky.skyblocker.skyblock.dwarven;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.chat.ChatFilterResult;
import de.hysky.skyblocker.utils.chat.ChatMessageListener;
import de.hysky.skyblocker.utils.scheduler.Scheduler;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlaciteColdOverlay {
	private static final Identifier POWDER_SNOW_OUTLINE = Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png");
	private static final Pattern COLD_PATTERN = Pattern.compile("Cold: -(\\d+)❄");
	private static int cold = 0;
	private static long resetTime = System.currentTimeMillis();

	@Init
	public static void init() {
		Scheduler.INSTANCE.scheduleCyclic(GlaciteColdOverlay::update, 20);
		ChatMessageListener.EVENT.register(GlaciteColdOverlay::onChatMessage);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, POWDER_SNOW_OUTLINE, (context, _) -> extract(context));
	}

	/**
	 * Reset cold when detecting
	 */
	@SuppressWarnings("SameReturnValue")
	private static ChatFilterResult onChatMessage(Component message, String messageText) {
		if (!Utils.isInDwarvenMines()) {
			return ChatFilterResult.PASS;
		}
		if (messageText.equals("The warmth of the campfire reduced your ❄ Cold to 0!")) {
			cold = 0;
			resetTime = System.currentTimeMillis();
		}

		return ChatFilterResult.PASS;
	}

	private static void update() {
		if (!Utils.isInDwarvenMines() || System.currentTimeMillis() - resetTime < 3000 || !SkyblockerConfigManager.get().mining.glacite.coldOverlay) {
			cold = 0;
			return;
		}
		for (String line : Utils.STRING_SCOREBOARD) {
			Matcher coldMatcher = COLD_PATTERN.matcher(line);
			if (coldMatcher.matches()) {
				String value = coldMatcher.group(1);
				cold = Integer.parseInt(value);
				return;
			}
		}
		cold = 0;
	}

	/**
	 * @see Hud#extractTextureOverlay as this is a carbon copy of it
	 */
	private static void extractOverlay(GuiGraphicsExtractor graphics, Identifier texture, float opacity) {
		int white = ARGB.white(opacity);
		graphics.blit(
			RenderPipelines.GUI_TEXTURED,
			texture,
			0,
			0,
			0.0f,
			0.0f,
			graphics.guiWidth(),
			graphics.guiHeight(),
			graphics.guiWidth(),
			graphics.guiHeight(),
			white
		);
	}

	public static void extract(GuiGraphicsExtractor graphics) {
		if (Utils.isInDwarvenMines() && SkyblockerConfigManager.get().mining.glacite.coldOverlay) {
			extractOverlay(graphics, POWDER_SNOW_OUTLINE, cold / 100f);
		}
	}
}
