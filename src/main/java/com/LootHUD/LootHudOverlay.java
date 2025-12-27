package com.LootHUD;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Constants;
import net.runelite.api.Point;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.loottracker.LootRecordType;

class LootHudOverlay extends Overlay
{
    private final LootHudPlugin plugin;
    private final LootHudConfig config;
    private final ItemManager itemManager;

    private static final int PADDING = 4;
    private static final int ITEM_GAP = 2;
    private static final int HEADER_HEIGHT = 20;
    private static final int ITEM_SIZE = Constants.ITEM_SPRITE_WIDTH;
    private static final int TYPE_ICON_SIZE = 12;

    private long animationStartTime = System.currentTimeMillis();

    @Inject
    private LootHudOverlay(LootHudPlugin plugin, LootHudConfig config, ItemManager itemManager)
    {
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;

        // Make overlay movable and dynamic
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.HIGH);
        setMovable(true);
        setResizable(config.allowResizing());
        setSnappable(true); // Added: Enable snapping to screen edges
        setPreferredSize(null);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.isOverlayVisible())
        {
            return null;
        }

        List<LootHudEntry> entries = plugin.getRecentLoot();
        if (entries.isEmpty())
        {
            return null;
        }

        // Calculate total size needed for all entries
        int totalHeight = 0;
        int maxWidth = 0;
        int entriesCounted = 0;

        for (LootHudEntry entry : entries)
        {
            if (config.minValueToShow() > 0 && entry.getTotalValue() < config.minValueToShow()) {
                continue;
            }

            int iconsPerRow = Math.min(config.iconsPerRow(), 8);
            int numIconsToShow = Math.min(entry.getItems().size(), config.maxIconsPerEntry());
            int rows = (int) Math.ceil((double) numIconsToShow / iconsPerRow);

            int entryWidth = (iconsPerRow * (ITEM_SIZE + ITEM_GAP)) - ITEM_GAP + (PADDING * 2);
            int entryHeight = HEADER_HEIGHT + (rows * (ITEM_SIZE + ITEM_GAP)) - ITEM_GAP + (PADDING * 2);

            maxWidth = Math.max(maxWidth, entryWidth);
            totalHeight += entryHeight + 2; // 2px gap between entries
            entriesCounted++;
        }

        // If no entries after filtering, don't render
        if (maxWidth == 0) {
            return null;
        }

        // Check if we should use fixed size or dynamic size
        Dimension preferredSize = null;
        if (config.allowResizing() && getPreferredSize() != null) {
            // Use user's preferred size if resizing is allowed and set
            preferredSize = getPreferredSize();

            // Ensure minimum size
            if (preferredSize.width < maxWidth) {
                preferredSize.width = maxWidth;
            }
            if (preferredSize.height < totalHeight) {
                preferredSize.height = totalHeight;
            }
        } else {
            // Use calculated size
            preferredSize = new Dimension(maxWidth, totalHeight);
        }

        // Update resizable property based on config
        setResizable(config.allowResizing());

        // Draw each entry
        int yOffset = 0;
        int entriesDrawn = 0;

        for (int i = 0; i < entries.size(); i++)
        {
            LootHudEntry entry = entries.get(i);

            if (config.minValueToShow() > 0 && entry.getTotalValue() < config.minValueToShow()) {
                continue;
            }

            // Apply fade effect if enabled and there are multiple entries
            Composite originalComposite = null;
            if (config.fadeOlderEntries() && entries.size() > 1) {
                originalComposite = graphics.getComposite();
                float alpha = calculateAlphaForPosition(i, entries.size());
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }

            yOffset += drawEntry(graphics, entry, yOffset, maxWidth, i);

            // Restore original composite if we changed it
            if (config.fadeOlderEntries() && entries.size() > 1) {
                graphics.setComposite(originalComposite);
            }

            entriesDrawn++;

            if (entriesDrawn >= config.maxNotifications()) {
                break;
            }
        }

        return preferredSize;
    }

    private float calculateAlphaForPosition(int position, int totalEntries)
    {
        float minAlpha = 0.4f;
        float maxAlpha = 1.0f;

        if (totalEntries <= 1) {
            return maxAlpha;
        }

        float t = (float) position / (totalEntries - 1);
        return maxAlpha - t * (maxAlpha - minAlpha);
    }

    private int drawEntry(Graphics2D graphics, LootHudEntry entry, int yOffset, int maxWidth, int position)
    {
        int iconsPerRow = Math.min(config.iconsPerRow(), 8);
        int numIconsToShow = Math.min(entry.getItems().size(), config.maxIconsPerEntry());
        int rows = (int) Math.ceil((double) numIconsToShow / iconsPerRow);

        int entryHeight = HEADER_HEIGHT + (rows * (ITEM_SIZE + ITEM_GAP)) - ITEM_GAP + (PADDING * 2);

        // Get base colors from config
        Color backgroundColor = new Color(
                config.backgroundColor().getRed(),
                config.backgroundColor().getGreen(),
                config.backgroundColor().getBlue(),
                config.backgroundAlpha()
        );

        Color borderColor = new Color(
                config.borderColor().getRed(),
                config.borderColor().getGreen(),
                config.borderColor().getBlue(),
                config.borderAlpha()
        );

        // Check if this is a rare entry and which highlight mode is active
        boolean isRare = entry.isRare();
        LootHudConfig.RareItemHighlight highlightMode = config.rareItemHighlight();

        // NEW: Only apply highlight to individual (non-grouped) entries
        boolean shouldHighlight = isRare &&
                highlightMode != LootHudConfig.RareItemHighlight.OFF &&
                !entry.isGrouped();

        // Apply highlight if rare and highlight mode is not OFF and entry is not grouped
        if (shouldHighlight) {
            if (highlightMode == LootHudConfig.RareItemHighlight.RAINBOW) {
                // Create animated rainbow effect
                Color rainbowColor = getRainbowColor(position);
                backgroundColor = new Color(
                        rainbowColor.getRed(),
                        rainbowColor.getGreen(),
                        rainbowColor.getBlue(),
                        config.rainbowAlpha()
                );

                // Create slightly different color for border (brighter)
                Color rainbowBorder = getRainbowColor(position + 2); // Offset for different hue
                borderColor = new Color(
                        rainbowBorder.getRed(),
                        rainbowBorder.getGreen(),
                        rainbowBorder.getBlue(),
                        config.rainbowAlpha()
                );
            } else if (highlightMode == LootHudConfig.RareItemHighlight.STATIC) {
                // Use static highlight color
                Color highlightColor = config.staticHighlightColor();
                backgroundColor = new Color(
                        highlightColor.getRed(),
                        highlightColor.getGreen(),
                        highlightColor.getBlue(),
                        config.staticHighlightAlpha()
                );

                // Create slightly brighter border color
                borderColor = new Color(
                        Math.min(255, highlightColor.getRed() + 30),
                        Math.min(255, highlightColor.getGreen() + 30),
                        Math.min(255, highlightColor.getBlue() + 30),
                        config.staticHighlightAlpha()
                );
            } else if (highlightMode == LootHudConfig.RareItemHighlight.PULSE) {
                // New pulse effect
                Color highlightColor = config.staticHighlightColor();

                // Calculate pulsing alpha
                int pulseAlpha = getPulseAlpha(config.staticHighlightAlpha(), config.pulseAlphaRange(), position);

                backgroundColor = new Color(
                        highlightColor.getRed(),
                        highlightColor.getGreen(),
                        highlightColor.getBlue(),
                        pulseAlpha
                );

                // Create slightly brighter border color with same pulse effect
                borderColor = new Color(
                        Math.min(255, highlightColor.getRed() + 30),
                        Math.min(255, highlightColor.getGreen() + 30),
                        Math.min(255, highlightColor.getBlue() + 30),
                        pulseAlpha
                );
            }
        }

        // Draw background rectangle for the entire entry
        graphics.setColor(backgroundColor);
        graphics.fillRect(0, yOffset, maxWidth, entryHeight);

        // Draw border
        graphics.setColor(borderColor);
        graphics.drawRect(0, yOffset, maxWidth - 1, entryHeight - 1);

        // Draw loot type icon if enabled
        int textXOffset = PADDING;
        if (config.showLootTypeIcon()) {
            Color typeColor = getColorForLootType(entry.getType());
            graphics.setColor(typeColor);
            graphics.fillRect(PADDING, yOffset + PADDING, TYPE_ICON_SIZE, TYPE_ICON_SIZE);
            textXOffset += TYPE_ICON_SIZE + 4;
        }

        // Draw header text (monster name + value)
        int textY = yOffset + PADDING + 14;

        // Format kill count text
        String killText = "";
        if (entry.isGrouped()) {
            // For grouped entries, show total kill count
            killText = " (x" + entry.getKillCount() + ")";
        } else if (config.groupLoot() && entry.getKillCount() > config.groupKillThreshold()) {
            // For ungrouped entries in grouping mode, only show kill count if above threshold
            killText = " (x" + entry.getKillCount() + ")";
        } else if (!config.groupLoot() && entry.getKillCount() > 1) {
            // For regular mode, show kill count if > 1
            killText = " (x" + entry.getKillCount() + ")";
        }

        String displayName = entry.getSourceName();

        // Truncate if too long
        if (displayName.length() > 20) {
            displayName = displayName.substring(0, 17) + "...";
        }

        // Draw monster name (always shown)
        // NEW: Use highlight text color only for individual rare entries
        Color textColor = shouldHighlight ? Color.WHITE : Color.YELLOW;

        // If pulse mode, also pulse the text color slightly
        if (shouldHighlight && highlightMode == LootHudConfig.RareItemHighlight.PULSE) {
            // Create a slightly dimmed version of the text color that pulses
            int pulseAlpha = getPulseAlpha(255, config.pulseAlphaRange() / 2, position);
            textColor = new Color(255, 255, 255, pulseAlpha);
        }

        OverlayUtil.renderTextLocation(graphics,
                new Point(textXOffset, textY),
                displayName + killText,
                textColor);

        // Draw value on right if enabled
        if (config.showTotalValue() && entry.getTotalValue() > 0) {
            String valueText = QuantityFormatter.quantityToStackSize(entry.getTotalValue()) + " gp";
            int textWidth = graphics.getFontMetrics().stringWidth(valueText);

            // NEW: Use highlight value color only for individual rare entries
            Color valueColor = shouldHighlight ? Color.WHITE : Color.GREEN;

            // If pulse mode, also pulse the value color slightly
            if (shouldHighlight && highlightMode == LootHudConfig.RareItemHighlight.PULSE) {
                int pulseAlpha = getPulseAlpha(255, config.pulseAlphaRange() / 2, position);
                valueColor = new Color(0, 255, 0, pulseAlpha);
            }

            OverlayUtil.renderTextLocation(graphics,
                    new Point(maxWidth - PADDING - textWidth, textY),
                    valueText,
                    valueColor);
        }

        // Draw item icons in a grid
        if (config.showItemIcons() && !entry.getItems().isEmpty()) {
            int iconIndex = 0;

            for (ItemStack item : entry.getItems()) {
                if (iconIndex >= config.maxIconsPerEntry()) {
                    break;
                }

                int row = iconIndex / iconsPerRow;
                int col = iconIndex % iconsPerRow;

                int x = PADDING + col * (ITEM_SIZE + ITEM_GAP);
                int y = yOffset + HEADER_HEIGHT + PADDING + row * (ITEM_SIZE + ITEM_GAP);

                BufferedImage itemImage = getItemImage(item);
                if (itemImage != null) {
                    graphics.drawImage(itemImage, x, y, null);
                }

                iconIndex++;
            }

            // Draw "+X" indicator if there are more items than we can show
            if (entry.getItems().size() > config.maxIconsPerEntry()) {
                int remaining = entry.getItems().size() - config.maxIconsPerEntry();
                int lastRow = (int) Math.ceil((double) Math.min(entry.getItems().size(), config.maxIconsPerEntry()) / iconsPerRow) - 1;
                int lastCol = Math.min(entry.getItems().size(), config.maxIconsPerEntry()) % iconsPerRow;
                if (lastCol == 0) lastCol = iconsPerRow;

                int x = PADDING + (lastCol) * (ITEM_SIZE + ITEM_GAP);
                int y = yOffset + HEADER_HEIGHT + PADDING + lastRow * (ITEM_SIZE + ITEM_GAP);

                graphics.setColor(new Color(60, 60, 60, 200));
                graphics.fillRect(x, y, ITEM_SIZE, ITEM_SIZE);

                graphics.setColor(Color.LIGHT_GRAY);
                String plusText = "+" + remaining;
                int textWidth = graphics.getFontMetrics().stringWidth(plusText);
                OverlayUtil.renderTextLocation(graphics,
                        new Point(x + (ITEM_SIZE - textWidth) / 2, y + 18),
                        plusText,
                        Color.LIGHT_GRAY);
            }
        }

        return entryHeight + 2;
    }

    private Color getRainbowColor(int positionOffset)
    {
        // Calculate time-based hue for smooth rainbow animation
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - animationStartTime;

        // Use animation speed to control the cycle speed
        float speed = config.rainbowAnimationSpeed() / 10.0f; // Convert to 0.1-1.0 range

        // Calculate hue: cycle through 0.0 to 1.0 based on elapsed time and position offset
        // Add positionOffset to create a wave effect across multiple entries
        float hue = ((elapsedTime * 0.001f * speed) + (positionOffset * 0.2f)) % 1.0f;

        // Fixed saturation and brightness for vibrant rainbow colors
        float saturation = 0.8f;  // 80% saturation
        float brightness = 0.9f;  // 90% brightness

        // Convert HSB to RGB
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        return new Color(rgb);
    }

    private int getPulseAlpha(int baseAlpha, int alphaRange, int positionOffset)
    {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - animationStartTime;

        // Calculate pulse period (2 seconds base, adjusted by speed)
        float speedFactor = config.pulseAnimationSpeed() / 10.0f;
        long pulsePeriod = (long) (2000 / speedFactor); // 2000ms = 2 seconds base

        // Calculate where we are in the pulse cycle (0.0 to 1.0)
        float cyclePosition = (elapsedTime % pulsePeriod) / (float) pulsePeriod;

        // Create smooth pulse using sine of the cycle position
        float sinePulse = (float) Math.sin(cyclePosition * Math.PI * 2);

        // Map from [-1, 1] to alpha range
        int pulseAlpha = (int) (baseAlpha + (sinePulse * alphaRange / 2));

        // Clamp to valid range
        return Math.max(0, Math.min(255, pulseAlpha));
    }

    private Color getColorForLootType(LootRecordType type)
    {
        switch (type)
        {
            case NPC:
                return new Color(255, 100, 100, 200);
            case PLAYER:
                return new Color(100, 100, 255, 200);
            case PICKPOCKET:
                return new Color(255, 200, 100, 200);
            case EVENT:
                return new Color(100, 255, 100, 200);
            default:
                return new Color(200, 200, 200, 200);
        }
    }

    private BufferedImage getItemImage(ItemStack item)
    {
        try
        {
            ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
            boolean showQuantity = itemComposition.isStackable() || item.getQuantity() > 1;
            return itemManager.getImage(item.getId(), item.getQuantity(), showQuantity);
        }
        catch (Exception e)
        {
            return null;
        }
    }
}