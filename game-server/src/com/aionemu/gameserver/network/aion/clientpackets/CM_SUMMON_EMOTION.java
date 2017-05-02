package com.aionemu.gameserver.network.aion.clientpackets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.Summon;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author ATracer
 */
public class CM_SUMMON_EMOTION extends AionClientPacket {

	private static final Logger log = LoggerFactory.getLogger(CM_SUMMON_EMOTION.class);

	@SuppressWarnings("unused")
	private int objId;
	private int emotionTypeId;

	public CM_SUMMON_EMOTION(int opcode, State state, State... restStates) {
		super(opcode, state, restStates);
	}

	@Override
	protected void readImpl() {
		objId = readD();
		emotionTypeId = readUC();
	}

	@Override
	protected void runImpl() {
		Player player = getConnection().getActivePlayer();
		EmotionType emotionType = EmotionType.getEmotionTypeById(emotionTypeId);

		Summon summon = player.getSummon();
		if (summon == null) // commonly due to lags when the pet dies
			return;

		switch (emotionType) {
			case FLY:
			case LAND:
				PacketSendUtility.broadcastPacket(summon, new SM_EMOTION(summon, EmotionType.START_EMOTE2));
				PacketSendUtility.broadcastPacket(summon, new SM_EMOTION(summon, emotionType));
				break;
			case JUMP:
			case SUMMON_STOP_JUMP:
				PacketSendUtility.broadcastPacket(summon, new SM_EMOTION(summon, emotionType));
				break;
			case ATTACKMODE_IN_MOVE: // start attacking
				summon.setState(CreatureState.WEAPON_EQUIPPED);
				PacketSendUtility.broadcastPacket(summon, new SM_EMOTION(summon, emotionType));
				break;
			case NEUTRALMODE_IN_MOVE: // stop attacking
				summon.unsetState(CreatureState.WEAPON_EQUIPPED);
				PacketSendUtility.broadcastPacket(summon, new SM_EMOTION(summon, emotionType));
				break;
			case UNK:
				log.warn("Unknown emotion type? 0x" + Integer.toHexString(emotionTypeId).toUpperCase() + " from " + player);
		}
	}
}
