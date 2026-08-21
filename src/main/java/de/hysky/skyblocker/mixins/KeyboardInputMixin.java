package de.hysky.skyblocker.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.hysky.skyblocker.skyblock.teleport.MoveUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
	private static boolean overrrideKey(KeyMapping instance, Operation<Boolean> original) {
		Minecraft client = Minecraft.getInstance();

		if (client.gui.screen() != null || !(MoveUtils.sequence.getCurrentAction() instanceof MoveUtils.Action.Move move)) return original.call(instance);

		if (instance == Minecraft.getInstance().options.keyUp) return move.up();
		else if (instance == Minecraft.getInstance().options.keyDown) return move.down();
		else if (instance == Minecraft.getInstance().options.keyLeft) return move.left();
		else if (instance == Minecraft.getInstance().options.keyRight) return move.right();
		else if (instance == Minecraft.getInstance().options.keyJump) return move.jump();
		else if (instance == Minecraft.getInstance().options.keyShift) return move.shift();
		else if (instance == Minecraft.getInstance().options.keySprint) return move.sprint();
		else return original.call(instance);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private static void nextAction(CallbackInfo ci) {
		MoveUtils.sequence.playerInputRecieved();
	}
}
