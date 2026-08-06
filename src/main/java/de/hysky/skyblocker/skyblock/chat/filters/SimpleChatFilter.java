package de.hysky.skyblocker.skyblock.chat.filters;

import de.hysky.skyblocker.utils.chat.ChatFilterResult;
import de.hysky.skyblocker.utils.chat.ChatMessageListener;
import de.hysky.skyblocker.utils.chat.ChatPatternListener;
import org.intellij.lang.annotations.Language;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;

public abstract class SimpleChatFilter extends ChatPatternListener {
	protected SimpleChatFilter(@Language("RegExp") String pattern) {
		super(pattern);
	}

	@Override
	protected final boolean onMatch(Component message, Matcher matcher) {
		return true;
	}
}
