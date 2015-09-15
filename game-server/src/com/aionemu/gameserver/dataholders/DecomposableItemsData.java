package com.aionemu.gameserver.dataholders;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.List;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import javolution.util.FastTable;

import com.aionemu.gameserver.model.templates.item.DecomposableItemInfo;
import com.aionemu.gameserver.model.templates.item.ExtractedItemsCollection;
import com.aionemu.gameserver.model.templates.item.ResultedItem;

/**
 * @author antness
 */
@XmlRootElement(name = "decomposable_items")
@XmlAccessorType(XmlAccessType.FIELD)
public class DecomposableItemsData {

	@XmlElement(name = "decomposable")
	private List<DecomposableItemInfo> decomposableItemsTemplates;
	private TIntObjectHashMap<List<ExtractedItemsCollection>> decomposableItemsInfo = new TIntObjectHashMap<List<ExtractedItemsCollection>>();
	private TIntObjectHashMap<List<ResultedItem>> selectableDecomposables = new TIntObjectHashMap<List<ResultedItem>>();

	void afterUnmarshal(Unmarshaller u, Object parent) {
		decomposableItemsInfo.clear();
		for (DecomposableItemInfo template : decomposableItemsTemplates)
			if (template.isIsSelectable()) {
				selectableDecomposables.put(template.getItemId(), template.getItemsCollections().get(0).getItems());
			} else {
				decomposableItemsInfo.put(template.getItemId(), template.getItemsCollections());
			}
	}

	public int size() {
		return decomposableItemsInfo.size();
	}

	public List<ResultedItem> getSelectableItems(int itemId) {
		if (selectableDecomposables.contains(itemId)) {
			List<ResultedItem> result = new FastTable<ResultedItem>();
			result.addAll(selectableDecomposables.get(itemId));
			return result;
		} else
			return null;
	}

	public List<ExtractedItemsCollection> getInfoByItemId(int itemId) {
		return decomposableItemsInfo.get(itemId);
	}
}
