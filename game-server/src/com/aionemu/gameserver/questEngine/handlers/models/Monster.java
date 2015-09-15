package com.aionemu.gameserver.questEngine.handlers.models;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

import javolution.util.FastTable;

/**
 * @author MrPoke
 * @reworked vlog, Bobobear, Artur
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Monster")
public class Monster {

	@XmlAttribute(name = "var", required = true)
	protected int var;

	@XmlAttribute(name = "start_var")
	protected Integer startVar;

	@XmlAttribute(name = "end_var", required = true)
	protected int endVar;

	@XmlAttribute(name = "end_var_reward")
	protected boolean rewardVar = false;

	@XmlAttribute(name = "npc_ids")
	protected List<Integer> npcIds;

	@XmlAttribute(name = "npc_seq")
	private Integer npcSequence;

	@XmlAttribute(name = "spawner_object")
	protected int spawnerObject;

	public int getVar() {
		return var;
	}

	public void setVar(int value) {
		this.var = value;
	}

	public Integer getStartVar() {
		return startVar;
	}

	public void setStartVar(int value) {
		this.startVar = value;
	}

	public int getEndVar() {
		return endVar;
	}

	public void setEndVar(int value) {
		this.endVar = value;
	}

	public List<Integer> getNpcIds() {
		return npcIds;
	}

	public void addNpcIds(List<Integer> value) {
		if (this.npcIds == null)
			this.npcIds = new FastTable<Integer>();
		for (Integer npc : value) {
			if (!this.npcIds.contains(npc))
				this.npcIds.add(npc);
		}
	}

	public Integer getNpcSequence() {
		return npcSequence;
	}

	public void setNpcSequence(Integer value) {
		this.npcSequence = value;
	}

	public int getSpawnerObject() {
		return spawnerObject;
	}

	public void setSpawnerObject(Integer value) {
		this.spawnerObject = value;
	}

	public boolean getRewardVar() {
		return rewardVar;
	}

	public void setRewardVar(boolean value) {
		this.rewardVar = value;
	}
}
