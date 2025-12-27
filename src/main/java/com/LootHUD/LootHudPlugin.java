package com.LootHUD;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
		name = "Loot HUD",
		description = "Displays loot notifications in an in-game overlay",
		tags = {"loot", "tracker", "overlay", "hud"}
)
public class LootHudPlugin extends Plugin
{
	@Inject
	private ItemManager itemManager;

	@Inject
	private LootHudConfig config;

	@Inject
	private LootHudOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private EventBus eventBus;

	// All loot entries (both individual and grouped)
	private final List<LootHudEntry> allEntries = new CopyOnWriteArrayList<>();

	// Running totals for each monster (for when grouping is ON)
	private final Map<String, RunningTotal> runningTotals = new HashMap<>();

	// Store individual kills separately for persistence
	private final Map<String, List<IndividualKill>> individualKills = new HashMap<>();

	private final Set<String> rareItemNamesCache = new HashSet<>();
	private final Set<String> ignoredItemNamesCache = new HashSet<>();

	private boolean overlayVisible = true;

	// Track the last processed event to avoid duplicates
	private String lastProcessedEventHash = "";
	private long lastProcessedEventTime = 0;

	@Provides
	LootHudConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LootHudConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.info("Loot HUD started!");
		overlayManager.add(overlay);
		keyManager.registerKeyListener(hotkeyListener);
		eventBus.register(this);
		updateRareItemNamesCache();
		updateIgnoredItemNamesCache();
	}

	@Override
	protected void shutDown()
	{
		log.info("Loot HUD stopped!");
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(hotkeyListener);
		eventBus.unregister(this);
		allEntries.clear();
		runningTotals.clear();
		individualKills.clear();
		rareItemNamesCache.clear();
		ignoredItemNamesCache.clear();
	}

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.toggleKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			overlayVisible = !overlayVisible;
		}
	};

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		log.info("Loot received from: {} (Type: {}, Amount: {}) - Items: {}",
				event.getName(), event.getType(), event.getAmount(), event.getItems());

		// Filter by source type if configured
		if (!shouldShowLoot(event.getType())) {
			return;
		}

		// Filter out ignored items
		List<ItemStack> filteredItems = filterIgnoredItems(new ArrayList<>(event.getItems()));

		if (filteredItems.isEmpty()) {
			log.debug("All items from {} were ignored, skipping display", event.getName());
			return;
		}

		// Calculate total value and check for rare items
		long totalValue = 0;
		boolean hasRareItem = false;
		List<ItemStack> sortedItems = new ArrayList<>(filteredItems);

		if (config.sortItemsByValue()) {
			sortedItems.sort((a, b) -> {
				int priceA = itemManager.getItemPrice(a.getId());
				int priceB = itemManager.getItemPrice(b.getId());
				return Integer.compare(priceB, priceA); // Descending
			});
		}

		for (ItemStack item : sortedItems)
		{
			int price = itemManager.getItemPrice(item.getId());
			if (price > 0) {
				totalValue += (long) price * item.getQuantity();
			}

			// Check if this item is in our rare items list by name
			if (!hasRareItem && !rareItemNamesCache.isEmpty()) {
				try {
					ItemComposition comp = itemManager.getItemComposition(item.getId());
					String itemName = comp.getName().toLowerCase().trim();

					// Check if the item name contains any of our rare item names
					for (String rareName : rareItemNamesCache) {
						if (itemName.contains(rareName)) {
							hasRareItem = true;
							log.debug("Rare item detected: {} (matches: {})", comp.getName(), rareName);
							break;
						}
					}
				} catch (Exception e) {
					log.warn("Error checking item name for ID: {}", item.getId(), e);
				}
			}
		}

		// Apply minimum value filter
		if (config.minValueToShow() > 0 && totalValue < config.minValueToShow()) {
			log.debug("Skipping loot from {}: value {} below minimum {}",
					event.getName(), totalValue, config.minValueToShow());
			return;
		}

		// Check if loot exceeds rare value threshold
		boolean exceedsRareValue = config.rareValueThreshold() > 0 && totalValue >= config.rareValueThreshold();
		boolean isRare = hasRareItem || exceedsRareValue;

		if (isRare) {
			log.info("RARE LOOT DETECTED from {}: {} gp (Has rare item: {}, Exceeds threshold: {})",
					event.getName(), totalValue, hasRareItem, exceedsRareValue);
		}

		// Determine expiration time
		Instant expirationTime;
		if (config.alwaysShowOverlay()) {
			// When "Always Show Overlay" is enabled, entries never expire
			expirationTime = Instant.MAX;
		} else {
			// Normal mode: use configured duration
			expirationTime = Instant.now().plusSeconds(config.displayDuration());
		}

		// Process the loot
		processLoot(event, sortedItems, totalValue, expirationTime, isRare);
	}

	private List<ItemStack> filterIgnoredItems(List<ItemStack> items) {
		if (ignoredItemNamesCache.isEmpty()) {
			return new ArrayList<>(items);
		}

		List<ItemStack> filtered = new ArrayList<>();
		for (ItemStack item : items) {
			try {
				ItemComposition comp = itemManager.getItemComposition(item.getId());
				String itemName = comp.getName().toLowerCase().trim();
				boolean ignore = false;

				// Check if this item should be ignored
				for (String ignoredName : ignoredItemNamesCache) {
					if (itemName.contains(ignoredName)) {
						ignore = true;
						log.debug("Ignoring item: {} (matches: {})", comp.getName(), ignoredName);
						break;
					}
				}

				if (!ignore) {
					filtered.add(item);
				}
			} catch (Exception e) {
				log.warn("Error checking item name for ID: {}", item.getId(), e);
				// If we can't check the name, include the item
				filtered.add(item);
			}
		}

		return filtered;
	}

	private void processLoot(LootReceived event, List<ItemStack> sortedItems, long totalValue, Instant expirationTime, boolean isRare)
	{
		// Create a hash of this event to avoid processing duplicates
		String eventHash = createEventHash(event, sortedItems, totalValue);
		long currentTime = System.currentTimeMillis();

		// Check if this is a duplicate event (same hash within 1 second)
		if (eventHash.equals(lastProcessedEventHash) && (currentTime - lastProcessedEventTime) < 1000) {
			log.debug("Skipping duplicate event for {}", event.getName());
			return;
		}

		lastProcessedEventHash = eventHash;
		lastProcessedEventTime = currentTime;

		String monsterKey = event.getName() + "|" + event.getType();

		// Always store the individual kill for persistence
		storeIndividualKill(event, sortedItems, totalValue, expirationTime, isRare, monsterKey);

		// Always update running totals (for when grouping is ON)
		updateRunningTotal(event, sortedItems, totalValue, expirationTime, isRare, monsterKey);

		// Now update the display based on current mode
		if (config.groupLoot()) {
			updateGroupedDisplay(event, monsterKey, expirationTime);
		} else {
			updateIndividualDisplay(event, monsterKey, expirationTime);
		}

		// Limit the total number of entries
		while (allEntries.size() > config.maxNotifications() * 2) {
			allEntries.remove(allEntries.size() - 1);
		}

		// Clean up expired data
		cleanupExpiredData();
	}

	private void storeIndividualKill(LootReceived event, List<ItemStack> sortedItems, long totalValue,
									 Instant expirationTime, boolean isRare, String monsterKey) {
		// Create an individual kill record
		IndividualKill kill = new IndividualKill(
				event.getName(),
				event.getType(),
				new ArrayList<>(sortedItems),
				event.getAmount(),
				totalValue,
				isRare,
				expirationTime
		);

		// Get or create the list for this monster
		List<IndividualKill> kills = individualKills.computeIfAbsent(monsterKey, k -> new ArrayList<>());

		// Add the new kill at the beginning (so newest is first)
		kills.add(0, kill);

		// Limit the number of stored individual kills per monster
		// We store more than maxNotifications to allow for multiple kills from same monster
		while (kills.size() > config.maxNotifications() * 3) {
			kills.remove(kills.size() - 1); // Remove oldest (last in list)
		}
	}

	private void updateRunningTotal(LootReceived event, List<ItemStack> sortedItems, long totalValue,
									Instant expirationTime, boolean isRare, String monsterKey) {
		// Update the running total for this monster
		RunningTotal runningTotal = runningTotals.get(monsterKey);
		if (runningTotal == null) {
			runningTotal = new RunningTotal(
					event.getName(),
					event.getType(),
					new ArrayList<>(sortedItems),
					event.getAmount(),
					totalValue,
					isRare,
					expirationTime
			);
			runningTotals.put(monsterKey, runningTotal);
		} else {
			runningTotal.addKill(sortedItems, event.getAmount(), totalValue, isRare, expirationTime);
		}
	}

	private void updateGroupedDisplay(LootReceived event, String monsterKey, Instant expirationTime) {
		// Get the running total for this monster
		RunningTotal runningTotal = runningTotals.get(monsterKey);
		if (runningTotal == null) {
			log.warn("No running total found for {} when in grouped mode", monsterKey);
			return;
		}

		// Remove any existing individual entries for this monster
		allEntries.removeIf(entry ->
				entry.getSourceName().equals(event.getName()) &&
						entry.getType() == event.getType() &&
						!entry.isGrouped()
		);

		// Remove any existing grouped entry for this monster
		allEntries.removeIf(entry ->
				entry.getSourceName().equals(event.getName()) &&
						entry.getType() == event.getType() &&
						entry.isGrouped()
		);

		// Create a new grouped entry
		LootHudEntry groupedEntry = new LootHudEntry(
				event.getName(),
				runningTotal.getItems(),
				runningTotal.getKillCount(),
				expirationTime,
				runningTotal.getTotalValue(),
				event.getType(),
				runningTotal.isRare(),
				true
		);

		// Add at the beginning (top of display)
		allEntries.add(0, groupedEntry);
	}

	private void updateIndividualDisplay(LootReceived event, String monsterKey, Instant expirationTime) {
		// First, collect ALL individual kills from ALL monsters
		List<IndividualKill> allKills = new ArrayList<>();

		// Process each monster's kills
		for (Map.Entry<String, List<IndividualKill>> entry : individualKills.entrySet()) {
			List<IndividualKill> monsterKills = entry.getValue();

			for (IndividualKill kill : monsterKills) {
				// Skip if expired (unless in always show mode)
				if (!config.alwaysShowOverlay() && Instant.now().isAfter(kill.getExpirationTime())) {
					continue;
				}

				allKills.add(kill);
			}
		}

		// Sort all kills by expiration time (newest first)
		allKills.sort((a, b) -> b.getExpirationTime().compareTo(a.getExpirationTime()));

		// Remove any grouped entries (we're in individual mode)
		allEntries.removeIf(entry -> entry.isGrouped());

		// Now rebuild all individual entries
		allEntries.clear();

		// Add all kills as individual entries (newest first)
		for (IndividualKill kill : allKills) {
			LootHudEntry individualEntry = new LootHudEntry(
					kill.getSourceName(),
					kill.getItems(),
					kill.getKillCount(),
					kill.getExpirationTime(),
					kill.getTotalValue(),
					kill.getType(),
					kill.isRare(),
					false
			);

			allEntries.add(individualEntry);
		}

		// Limit to maxNotifications * 2 to prevent excessive growth
		while (allEntries.size() > config.maxNotifications() * 2) {
			allEntries.remove(allEntries.size() - 1);
		}
	}

	private String createEventHash(LootReceived event, List<ItemStack> items, long totalValue) {
		StringBuilder hash = new StringBuilder();
		hash.append(event.getName()).append("|");
		hash.append(event.getType()).append("|");
		hash.append(event.getAmount()).append("|");
		hash.append(totalValue).append("|");

		for (ItemStack item : items) {
			hash.append(item.getId()).append(":").append(item.getQuantity()).append(";");
		}

		return hash.toString();
	}

	private void cleanupExpiredData() {
		Instant now = Instant.now();

		// Clean up expired individual kills
		if (!config.alwaysShowOverlay()) {
			for (List<IndividualKill> kills : individualKills.values()) {
				kills.removeIf(kill -> now.isAfter(kill.getExpirationTime()));
			}

			// Remove empty monster entries
			individualKills.entrySet().removeIf(entry -> entry.getValue().isEmpty());

			// Clean up expired running totals
			runningTotals.entrySet().removeIf(entry -> now.isAfter(entry.getValue().getExpirationTime()));
		}
	}

	private void updateRareItemNamesCache()
	{
		rareItemNamesCache.clear();
		String rareItemNames = config.rareItemNames();
		if (rareItemNames != null && !rareItemNames.trim().isEmpty()) {
			String[] names = rareItemNames.split(",");
			for (String name : names) {
				name = name.trim().toLowerCase();
				if (!name.isEmpty()) {
					rareItemNamesCache.add(name);
					log.debug("Added rare item name: {}", name);
				}
			}
		}
		log.info("Loaded {} rare item names from config", rareItemNamesCache.size());
	}

	private void updateIgnoredItemNamesCache()
	{
		ignoredItemNamesCache.clear();
		String ignoredItemNames = config.ignoredItemNames();
		if (ignoredItemNames != null && !ignoredItemNames.trim().isEmpty()) {
			String[] names = ignoredItemNames.split(",");
			for (String name : names) {
				name = name.trim().toLowerCase();
				if (!name.isEmpty()) {
					ignoredItemNamesCache.add(name);
					log.debug("Added ignored item name: {}", name);
				}
			}
		}
		log.info("Loaded {} ignored item names from config", ignoredItemNamesCache.size());
	}

	// Handle config changes
	@Subscribe
	private void onConfigChanged(net.runelite.client.events.ConfigChanged event)
	{
		if (!event.getGroup().equals("loothud")) {
			return;
		}

		if (event.getKey().equals("rareItemNames")) {
			updateRareItemNamesCache();
		} else if (event.getKey().equals("ignoredItemNames")) {
			updateIgnoredItemNamesCache();
			// Rebuild all entries with new ignore filter
			rebuildAllEntriesWithFilter();
		} else if (event.getKey().equals("resetGroupOnLogout") && config.resetGroupOnLogout()) {
			runningTotals.clear();
			individualKills.clear();
			log.info("Cleared all loot data due to config change");
		} else if (event.getKey().equals("groupLoot")) {
			// When grouping mode changes, rebuild the display from stored data
			rebuildDisplay();
		}
	}

	private void rebuildAllEntriesWithFilter() {
		// Clear current entries and rebuild from stored data
		allEntries.clear();
		runningTotals.clear();

		// We need to re-filter all stored individual kills
		Map<String, List<IndividualKill>> newIndividualKills = new HashMap<>();

		for (Map.Entry<String, List<IndividualKill>> entry : individualKills.entrySet()) {
			String monsterKey = entry.getKey();
			List<IndividualKill> oldKills = entry.getValue();
			List<IndividualKill> newKills = new ArrayList<>();

			for (IndividualKill oldKill : oldKills) {
				// Filter ignored items from this kill
				List<ItemStack> filteredItems = filterIgnoredItems(oldKill.getItems());

				if (filteredItems.isEmpty()) {
					// All items were ignored, skip this kill entirely
					continue;
				}

				// Recalculate total value for filtered items
				long filteredTotalValue = 0;
				boolean filteredIsRare = false;

				for (ItemStack item : filteredItems) {
					int price = itemManager.getItemPrice(item.getId());
					if (price > 0) {
						filteredTotalValue += (long) price * item.getQuantity();
					}

					// Check if rare
					if (!filteredIsRare && !rareItemNamesCache.isEmpty()) {
						try {
							ItemComposition comp = itemManager.getItemComposition(item.getId());
							String itemName = comp.getName().toLowerCase().trim();
							for (String rareName : rareItemNamesCache) {
								if (itemName.contains(rareName)) {
									filteredIsRare = true;
									break;
								}
							}
						} catch (Exception e) {
							log.warn("Error re-checking rare status for item ID: {}", item.getId(), e);
						}
					}
				}

				// Check value threshold
				boolean exceedsRareValue = config.rareValueThreshold() > 0 && filteredTotalValue >= config.rareValueThreshold();
				filteredIsRare = filteredIsRare || exceedsRareValue;

				// Create new kill with filtered items
				IndividualKill newKill = new IndividualKill(
						oldKill.getSourceName(),
						oldKill.getType(),
						filteredItems,
						oldKill.getKillCount(),
						filteredTotalValue,
						filteredIsRare,
						oldKill.getExpirationTime()
				);

				newKills.add(newKill);
			}

			if (!newKills.isEmpty()) {
				newIndividualKills.put(monsterKey, newKills);
			}
		}

		// Replace old individual kills with filtered ones
		individualKills.clear();
		individualKills.putAll(newIndividualKills);

		// Rebuild display
		rebuildDisplay();
	}

	private void rebuildDisplay()
	{
		// Clear current display
		allEntries.clear();

		if (config.groupLoot()) {
			// Switch to grouping mode - show running totals
			List<RunningTotal> sortedTotals = new ArrayList<>(runningTotals.values());

			// Sort by expiration time (newest first)
			sortedTotals.sort((a, b) -> b.getExpirationTime().compareTo(a.getExpirationTime()));

			// Add to display in order
			for (RunningTotal total : sortedTotals) {
				// Skip if expired (unless in always show mode)
				if (!config.alwaysShowOverlay() && Instant.now().isAfter(total.getExpirationTime())) {
					continue;
				}

				LootHudEntry groupedEntry = new LootHudEntry(
						total.getSourceName(),
						total.getItems(),
						total.getKillCount(),
						total.getExpirationTime(),
						total.getTotalValue(),
						total.getType(),
						total.isRare(),
						true
				);

				allEntries.add(groupedEntry);
			}
		} else {
			// Switch to individual mode - show all individual kills
			List<IndividualKill> allKills = new ArrayList<>();

			// Collect all individual kills from all monsters
			for (Map.Entry<String, List<IndividualKill>> entry : individualKills.entrySet()) {
				List<IndividualKill> kills = entry.getValue();

				for (IndividualKill kill : kills) {
					// Skip if expired (unless in always show mode)
					if (!config.alwaysShowOverlay() && Instant.now().isAfter(kill.getExpirationTime())) {
						continue;
					}

					allKills.add(kill);
				}
			}

			// Sort by expiration time (newest first)
			allKills.sort((a, b) -> b.getExpirationTime().compareTo(a.getExpirationTime()));

			// Add to display in order
			for (IndividualKill kill : allKills) {
				LootHudEntry individualEntry = new LootHudEntry(
						kill.getSourceName(),
						kill.getItems(),
						kill.getKillCount(),
						kill.getExpirationTime(),
						kill.getTotalValue(),
						kill.getType(),
						kill.isRare(),
						false
				);

				allEntries.add(individualEntry);
			}

			log.info("Switched to ungrouped mode - showing {} individual kills", allEntries.size());
		}
	}

	private boolean shouldShowLoot(net.runelite.http.api.loottracker.LootRecordType type)
	{
		switch (type)
		{
			case NPC:
				return config.includeNPCKills();
			case PLAYER:
				return config.includePlayerKills();
			case PICKPOCKET:
				return config.includePickpocket();
			case EVENT:
				return config.includeEvents();
			default:
				return true;
		}
	}

	List<LootHudEntry> getRecentLoot()
	{
		// Clean up expired entries if not in "always show" mode
		if (!config.alwaysShowOverlay()) {
			Instant now = Instant.now();
			allEntries.removeIf(entry -> now.isAfter(entry.getExpirationTime()));
		}

		// Return up to maxNotifications entries
		List<LootHudEntry> result = new ArrayList<>();
		int count = 0;
		for (LootHudEntry entry : allEntries) {
			if (config.minValueToShow() > 0 && entry.getTotalValue() < config.minValueToShow()) {
				continue;
			}
			if (count >= config.maxNotifications()) {
				break;
			}
			result.add(entry);
			count++;
		}
		return result;
	}

	boolean isOverlayVisible()
	{
		return overlayVisible;
	}

	// Class to track running totals for each monster
	private static class RunningTotal {
		private final String sourceName;
		private final net.runelite.http.api.loottracker.LootRecordType type;
		private List<ItemStack> items;
		private int killCount;
		private long totalValue;
		private boolean isRare;
		private Instant expirationTime;

		public RunningTotal(String sourceName, net.runelite.http.api.loottracker.LootRecordType type,
							List<ItemStack> items, int killCount, long totalValue,
							boolean isRare, Instant expirationTime) {
			this.sourceName = sourceName;
			this.type = type;
			this.items = new ArrayList<>(items);
			this.killCount = killCount;
			this.totalValue = totalValue;
			this.isRare = isRare;
			this.expirationTime = expirationTime;
		}

		public void addKill(List<ItemStack> newItems, int newKillCount, long newTotalValue,
							boolean newIsRare, Instant newExpirationTime) {
			// Merge items
			Map<Integer, ItemStack> itemMap = new HashMap<>();

			// Add existing items
			for (ItemStack item : this.items) {
				itemMap.put(item.getId(), new ItemStack(item.getId(), item.getQuantity()));
			}

			// Add new items
			for (ItemStack newItem : newItems) {
				int itemId = newItem.getId();
				ItemStack existing = itemMap.get(itemId);
				if (existing != null) {
					itemMap.put(itemId, new ItemStack(itemId, existing.getQuantity() + newItem.getQuantity()));
				} else {
					itemMap.put(itemId, new ItemStack(itemId, newItem.getQuantity()));
				}
			}

			// Convert back to list
			this.items = new ArrayList<>(itemMap.values());

			// Update other fields
			this.killCount += newKillCount;
			this.totalValue += newTotalValue;
			this.isRare = this.isRare || newIsRare;

			// Use the latest expiration time
			if (newExpirationTime.isAfter(this.expirationTime)) {
				this.expirationTime = newExpirationTime;
			}
		}

		// Getters
		public String getSourceName() { return sourceName; }
		public net.runelite.http.api.loottracker.LootRecordType getType() { return type; }
		public List<ItemStack> getItems() { return new ArrayList<>(items); } // Return copy
		public int getKillCount() { return killCount; }
		public long getTotalValue() { return totalValue; }
		public boolean isRare() { return isRare; }
		public Instant getExpirationTime() { return expirationTime; }
	}

	// Class to store individual kills for persistence
	private static class IndividualKill {
		private final String sourceName;
		private final net.runelite.http.api.loottracker.LootRecordType type;
		private final List<ItemStack> items;
		private final int killCount;
		private final long totalValue;
		private final boolean isRare;
		private final Instant expirationTime;

		public IndividualKill(String sourceName, net.runelite.http.api.loottracker.LootRecordType type,
							  List<ItemStack> items, int killCount, long totalValue,
							  boolean isRare, Instant expirationTime) {
			this.sourceName = sourceName;
			this.type = type;
			this.items = new ArrayList<>(items);
			this.killCount = killCount;
			this.totalValue = totalValue;
			this.isRare = isRare;
			this.expirationTime = expirationTime;
		}

		// Getters
		public String getSourceName() { return sourceName; }
		public net.runelite.http.api.loottracker.LootRecordType getType() { return type; }
		public List<ItemStack> getItems() { return new ArrayList<>(items); }
		public int getKillCount() { return killCount; }
		public long getTotalValue() { return totalValue; }
		public boolean isRare() { return isRare; }
		public Instant getExpirationTime() { return expirationTime; }
	}
}