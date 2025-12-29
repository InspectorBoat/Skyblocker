package de.hysky.skyblocker.mixins;

import de.hysky.skyblocker.events.WorldEvents;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientPacketListenerImplMixin {
	@Inject(method = "handlePing", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	public void skyblocker$serverTick(ClientboundPingPacket clientboundPingPacket, CallbackInfo ci) {
		WorldEvents.SERVER_TICK.invoker().onServerTick();
	}
}
