package com.aionemu.gameserver.network.aion.clientpackets;

import javolution.util.FastTable;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team2.group.PlayerFilters.ExcludePlayerFilter;
import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INSTANCE_INFO;

/**
 * @author nrg
 * @modified Neon
 */
public class CM_INSTANCE_INFO extends AionClientPacket {

	private byte updateType; // 0 = reset to client default values and overwrite, 1 = update team member info, 2 = overwrite only

	public CM_INSTANCE_INFO(int opcode, State state, State... restStates) {
		super(opcode, state, restStates);
	}

	@Override
	protected void readImpl() {
		readD(); // unk (always 0)
		updateType = (byte) readC();
	}

	@Override
	protected void runImpl() {
		Player player = getConnection().getActivePlayer();
		if (updateType == 1) {
			// update team info
			sendPacket(new SM_INSTANCE_INFO(updateType, player.isInTeam() ? player.getCurrentTeam().filterMembers(new ExcludePlayerFilter(player)) : new FastTable<>()));
			// update own info
			sendPacket(new SM_INSTANCE_INFO((byte) 2, player));
		} else {
			sendPacket(new SM_INSTANCE_INFO(updateType, player));
		}
	}
}
