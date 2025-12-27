package com.LootHUD;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("loothud")
public interface LootHudConfig extends Config
{
	// ========== SECTION DEFINITIONS ==========
	@ConfigSection(
			name = "General",
			description = "Basic overlay settings",
			position = 0
	)
	String generalSection = "general";

	@ConfigSection(
			name = "Appearance",
			description = "Colors, transparency, and borders",
			position = 1
	)
	String appearanceSection = "appearance";

	@ConfigSection(
			name = "Display",
			description = "How loot entries are displayed",
			position = 2
	)
	String displaySection = "display";

	@ConfigSection(
			name = "Icons",
			description = "Item icon display settings",
			position = 3
	)
	String iconsSection = "icons";

	@ConfigSection(
			name = "Highlight Effects",
			description = "Rare item highlight settings",
			position = 4
	)
	String highlightSection = "highlight";

	@ConfigSection(
			name = "Filtering",
			description = "Filter what loot is shown",
			position = 5
	)
	String filterSection = "filters";

	@ConfigSection(
			name = "Grouping",
			description = "Group multiple kills from same source",
			position = 6
	)
	String groupingSection = "grouping";

	// ========== GENERAL SECTION ==========
	@ConfigItem(
			keyName = "toggleKeybind",
			name = "Toggle keybind",
			description = "Key to show/hide the loot overlay",
			position = 0,
			section = generalSection
	)
	default Keybind toggleKeybind()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "allowResizing",
			name = "Allow resizing",
			description = "Allow dragging edges to resize overlay",
			position = 1,
			section = generalSection
	)
	default boolean allowResizing()
	{
		return true;
	}

	// ========== APPEARANCE SECTION ==========
	@ConfigItem(
			keyName = "backgroundColor",
			name = "Background color",
			description = "Color for loot entry backgrounds",
			position = 0,
			section = appearanceSection
	)
	default java.awt.Color backgroundColor()
	{
		return new java.awt.Color(0, 0, 0);
	}

	@ConfigItem(
			keyName = "backgroundAlpha",
			name = "Background transparency",
			description = "Transparency level for background (0=invisible, 255=opaque)",
			position = 1,
			section = appearanceSection
	)
	@Range(min = 0, max = 255)
	default int backgroundAlpha()
	{
		return 180;
	}

	@ConfigItem(
			keyName = "borderColor",
			name = "Border color",
			description = "Color for loot entry borders",
			position = 2,
			section = appearanceSection
	)
	default java.awt.Color borderColor()
	{
		return new java.awt.Color(100, 100, 100);
	}

	@ConfigItem(
			keyName = "borderAlpha",
			name = "Border transparency",
			description = "Transparency level for borders (0=invisible, 255=opaque)",
			position = 3,
			section = appearanceSection
	)
	@Range(min = 0, max = 255)
	default int borderAlpha()
	{
		return 150;
	}

	// ========== DISPLAY SECTION ==========
	@ConfigItem(
			keyName = "displayDuration",
			name = "Display time",
			description = "How long each loot notification stays visible (seconds)",
			position = 0,
			section = displaySection
	)
	@Range(min = 1, max = 60)
	default int displayDuration()
	{
		return 10;
	}

	@ConfigItem(
			keyName = "alwaysShowOverlay",
			name = "Always show overlay",
			description = "Keep overlay visible even when no new loot is received",
			position = 1,
			section = displaySection
	)
	default boolean alwaysShowOverlay()
	{
		return false;
	}

	@ConfigItem(
			keyName = "maxNotifications",
			name = "Max entries",
			description = "Maximum number of loot entries to show at once",
			position = 2,
			section = displaySection
	)
	@Range(min = 1, max = 10)
	default int maxNotifications()
	{
		return 3;
	}

	@ConfigItem(
			keyName = "fadeOlderEntries",
			name = "Fade older entries",
			description = "Gradually fade older entries for smooth transitions",
			position = 3,
			section = displaySection
	)
	default boolean fadeOlderEntries()
	{
		return false;
	}

	@ConfigItem(
			keyName = "showTotalValue",
			name = "Show total value",
			description = "Display total GE value of each loot pile",
			position = 4,
			section = displaySection
	)
	default boolean showTotalValue()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showLootTypeIcon",
			name = "Show source type icon",
			description = "Show small icon for loot source (NPC, player, etc.)",
			position = 5,
			section = displaySection
	)
	default boolean showLootTypeIcon()
	{
		return false;
	}

	// ========== ICONS SECTION ==========
	@ConfigItem(
			keyName = "showItemIcons",
			name = "Show item icons",
			description = "Display item icons in loot entries",
			position = 0,
			section = iconsSection
	)
	default boolean showItemIcons()
	{
		return true;
	}

	@ConfigItem(
			keyName = "maxIconsPerEntry",
			name = "Max icons per entry",
			description = "Maximum number of item icons to show per loot entry",
			position = 1,
			section = iconsSection
	)
	@Range(min = 1, max = 28)
	default int maxIconsPerEntry()
	{
		return 12;
	}

	@ConfigItem(
			keyName = "iconsPerRow",
			name = "Icons per row",
			description = "Number of item icons per row",
			position = 2,
			section = iconsSection
	)
	@Range(min = 1, max = 8)
	default int iconsPerRow()
	{
		return 4;
	}

	@ConfigItem(
			keyName = "sortItemsByValue",
			name = "Sort by value",
			description = "Show most valuable items first",
			position = 3,
			section = iconsSection
	)
	default boolean sortItemsByValue()
	{
		return true;
	}

	// ========== HIGHLIGHT EFFECTS SECTION ==========
	@ConfigItem(
			keyName = "rareItemHighlight",
			name = "Highlight style",
			description = "How to highlight rare item drops",
			position = 0,
			section = highlightSection
	)
	default RareItemHighlight rareItemHighlight()
	{
		return RareItemHighlight.OFF;
	}

	@ConfigItem(
			keyName = "rareItemNames",
			name = "Rare item list",
			description = "Comma-separated list of item names to highlight<br>Example: Twisted bow, Scythe of vitur, Tumeken's shadow",
			position = 1,
			section = highlightSection
	)
	default String rareItemNames()
	{
		return "Twisted bow, Scythe of vitur, Tumeken's shadow, Torva full helm, Masori body";
	}

	@ConfigItem(
			keyName = "rareValueThreshold",
			name = "Value threshold",
			description = "Minimum loot value to trigger highlight (GP)",
			position = 2,
			section = highlightSection
	)
	@Range(min = 0, max = 100000000)
	default int rareValueThreshold()
	{
		return 1000000;
	}

	@ConfigItem(
			keyName = "staticHighlightColor",
			name = "Highlight color",
			description = "Color for highlighting rare items",
			position = 3,
			section = highlightSection
	)
	default java.awt.Color staticHighlightColor()
	{
		return new java.awt.Color(255, 215, 0); // Gold color
	}

	@ConfigItem(
			keyName = "staticHighlightAlpha",
			name = "Highlight transparency",
			description = "Transparency for highlight color (0-255)",
			position = 4,
			section = highlightSection
	)
	@Range(min = 0, max = 255)
	default int staticHighlightAlpha()
	{
		return 220;
	}

	// Rainbow effect settings
	@ConfigItem(
			keyName = "rainbowAnimationSpeed",
			name = "Rainbow speed",
			description = "Speed of rainbow color cycling",
			position = 5,
			section = highlightSection
	)
	@Range(min = 1, max = 10)
	default int rainbowAnimationSpeed()
	{
		return 5;
	}

	@ConfigItem(
			keyName = "rainbowAlpha",
			name = "Rainbow transparency",
			description = "Transparency for rainbow effect (0-255)",
			position = 6,
			section = highlightSection
	)
	@Range(min = 0, max = 255)
	default int rainbowAlpha()
	{
		return 200;
	}

	// Pulse effect settings
	@ConfigItem(
			keyName = "pulseAnimationSpeed",
			name = "Pulse speed",
			description = "Speed of pulsing animation",
			position = 7,
			section = highlightSection
	)
	@Range(min = 1, max = 10)
	default int pulseAnimationSpeed()
	{
		return 5;
	}

	@ConfigItem(
			keyName = "pulseAlphaRange",
			name = "Pulse intensity",
			description = "How much transparency changes during pulse",
			position = 8,
			section = highlightSection
	)
	@Range(min = 0, max = 255)
	default int pulseAlphaRange()
	{
		return 80;
	}

	// ========== FILTERING SECTION ==========
	@ConfigItem(
			keyName = "includeNPCKills",
			name = "NPC kills",
			description = "Show loot from killing NPCs",
			position = 0,
			section = filterSection
	)
	default boolean includeNPCKills()
	{
		return true;
	}

	@ConfigItem(
			keyName = "includePlayerKills",
			name = "Player kills",
			description = "Show loot from killing players",
			position = 1,
			section = filterSection
	)
	default boolean includePlayerKills()
	{
		return true;
	}

	@ConfigItem(
			keyName = "includePickpocket",
			name = "Pickpocketing",
			description = "Show loot from pickpocketing",
			position = 2,
			section = filterSection
	)
	default boolean includePickpocket()
	{
		return true;
	}

	@ConfigItem(
			keyName = "includeEvents",
			name = "Events/minigames",
			description = "Show loot from events, chests, and minigames",
			position = 3,
			section = filterSection
	)
	default boolean includeEvents()
	{
		return true;
	}

	@ConfigItem(
			keyName = "minValueToShow",
			name = "Minimum value",
			description = "Only show loot worth more than this amount",
			position = 4,
			section = filterSection
	)
	@Range(min = 0, max = 10000000)
	default int minValueToShow()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "ignoredItemNames",
			name = "Ignored items",
			description = "Comma-separated list of item names to hide from display<br>Example: Bones, Hammer, Coins",
			position = 5,
			section = filterSection
	)
	default String ignoredItemNames()
	{
		return "";
	}

	// ========== GROUPING SECTION ==========
	@ConfigItem(
			keyName = "groupLoot",
			name = "Group kills",
			description = "Combine multiple kills from same source into one entry",
			position = 0,
			section = groupingSection
	)
	default boolean groupLoot()
	{
		return false;
	}

	@ConfigItem(
			keyName = "groupKillThreshold",
			name = "Show kill count after",
			description = "Minimum kills before showing kill count (0 = always show)",
			position = 1,
			section = groupingSection
	)
	@Range(min = 0, max = 100)
	default int groupKillThreshold()
	{
		return 1;
	}

	@ConfigItem(
			keyName = "resetGroupOnLogout",
			name = "Reset on logout",
			description = "Clear grouped loot when logging out",
			position = 2,
			section = groupingSection
	)
	default boolean resetGroupOnLogout()
	{
		return true;
	}

	// ========== ENUM DEFINITIONS ==========
	enum RareItemHighlight
	{
		OFF("Off"),
		STATIC("Static Color"),
		PULSE("Pulse"),
		RAINBOW("Rainbow");

		private final String name;

		RareItemHighlight(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}