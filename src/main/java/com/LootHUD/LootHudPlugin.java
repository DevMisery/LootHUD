package com.LootHUD;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
		tags = {"loot", "tracker", "overlay", "hud", "drops"}
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

	private final List<LootHudEntry> allEntries = new CopyOnWriteArrayList<>();
	private final Map<String, RunningTotal> runningTotals = new HashMap<>();
	private final Map<String, List<IndividualKill>> individualKills = new HashMap<>();
	private final Set<String> rareItemNamesCache = new HashSet<>();
	private final Set<String> ignoredItemNamesCache = new HashSet<>();
	private final Set<String> ignoredSourcesCache = new HashSet<>();

	private boolean overlayVisible = true;
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
		updateIgnoredSourcesCache();
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
		ignoredSourcesCache.clear();
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

		if (!shouldShowLoot(event.getType())) {
			return;
		}

		if (isSourceIgnored(event.getName())) {
			log.debug("Skipping loot from ignored source: {}", event.getName());
			return;
		}

		List<ItemStack> filteredItems = filterIgnoredItems(new ArrayList<>(event.getItems()));

		if (filteredItems.isEmpty()) {
			log.debug("All items from {} were ignored, skipping display", event.getName());
			return;
		}

		long totalValue = 0;
		boolean hasRareItem = false;
		List<ItemStack> sortedItems = new ArrayList<>(filteredItems);

		if (config.sortItemsByValue()) {
			sortedItems.sort((a, b) -> {
				int priceA = itemManager.getItemPrice(a.getId());
				int priceB = itemManager.getItemPrice(b.getId());
				return Integer.compare(priceB, priceA);
			});
		}

		for (ItemStack item : sortedItems)
		{
			int price = itemManager.getItemPrice(item.getId());
			if (price > 0) {
				totalValue += (long) price * item.getQuantity();
			}

			if (!hasRareItem && !rareItemNamesCache.isEmpty()) {
				try {
					ItemComposition comp = itemManager.getItemComposition(item.getId());
					String itemName = comp.getName().toLowerCase().trim();

					for (String rarePattern : rareItemNamesCache) {
						if (WildcardMatcher.matches(rarePattern, itemName)) {
							hasRareItem = true;
							log.debug("Rare item detected: {} (matches pattern: {})", comp.getName(), rarePattern);
							break;
						}
					}
				} catch (Exception e) {
					log.warn("Error checking item name for ID: {}", item.getId(), e);
				}
			}
		}

		if (config.minValueToShow() > 0 && totalValue < config.minValueToShow()) {
			log.debug("Skipping loot from {}: value {} below minimum {}",
					event.getName(), totalValue, config.minValueToShow());
			return;
		}

		boolean exceedsRareValue = config.rareValueThreshold() > 0 && totalValue >= config.rareValueThreshold();
		boolean isRare = hasRareItem || exceedsRareValue;

		if (isRare) {
			log.info("RARE LOOT DETECTED from {}: {} gp (Has rare item: {}, Exceeds threshold: {})",
					event.getName(), totalValue, hasRareItem, exceedsRareValue);
		}

		Instant expirationTime;
		if (config.alwaysShowOverlay()) {
			expirationTime = Instant.MAX;
		} else {
			expirationTime = Instant.now().plusSeconds(config.displayDuration());
		}

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

				for (String ignoredPattern : ignoredItemNamesCache) {
					if (WildcardMatcher.matches(ignoredPattern, itemName)) {
						ignore = true;
						log.debug("Ignoring item: {} (matches pattern: {})", comp.getName(), ignoredPattern);
						break;
					}
				}

				if (!ignore) {
					filtered.add(item);
				}
			} catch (Exception e) {
				log.warn("Error checking item name for ID: {}", item.getId(), e);
				filtered.add(item);
			}
		}

		return filtered;
	}

	private void processLoot(LootReceived event, List<ItemStack> sortedItems, long totalValue, Instant expirationTime, boolean isRare)
	{
		String eventHash = createEventHash(event, sortedItems, totalValue);
		long currentTime = System.currentTimeMillis();

		if (eventHash.equals(lastProcessedEventHash) && (currentTime - lastProcessedEventTime) < 1000) {
			log.debug("Skipping duplicate event for {}", event.getName());
			return;
		}

		lastProcessedEventHash = eventHash;
		lastProcessedEventTime = currentTime;

		String monsterKey = event.getName() + "|" + event.getType();

		storeIndividualKill(event, sortedItems, totalValue, expirationTime, isRare, monsterKey);
		updateRunningTotal(event, sortedItems, totalValue, expirationTime, isRare, monsterKey);

		if (config.groupLoot()) {
			updateGroupedDisplay(event, monsterKey, expirationTime);
		} else {
			updateIndividualDisplay(event, monsterKey, expirationTime);
		}

		while (allEntries.size() > config.maxNotifications() * 2) {
			allEntries.remove(allEntries.size() - 1);
		}

		cleanupExpiredData();
	}

	private void storeIndividualKill(LootReceived event, List<ItemStack> sortedItems, long totalValue,
									 Instant expirationTime, boolean isRare, String monsterKey) {
		IndividualKill kill = new IndividualKill(
				event.getName(),
				event.getType(),
				new ArrayList<>(sortedItems),
				event.getAmount(),
				totalValue,
				isRare,
				expirationTime
		);

		List<IndividualKill> kills = individualKills.computeIfAbsent(monsterKey, k -> new ArrayList<>());
		kills.add(0, kill);

		while (kills.size() > config.maxNotifications() * 3) {
			kills.remove(kills.size() - 1);
		}
	}

	private void updateRunningTotal(LootReceived event, List<ItemStack> sortedItems, long totalValue,
									Instant expirationTime, boolean isRare, String monsterKey) {
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
		RunningTotal runningTotal = runningTotals.get(monsterKey);
		if (runningTotal == null) {
			log.warn("No running total found for {} when in grouped mode", monsterKey);
			return;
		}

		allEntries.removeIf(entry ->
				entry.getSourceName().equals(event.getName()) &&
						entry.getType() == event.getType() &&
						!entry.isGrouped()
		);

		allEntries.removeIf(entry ->
				entry.getSourceName().equals(event.getName()) &&
						entry.getType() == event.getType() &&
						entry.isGrouped()
		);

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

		allEntries.add(0, groupedEntry);
	}

	private void updateIndividualDisplay(LootReceived event, String monsterKey, Instant expirationTime) {
		List<IndividualKill> allKills = new ArrayList<>();

		for (Map.Entry<String, List<IndividualKill>> entry : individualKills.entrySet()) {
			List<IndividualKill> monsterKills = entry.getValue();

			for (IndividualKill kill : monsterKills) {
				if (!config.alwaysShowOverlay() && Instant.now().isAfter(kill.getExpirationTime())) {
					continue;
				}

				allKills.add(kill);
			}
		}

		allKills.sort((a, b) -> b.getExpirationTime().compareTo(a.getExpirationTime()));

		allEntries.removeIf(entry -> entry.isGrouped());
		allEntries.clear();

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

		if (!config.alwaysShowOverlay()) {
			for (List<IndividualKill> kills : individualKills.values()) {
				kills.removeIf(kill -> now.isAfter(kill.getExpirationTime()));
			}

			individualKills.entrySet().removeIf(entry -> entry.getValue().isEmpty());
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
					log.debug("Added rare item pattern: {}", name);
				}
			}
		}
		log.info("Loaded {} rare item patterns from config", rareItemNamesCache.size());
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
					log.debug("Added ignored item pattern: {}", name);
				}
			}
		}
		log.info("Loaded {} ignored item patterns from config", ignoredItemNamesCache.size());
	}

	private void updateIgnoredSourcesCache()
	{
		ignoredSourcesCache.clear();
		String ignoredSources = config.ignoredSources();
		if (ignoredSources != null && !ignoredSources.trim().isEmpty()) {
			String[] names = ignoredSources.split(",");
			for (String name : names) {
				name = name.trim().toLowerCase();
				if (!name.isEmpty()) {
					ignoredSourcesCache.add(name);
					log.debug("Added ignored source pattern: {}", name);
				}
			}
		}
		log.info("Loaded {} ignored source patterns from config", ignoredSourcesCache.size());
	}

	@Subscribe
	private void onConfigChanged(net.runelite.client.events.ConfigChanged event)
	{
		if (!event.getGroup().equals("loothud")) {
			return;
		}

		switch (event.getKey()) {
			case "rareItemNames":
				updateRareItemNamesCache();
				break;
			case "ignoredItemNames":
				updateIgnoredItemNamesCache();
				rebuildAllEntriesWithFilter();
				break;
			case "ignoredSources":
				updateIgnoredSourcesCache();
				cleanupEntriesFromIgnoredSources();
				break;
			case "resetGroupOnLogout":
				if (config.resetGroupOnLogout()) {
					runningTotals.clear();
					individualKills.clear();
					log.info("Cleared all loot data due to config change");
				}
				break;
			case "groupLoot":
				rebuildDisplay();
				break;
		}
	}

	private void cleanupEntriesFromIgnoredSources()
	{
		if (ignoredSourcesCache.isEmpty()) {
			return;
		}

		allEntries.removeIf(entry -> isSourceIgnored(entry.getSourceName()));

		for (Iterator<Map.Entry<String, List<IndividualKill>>> it = individualKills.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, List<IndividualKill>> entry = it.next();
			String sourceName = entry.getKey().split("\\|")[0];
			if (isSourceIgnored(sourceName)) {
				it.remove();
			}
		}

		for (Iterator<Map.Entry<String, RunningTotal>> it = runningTotals.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, RunningTotal> entry = it.next();
			String sourceName = entry.getKey().split("\\|")[0];
			if (isSourceIgnored(sourceName)) {
				it.remove();
			}
		}

		log.info("Cleaned up entries from ignored sources");
	}

	private void rebuildAllEntriesWithFilter() {
		allEntries.clear();
		runningTotals.clear();

		Map<String, List<IndividualKill>> newIndividualKills = new HashMap<>();

		for (Map.Entry<String, List<IndividualKill>> entry : individualKills.entrySet()) {
			String monsterKey = entry.getKey();
			List<IndividualKill> oldKills = entry.getValue();
			List<IndividualKill> newKills = new ArrayList<>();

			for (IndividualKill oldKill : oldKills) {
				List<ItemStack> filteredItems = filterIgnoredItems(oldKill.getItems());

				if (filteredItems.isEmpty()) {
					continue;
				}

				long filteredTotalValue = 0;
				boolean filteredIsRare = false;

				for (ItemStack item : filteredItems) {
					int price = itemManager.getItemPrice(item.getId());
					if (price > 0) {
						filteredTotalValue += (long) price * item.getQuantity();
					}

					if (!filteredIsRare) {
						try {
							ItemComposition comp = itemManager.getItemComposition(item.getId());
							String itemName = comp.getName().toLowerCase().trim();
							for (String rarePattern : rareItemNamesCache) {
								if (WildcardMatcher.matches(rarePattern, itemName)) {
									filteredIsRare = true;
									break;
								}
							}
						} catch (Exception e) {
							log.warn("Error re-checking rare status for item ID: {}", item.getId(), e);
						}
					}
				}

				boolean exceedsRareValue = config.rareValueThreshold() > 0 && filteredTotalValue >= config.rareValueThreshold();
				filteredIsRare = filteredIsRare || exceedsRareValue;

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

		individualKills.clear();
		individualKills.putAll(newIndividualKills);

		rebuildDisplay();
	}

	private void rebuildDisplay()
	{
		allEntries.clear();

		if (config.groupLoot()) {
			List<RunningTotal> sortedTotals = new ArrayList<>(runningTotals.values());
			sortedTotals.sort((a, b) -> b.getExpirationTime().compareTo(a.getExpirationTime()));

			for (RunningTotal total : sortedTotals) {
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
			List<IndividualKill> allKills = new ArrayList<>();

			for (Map.Entry<String, List<IndividualKill>> entry : individualKills.entrySet()) {
				List<IndividualKill> kills = entry.getValue();

				for (IndividualKill kill : kills) {
					if (!config.alwaysShowOverlay() && Instant.now().isAfter(kill.getExpirationTime())) {
						continue;
					}

					allKills.add(kill);
				}
			}

			allKills.sort((a, b) -> b.getExpirationTime().compareTo(a.getExpirationTime()));

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

	private boolean isSourceIgnored(String sourceName)
	{
		if (ignoredSourcesCache.isEmpty()) {
			return false;
		}

		return WildcardMatcher.anyMatches(ignoredSourcesCache, sourceName);
	}

	List<LootHudEntry> getRecentLoot()
	{
		List<LootHudEntry> visibleEntries = new ArrayList<>();

		for (LootHudEntry entry : allEntries) {
			if (!config.alwaysShowOverlay() && entry.isExpired()) {
				continue;
			}

			if (config.minValueToShow() > 0 && entry.getTotalValue() < config.minValueToShow()) {
				continue;
			}

			// Update fade-out animation if enabled
			if (config.fadeOutAnimation() && !config.alwaysShowOverlay()) {
				long fadeDurationMillis = config.fadeOutDuration() * 1000L;
				entry.updateFadeAnimation(fadeDurationMillis);
			}

			visibleEntries.add(entry);
		}

		// Sort by expiration time (newest first)
		visibleEntries.sort((a, b) -> b.getExpirationTime().compareTo(a.getExpirationTime()));

		// Limit to max notifications
		int limit = Math.min(config.maxNotifications(), visibleEntries.size());
		return visibleEntries.subList(0, limit);
	}

	boolean isOverlayVisible()
	{
		return overlayVisible;
	}

	/**
	 * Gets the color for an item based on its value and configured thresholds.
	 * @param itemValue The value of the item
	 * @return The appropriate color for this item value
	 */
	java.awt.Color getItemValueColor(long itemValue)
	{
		if (itemValue >= config.valueThreshold5()) {
			return config.valueColor5();
		} else if (itemValue >= config.valueThreshold4()) {
			return config.valueColor4();
		} else if (itemValue >= config.valueThreshold3()) {
			return config.valueColor3();
		} else if (itemValue >= config.valueThreshold2()) {
			return config.valueColor2();
		} else if (itemValue >= config.valueThreshold1()) {
			return config.valueColor1();
		}
		return config.itemNameColor();
	}

	/**
	 * Gets the overlay color based on total value and configured thresholds.
	 * @param totalValue The total value of the loot entry
	 * @return The appropriate overlay color for this total value
	 */
	java.awt.Color getOverlayValueColor(long totalValue)
	{
		if (totalValue >= config.valueThreshold5()) {
			return config.overlayColor5();
		} else if (totalValue >= config.valueThreshold4()) {
			return config.overlayColor4();
		} else if (totalValue >= config.valueThreshold3()) {
			return config.overlayColor3();
		} else if (totalValue >= config.valueThreshold2()) {
			return config.overlayColor2();
		} else if (totalValue >= config.valueThreshold1()) {
			return config.overlayColor1();
		}
		return config.backgroundColor();
	}

	/**
	 * Gets the header overlay color based on total value and configured thresholds.
	 * @param totalValue The total value of the loot entry
	 * @return The appropriate header overlay color for this total value
	 */
	java.awt.Color getHeaderValueColor(long totalValue)
	{
		// For header, use a slightly darker version of the overlay color
		java.awt.Color baseColor = getOverlayValueColor(totalValue);
		if (baseColor.equals(config.backgroundColor())) {
			return config.headerBackgroundColor();
		}

		// Darken the color by reducing RGB values
		return new java.awt.Color(
				Math.max(0, baseColor.getRed() - 40),
				Math.max(0, baseColor.getGreen() - 40),
				Math.max(0, baseColor.getBlue() - 40),
				baseColor.getAlpha()
		);
	}

	// Add this getter method to expose the cache to the overlay
	Set<String> getRareItemNamesCache()
	{
		return rareItemNamesCache;
	}

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
			Map<Integer, ItemStack> itemMap = new HashMap<>();

			for (ItemStack item : this.items) {
				itemMap.put(item.getId(), new ItemStack(item.getId(), item.getQuantity()));
			}

			for (ItemStack newItem : newItems) {
				int itemId = newItem.getId();
				ItemStack existing = itemMap.get(itemId);
				if (existing != null) {
					itemMap.put(itemId, new ItemStack(itemId, existing.getQuantity() + newItem.getQuantity()));
				} else {
					itemMap.put(itemId, new ItemStack(itemId, newItem.getQuantity()));
				}
			}

			this.items = new ArrayList<>(itemMap.values());
			this.killCount += newKillCount;
			this.totalValue += newTotalValue;
			this.isRare = this.isRare || newIsRare;

			if (newExpirationTime.isAfter(this.expirationTime)) {
				this.expirationTime = newExpirationTime;
			}
		}

		public String getSourceName() { return sourceName; }
		public net.runelite.http.api.loottracker.LootRecordType getType() { return type; }
		public List<ItemStack> getItems() { return new ArrayList<>(items); }
		public int getKillCount() { return killCount; }
		public long getTotalValue() { return totalValue; }
		public boolean isRare() { return isRare; }
		public Instant getExpirationTime() { return expirationTime; }
	}

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

		public String getSourceName() { return sourceName; }
		public net.runelite.http.api.loottracker.LootRecordType getType() { return type; }
		public List<ItemStack> getItems() { return new ArrayList<>(items); }
		public int getKillCount() { return killCount; }
		public long getTotalValue() { return totalValue; }
		public boolean isRare() { return isRare; }
		public Instant getExpirationTime() { return expirationTime; }
	}
}