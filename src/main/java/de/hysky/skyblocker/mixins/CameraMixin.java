package de.hysky.skyblocker.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.skyblock.teleport.PredictiveSmoothAOTE;
import de.hysky.skyblocker.skyblock.teleport.ReactiveSmoothAOTE;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
public abstract class CameraMixin {
//	@ModifyReturnValue(method = "position", at = @At("RETURN"))
//	private Vec3 skyblocker$onCameraUpdate(Vec3 original) {
//		if (SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE.predictive) {
//			Vec3 pos = PredictiveSmoothAOTE.getInterpolatedPos();
//			if (pos != null) {
//				return pos;
//			}
//		} else {
//			Vec3 pos = ReactiveSmoothAOTE.getInterpolatedPos(original);
//			if (pos != null) {
//				return pos;
//			}
//		}
//
//		return original;
//	}

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
	private void overridePosition(Camera camera, double originalX, double originalY, double originalZ, @Local(argsOnly = true) float partialTicks) {
		final Vec3 originalPos = new Vec3(originalX, originalY - eyeHeight, originalZ);
		double eyeHeight = Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight);
		Vec3 pos;
		if (SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE.predictive) {
			pos = PredictiveSmoothAOTE.getInterpolatedPos(originalPos, eyeHeight);
		} else {
			pos = ReactiveSmoothAOTE.getInterpolatedPos(originalPos);
		}
		if (pos != null) setPosition(pos);
		else setPosition(originalPos.add(0, eyeHeight, 0));
	}

	@Shadow
	private float eyeHeightOld;
	@Shadow
	private float eyeHeight;

	@Shadow
	protected abstract void setPosition(Vec3 pos);
}
