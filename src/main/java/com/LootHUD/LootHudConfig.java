package com.LootHUD;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Alpha;

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

	@ConfigSection(
			name = "Text Colors",
			description = "Text color settings",
			position = 7
	)
	String textColorsSection = "textColors";

	@ConfigSection(
			name = "Value Thresholds",
			description = "Item value-based highlighting",
			position = 8
	)
	String valueThresholdsSection = "valueThresholds";

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
	@Alpha
	@ConfigItem(
			keyName = "backgroundColor",
			name = "Background color",
			description = "Color for loot entry backgrounds (includes transparency)",
			position = 0,
			section = appearanceSection
	)
	default java.awt.Color backgroundColor()
	{
		return new java.awt.Color(0, 0, 0, 180);
	}

	@Alpha
	@ConfigItem(
			keyName = "headerBackgroundColor",
			name = "Header background color",
			description = "Separate background color for source name header",
			position = 1,
			section = appearanceSection
	)
	default java.awt.Color headerBackgroundColor()
	{
		return new java.awt.Color(40, 40, 40, 200);
	}

	@Alpha
	@ConfigItem(
			keyName = "borderColor",
			name = "Border color",
			description = "Color for loot entry borders (includes transparency)",
			position = 2,
			section = appearanceSection
	)
	default java.awt.Color borderColor()
	{
		return new java.awt.Color(100, 100, 100, 150);
	}

	@ConfigItem(
			keyName = "borderWidth",
			name = "Border width",
			description = "Width of the loot entry borders",
			position = 3,
			section = appearanceSection
	)
	@Range(min = 0, max = 10)
	default int borderWidth()
	{
		return 1;
	}

	@ConfigItem(
			keyName = "useGradient",
			name = "Use gradient background",
			description = "Use gradient instead of solid color",
			position = 4,
			section = appearanceSection
	)
	default boolean useGradient()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
			keyName = "gradientEndColor",
			name = "Gradient end color",
			description = "End color for gradient background",
			position = 5,
			section = appearanceSection
	)
	default java.awt.Color gradientEndColor()
	{
		return new java.awt.Color(50, 50, 50, 180);
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
			keyName = "fadeOutAnimation",
			name = "Fade out animation",
			description = "Gradually fade out entries when they expire",
			position = 4,
			section = displaySection
	)
	default boolean fadeOutAnimation()
	{
		return true;
	}

	@ConfigItem(
			keyName = "fadeOutDuration",
			name = "Fade out duration",
			description = "How long the fade out animation lasts (seconds)",
			position = 5,
			section = displaySection
	)
	@Range(min = 1, max = 10)
	default int fadeOutDuration()
	{
		return 3;
	}

	@ConfigItem(
			keyName = "showTotalValue",
			name = "Show total value",
			description = "Display total GE value of each loot pile",
			position = 6,
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
			position = 7,
			section = displaySection
	)
	default boolean showLootTypeIcon()
	{
		return false;
	}

	@ConfigItem(
			keyName = "showItemNames",
			name = "Show item names",
			description = "Display item names next to icons",
			position = 8,
			section = displaySection
	)
	default boolean showItemNames()
	{
		return false;
	}

	@ConfigItem(
			keyName = "itemNamePosition",
			name = "Item name position",
			description = "Where to show item names relative to icons",
			position = 9,
			section = displaySection
	)
	default ItemNamePosition itemNamePosition()
	{
		return ItemNamePosition.RIGHT;
	}

	@ConfigItem(
			keyName = "tooltipOnHover",
			name = "Tooltip on hover",
			description = "Show item name tooltip when hovering over icons",
			position = 10,
			section = displaySection
	)
	default boolean tooltipOnHover()
	{
		return true;
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
			description = "Comma-separated list of item names to highlight<br>Supports wildcards: *bow, dragon *<br>Example: Twisted bow, Scythe of vitur, Tumeken's shadow, dragon *, *fire cape",
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

	@Alpha
	@ConfigItem(
			keyName = "staticHighlightColor",
			name = "Highlight color",
			description = "Color for highlighting rare items (includes transparency)",
			position = 3,
			section = highlightSection
	)
	default java.awt.Color staticHighlightColor()
	{
		return new java.awt.Color(255, 215, 0, 220);
	}

	@ConfigItem(
			keyName = "rainbowAnimationSpeed",
			name = "Rainbow speed",
			description = "Speed of rainbow color cycling",
			position = 4,
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
			position = 5,
			section = highlightSection
	)
	@Range(min = 0, max = 255)
	default int rainbowAlpha()
	{
		return 200;
	}

	@ConfigItem(
			keyName = "pulseAnimationSpeed",
			name = "Pulse speed",
			description = "Speed of pulsing animation",
			position = 6,
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
			position = 7,
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
			description = "Comma-separated list of item names to hide from display<br>Supports wildcards: *gloves, bones*, *of the gods<br>Example: Bones, *gloves, Hammer, Coins",
			position = 5,
			section = filterSection
	)
	default String ignoredItemNames()
	{
		return "";
	}

	@ConfigItem(
			keyName = "ignoredSources",
			name = "Ignored sources",
			description = "Comma-separated list of NPC/player names to hide loot from<br>Supports wildcards: *goblin, Bandit*, Lizardman*<br>Example: Man, *goblin, Cow",
			position = 6,
			section = filterSection
	)
	default String ignoredSources()
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

	// ========== TEXT COLORS SECTION ==========
	@Alpha
	@ConfigItem(
			keyName = "sourceNameColor",
			name = "Source name color",
			description = "Color for source name text",
			position = 0,
			section = textColorsSection
	)
	default java.awt.Color sourceNameColor()
	{
		return new java.awt.Color(255, 255, 0, 255);
	}

	@Alpha
	@ConfigItem(
			keyName = "itemNameColor",
			name = "Item name color",
			description = "Color for regular item names",
			position = 1,
			section = textColorsSection
	)
	default java.awt.Color itemNameColor()
	{
		return new java.awt.Color(255, 255, 255, 255);
	}

	@Alpha
	@ConfigItem(
			keyName = "highlightedItemNameColor",
			name = "Highlighted item name color",
			description = "Color for highlighted/rare item names",
			position = 2,
			section = textColorsSection
	)
	default java.awt.Color highlightedItemNameColor()
	{
		return new java.awt.Color(255, 215, 0, 255);
	}

	@Alpha
	@ConfigItem(
			keyName = "valueTextColor",
			name = "Value text color",
			description = "Color for total value text",
			position = 3,
			section = textColorsSection
	)
	default java.awt.Color valueTextColor()
	{
		return new java.awt.Color(0, 255, 0, 255);
	}

	// ========== VALUE THRESHOLDS SECTION ==========
	@ConfigItem(
			keyName = "valueThreshold1",
			name = "Low value items",
			description = "First value threshold for item highlighting",
			position = 0,
			section = valueThresholdsSection
	)
	@Range(min = 0, max = 100000000)
	default int valueThreshold1()
	{
		return 1000;
	}

	@Alpha
	@ConfigItem(
			keyName = "valueColor1",
			name = "Text Color: Low",
			description = "Text color for items above threshold 1",
			position = 1,
			section = valueThresholdsSection
	)
	default java.awt.Color valueColor1()
	{
		return new java.awt.Color(255, 255, 255, 255);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayColor1",
			name = "Overlay Color: Low",
			description = "Overlay color for total value above threshold 1",
			position = 2,
			section = valueThresholdsSection
	)
	default java.awt.Color overlayColor1()
	{
		return new java.awt.Color(0, 0, 0, 180);
	}

	@ConfigItem(
			keyName = "valueThreshold2",
			name = "Medium value items",
			description = "Second value threshold for item highlighting",
			position = 3,
			section = valueThresholdsSection
	)
	@Range(min = 0, max = 100000000)
	default int valueThreshold2()
	{
		return 10000;
	}

	@Alpha
	@ConfigItem(
			keyName = "valueColor2",
			name = "Text Color: Medium",
			description = "Text color for items above threshold 2",
			position = 4,
			section = valueThresholdsSection
	)
	default java.awt.Color valueColor2()
	{
		return new java.awt.Color(0, 255, 0, 255);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayColor2",
			name = "Overlay Color: Medium",
			description = "Overlay color for total value above threshold 2",
			position = 5,
			section = valueThresholdsSection
	)
	default java.awt.Color overlayColor2()
	{
		return new java.awt.Color(0, 40, 0, 180);
	}

	@ConfigItem(
			keyName = "valueThreshold3",
			name = "High value items",
			description = "Third value threshold for item highlighting",
			position = 6,
			section = valueThresholdsSection
	)
	@Range(min = 0, max = 100000000)
	default int valueThreshold3()
	{
		return 50000;
	}

	@Alpha
	@ConfigItem(
			keyName = "valueColor3",
			name = "Text Color: High",
			description = "Text color for items above threshold 3",
			position = 7,
			section = valueThresholdsSection
	)
	default java.awt.Color valueColor3()
	{
		return new java.awt.Color(0, 200, 255, 255);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayColor3",
			name = "Overlay Color: High",
			description = "Overlay color for total value above threshold 3",
			position = 8,
			section = valueThresholdsSection
	)
	default java.awt.Color overlayColor3()
	{
		return new java.awt.Color(0, 20, 40, 180);
	}

	@ConfigItem(
			keyName = "valueThreshold4",
			name = "Insane value items",
			description = "Fourth value threshold for item highlighting",
			position = 9,
			section = valueThresholdsSection
	)
	@Range(min = 0, max = 100000000)
	default int valueThreshold4()
	{
		return 250000;
	}

	@Alpha
	@ConfigItem(
			keyName = "valueColor4",
			name = "Text Color: Insane",
			description = "Text color for items above threshold 4",
			position = 10,
			section = valueThresholdsSection
	)
	default java.awt.Color valueColor4()
	{
		return new java.awt.Color(255, 165, 0, 255);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayColor4",
			name = "Overlay Color: Insane",
			description = "Overlay color for total value above threshold 4",
			position = 11,
			section = valueThresholdsSection
	)
	default java.awt.Color overlayColor4()
	{
		return new java.awt.Color(40, 20, 0, 180);
	}

	@ConfigItem(
			keyName = "valueThreshold5",
			name = "Legendary value items",
			description = "Fifth value threshold for item highlighting",
			position = 12,
			section = valueThresholdsSection
	)
	@Range(min = 0, max = 100000000)
	default int valueThreshold5()
	{
		return 1000000;
	}

	@Alpha
	@ConfigItem(
			keyName = "valueColor5",
			name = "Text Color: Legendary",
			description = "Text color for items above threshold 5",
			position = 13,
			section = valueThresholdsSection
	)
	default java.awt.Color valueColor5()
	{
		return new java.awt.Color(255, 0, 0, 255);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayColor5",
			name = "Overlay Color: Legendary",
			description = "Overlay color for total value above threshold 5",
			position = 14,
			section = valueThresholdsSection
	)
	default java.awt.Color overlayColor5()
	{
		return new java.awt.Color(40, 0, 0, 180);
	}

	@ConfigItem(
			keyName = "valueBasedOverlay",
			name = "Enable value-based overlay",
			description = "Change overlay background color based on total value",
			position = 15,
			section = valueThresholdsSection
	)
	default boolean valueBasedOverlay()
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

	enum ItemNamePosition
	{
		LEFT("Left"),
		RIGHT("Right");

		private final String name;

		ItemNamePosition(String name)
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