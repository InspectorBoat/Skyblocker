package de.hysky.skyblocker.skyblock.chat.filters;

import de.hysky.skyblocker.skyblock.teleport.MoveUtils;
import de.hysky.skyblocker.utils.chat.ChatFilterResult;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;

public class WarpFilter extends SimpleChatFilter {
	public WarpFilter() {
		super("^Warping\\.\\.\\.$");
	}

	@Override
	public ChatFilterResult state() {
		return ChatFilterResult.TOAST;
	}

	@Override
	protected boolean onMatch(Component message, Matcher matcher) {
		MoveUtils.sequence.movedToSpawn();
		return true;
	}

}
