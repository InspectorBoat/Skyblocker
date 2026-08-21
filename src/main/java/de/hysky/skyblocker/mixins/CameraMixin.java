package de.hysky.skyblocker.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
	private void overridePosition(Camera instance, double x, double y, double z, Operation<Void> original, @Local(argsOnly = true) float partialTicks) {
		final Vec3 originalPos = new Vec3(x, x - eyeHeight, x);
		double eyeHeight = Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight);
		Vec3 pos;
		if (SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE.predictive) {
			pos = PredictiveSmoothAOTE.getCameraPos(eyeHeight);
		} else {
			pos = ReactiveSmoothAOTE.getInterpolatedPos(originalPos);
		}
		if (pos != null) setPosition(pos);
		else original.call(instance, x, y, z);
	}

	@WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
	private void overrideRotation(Camera instance, float yRot, float xRot, Operation<Void> original) {
		if (SkyblockerConfigManager.get().uiAndVisuals.smoothAOTE.predictive) {
			var newRot = PredictiveSmoothAOTE.getCameraRot();
			if (newRot != null) {
				setRotation(newRot.y, newRot.x);
				return;
			}
		}
		original.call(instance, yRot, xRot);
	}

	@Shadow
	private float eyeHeightOld;
	@Shadow
	private float eyeHeight;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);
}
