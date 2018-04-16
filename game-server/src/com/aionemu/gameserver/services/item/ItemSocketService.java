package com.aionemu.gameserver.services.item;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.controllers.observer.StartMovingListener;
import com.aionemu.gameserver.dao.ItemStoneListDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ManaStone;
import com.aionemu.gameserver.model.items.storage.Storage;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.trade.PricesService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.audit.AuditLogger;

/**
 * @author ATracer
 */
public class ItemSocketService {

	private static final Logger log = LoggerFactory.getLogger(ItemSocketService.class);

	public static ManaStone addManaStone(Item item, int itemId) {
		if (item == null)
			return null;

		int maxSlots = item.getSockets(false);
		Set<ManaStone> manaStones = item.getItemStones();
		if (manaStones.size() >= maxSlots)
			return null;

		ItemGroup manastoneCategory = DataManager.ITEM_DATA.getItemTemplate(itemId).getItemGroup();
		int specialSlotCount = item.getItemTemplate().getSpecialSlots();
		if (manastoneCategory == ItemGroup.SPECIAL_MANASTONE && specialSlotCount == 0)
			return null;

		int specialSlotsOccupied = 0;
		int normalSlotsOccupied = 0;
		HashSet<Integer> allSlots = new HashSet<>();
		for (ManaStone ms : manaStones) {
			ItemGroup category = DataManager.ITEM_DATA.getItemTemplate(ms.getItemId()).getItemGroup();
			if (category == ItemGroup.SPECIAL_MANASTONE)
				specialSlotsOccupied++;
			else
				normalSlotsOccupied++;
			allSlots.add(ms.getSlot());
		}

		if ((manastoneCategory == ItemGroup.SPECIAL_MANASTONE && specialSlotsOccupied >= specialSlotCount)
			|| (manastoneCategory == ItemGroup.MANASTONE && normalSlotsOccupied >= (maxSlots - specialSlotCount)))
			return null;

		int start = manastoneCategory == ItemGroup.SPECIAL_MANASTONE ? 0 : specialSlotCount;
		int end = manastoneCategory == ItemGroup.SPECIAL_MANASTONE ? specialSlotCount : maxSlots;
		int nextSlot = start;
		boolean slotFound = false;
		for (; nextSlot < end; nextSlot++) {
			if (!allSlots.contains(nextSlot)) {
				slotFound = true;
				break;
			}
		}
		if (!slotFound)
			return null;

		ManaStone stone = new ManaStone(item.getObjectId(), itemId, nextSlot, PersistentState.NEW);
		manaStones.add(stone);

		return stone;
	}

	public static ManaStone addManaStone(Item item, int itemId, int slotId) {
		if (item == null)
			return null;

		Set<ManaStone> manaStones = item.getItemStones();
		if (manaStones.size() >= Item.MAX_BASIC_STONES)
			return null;

		ManaStone stone = new ManaStone(item.getObjectId(), itemId, slotId, PersistentState.NEW);
		manaStones.add(stone);
		return stone;
	}

	public static void copyFusionStones(Item source, Item target) {
		if (source.hasManaStones()) {
			for (ManaStone manaStone : source.getItemStones()) {
				target.getFusionStones().add(new ManaStone(target.getObjectId(), manaStone.getItemId(), manaStone.getSlot(), PersistentState.NEW));
			}
		}
	}

	public static ManaStone addFusionStone(Item item, int itemId) {
		if (item == null)
			return null;

		int maxSlots = item.getSockets(true);
		Set<ManaStone> manaStones = item.getFusionStones();
		if (manaStones.size() >= maxSlots)
			return null;

		ItemGroup manastoneCategory = DataManager.ITEM_DATA.getItemTemplate(itemId).getItemGroup();
		int specialSlotCount = item.getFusionedItemTemplate().getSpecialSlots();
		if (manastoneCategory == ItemGroup.SPECIAL_MANASTONE && specialSlotCount == 0)
			return null;

		int specialSlotsOccupied = 0;
		int normalSlotsOccupied = 0;
		HashSet<Integer> allSlots = new HashSet<>();
		for (ManaStone ms : manaStones) {
			ItemGroup category = DataManager.ITEM_DATA.getItemTemplate(ms.getItemId()).getItemGroup();
			if (category == ItemGroup.SPECIAL_MANASTONE)
				specialSlotsOccupied++;
			else
				normalSlotsOccupied++;
			allSlots.add(ms.getSlot());
		}

		if ((manastoneCategory == ItemGroup.SPECIAL_MANASTONE && specialSlotsOccupied >= specialSlotCount)
			|| (manastoneCategory == ItemGroup.MANASTONE && normalSlotsOccupied >= (maxSlots - specialSlotCount)))
			return null;

		int start = manastoneCategory == ItemGroup.SPECIAL_MANASTONE ? 0 : specialSlotCount;
		int end = manastoneCategory == ItemGroup.SPECIAL_MANASTONE ? specialSlotCount : maxSlots;
		int nextSlot = start;
		boolean slotFound = false;
		for (; nextSlot < end; nextSlot++) {
			if (!allSlots.contains(nextSlot)) {
				slotFound = true;
				break;
			}
		}
		if (!slotFound)
			return null;

		ManaStone stone = new ManaStone(item.getObjectId(), itemId, nextSlot, PersistentState.NEW);
		manaStones.add(stone);
		return stone;
	}

	public static ManaStone addFusionStone(Item item, int itemId, int slotId) {
		if (item == null)
			return null;

		Set<ManaStone> fusionStones = item.getFusionStones();
		if (fusionStones.size() > item.getSockets(true))
			return null;

		ManaStone stone = new ManaStone(item.getObjectId(), itemId, slotId, PersistentState.NEW);
		fusionStones.add(stone);
		return stone;
	}

	public static void removeManastone(Player player, int itemObjId, int slotNum) {
		Storage inventory = player.getInventory();
		Item item = inventory.getItemByObjId(itemObjId);
		long price = PricesService.getPriceForService(650, player.getRace());

		if (player.getInventory().getKinah() < price) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_NOT_ENOUGH_GOLD(item.getL10n()));
			return;
		}

		if (item == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_NO_TARGET_ITEM());
			return;
		}

		if (!item.hasManaStones()) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_NO_OPTION_TO_REMOVE(item.getL10n()));
			log.warn("Item stone list is empty");
			return;
		}

		Set<ManaStone> itemStones = item.getItemStones();

		boolean found = false;
		for (ManaStone ms : itemStones) {
			if (ms.getSlot() == slotNum) {
				ms.setPersistentState(PersistentState.DELETED);
				DAOManager.getDAO(ItemStoneListDAO.class).storeManaStones(Collections.singleton(ms));
				itemStones.remove(ms);
				found = true;
				break;
			}
		}
		if (!found) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_INVALID_OPTION_SLOT_NUMBER(item.getL10n()));
			log.warn("Invalid slot ID at manastone removal!");
			return;
		}
		player.getInventory().decreaseKinah(price);
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_SUCCEED(item.getL10n()));
		ItemPacketService.updateItemAfterInfoChange(player, item);
	}

	public static void removeFusionstone(Player player, int itemObjId, int slotNum) {

		Storage inventory = player.getInventory();
		Item item = inventory.getItemByObjId(itemObjId);
		long price = PricesService.getPriceForService(650, player.getRace());
		if (player.getInventory().getKinah() < price) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_NOT_ENOUGH_GOLD(item.getL10n()));
			return;
		}

		if (item == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_NO_TARGET_ITEM());
			return;
		}

		if (!item.hasFusionStones()) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_NO_OPTION_TO_REMOVE(item.getL10n()));
			log.warn("Item stone list is empty");
			return;
		}

		Set<ManaStone> itemStones = item.getFusionStones();

		boolean found = false;
		for (ManaStone ms : itemStones) {
			if (ms.getSlot() == slotNum) {
				ms.setPersistentState(PersistentState.DELETED);
				DAOManager.getDAO(ItemStoneListDAO.class).storeFusionStone(Collections.singleton(ms));
				itemStones.remove(ms);
				found = true;
				break;
			}
		}
		if (!found) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_INVALID_OPTION_SLOT_NUMBER(item.getL10n()));
			log.warn("Invalid slot ID at manastone removal!");
			return;
		}
		player.getInventory().decreaseKinah(price);
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_REMOVE_ITEM_OPTION_SUCCEED(item.getL10n()));
		ItemPacketService.updateItemAfterInfoChange(player, item);
	}

	public static void removeAllManastone(Player player, Item item) {
		if (item == null) {
			log.warn("Item not found during manastone remove");
			return;
		}

		if (!item.hasManaStones()) {
			return;
		}

		Set<ManaStone> itemStones = item.getItemStones();
		for (ManaStone ms : itemStones) {
			ms.setPersistentState(PersistentState.DELETED);
		}
		DAOManager.getDAO(ItemStoneListDAO.class).storeManaStones(itemStones);
		itemStones.clear();

		ItemPacketService.updateItemAfterInfoChange(player, item);
	}

	public static void socketGodstone(Player player, Item weapon, int stoneId) {
		if (weapon == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GIVE_ITEM_PROC_NO_TARGET_ITEM());
			return;
		}

		if (!weapon.canSocketGodstone()) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GIVE_ITEM_PROC_NOT_PROC_GIVABLE_ITEM(weapon.getL10n()));
			AuditLogger.log(player, "tried to insert godstone in not compatible item " + weapon.getItemId());
			return;
		}

		final StartMovingListener move = new StartMovingListener() {

			@Override
			public void moved() {
				super.moved();
				player.getObserveController().removeObserver(this);
				player.getController().cancelUseItem();
				PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1402238, weapon.getL10n()));

			}
		};

		player.getObserveController().attach(move);

		Item godstone = player.getInventory().getItemByObjId(stoneId);
		if (godstone == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GIVE_ITEM_PROC_NO_PROC_GIVE_ITEM());
			return;
		}

		ItemTemplate itemTemplate = godstone.getItemTemplate();
		if (itemTemplate.getGodstoneInfo() == null) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GIVE_ITEM_PROC_NO_PROC_GIVE_ITEM());
			return;
		}

		PacketSendUtility.broadcastPacketAndReceive(player,
			new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), stoneId, itemTemplate.getTemplateId(), 2000, 0, 0));

		player.getController().addTask(TaskId.ITEM_USE, ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				player.getObserveController().removeObserver(move);

				PacketSendUtility.broadcastPacketAndReceive(player,
					new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), stoneId, itemTemplate.getTemplateId(), 0, 1, 0));

				if (!player.getInventory().decreaseByObjectId(stoneId, 1))
					return;

				weapon.addGodStone(itemTemplate.getTemplateId());
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GIVE_ITEM_PROC_ENCHANTED_TARGET_ITEM(weapon.getL10n()));

				ItemPacketService.updateItemAfterInfoChange(player, weapon);
			}
		}, 2000));
	}
}
