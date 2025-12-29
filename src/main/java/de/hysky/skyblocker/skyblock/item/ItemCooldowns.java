package de.hysky.skyblocker.skyblock.item;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.events.PlaySoundEvents;
import de.hysky.skyblocker.events.WorldEvents;
import de.hysky.skyblocker.utils.ItemUtils;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemCooldowns {
	private static final String JUNGLE_AXE_ID = "JUNGLE_AXE";
	private static final String TREECAPITATOR_ID = "TREECAPITATOR_AXE";
	private static final String FIG_AXE_ID = "FIG_AXE";
	private static final String FIGSTONE_ID = "FIGSTONE_AXE";
	private static final String GRAPPLING_HOOK_ID = "GRAPPLING_HOOK";
	private static final String ROGUE_SWORD_ID = "ROGUE_SWORD";
	private static final String LEAPING_SWORD_ID = "LEAPING_SWORD";
	private static final String SILK_EDGE_SWORD_ID = "SILK_EDGE_SWORD";
	private static final String GREAT_SPOOK_STAFF_ID = "GREAT_SPOOK_STAFF";
	private static final String SPIRIT_LEAP_ID = "SPIRIT_LEAP";
	private static final String GIANTS_SWORD_ID = "GIANTS_SWORD";
	private static final String SHADOW_FURY_ID = "SHADOW_FURY";
	private static final String LIVID_DAGGER_ID = "LIVID_DAGGER";
	private static final String INK_WAND_ID = "INK_WAND";
	private static final List<String> WITHER_BLADES = Arrays.asList("NECRON_BLADE", "HYPERION", "SCYLLA", "ASTRAEA", "VALKYRIE");

	private static final List<String> BAT_ARMOR_IDS = List.of("BAT_PERSON_HELMET", "BAT_PERSON_CHESTPLATE", "BAT_PERSON_LEGGINGS", "BAT_PERSON_BOOTS");
	private static final Map<String, CooldownEntry> ITEM_COOLDOWNS = new HashMap<>();
	private static int currentTick = 0;

	@Init
	public static void init() {
		UseItemCallback.EVENT.register(ItemCooldowns::onItemInteract);
		PlaySoundEvents.FROM_SERVER.register(ItemCooldowns::listenForWitherShield);
		WorldEvents.SERVER_TICK.register(ItemCooldowns::tickCooldowns);
	}

	private static void tickCooldowns() {
		currentTick += 1;
	}

	private static void listenForWitherShield(ClientboundSoundPacket packet) {
		final ResourceLocation sound = packet.getSound().value().location();
		if (sound.equals(SoundEvents.ZOMBIE_VILLAGER_CURE.location())) {
			if (packet.getPitch() != 0.6984127 || packet.getVolume() != 1.0) return;
			startItemCooldown(WITHER_BLADES.get(0), 5 * 20);
			startItemCooldown(WITHER_BLADES.get(1), 5 * 20);
			startItemCooldown(WITHER_BLADES.get(2), 5 * 20);
			startItemCooldown(WITHER_BLADES.get(3), 5 * 20);
			startItemCooldown(WITHER_BLADES.get(4), 5 * 20);
		} else if (sound.equals(SoundEvents.PLAYER_LEVELUP.location())) {
			if (packet.getPitch() != 3.0 || packet.getVolume() != 1.0) return;
			ITEM_COOLDOWNS.remove(WITHER_BLADES.get(0));
			ITEM_COOLDOWNS.remove(WITHER_BLADES.get(1));
			ITEM_COOLDOWNS.remove(WITHER_BLADES.get(2));
			ITEM_COOLDOWNS.remove(WITHER_BLADES.get(3));
			ITEM_COOLDOWNS.remove(WITHER_BLADES.get(4));
		}
	}

	private static InteractionResult onItemInteract(Player player, Level world, InteractionHand hand) {
		if (!SkyblockerConfigManager.get().uiAndVisuals.itemCooldown.enableItemCooldowns)
			return InteractionResult.PASS;
		String usedItemId = player.getMainHandItem().getSkyblockId();
		switch (usedItemId) {
			case FIG_AXE_ID, FIGSTONE_ID, JUNGLE_AXE_ID, TREECAPITATOR_ID -> startItemCooldown(usedItemId, 1 * 20);
			case SILK_EDGE_SWORD_ID, LEAPING_SWORD_ID -> startItemCooldown(usedItemId, 1 * 20);
			case GRAPPLING_HOOK_ID -> {
				if (player.fishing != null && !isWearingBatArmor(player)) startItemCooldown(GRAPPLING_HOOK_ID, 2 * 20);
			}
			case ROGUE_SWORD_ID, SPIRIT_LEAP_ID, LIVID_DAGGER_ID -> startItemCooldown(usedItemId, 5 * 20);
			case SHADOW_FURY_ID -> startItemCooldown(SHADOW_FURY_ID, 15 * 20);
			case INK_WAND_ID, GIANTS_SWORD_ID -> startItemCooldown(usedItemId, 30 * 20);
			case GREAT_SPOOK_STAFF_ID -> startItemCooldown(GREAT_SPOOK_STAFF_ID, 60 * 20);
			case String s when WITHER_BLADES.contains(usedItemId) -> {
				startItemCooldown(WITHER_BLADES.get(0), 5 * 20);
				startItemCooldown(WITHER_BLADES.get(1), 5 * 20);
				startItemCooldown(WITHER_BLADES.get(2), 5 * 20);
				startItemCooldown(WITHER_BLADES.get(3), 5 * 20);
				startItemCooldown(WITHER_BLADES.get(4), 5 * 20);
			}
			// Handle any unlisted items if necessary
			default -> {}
		}
		return InteractionResult.PASS;
	}

	// Method to handle item cooldowns with optional condition
	private static void startItemCooldown(String itemId, int cooldownTime) {
		if (!isOnCooldown(itemId)) {
			ITEM_COOLDOWNS.put(itemId, new CooldownEntry(cooldownTime));
		}
	}

	public static boolean isOnCooldown(ItemStack itemStack) {
		return isOnCooldown(itemStack.getSkyblockId());
	}

	private static boolean isOnCooldown(String itemId) {
		if (ITEM_COOLDOWNS.containsKey(itemId)) {
			CooldownEntry cooldownEntry = ITEM_COOLDOWNS.get(itemId);
			if (cooldownEntry.isOnCooldown()) {
				return true;
			} else {
				ITEM_COOLDOWNS.remove(itemId);
				return false;
			}
		}

		return false;
	}

	public static CooldownEntry getItemCooldownEntry(ItemStack itemStack) {
		return ITEM_COOLDOWNS.get(itemStack.getSkyblockId());
	}

	private static boolean isWearingBatArmor(Player player) {
		for (ItemStack stack : ItemUtils.getArmor(player)) {
			String itemId = stack.getSkyblockId();
			if (!BAT_ARMOR_IDS.contains(itemId)) {
				return false;
			}
		}
		return true;
	}

	public record CooldownEntry(int cooldown, long startTick) {
		public CooldownEntry(int cooldown) {
			this(cooldown, currentTick);
		}

		public boolean isOnCooldown() {
			return (this.startTick + this.cooldown) > currentTick;
		}

		public long getRemainingCooldown() {
			long time = (this.startTick + this.cooldown) - currentTick;
			return Math.max(time, 0);
		}

		public float getRemainingCooldownPercent() {
			return this.isOnCooldown() ? (float) this.getRemainingCooldown() / cooldown : 0.0f;
		}
	}
}
