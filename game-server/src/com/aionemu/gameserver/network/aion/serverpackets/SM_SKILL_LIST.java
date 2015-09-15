package com.aionemu.gameserver.network.aion.serverpackets;

import java.util.List;

import javolution.util.FastTable;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.skill.PlayerSkillEntry;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.AionServerPacket;

/**
 * @author MrPoke
 * @modified ATracer, Neon
 */
public class SM_SKILL_LIST extends AionServerPacket {

	private List<PlayerSkillEntry> skillList;
	private int messageId;
	private int skillNameId;
	private String skillLvl;
	boolean isNew = false;

	/**
	 * This constructor is used on player entering the world Constructs new <tt>SM_SKILL_LIST</tt> packet
	 */
	public SM_SKILL_LIST(PlayerSkillEntry skill) {
		this.skillList = new FastTable<>();
		this.skillList.add(skill);
		this.messageId = 0;
	}

	public SM_SKILL_LIST(List<PlayerSkillEntry> skillList) {
		this.skillList = skillList;
		this.messageId = 0;
	}

	public SM_SKILL_LIST(PlayerSkillEntry skillListEntry, int messageId, boolean isNew) {
		this.skillList = new FastTable<>();
		this.skillList.add(skillListEntry);
		this.messageId = messageId;
		this.skillNameId = DataManager.SKILL_DATA.getSkillTemplate(skillListEntry.getSkillId()).getNameId();
		this.skillLvl = String.valueOf(skillListEntry.getSkillLevel());
		this.isNew = isNew;
	}

	@Override
	protected void writeImpl(AionConnection con) {

		final int size = skillList.size();
		writeH(size); // skills list size
		writeC(isNew ? 0 : 1); // 1 all learned skills, 0 skills can be learned ?

		if (size > 0) {
			for (PlayerSkillEntry entry : skillList) {
				writeH(entry.getSkillId());// id
				writeH(entry.getSkillLevel());// lvl
				writeC(0x00);
				int extraLevel = entry.getExtraLvl();
				writeC(extraLevel);
				if (isNew && extraLevel == 0 && !entry.isStigma())
					writeD((int) (System.currentTimeMillis() / 1000)); // Learned date NCSoft......
				else
					writeD(0);
				writeC(entry.isStigma() ? 1 : 0); // stigma
			}
		}
		writeD(messageId);
		if (messageId != 0) {
			writeH(0x24); // unk
			writeD(skillNameId);
			writeH(0x00);
			writeS(skillLvl);
		}
	}
}
