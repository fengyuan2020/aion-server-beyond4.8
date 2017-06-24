package com.aionemu.gameserver.services.siege;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.siege.SiegeModType;
import com.aionemu.gameserver.model.siege.SiegeRace;
import com.aionemu.gameserver.model.templates.siegelocation.SiegeMercenaryZone;
import com.aionemu.gameserver.model.templates.spawns.Spawn;
import com.aionemu.gameserver.model.templates.spawns.SpawnSpotTemplate;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.model.templates.spawns.mercenaries.MercenaryRace;
import com.aionemu.gameserver.model.templates.spawns.mercenaries.MercenarySpawn;
import com.aionemu.gameserver.model.templates.spawns.mercenaries.MercenaryZone;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.SiegeService;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author Whoop
 */
public class MercenaryLocation {

	private final static Logger log = LoggerFactory.getLogger(MercenaryLocation.class);
	private List<VisibleObject> spawnedMercs = new ArrayList<>();
	private MercenaryZone spawns; // TODO: Change this to SpawnGroup
	private SiegeMercenaryZone smz;
	private Race race;
	private long lastSpawn;
	private int siegeId;

	public MercenaryLocation(SiegeMercenaryZone template, SiegeRace race, int siegeId) {
		this.smz = template;
		this.siegeId = siegeId;
		this.race = Race.getRaceByString(race.name());
		if (this.race != null)
			spawns = getSpawnZone();
	}

	public void spawn() {
		despawnCurrentMercs();
		if (spawns == null)
			return;
		List<VisibleObject> mercs = new ArrayList<>();
		for (Spawn spawn : spawns.getSpawns()) {
			for (SpawnSpotTemplate sst : spawn.getSpawnSpotTemplates()) {
				SpawnTemplate spawnTemplate = SpawnEngine.newSiegeSpawn(spawns.getWorldId(), spawn.getNpcId(), siegeId, SiegeRace.getByRace(race),
					SiegeModType.SIEGE, sst.getX(), sst.getY(), sst.getZ(), sst.getHeading());
				spawnTemplate.setStaticId(sst.getStaticId());
				VisibleObject newMerc = SpawnEngine.spawnObject(spawnTemplate, 1);
				mercs.add(newMerc);
			}
		}
		spawnedMercs.addAll(mercs);
		lastSpawn = System.currentTimeMillis();

		SiegeService.getInstance().getFortress(siegeId).forEachPlayer(new Consumer<Player>() {

			@Override
			public void accept(Player player) {
				PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(smz.getAnnounceId()));
			}
		});
	}

	public void despawnCurrentMercs() {
		if (spawnedMercs.isEmpty())
			return;
		for (VisibleObject merc : spawnedMercs) {
			if (merc.isSpawned() && !((Npc) merc).isDead())
				merc.getController().delete();
		}
		spawnedMercs.clear();
	}

	/**
	 * @return true if cooldown is expired and enough mercs are dead
	 */
	public boolean isRequestValid() {
		if (spawns == null)
			return false;
		if (!((System.currentTimeMillis() - lastSpawn) > smz.getCooldown()))
			return false;
		if (!hasEnoughMercsAlive())
			return false;
		return true;
	}

	/**
	 * Check if enough mercs are still alive
	 * 
	 * @return false if 50% are alive
	 */
	private boolean hasEnoughMercsAlive() {
		int totalMercs = spawnedMercs.size();
		int livingMercs = 0;
		for (VisibleObject vo : spawnedMercs) {
			if (vo instanceof Npc) {
				if (vo.isSpawned() && !((Npc) vo).isDead())
					livingMercs++;
			}
		}
		return livingMercs < (totalMercs / 2);
	}

	private MercenaryZone getSpawnZone() {
		MercenaryZone tempZone = null;
		MercenarySpawn spawn = DataManager.SPAWNS_DATA.getMercenarySpawnBySiegeId(siegeId);
		if (spawn == null) {
			log.error("[MERC] There is no mercenaries spawns for siege " + siegeId + " and zone" + smz.getId());
			return tempZone;
		}
		MercenaryRace targetRace = null;
		for (MercenaryRace mrace : spawn.getMercenaryRaces()) {
			if (mrace.getRace() == race) {
				targetRace = mrace;
				break;
			}
		}
		if (targetRace == null) {
			log.error("[MERC] There is no mercenary race for siege " + siegeId + ", zone" + smz.getId() + ", race:" + race.toString());
			return tempZone;
		}
		for (MercenaryZone mzone : targetRace.getMercenaryZones()) {
			if (mzone.getZoneId() == smz.getId()) {
				tempZone = mzone;
				break;
			}
		}
		if (tempZone == null) {
			log.error("[MERC] There is no mercenary zone for siege " + siegeId + ", zone" + smz.getId() + ", race:" + race.toString());
			return tempZone;
		}
		return tempZone;
	}

	public int getCosts() {
		return smz.getCosts();
	}

	public int getMsgId() {
		return smz.getMsgId();
	}
}
