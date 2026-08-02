package de.hysky.skyblocker.skyblock.entity.glow.adder;

import java.util.List;
import java.util.Optional;

import de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonMapUtils;
import de.hysky.skyblocker.skyblock.dungeon.secrets.Room;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.skyblock.dungeon.DungeonScore;
import de.hysky.skyblocker.skyblock.dungeon.LividColor;
import de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonManager;
import de.hysky.skyblocker.skyblock.dungeon.secrets.DungeonPlayerManager;
import de.hysky.skyblocker.skyblock.entity.MobGlow;
import de.hysky.skyblocker.skyblock.entity.MobGlowAdder;
import de.hysky.skyblocker.skyblock.item.HeadTextures;
import de.hysky.skyblocker.utils.ItemUtils;
import de.hysky.skyblocker.utils.Utils;
import net.minecraft.world.phys.AABB;

public class DungeonGlowAdder extends MobGlowAdder {
	public static final DungeonGlowAdder INSTANCE = new DungeonGlowAdder();
	protected static final int STARRED_COLOUR = 0xF57738;
	private static final int LOST_ADVENTURER_COLOUR = 0xFEE15C;
	private static final int SHADOW_ASSASSIN_COLOUR = 0x5B2CB2;
	private static final int ANGRY_ARCHAEOLOGIST_COLOUR = 0x57C2F7;
	private static final int ENDERMAN_EYE_COLOUR = 0xCC00FA;

	@Init
	public static void init() {}

	@Override
	public int computeColour(Entity entity) {

		Room currentRoom = DungeonManager.getCurrentRoom();
		if (currentRoom != null) {
			if (!currentRoom.getSegments().contains(DungeonMapUtils.getPhysicalRoomPos(entity.position()))) return NO_GLOW;
		}

		if (STARRED_MOBS.contains(entity.getId())) return STARRED_COLOUR;

		String name = entity.getName().getString();
		return switch (entity) {
			// Minibosses
			case Player _ when SkyblockerConfigManager.get().dungeons.starredMobGlow && !DungeonManager.getBoss().isFloor(4) && name.equals("Lost Adventurer") -> LOST_ADVENTURER_COLOUR;
			case Player _ when SkyblockerConfigManager.get().dungeons.starredMobGlow && !DungeonManager.getBoss().isFloor(4) && name.equals("Shadow Assassin") -> SHADOW_ASSASSIN_COLOUR;
			case Player _ when SkyblockerConfigManager.get().dungeons.starredMobGlow && !DungeonManager.getBoss().isFloor(4) && name.equals("Diamond Guy") -> ANGRY_ARCHAEOLOGIST_COLOUR;
			case Player _ when entity.getId() == LividColor.getCorrectLividId() && LividColor.shouldGlow(name) -> LividColor.getGlowColor(name);

			// Bats
			case Bat b when SkyblockerConfigManager.get().dungeons.starredMobGlow && !b.isInvisible() -> STARRED_COLOUR;

			// Wither & Blood Keys
			case ArmorStand as when SkyblockerConfigManager.get().dungeons.highlightDoorKeys && as.hasItemInSlot(EquipmentSlot.HEAD) -> switch (ItemUtils.getHeadTexture(as.getItemBySlot(EquipmentSlot.HEAD))) {
				case String s when s.equals(HeadTextures.WITHER_KEY) -> DyeColor.CYAN.getTextColor();
				case String s when s.equals(HeadTextures.BLOOD_KEY) -> DyeColor.CYAN.getTextColor();
				default -> NO_GLOW;
			};

			// Armor Stands
			case ArmorStand _ -> 0;

			//Class-based glow
			case Player p when SkyblockerConfigManager.get().dungeons.classBasedPlayerGlow && DungeonScore.isDungeonStarted() -> DungeonPlayerManager.getClassFromPlayer(p).glowColor();

			default -> NO_GLOW;
		};
	}

	@Override
	public boolean isEnabled() {
		return Utils.isInDungeons();
	}

	/**
	 * Checks if an entity is starred by checking if its armor stand contains a star in its name.
	 *
	 * @param entity the entity to check.
	 * @return true if the entity is starred, false otherwise
	 */
	public static boolean isStarred(Entity entity) {
		List<ArmorStand> armorStands = MobGlow.getArmorStands(entity);
		return !armorStands.isEmpty() && armorStands.getFirst().getName().getString().contains("✯");
	}

	public static IntOpenHashSet STARRED_MOBS = new IntOpenHashSet();

	@SuppressWarnings("unchecked")
	public static void onEntityUpdate(ClientboundSetEntityDataPacket packet, ArmorStand armorStand) {
		if (!INSTANCE.isEnabled()) return;
		for (SynchedEntityData.DataValue<?> entry : packet.packedItems()) {
			if (entry.serializer().equals(EntityDataSerializers.OPTIONAL_COMPONENT)) {
				((Optional<Component>) entry.value()).filter(DungeonGlowAdder::componentContainsStar).ifPresent(_ -> {
					var entities = armorStand.level().getEntities(armorStand, AABB.ofSize(armorStand.position(), 0.2, 2, 0.2), e -> e instanceof LivingEntity && !(e instanceof ArmorStand) && !(e instanceof WitherBoss));
					if (!entities.isEmpty()) {
						STARRED_MOBS.add(entities.getFirst().getId());
					}
				});
			}
		}
	}

	public static boolean componentContainsStar(Component component) {
		return component.visit(string -> string.indexOf('✯') != -1 ? FormattedText.STOP_ITERATION : Optional.empty()).isPresent();
	}

	public static void remove(Entity e) {
		STARRED_MOBS.remove(e.getId());
	}
}
