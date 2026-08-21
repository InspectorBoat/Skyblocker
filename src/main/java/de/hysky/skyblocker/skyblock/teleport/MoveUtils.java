package de.hysky.skyblocker.skyblock.teleport;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.utils.Location;
import de.hysky.skyblocker.utils.Utils;
import de.hysky.skyblocker.utils.scheduler.MessageScheduler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.jspecify.annotations.Nullable;
import de.hysky.skyblocker.skyblock.teleport.MoveUtils.Action.Wait;
import de.hysky.skyblocker.skyblock.teleport.MoveUtils.Action.WarpHome;
import de.hysky.skyblocker.skyblock.teleport.MoveUtils.Action.WaitHome;
import de.hysky.skyblocker.skyblock.teleport.MoveUtils.Action.Look;
import de.hysky.skyblocker.skyblock.teleport.MoveUtils.Action.Click;
import de.hysky.skyblocker.skyblock.teleport.MoveUtils.Action.Schedule;

import java.util.ArrayList;

import static de.hysky.skyblocker.skyblock.teleport.TeleportLogger.say;

public class MoveUtils {
	public static Sequence sequence = new Sequence();

	private static boolean ctrlLastPressed = false;
	private static boolean shiftLastPressed = false;

	public static float targetPitch = 0f;

	public static boolean autoContinue = false;

	public static int teleportAtEnd;

	@Init
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(_ -> sequence.clientTicked());
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.hasControlDown() && !ctrlLastPressed) {
				startNewSequence();
			}
			if (client.hasShiftDown() && !shiftLastPressed) {
				autoContinue = !autoContinue;
				say("autoContinue: %s".formatted(autoContinue));
			}
			ctrlLastPressed = client.hasControlDown();
			shiftLastPressed = client.hasShiftDown();
		});
	}

	public static void startNewSequence() {
		say("Starting new sequence");
		MoveUtils.sequence = new MoveUtils.Sequence(
				new Wait(20),
				new WarpHome(),
				new WaitHome(),
				new Look(180f, targetPitch),
				new Wait(20),
				new Look(180f, targetPitch),
				new Look(180f, targetPitch),
				new Click(),
				new Schedule(() -> {
					targetPitch -= 1;
					if (targetPitch <= -45 || teleportAtEnd > 5) {
						teleportAtEnd = 0;
						targetPitch = 0;
						say("COMPLETED");

						MoveUtils.sequence = new MoveUtils.Sequence(
								new Wait(20),
								new WarpHome(),
								new WaitHome(),
								new Wait(20),
								new Look(180f, targetPitch),
								new Look(180f, targetPitch),
								new Action.Move(0, 0, false, true, false),
								new Wait(1),
								new Action.Move(0, 1, false, true, false),
								new Wait(1),
								new Action.Move(0, 1, false, true, false),
								new Wait(1),
								new Action.Move(0, 1, false, true, false),
								new Wait(15),
								new Schedule(() -> {
									MessageScheduler.INSTANCE.queueMessage("/setspawn", true, 0);
								}),
								new Wait(15),
								new Schedule(() -> {
									say("RESTARTING");
									startNewSequence();
								})
						);

						return;
					}
					if (autoContinue) startNewSequence();
				})
		);
	}

	public static class Sequence {
		public ArrayList<Action> actions;
		public int index;

		public Sequence(Action... actions) {
			ArrayList<Action> newActions = new ArrayList<>();
			for (Action action : actions) {
				if (action instanceof Wait(int ticks)) {
					for (int i = 0; i < ticks; i++) {
						newActions.add(new Wait(0));
					}
				} else newActions.add(action);
			}
			this.actions = newActions;
			this.index = 0;
		}

		public void movedToSpawn() {
			if (this.getCurrentAction() instanceof Action.WaitHome) this.nextAction();
		}

		public void playerInputRecieved() {
			if (this.getCurrentAction() instanceof Action.Move) this.nextAction();
		}

		public void playerRotRecieved() {
			if (this.getCurrentAction() instanceof Action.Look) this.nextAction();
		}

		public void playerClicked() {
			if (this.getCurrentAction() instanceof Action.Click) this.nextAction();
		}

		public void clientTicked() {
			if (this.getCurrentAction() instanceof Action.Wait) this.nextAction();
		}

		public void nextAction() {
			if (Utils.getLocation() != Location.PRIVATE_ISLAND) return;
			this.index += 1;
			if (this.index == this.actions.size()) say("Sequence Finished (%s)".formatted(this.actions.size()));
			if (this.getCurrentAction() instanceof Action.WarpHome) {
				MessageScheduler.INSTANCE.queueMessage("/is", true, 0);
				nextAction();
			} else if (this.getCurrentAction() instanceof Schedule(Runnable runnable)) {
				runnable.run();
				nextAction();
			}
		}

		public @Nullable Action getCurrentAction() {
			if (this.index >= this.actions.size()) return null;
			return this.actions.get(this.index);
		}
	}

	public sealed interface Action {
		record WarpHome() implements Action {}

		record WaitHome() implements Action {}

		record Move(int strafe, int forwards, boolean jump, boolean shift, boolean sprint) implements Action {
			public boolean left() {return strafe == -1;}

			public boolean right() {return strafe == 1;}

			public boolean up() {return forwards == 1;}

			public boolean down() {return forwards == -1;}
		}

		record Look(@Nullable Float yaw, @Nullable Float pitch) implements Action {}

		record Click() implements Action {}

		record Wait(int i) implements Action {}

		record Schedule(Runnable runnable) implements Action {}
	}
}
