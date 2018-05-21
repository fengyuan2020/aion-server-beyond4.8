package com.aionemu.gameserver.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.configs.main.CustomConfig;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.legionDominion.LegionDominionLocation;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SERIAL_KILLER;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.serialkillers.SerialKiller;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.zone.ZoneInstance;

/**
 * @author Source
 * @modified Dtem, ginho
 * @reworked Yeats 17.01.2016
 */
public class SerialKillerService {

	private final Map<Integer, SerialKiller> serialKillers = new ConcurrentHashMap<>();
	private final Map<Integer, Long> cooldowns = new ConcurrentHashMap<>(); // cd for intruder scan
	private final Collection<Integer> ldPlayers = new ArrayList<>();
	private final Map<Integer, Race> handledWorlds = new HashMap<>();

	private SerialKillerService() {
	}

	public void initSerialKillers() {
		if (!CustomConfig.SERIALKILLER_ENABLED || CustomConfig.SERIALKILLER_WORLDS.isEmpty())
			return;

		for (int worldId : CustomConfig.SERIALKILLER_WORLDS) {
			int worldType = worldId / 10000000 % 10; // the second digit in the map ID denotes the world type (0 = all, 1 = elyos, 2 = asmodians)
			Race type = worldType > 0 ? worldType > 1 ? Race.ASMODIANS : Race.ELYOS : Race.PC_ALL;
			handledWorlds.put(worldId, type);
		}

		ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				for (SerialKiller info : serialKillers.values()) {
					Player player = World.getInstance().findPlayer(info.getPlayerId());
					if (player == null || !player.isOnline() || !isHandledWorld(player.getWorldId())) {
						removeSerialKiller(info.getPlayerId());
						getOrRemoveCooldown(info.getPlayerId());
						continue;
					}
					getOrRemoveCooldown(player.getObjectId());
					if (info.getVictims() > 0) {
						info.setVictims(Math.max(0, info.getVictims() - CustomConfig.SERIALKILLER_DECREASE));

						if (info.getVictims() == 0) {
							removeSerialKiller(player.getObjectId());
							continue;
						}
						int newRank = getKillerRank(info.getVictims());

						if (info.getRank() != newRank) {
							info.setRank(newRank);
							if (!ldPlayers.contains(player.getObjectId())) {
								boolean isEnemyWorld = isEnemyWorld(player);

								PacketSendUtility.sendPacket(player, new SM_SERIAL_KILLER(isEnemyWorld ? 1 : 8, info.getRank()));
								PacketSendUtility.broadcastPacket(player, new SM_SERIAL_KILLER(isEnemyWorld ? 6 : 7, player));
							}
						}
					}
				}
			}
		}, CustomConfig.SERIALKILLER_REFRESH * 60000, CustomConfig.SERIALKILLER_REFRESH * 60000); // kills remove timer
	}

	private List<Player> getWorldKillers(Player player) {
		List<Player> killers = new ArrayList<>();
		for (SerialKiller sk : serialKillers.values()) {
			if (canSee(player.getSKInfo(), sk)) {
				Player intruder = World.getInstance().findPlayer(sk.getPlayerId());
				if (intruder != null && intruder.getRace() != player.getRace() && PositionUtil.isInRange(intruder, player, 500))
					killers.add(intruder);
			}
		}
		return killers;
	}

	private boolean canSee(SerialKiller protector, SerialKiller intruder) {
		int rank = Math.max(protector.getLDRank(), protector.getRank());
		if (rank == 0) { // ok this should never happen but anyways
			return false;
		} else if (rank == 1) {
			return intruder.getRank() == 3; // protectors rank 1 can only see intruders rank 3
		} else if (rank == 2) {
			return intruder.getRank() >= 2; // protectors rank 2 can only see intruders rank 2 & 3
		}
		return true; // protectors rank 3 & stonespear owner can see all ranks
	}

	public void onLogin(Player player) {
		if (!CustomConfig.SERIALKILLER_ENABLED)
			return;
		SerialKiller serialKillerInfo = serialKillers.get(player.getObjectId());
		if (serialKillerInfo != null) {
			boolean isEnemyWorld = isEnemyWorld(player);
			player.setSKInfo(serialKillerInfo);
			if (ldPlayers.contains(player.getObjectId())) {
				PacketSendUtility.sendPacket(player, new SM_SERIAL_KILLER(7, player.getSKInfo().getLDRank(), getOrRemoveCooldown(player.getObjectId())));
			} else {
				PacketSendUtility.sendPacket(player,
					new SM_SERIAL_KILLER(isEnemyWorld ? 0 : 7, player.getSKInfo().getRank(), getOrRemoveCooldown(player.getObjectId())));
			}
		}
	}

	public void onEnterMap(final Player player) {

	}

	public void onLeaveMap(Player player) {
		if (!CustomConfig.SERIALKILLER_ENABLED)
			return;

		removeSerialKiller(player.getObjectId());
	}

	// for Legion Dominion Zones
	public void onEnterZone(Player player, ZoneInstance zone) {
		if (!CustomConfig.SERIALKILLER_ENABLED)
			return;
		if (zone.isDominionZone()) {
			if (player.getLegion() != null && player.getLegion().getOccupiedLegionDominion() > 0) {
				LegionDominionLocation loc = LegionDominionService.getInstance().getLegionDominionByZone(zone.getAreaTemplate().getZoneName().name());
				if (loc != null && loc.getLegionId() == player.getLegion().getLegionId()) {
					if (!ldPlayers.contains(player.getObjectId())) {
						ldPlayers.add(player.getObjectId());
						player.getSKInfo().setLDRank(3);
						player.getSKInfo().getBuff().applyEffect(player, "GUARD", player.getRace(), 3);
						PacketSendUtility.sendPacket(player, new SM_SERIAL_KILLER(7, player.getSKInfo().getLDRank(), getOrRemoveCooldown(player.getObjectId())));
						serialKillers.putIfAbsent(player.getObjectId(), player.getSKInfo());
					}
				}
			}
		}

	}

	// for Legion Dominion Zones
	public void onLeaveZone(Player player, ZoneInstance zone) {
		if (!CustomConfig.SERIALKILLER_ENABLED)
			return;
		if (zone.isDominionZone() && ldPlayers.contains(player.getObjectId())) {
			ldPlayers.remove(player.getObjectId());
			player.getSKInfo().getBuff().endEffect(player);
			player.getSKInfo().setLDRank(0);
			updateRank(player);
		}
	}

	// update victims rank
	public void onKillSerialKiller(Player killer, Player victim) {
		if (!CustomConfig.SERIALKILLER_ENABLED)
			return;
		if (isHandledWorld(victim.getWorldId())) {
			SerialKiller info = victim.getSKInfo();
			if (killer.getLevel() - victim.getLevel() <= CustomConfig.SERIALKILLER_LEVEL_DIFF) {
				if (info.getVictims() > 0) {
					info.setVictims(info.getVictims() - 1);
					int rank = getKillerRank(info.getVictims());
					boolean isEnemyWorld = isEnemyWorld(victim);
					if (isEnemyWorld && info.getRank() == 3) { // old rank
						SM_SYSTEM_MESSAGE msg = killer.getRace() == Race.ASMODIANS
							? SM_SYSTEM_MESSAGE.STR_MSG_SLAYER_LIGHT_DEATH_TO_B(killer.getName(), victim.getName())
							: SM_SYSTEM_MESSAGE.STR_MSG_SLAYER_DARK_DEATH_TO_B(killer.getName(), victim.getName());
						PacketSendUtility.broadcastToMap(victim, msg);
					}
					if (info.getRank() != rank) {
						info.setRank(rank);
						if (!ldPlayers.contains(victim.getObjectId())) {
							victim.getSKInfo().getBuff().endEffect(victim);
							victim.getSKInfo().getBuff().applyEffect(victim, isEnemyWorld ? "KILLER" : "GUARD", victim.getRace(), rank);

							PacketSendUtility.sendPacket(victim, new SM_SERIAL_KILLER(isEnemyWorld ? 1 : 8, info.getRank()));
							PacketSendUtility.broadcastPacket(victim, new SM_SERIAL_KILLER(isEnemyWorld ? 6 : 9, victim));
						}
					}
					serialKillers.putIfAbsent(victim.getObjectId(), info);
				}
			}
		}
	}

	public void intruderScan(Player player) {
		int worldId = player.getWorldId();
		if (!isHandledWorld(worldId))
			return;
		if (getOrRemoveCooldown(player.getObjectId()) > 0) {
			return;
		}
		cooldowns.put(player.getObjectId(), System.currentTimeMillis() + 180 * 1000);
		PacketSendUtility.sendPacket(player, new SM_SERIAL_KILLER(getWorldKillers(player)));
	}

	private int getOrRemoveCooldown(int objectId) {
		if (cooldowns.containsKey(objectId)) {
			int estimated = (int) ((cooldowns.get(objectId) - System.currentTimeMillis()) / 1000);
			if (estimated > 0) {
				return estimated;
			} else {
				cooldowns.remove(objectId);
				return 0;
			}
		}
		return 0;
	}

	// update killers rank
	public void updateRank(Player killer, Player victim) {
		if (!CustomConfig.SERIALKILLER_ENABLED)
			return;
		if (isHandledWorld(killer.getWorldId())) {
			getOrRemoveCooldown(killer.getObjectId());
			SerialKiller info = killer.getSKInfo();
			if (killer.getLevel() - victim.getLevel() <= CustomConfig.SERIALKILLER_LEVEL_DIFF) {
				info.setVictims(info.getVictims() + 1);
				int rank = getKillerRank(info.getVictims());

				if (info.getRank() != rank) {
					boolean isEnemyWorld = isEnemyWorld(killer);

					info.setRank(rank);
					if (!ldPlayers.contains(killer.getObjectId())) {
						killer.getSKInfo().getBuff().applyEffect(killer, isEnemyWorld ? "KILLER" : "GUARD", killer.getRace(), rank);

						PacketSendUtility.sendPacket(killer, new SM_SERIAL_KILLER(isEnemyWorld ? 1 : 8, info.getRank()));
						PacketSendUtility.broadcastPacket(killer, new SM_SERIAL_KILLER(isEnemyWorld ? 6 : 9, killer));
					}
				}
				serialKillers.putIfAbsent(killer.getObjectId(), info);
			}
		}
	}

	private void updateRank(Player player) {
		if (!CustomConfig.SERIALKILLER_ENABLED)
			return;
		if (isHandledWorld(player.getWorldId())) {
			SerialKiller info = player.getSKInfo();
			boolean isEnemyWorld = isEnemyWorld(player);
			getOrRemoveCooldown(player.getObjectId());
			if (info.getRank() <= 0) {
				player.getSKInfo().getBuff().endEffect(player);
			} else {
				player.getSKInfo().getBuff().applyEffect(player, isEnemyWorld ? "KILLER" : "GUARD", player.getRace(), info.getRank());
			}

			PacketSendUtility.sendPacket(player, new SM_SERIAL_KILLER(isEnemyWorld ? 1 : 8, info.getRank()));
			PacketSendUtility.broadcastPacket(player, new SM_SERIAL_KILLER(isEnemyWorld ? 6 : 9, player));
		}
	}

	private int getKillerRank(int kills) {
		if (kills >= CustomConfig.KILLER_3ND_RANK_KILLS)
			return 3;
		if (kills >= CustomConfig.KILLER_2ND_RANK_KILLS)
			return 2;
		if (kills >= CustomConfig.KILLER_1ST_RANK_KILLS)
			return 1;
		return 0;
	}

	public boolean isRestrictPortal(Player killer) {
		return false;
	}

	public boolean isRestrictDynamicBindstone(Player killer) {
		return false;
	}

	public boolean isHandledWorld(int worldId) {
		return handledWorlds.containsKey(worldId);
	}

	public boolean isEnemyWorld(Player player) {
		Race race = handledWorlds.get(player.getWorldId());
		return race != null && race != player.getRace();
	}

	public void removeSerialKiller(int playerId) {
		SerialKiller sk = serialKillers.remove(playerId);
		ldPlayers.remove(playerId);
		if (sk != null) {
			sk.setVictims(0);
			sk.setRank(0);
			sk.setLDRank(0);
			Player player = World.getInstance().findPlayer(playerId);
			if (player != null)
				sk.getBuff().endEffect(player);
		}
	}

	public static SerialKillerService getInstance() {
		return SerialKillerService.SingletonHolder.instance;
	}

	private static class SingletonHolder {

		protected static final SerialKillerService instance = new SerialKillerService();
	}

}
