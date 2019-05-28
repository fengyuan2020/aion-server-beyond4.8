package com.aionemu.gameserver.model.house;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.configs.administration.AdminConfig;
import com.aionemu.gameserver.configs.main.HousingConfig;
import com.aionemu.gameserver.controllers.HouseController;
import com.aionemu.gameserver.dao.HouseScriptsDAO;
import com.aionemu.gameserver.dao.HousesDAO;
import com.aionemu.gameserver.dao.PlayerRegisteredItemsDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.gameobjects.HouseDecoration;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.Persistable;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.HouseOwnerState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerScripts;
import com.aionemu.gameserver.model.templates.housing.Building;
import com.aionemu.gameserver.model.templates.housing.BuildingType;
import com.aionemu.gameserver.model.templates.housing.HouseAddress;
import com.aionemu.gameserver.model.templates.housing.HouseType;
import com.aionemu.gameserver.model.templates.housing.HousingLand;
import com.aionemu.gameserver.model.templates.housing.PartType;
import com.aionemu.gameserver.model.templates.housing.Sale;
import com.aionemu.gameserver.model.templates.spawns.HouseSpawn;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.model.templates.spawns.SpawnType;
import com.aionemu.gameserver.model.templates.zone.ZoneClassName;
import com.aionemu.gameserver.services.HousingBidService;
import com.aionemu.gameserver.services.HousingService;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.spawnengine.VisibleObjectSpawner;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldPosition;
import com.aionemu.gameserver.world.knownlist.PlayerAwareKnownList;
import com.aionemu.gameserver.world.zone.ZoneInstance;
import com.aionemu.gameserver.world.zone.ZoneService;

/**
 * @author Rolandas
 */
public class House extends VisibleObject implements Persistable {

	private static final Logger log = LoggerFactory.getLogger(House.class);
	private HousingLand land;
	private HouseAddress address;
	private Building building;
	private String name;
	private int playerObjectId;
	private Timestamp acquiredTime;
	private int permissions;
	private HouseStatus status;
	private boolean feePaid = true;
	private Timestamp nextPay;
	private Timestamp sellStarted;
	private Map<SpawnType, Npc> spawns = new EnumMap<>(SpawnType.class);
	private HouseRegistry houseRegistry;
	private byte houseOwnerStates = HouseOwnerState.SINGLE_HOUSE.getId();
	private PlayerScripts playerScripts;
	private PersistentState persistentState;
	private String signNotice;

	public House(Building building, HouseAddress address, int instanceId) {
		this(IDFactory.getInstance().nextId(), building, address, instanceId);
	}

	public House(int objectId, Building building, HouseAddress address, int instanceId) {
		super(objectId, new HouseController(), null, null, null);
		getController().setOwner(this);
		this.address = address;
		this.building = building;
		this.name = "HOUSE_" + address.getId();
		setKnownlist(new PlayerAwareKnownList(this));
		setPersistentState(PersistentState.UPDATED);
		getRegistry();
	}

	@Override
	public HouseController getController() {
		return (HouseController) super.getController();
	}

	private void putDefaultParts() {
		for (PartType partType : PartType.values()) {
			Integer partId = building.getDefaultPartId(partType);
			if (partId == null)
				continue;
			for (int line = partType.getStartLineNr(); line <= partType.getEndLineNr(); line++) {
				int room = partType.getEndLineNr() - line;
				HouseDecoration decor = new HouseDecoration(0, partId, room);
				getRegistry().putDefaultPart(decor, room);
			}
		}
	}

	/**
	 * TODO: improve this, now it's inefficient during startup
	 */
	public HousingLand getLand() {
		if (land == null) {
			for (HousingLand housingland : DataManager.HOUSE_DATA.getLands()) {
				for (HouseAddress houseAddress : housingland.getAddresses()) {
					if (this.getAddress().getId() == houseAddress.getId()) {
						this.land = housingland;
						break;
					}
				}
			}
		}
		return this.land;
	}

	@Override
	public String getName() {
		return name;
	}

	public HouseAddress getAddress() {
		return address;
	}

	public Building getBuilding() {
		return building;
	}

	public void setBuilding(Building building) {
		this.building = building;
	}

	public synchronized void spawn(int instanceId) {
		playerScripts = DAOManager.getDAO(HouseScriptsDAO.class).getPlayerScripts(getObjectId());

		if (playerObjectId > 0 && (status == HouseStatus.ACTIVE || status == HouseStatus.SELL_WAIT)) {
			DAOManager.getDAO(PlayerRegisteredItemsDAO.class).loadRegistry(playerObjectId);
		}

		fixBuildingStates();

		// Studios are brought into world already, skip them
		if (getPosition() == null || !getPosition().isSpawned()) {
			WorldPosition position = World.getInstance().createPosition(address.getMapId(), address.getX(), address.getY(), address.getZ(), (byte) 0,
				instanceId);
			this.setPosition(position);
			SpawnEngine.bringIntoWorld(this);
		}

		List<HouseSpawn> templates = DataManager.HOUSE_NPCS_DATA.getSpawnsByAddress(getAddress().getId());
		if (templates == null) {
			Collection<ZoneInstance> zones = ZoneService.getInstance().getZoneInstancesByWorldId(getAddress().getMapId()).values();
			String msg = null;
			for (ZoneInstance zone : zones) {
				if (zone.getZoneTemplate().getZoneType() != ZoneClassName.SUB || zone.getZoneTemplate().getPriority() > 20)
					continue;
				if (zone.isInsideCordinate(getAddress().getX(), getAddress().getY(), getAddress().getZ())) {
					msg = "zone=" + zone.getZoneTemplate().getXmlName();
					break;
				}
			}
			if (msg == null)
				msg = "address=" + this.getAddress().getId() + "; map=" + this.getAddress().getMapId();
			msg += "; x=" + getAddress().getX() + ", y=" + getAddress().getY() + ", z=" + getAddress().getZ();
			log.warn("Missing npcs for house: " + msg);
			return;
		}

		int creatorId = getAddress().getId();
		String masterName = "";
		if (playerObjectId != 0) {
			masterName = PlayerService.getPlayerName(playerObjectId);
			if (masterName == null) {
				revokeOwner();
				log.warn("Owner (Player ID: " + playerObjectId + ") of house " + getAddress() + " doesn't exist anymore, revoked ownership.");
			}
		}

		for (HouseSpawn spawn : templates) {
			Npc npc;
			if (spawn.getType() == SpawnType.MANAGER) {
				SpawnTemplate t = SpawnEngine.newSingleTimeSpawn(getAddress().getMapId(), getLand().getManagerNpcId(), spawn.getX(), spawn.getY(),
					spawn.getZ(), spawn.getH());
				npc = VisibleObjectSpawner.spawnHouseNpc(t, getInstanceId(), this, masterName);
			} else if (spawn.getType() == SpawnType.TELEPORT) {
				SpawnTemplate t = SpawnEngine.newSingleTimeSpawn(getAddress().getMapId(), getLand().getTeleportNpcId(), spawn.getX(), spawn.getY(),
					spawn.getZ(), spawn.getH());
				npc = VisibleObjectSpawner.spawnHouseNpc(t, getInstanceId(), this, masterName);
			} else if (spawn.getType() == SpawnType.SIGN) {
				// Signs do not have master name displayed, but have creatorId
				SpawnTemplate t = SpawnEngine.newSingleTimeSpawn(getAddress().getMapId(), getCurrentSignNpcId(), spawn.getX(), spawn.getY(), spawn.getZ(),
					spawn.getH(), creatorId);
				npc = (Npc) SpawnEngine.spawnObject(t, getInstanceId());
			} else {
				log.warn("Unhandled spawn type " + spawn.getType());
				continue;
			}
			if (npc == null)
				log.warn("Invalid " + spawn.getType() + " npc ID for house " + getAddress());
			else if (spawns.putIfAbsent(spawn.getType(), npc) != null)
				log.warn("Duplicate " + spawn.getType() + " spawn for house " + getAddress());
		}
	}

	@Override
	public float getVisibleDistance() {
		return HousingConfig.VISIBILITY_DISTANCE;
	}

	public int getOwnerId() {
		return playerObjectId;
	}

	public void setOwnerId(int playerObjectId) {
		if (this.playerObjectId != playerObjectId) {
			this.playerObjectId = playerObjectId;
			signNotice = null;
		}
		fixBuildingStates();
	}

	public Timestamp getAcquiredTime() {
		return acquiredTime;
	}

	public void setAcquiredTime(Timestamp acquiredTime) {
		this.acquiredTime = acquiredTime;
	}

	public int getPermissions() {
		if (playerObjectId == 0) {
			setDoorState(status == HouseStatus.SELL_WAIT ? HousePermissions.DOOR_OPENED_ALL : HousePermissions.DOOR_CLOSED);
			setNoticeState(HousePermissions.NOT_SET);
		} else {
			if (permissions == 0) {
				setNoticeState(HousePermissions.SHOW_OWNER);
				if (getBuilding().getType() == BuildingType.PERSONAL_FIELD) {
					setDoorState(HousePermissions.DOOR_CLOSED);
				}
			}
		}
		return permissions;
	}

	public void setPermissions(int permissions) {
		this.permissions = permissions;
	}

	public HousePermissions getDoorState() {
		return HousePermissions.getDoorState(getPermissions());
	}

	public void setDoorState(HousePermissions doorState) {
		permissions = HousePermissions.setDoorState(permissions, doorState);
	}

	public HousePermissions getNoticeState() {
		return HousePermissions.getNoticeState(getPermissions());
	}

	public void setNoticeState(HousePermissions noticeState) {
		permissions = HousePermissions.setNoticeState(permissions, noticeState);
	}

	public synchronized HouseStatus getStatus() {
		return status;
	}

	public synchronized void setStatus(HouseStatus status) {
		if (this.status != status) {
			// fix invalid status from DB, or automatically remove sign from not auctioned houses
			if (this.playerObjectId == 0 && status == HouseStatus.ACTIVE) {
				status = HouseStatus.NOSALE;
			}
			this.status = status;
			fixBuildingStates();

			if ((status != HouseStatus.INACTIVE || getSellStarted() != null) && spawns.get(SpawnType.SIGN) != null) {
				Npc sign = spawns.get(SpawnType.SIGN);
				int oldNpcId = sign.getNpcId();
				int newNpcId = getCurrentSignNpcId();

				if (newNpcId != oldNpcId) {
					SpawnTemplate t = sign.getSpawn();
					sign.getController().delete();
					t = SpawnEngine.newSingleTimeSpawn(t.getWorldId(), newNpcId, t.getX(), t.getY(), t.getZ(), t.getHeading());
					sign = (Npc) SpawnEngine.spawnObject(t, getInstanceId());
					spawns.put(SpawnType.SIGN, sign);
				}
			}
		}
	}

	public boolean isFeePaid() {
		return feePaid;
	}

	public void setFeePaid(boolean feePaid) {
		this.feePaid = feePaid;
	}

	public Timestamp getNextPay() {
		return nextPay;
	}

	public void setNextPay(Timestamp nextPay) {
		Timestamp result = null;
		if (nextPay != null) { // round to midnight
			result = new Timestamp(DateUtils.round(nextPay, Calendar.DAY_OF_MONTH).getTime());
		}
		this.nextPay = result;
	}

	public Timestamp getSellStarted() {
		return sellStarted;
	}

	public void setSellStarted(Timestamp sellStarted) {
		this.sellStarted = sellStarted;
	}

	public boolean isInGracePeriod() {
		return playerObjectId > 0 && status != HouseStatus.INACTIVE && HousingService.getInstance().searchPlayerHouses(playerObjectId).size() > 1
			&& sellStarted != null && sellStarted.getTime() <= HousingBidService.getInstance().getAuctionStartTime();
	}

	public synchronized Npc getButler() {
		return spawns.get(SpawnType.MANAGER);
	}

	public Race getPlayerRace() {
		if (getButler() == null)
			return Race.NONE;
		if (getButler().getTribe() == TribeClass.GENERAL)
			return Race.ELYOS;
		return Race.ASMODIANS;
	}

	public synchronized Npc getRelationshipCrystal() {
		return spawns.get(SpawnType.TELEPORT);
	}

	public synchronized Npc getCurrentSign() {
		return spawns.get(SpawnType.SIGN);
	}

	/**
	 * Do not use directly !!! It's for instance destroy of studios only (studios and their instance get reused, but house npcs are respawned)
	 */
	public synchronized void despawnNpcs() {
		for (Npc npc : spawns.values()) {
			npc.getController().delete();
		}
		spawns.clear();
	}

	public int getCurrentSignNpcId() {
		int npcId = getLand().getWaitingSignNpcId(); // bidding closed
		if (status == HouseStatus.NOSALE)
			npcId = getLand().getNosaleSignNpcId(); // invisible npc
		else if (status == HouseStatus.SELL_WAIT) {
			if (HousingBidService.getInstance().isBiddingAllowed())
				npcId = getLand().getSaleSignNpcId(); // bidding open
		} else if (playerObjectId != 0) {
			if (status == HouseStatus.ACTIVE)
				npcId = getLand().getHomeSignNpcId(); // resident information
		}
		return npcId;
	}

	public synchronized boolean revokeOwner() {
		if (playerObjectId == 0)
			return false;
		getRegistry().despawnObjects();
		if (playerScripts == null)
			playerScripts = DAOManager.getDAO(HouseScriptsDAO.class).getPlayerScripts(getObjectId());
		playerScripts.removeAll();
		if (getBuilding().getType() == BuildingType.PERSONAL_INS) {
			HousingService.getInstance().removeStudio(playerObjectId);
			DAOManager.getDAO(HousesDAO.class).deleteHouse(playerObjectId);
			return true;
		}
		houseRegistry = null;
		acquiredTime = null;
		sellStarted = null;
		nextPay = null;
		feePaid = true;

		Building defaultBuilding = getLand().getDefaultBuilding();
		setOwnerId(0);
		if (defaultBuilding != building)
			HousingService.getInstance().switchHouseBuilding(this, defaultBuilding.getId());
		if (getStatus() != HouseStatus.SELL_WAIT)
			setStatus(HouseStatus.NOSALE);
		save();
		return true;
	}

	public HouseRegistry getRegistry() {
		if (houseRegistry == null) {
			houseRegistry = new HouseRegistry(this);
			putDefaultParts();
		}
		return houseRegistry;
	}

	public synchronized void reloadHouseRegistry() {
		houseRegistry = null;
		getRegistry();
		if (playerObjectId != 0)
			DAOManager.getDAO(PlayerRegisteredItemsDAO.class).loadRegistry(playerObjectId);
	}

	public HouseDecoration getRenderPart(PartType partType, int room) {
		return getRegistry().getRenderPart(partType, room);
	}

	public HouseDecoration getDefaultPart(PartType partType, int room) {
		return getRegistry().getDefaultPartByType(partType, room);
	}

	public PlayerScripts getPlayerScripts() {
		return playerScripts;
	}

	public HouseType getHouseType() {
		return HouseType.fromValue(getBuilding().getSize());
	}

	public synchronized void save() {
		DAOManager.getDAO(HousesDAO.class).storeHouse(this);
		// save registry if needed
		if (houseRegistry != null)
			this.houseRegistry.save();
	}

	@Override
	public PersistentState getPersistentState() {
		return persistentState;
	}

	@Override
	public void setPersistentState(PersistentState persistentState) {
		this.persistentState = persistentState;
	}

	public byte getHouseOwnerStates() {
		return houseOwnerStates;
	}

	public void fixBuildingStates() {
		houseOwnerStates = HouseOwnerState.SINGLE_HOUSE.getId();
		if (playerObjectId != 0) {
			houseOwnerStates |= HouseOwnerState.HAS_OWNER.getId();
			if (status == HouseStatus.ACTIVE) {
				houseOwnerStates |= HouseOwnerState.BIDDING_ALLOWED.getId();
				houseOwnerStates &= ~HouseOwnerState.SINGLE_HOUSE.getId();
			}
		} else if (status == HouseStatus.SELL_WAIT) {
			houseOwnerStates = HouseOwnerState.SELLING_HOUSE.getId();
		}
	}

	public String getSignNotice() {
		return signNotice;
	}

	public void setSignNotice(String notice) {
		signNotice = notice;
	}

	public int getLevelRestrict() {
		return land != null ? land.getSaleOptions().getMinLevel() : 10;
	}

	public boolean canEnter(Player player) {
		if (getOwnerId() != player.getObjectId() && !player.hasAccess(AdminConfig.HOUSE_ENTER_ALL)) {
			if (getLevelRestrict() > player.getLevel())
				return false;
			switch (getDoorState()) {
				case DOOR_CLOSED:
					return false;
				case DOOR_OPENED_FRIENDS:
					if (player.getFriendList().getFriend(getOwnerId()) == null && (player.getLegion() == null || !player.getLegion().isMember(getOwnerId())))
						return false;
			}
		}
		return true;
	}

	public final long getDefaultAuctionPrice() {
		Sale saleOptions = getLand().getSaleOptions();
		switch (getHouseType()) {
			case HOUSE:
				if (HousingConfig.HOUSE_MIN_BID > 0)
					return HousingConfig.HOUSE_MIN_BID;
				break;
			case MANSION:
				if (HousingConfig.MANSION_MIN_BID > 0)
					return HousingConfig.MANSION_MIN_BID;
				break;
			case ESTATE:
				if (HousingConfig.ESTATE_MIN_BID > 0)
					return HousingConfig.ESTATE_MIN_BID;
				break;
			case PALACE:
				if (HousingConfig.PALACE_MIN_BID > 0)
					return HousingConfig.PALACE_MIN_BID;
				break;
		}
		return saleOptions.getGoldPrice();
	}

	/**
	 * @return Calculated heading for a player inside looking towards the wall where butler, relationship crystal and the door are located.
	 */
	public byte getTeleportHeading() {
		return PositionUtil.getHeadingTowards(getX(), getY(), getRelationshipCrystal().getSpawn().getX(), getRelationshipCrystal().getSpawn().getY());
	}
}
