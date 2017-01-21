package ai.worlds.levinshor;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.handler.TargetEventHandler;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author Yeats
 */
@AIName("LDF4_Advance_Ancient_Monster")
public class AncientMonsterAI extends AggressiveNpcAI {

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		getOwner().getController().addTask(TaskId.DESPAWN, ThreadPoolManager.getInstance().schedule(() -> {
			if (!isAlreadyDead())
				getOwner().getController().delete();
		}, 1000 * 60 * 60));
	}

	@Override
	protected void handleMoveArrived() {
		super.handleMoveArrived();
		if (getOwner().getDistanceToSpawnLocation() > 15)
			TargetEventHandler.onTargetGiveup(this);
	}
}