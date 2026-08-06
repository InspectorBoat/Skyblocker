package de.hysky.skyblocker.skyblock.dungeon.puzzle;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonManager;
import de.hysky.skyblocker.skyblock.dungeon.secrets.Room;
import de.hysky.skyblocker.utils.chat.ChatFilterResult;
import de.hysky.skyblocker.utils.chat.ChatMessageListener;
import de.hysky.skyblocker.utils.render.RenderHelper;
import de.hysky.skyblocker.utils.render.primitive.PrimitiveCollector;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

public class ThreeWeirdos extends DungeonPuzzle {
	@SuppressWarnings("unused")
	private static final ThreeWeirdos INSTANCE = new ThreeWeirdos();
	/// Matches only the weirdo with the correct chest
	protected static final Pattern PATTERN = Pattern.compile("^\\[NPC] ([A-Z][a-z]+): (?:The reward is(?: not in my chest!|n't in any of our chests\\.)|My chest (?:doesn't have the reward\\. We are all telling the truth\\.|has the reward and I'm telling the truth!)|At least one of them is lying, and the reward is not in [A-Z][a-z]+'s chest!|Both of them are telling the truth\\. Also, [A-Z][a-z]+ has the reward in their chest!)$");
	private static final float[] GREEN_COLOR_COMPONENTS = new float[]{0, 1, 0};
	private static @Nullable BlockPos pos;
	static @Nullable AABB boundingBox;

	private ThreeWeirdos() {
		super("three-weirdos", "three-chests");
		ChatMessageListener.EVENT.register(this::onChatMessage);
		UseBlockCallback.EVENT.register((_, _, _, blockHitResult) -> {
			if (blockHitResult.getType() == HitResult.Type.BLOCK && blockHitResult.getBlockPos().equals(pos)) pos = null;
			return InteractionResult.PASS;
		});
	}

	@Init
	public static void init() {
	}

	/**
	 * We only need to matche the messag of the weirdo with the correct chest, other weirdos are unnecessary
	 */
	@SuppressWarnings("SameReturnValue")
	private ChatFilterResult onChatMessage(Component message, String messageText) {
		ClientLevel world = Minecraft.getInstance().level;
		if (!shouldSolve() || !SkyblockerConfigManager.get().dungeons.puzzleSolvers.solveThreeWeirdos || world == null || !DungeonManager.isCurrentRoomMatched()) return ChatFilterResult.PASS;

		Matcher matcher = PATTERN.matcher(messageText);
		if (!matcher.matches()) return ChatFilterResult.PASS;
		String name = matcher.group(1);
		Room room = DungeonManager.getCurrentRoom();
		if (room == null || !room.isMatched()) return ChatFilterResult.PASS;

		checkForNPC(world, room, new BlockPos(13, 69, 24), name);
		checkForNPC(world, room, new BlockPos(15, 69, 25), name);
		checkForNPC(world, room, new BlockPos(17, 69, 24), name);

		return ChatFilterResult.PASS;
	}


	private void checkForNPC(ClientLevel world, Room room, BlockPos relative, String name) {
		BlockPos npcPos = room.relativeToActual(relative);
		List<ArmorStand> npcs = world.getEntitiesOfClass(
				ArmorStand.class,
				AABB.encapsulatingFullBlocks(npcPos, npcPos),
				entity -> entity.getName().getString().equals(name)
		);
		if (!npcs.isEmpty()) {
			pos = room.relativeToActual(relative.offset(1, 0, 0));
			boundingBox = RenderHelper.getBlockBoundingBox(world, pos);
			npcs.forEach(entity -> entity.skyblocker$setCustomName(Component.literal(name).withStyle(ChatFormatting.GREEN)));
		}
	}

	@Override
	public void tick(Minecraft client) {}

	@Override
	public void extractRendering(PrimitiveCollector collector) {
		if (shouldSolve() && boundingBox != null) {
			collector.submitFilledBox(boundingBox, GREEN_COLOR_COMPONENTS, 0.5f, false);
		}
	}

	@Override
	public void reset() {
		super.reset();
		pos = null;
	}
}
