package de.hysky.skyblocker.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import de.hysky.skyblocker.skyblock.teleport.PredictiveSmoothAOTE;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.hysky.skyblocker.utils.ServerTickCounter;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;

import java.util.Queue;

import static de.hysky.skyblocker.skyblock.teleport.PredictiveSmoothAOTE.calculateTeleportUse;
import static de.hysky.skyblocker.skyblock.teleport.PredictiveSmoothAOTE.lastSentPos;

@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerImplMixin {
	@Unique
	private boolean ignoreNextUseItemPacket = false;

	@Inject(method = "Lnet/minecraft/client/multiplayer/ClientCommonPacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void readPacket(CallbackInfo ci, @Local(argsOnly = true) Packet<?> packet) {
//		if (PredictiveSmoothAOTE.logPackets > 0) PredictiveSmoothAOTE.say(packet.toString());
		Queue queue = PredictiveSmoothAOTE.queue;
		switch (packet) {
			case ServerboundUseItemOnPacket useItemOnPacket -> {
				if (useItemOnPacket.getHand() == InteractionHand.OFF_HAND) return;
//				PredictiveSmoothAOTE.say("USE ON: " + useItemOnPacket.getHitResult().getBlockPos());
				calculateTeleportUse(useItemOnPacket.getHand(), useItemOnPacket.getHitResult(), PredictiveSmoothAOTE.CLIENT.player.xRotLast, PredictiveSmoothAOTE.CLIENT.player.yRotLast);
				ignoreNextUseItemPacket = true;
			}
			case ServerboundUseItemPacket useItemPacket -> {
//				queue.forEach(p -> PredictiveSmoothAOTE.say("%s %s".formatted(
//						((ServerboundMovePlayerPacket) p).getXRot(0), ((ServerboundMovePlayerPacket) p).getYRot(0)))
//				);
//				PredictiveSmoothAOTE.say("USE ITEM: %s %s".formatted(useItemPacket.getXRot(), useItemPacket.getYRot()));
				if (ignoreNextUseItemPacket) {
					ignoreNextUseItemPacket = false;
					return;
				}
				calculateTeleportUse(useItemPacket.getHand(), null, useItemPacket.getXRot(), useItemPacket.getYRot());
			}
			case ServerboundMovePlayerPacket movePlayerPacket -> {
				if (movePlayerPacket.hasPosition()) {
					lastSentPos = new Vec3(movePlayerPacket.getX(0), movePlayerPacket.getY(0), movePlayerPacket.getZ(0));
				}
				if (PredictiveSmoothAOTE.logPackets >= 0) {
//					PredictiveSmoothAOTE.say("ROT: %s %s %s %s".formatted(PredictiveSmoothAOTE.logPackets, movePlayerPacket.type(), movePlayerPacket.getXRot(0), movePlayerPacket.getYRot(0)));
				}
				queue.add(packet);
				if (queue.size() > 5) queue.remove();
			}
			default -> {}
		}
	}

	@Inject(method = "handlePing", at = @At("RETURN"))
	private void skyblocker$onServerTick(ClientboundPingPacket packet, CallbackInfo ci) {
		ServerTickCounter.onServerTick(packet);
	}
}
