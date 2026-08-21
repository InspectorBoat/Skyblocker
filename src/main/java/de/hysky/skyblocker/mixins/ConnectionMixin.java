package de.hysky.skyblocker.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import de.hysky.skyblocker.skyblock.teleport.PredictiveSmoothAOTE;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

import static de.hysky.skyblocker.skyblock.teleport.PredictiveSmoothAOTE.predictTeleport;
import static de.hysky.skyblocker.skyblock.teleport.PredictiveSmoothAOTE.lastSentPos;
import static de.hysky.skyblocker.skyblock.teleport.PredictiveSmoothAOTE.lastSentRot;
import static de.hysky.skyblocker.skyblock.teleport.TeleportLogger.onClick;

@Mixin(Connection.class)
public class ConnectionMixin {
	@Unique
	private boolean ignoreNextUseItemPacket = false;

	/// There is a strange interaction here:
	/// When you right click in the air with a shovel, normally the packet order looks like this:
	/// UseItem (main hand) -> MovePlayer
	/// (Irrelevant packets in between omitted for clarity)
	/// MovePlayer (and its subclasses) are responsible for relaying player rotation to the server. Since UseItem
	/// gets sent *before* MovePlayer, does this mean the server uses the *last* tick's rotation for the AOTV teleport?
	/// No. Actually, UseItem contains fields yRot & xRot, which sends the player's most recent rotation.
	/// HOWEVER, what happens when you right click on a *block*? The packet order looks like this:
	/// UseItemOn (main hand) -> UseItem (main hand) -> MovePlayer
	/// Now, it appears that Hypixel *also* triggers the teleport when receiving the UseItemOn packet.
	/// (Apparently, they then ignore the subsequent UseItem packet.)
	/// But UseItemOn *doesn't* contain the client's rotation! So the server will use an outdated rotation.
	///
	/// In summary, when you right click in the air, the server uses your up-to-date rotation to perform the teleport.
	/// But if you right click on a block, the server uses the last tick's rotation.
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
	public void readOutgoingPacket(CallbackInfo ci, @Local(argsOnly = true) Packet<?> packet) {
		Queue<Packet<?>> queue = PredictiveSmoothAOTE.queue;
		switch (packet) {
			case ServerboundUseItemOnPacket useItemOnPacket -> {
				if (useItemOnPacket.getHand() == InteractionHand.OFF_HAND) return;
				predictTeleport(useItemOnPacket.getHitResult(), lastSentRot != null ? lastSentRot.x : PredictiveSmoothAOTE.CLIENT.player.xRotLast, lastSentRot != null ? lastSentRot.y : PredictiveSmoothAOTE.CLIENT.player.yRotLast, true);
				onClick(lastSentRot != null ? lastSentRot.x : PredictiveSmoothAOTE.CLIENT.player.xRotLast, lastSentRot != null ? lastSentRot.y : PredictiveSmoothAOTE.CLIENT.player.yRotLast);
				ignoreNextUseItemPacket = true;
				queue.add(packet);
				if (queue.size() > 10) queue.remove();

			}
			case ServerboundUseItemPacket useItemPacket -> {
				if (ignoreNextUseItemPacket) {
					ignoreNextUseItemPacket = false;
					return;
				}
				queue.add(packet);
				if (queue.size() > 10) queue.remove();
				predictTeleport(null, useItemPacket.getXRot(), useItemPacket.getYRot(), false);
				onClick(useItemPacket.getXRot(), useItemPacket.getYRot());
			}
			case ServerboundMovePlayerPacket movePlayerPacket -> {
				if (movePlayerPacket.hasPosition()) {
					lastSentPos = new Vec3(movePlayerPacket.getX(0), movePlayerPacket.getY(0), movePlayerPacket.getZ(0));
				}
				if (movePlayerPacket.hasRotation()) {
					lastSentRot = new Vec2(movePlayerPacket.getXRot(0), movePlayerPacket.getYRot(0));
				}
				queue.add(packet);
				if (queue.size() > 10) queue.remove();
			}
			default -> {}
		}
	}
}
