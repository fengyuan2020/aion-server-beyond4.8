package ai.instance.theShugoEmperorsVault;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.instance.handlers.InstanceHandler;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.instance.InstanceProgressionType;
import com.aionemu.gameserver.model.instance.instancereward.InstanceReward;
import com.aionemu.gameserver.skillengine.model.Effect;

import ai.AggressiveNpcAI;

/**
 * @author Yeats
 */
@AIName("IDSweep_shugos")
public class IDSweep_Shugos extends AggressiveNpcAI {
	
	private int baseDamage;

	public IDSweep_Shugos(Npc owner) {
		super(owner); 
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		InstanceHandler handler = getOwner().getPosition().getWorldMapInstance().getInstanceHandler();
		InstanceReward<?> reward = null;
		if (handler != null) {
			reward = handler.getInstanceReward();
			if (reward != null) {
				if (reward.getInstanceProgressionType() == InstanceProgressionType.END_PROGRESS)
					getOwner().getController().delete();
			}
		}
		baseDamage = getOwner().getGameStats().getStatsTemplate().getAttack();
	}

	@Override
	public int modifyOwnerDamage(int damage, Effect effect) {
		if (effect == null) {
			int rndDamage = Rnd.get(-Math.round(baseDamage * 0.2f), Math.round(baseDamage * 0.25f));
			damage = baseDamage + rndDamage;
		}
		return damage;
	}

	@Override
	public boolean ask(AIQuestion question) {
		switch (question) {
			case SHOULD_LOOT:
				return false;
			default:
				return super.ask(question);
		}
	}
}
