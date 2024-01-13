package com.aionemu.gameserver.model.autogroup;

import java.util.List;

import com.aionemu.gameserver.model.Race;

/**
 * @author xTz
 */
public class LookingForParty implements Comparable<LookingForParty> {

	private final List<Integer> memberObjectIds;
	private final EntryRequestType ert;
	private final Race race;
	private final long registrationTime = System.currentTimeMillis();
	private final int maskId;
	private long startEnterTime;
	private int leaderObjId;

	public LookingForParty(List<Integer> memberObjectIds, Race race, EntryRequestType ert, int maskId, int leaderObjId) {
		this.memberObjectIds = memberObjectIds;
		this.ert = ert;
		this.race = race;
		this.maskId = maskId;
		this.leaderObjId = leaderObjId;
	}

	public List<Integer> getMemberObjectIds() {
		return memberObjectIds;
	}

	public boolean isMember(int objectId) {
		return memberObjectIds.contains(objectId);
	}

	public void unregisterMember(Integer objectId) {
		memberObjectIds.remove(objectId);
	}

	public EntryRequestType getEntryRequestType() {
		return ert;
	}

	public Race getRace() {
		return race;
	}

	public long getRegistrationTime() {
		return registrationTime;
	}

	public int getMaskId() {
		return maskId;
	}

	public int getLeaderObjId() {
		return leaderObjId;
	}

	public void setLeaderObjId(int leaderObjId) {
		this.leaderObjId = leaderObjId;
	}

	public boolean isLeader(int objectId) {
		return objectId == leaderObjId;
	}

	public void setStartEnterTime() {
		startEnterTime = System.currentTimeMillis();
	}

	public int getRemainingTime() {
		return (int) (System.currentTimeMillis() - registrationTime) / 1000 * 256;
	}

	public boolean isOnStartEnterTask() {
		return System.currentTimeMillis() - startEnterTime <= 120000;
	}

	public boolean canMatch(LookingForParty lfp) {
		return !this.equals(lfp) && race == lfp.getRace();
	}

	@Override
	public int compareTo(LookingForParty lfp) {
		if (ert != lfp.ert)
			return lfp.ert.ordinal() - ert.ordinal();

		int memberDiff = lfp.memberObjectIds.size() - memberObjectIds.size();
		if (memberDiff != 0)
			return memberDiff;

		return (int) (registrationTime - lfp.registrationTime);
	}
}
