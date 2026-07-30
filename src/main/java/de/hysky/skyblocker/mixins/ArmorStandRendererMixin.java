package de.hysky.skyblocker.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ArmorStandRenderer.class)
public abstract class ArmorStandRendererMixin {
	@ModifyExpressionValue(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;isMarker:Z", opcode = Opcodes.GETFIELD))
	private boolean modifyMarkerValue(boolean original) {
		return true;
	}
}
