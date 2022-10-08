package ai.instance.empyreanCrucible;

import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.instance.StageType;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author Luzien
 */
@AIName("spectral_warrior")
public class SpectralWarriorAI extends AggressiveNpcAI {

	private AtomicBoolean isDone = new AtomicBoolean(false);

	public SpectralWarriorAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		checkPercentage(getLifeStats().getHpPercentage());
	}

	private void checkPercentage(int hpPercentage) {
		if (hpPercentage <= 50 && isDone.compareAndSet(false, true)) {
			getPosition().getWorldMapInstance().getInstanceHandler().onChangeStage(StageType.START_STAGE_6_ROUND_5);
			ThreadPoolManager.getInstance().schedule(this::resurrectAllies, 2000);
		}
	}

	private void resurrectAllies() {
		getKnownList().forEachNpc(npc -> {
			if (npc.isDead())
				return;

			switch (npc.getNpcId()) {
				case 205413:
					spawn(217576, npc.getX(), npc.getY(), npc.getZ(), npc.getHeading());
					npc.getController().delete();
					break;
				case 205414:
					spawn(217577, npc.getX(), npc.getY(), npc.getZ(), npc.getHeading());
					npc.getController().delete();
					break;
			}
		});
	}
}
