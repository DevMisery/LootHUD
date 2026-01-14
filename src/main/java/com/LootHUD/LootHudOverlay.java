package com.LootHUD;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.GradientPaint;
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
    private static final int ITEM_NAME_PADDING = 4;

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
        setSnappable(true);
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

            int entryWidth = calculateEntryWidth(graphics, entry);
            int entryHeight = calculateEntryHeight(entry);

            maxWidth = Math.max(maxWidth, entryWidth);
            totalHeight += entryHeight + 2;
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

            // Apply fade-out animation if enabled
            float currentAlpha = entry.getCurrentAlpha();
            if (config.fadeOutAnimation() && currentAlpha < 1.0f) {
                if (originalComposite == null) {
                    originalComposite = graphics.getComposite();
                }
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, currentAlpha));
            }

            yOffset += drawEntry(graphics, entry, yOffset, maxWidth, i);

            // Restore original composite if we changed it
            if (originalComposite != null) {
                graphics.setComposite(originalComposite);
            }

            entriesDrawn++;

            if (entriesDrawn >= config.maxNotifications()) {
                break;
            }
        }

        return preferredSize;
    }

    private int calculateEntryWidth(Graphics2D graphics, LootHudEntry entry)
    {
        if (config.showItemNames() && !entry.getItems().isEmpty()) {
            int maxItemWidth = 0;
            int numItemsToShow = Math.min(entry.getItems().size(), config.maxIconsPerEntry());

            for (int i = 0; i < numItemsToShow; i++) {
                ItemStack item = entry.getItems().get(i);
                int itemWidth = ITEM_SIZE;

                if (config.showItemNames()) {
                    try {
                        ItemComposition comp = itemManager.getItemComposition(item.getId());
                        String itemName = formatItemName(comp.getName(), item.getQuantity());
                        int textWidth = graphics.getFontMetrics().stringWidth(itemName);

                        if (config.itemNamePosition() == LootHudConfig.ItemNamePosition.LEFT ||
                                config.itemNamePosition() == LootHudConfig.ItemNamePosition.RIGHT) {
                            itemWidth += textWidth + ITEM_NAME_PADDING;
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                maxItemWidth = Math.max(maxItemWidth, itemWidth);
            }

            return maxItemWidth + (PADDING * 2);
        } else {
            int iconsPerRow = Math.min(config.iconsPerRow(), 8);
            return (iconsPerRow * (ITEM_SIZE + ITEM_GAP)) - ITEM_GAP + (PADDING * 2);
        }
    }

    private int calculateEntryHeight(LootHudEntry entry)
    {
        if (config.showItemNames() && !entry.getItems().isEmpty()) {
            // Items in a vertical list
            int numItemsToShow = Math.min(entry.getItems().size(), config.maxIconsPerEntry());
            return HEADER_HEIGHT + (numItemsToShow * (ITEM_SIZE + ITEM_GAP)) - ITEM_GAP + (PADDING * 2);
        } else {
            int iconsPerRow = Math.min(config.iconsPerRow(), 8);
            int numIconsToShow = Math.min(entry.getItems().size(), config.maxIconsPerEntry());
            int rows = (int) Math.ceil((double) numIconsToShow / iconsPerRow);
            return HEADER_HEIGHT + (rows * (ITEM_SIZE + ITEM_GAP)) - ITEM_GAP + (PADDING * 2);
        }
    }

    private String formatItemName(String name, int quantity)
    {
        if (quantity > 1) {
            return name + " (" + quantity + ")";
        }
        return name;
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
        int entryHeight = calculateEntryHeight(entry);

        // Get border width
        int borderWidth = Math.max(0, Math.min(10, config.borderWidth()));

        // Determine base colors
        Color backgroundColor = config.backgroundColor();
        Color headerBackgroundColor = config.headerBackgroundColor();
        Color gradientEndColor = config.gradientEndColor();

        // Apply value-based overlay colors if enabled and not a rare entry
        if (config.valueBasedOverlay() && !entry.isRare()) {
            backgroundColor = plugin.getOverlayValueColor(entry.getTotalValue());
            headerBackgroundColor = plugin.getHeaderValueColor(entry.getTotalValue());

            // For gradient end color with value-based overlay, create a slightly darker version
            gradientEndColor = new Color(
                    Math.max(0, backgroundColor.getRed() - 30),
                    Math.max(0, backgroundColor.getGreen() - 30),
                    Math.max(0, backgroundColor.getBlue() - 30),
                    backgroundColor.getAlpha()
            );
        } else {
            // Use the configured gradient end color when value-based overlay is disabled or entry is rare
            gradientEndColor = config.gradientEndColor();
        }

        // Draw header background
        graphics.setColor(headerBackgroundColor);
        graphics.fillRect(0, yOffset, maxWidth, HEADER_HEIGHT);

        // Draw main background
        if (config.useGradient()) {
            GradientPaint gradient = new GradientPaint(
                    0, yOffset + HEADER_HEIGHT, backgroundColor,
                    0, yOffset + entryHeight, gradientEndColor
            );
            graphics.setPaint(gradient);
            graphics.fillRect(0, yOffset + HEADER_HEIGHT, maxWidth, entryHeight - HEADER_HEIGHT);
        } else {
            graphics.setColor(backgroundColor);
            graphics.fillRect(0, yOffset + HEADER_HEIGHT, maxWidth, entryHeight - HEADER_HEIGHT);
        }

        // Check if this is a rare entry and which highlight mode is active
        boolean isRare = entry.isRare();
        LootHudConfig.RareItemHighlight highlightMode = config.rareItemHighlight();

        // Only apply highlight to individual (non-grouped) entries
        boolean shouldHighlight = isRare &&
                highlightMode != LootHudConfig.RareItemHighlight.OFF &&
                !entry.isGrouped();

        // Apply highlight if rare and highlight mode is not OFF and entry is not grouped
        Color headerHighlightColor = null;
        Color bodyHighlightColor = null;

        if (shouldHighlight) {
            if (highlightMode == LootHudConfig.RareItemHighlight.RAINBOW) {
                // Create animated rainbow effect
                Color rainbowColor = getRainbowColor(position);
                headerHighlightColor = new Color(
                        rainbowColor.getRed(),
                        rainbowColor.getGreen(),
                        rainbowColor.getBlue(),
                        config.rainbowAlpha()
                );
                bodyHighlightColor = new Color(
                        rainbowColor.getRed(),
                        rainbowColor.getGreen(),
                        rainbowColor.getBlue(),
                        config.rainbowAlpha() - 30
                );

                // Apply to header
                graphics.setColor(headerHighlightColor);
                graphics.fillRect(0, yOffset, maxWidth, HEADER_HEIGHT);
            } else if (highlightMode == LootHudConfig.RareItemHighlight.STATIC) {
                // Use static highlight color (with alpha already included)
                Color highlightColor = config.staticHighlightColor();
                headerHighlightColor = highlightColor;
                bodyHighlightColor = new Color(
                        highlightColor.getRed(),
                        highlightColor.getGreen(),
                        highlightColor.getBlue(),
                        Math.max(0, highlightColor.getAlpha() - 50)
                );

                graphics.setColor(headerHighlightColor);
                graphics.fillRect(0, yOffset, maxWidth, HEADER_HEIGHT);
            } else if (highlightMode == LootHudConfig.RareItemHighlight.PULSE) {
                // New pulse effect
                Color highlightColor = config.staticHighlightColor();

                // Calculate pulsing alpha
                int pulseAlpha = getPulseAlpha(highlightColor.getAlpha(), config.pulseAlphaRange(), position);
                headerHighlightColor = new Color(
                        highlightColor.getRed(),
                        highlightColor.getGreen(),
                        highlightColor.getBlue(),
                        pulseAlpha
                );
                bodyHighlightColor = new Color(
                        highlightColor.getRed(),
                        highlightColor.getGreen(),
                        highlightColor.getBlue(),
                        Math.max(0, pulseAlpha - 50)
                );

                graphics.setColor(headerHighlightColor);
                graphics.fillRect(0, yOffset, maxWidth, HEADER_HEIGHT);
            }
        }

        // Draw border with configurable width
        if (borderWidth > 0) {
            Color borderColor = config.borderColor();
            if (shouldHighlight && highlightMode != LootHudConfig.RareItemHighlight.OFF) {
                if (highlightMode == LootHudConfig.RareItemHighlight.RAINBOW) {
                    Color rainbowBorder = getRainbowColor(position + 2);
                    borderColor = new Color(
                            rainbowBorder.getRed(),
                            rainbowBorder.getGreen(),
                            rainbowBorder.getBlue(),
                            config.rainbowAlpha()
                    );
                } else if (highlightMode == LootHudConfig.RareItemHighlight.PULSE) {
                    int pulseAlpha = getPulseAlpha(config.staticHighlightColor().getAlpha(),
                            config.pulseAlphaRange(), position);
                    borderColor = new Color(
                            config.staticHighlightColor().getRed(),
                            config.staticHighlightColor().getGreen(),
                            config.staticHighlightColor().getBlue(),
                            pulseAlpha
                    );
                }
            }

            graphics.setColor(borderColor);
            graphics.setStroke(new BasicStroke(borderWidth));
            graphics.drawRect(
                    borderWidth / 2,
                    yOffset + borderWidth / 2,
                    maxWidth - borderWidth,
                    entryHeight - borderWidth
            );
            graphics.setStroke(new BasicStroke(1));
        }

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
            killText = " (x" + entry.getKillCount() + ")";
        } else if (config.groupLoot() && entry.getKillCount() > config.groupKillThreshold()) {
            killText = " (x" + entry.getKillCount() + ")";
        } else if (!config.groupLoot() && entry.getKillCount() > 1) {
            killText = " (x" + entry.getKillCount() + ")";
        }

        String displayName = entry.getSourceName();

        // Truncate if too long
        int availableWidth = maxWidth - (PADDING * 2) - textXOffset;
        if (config.showTotalValue()) {
            // Reserve space for value text
            availableWidth -= 80; // Approximate width for value text
        }

        if (graphics.getFontMetrics().stringWidth(displayName + killText) > availableWidth) {
            // Truncate with ellipsis
            while (displayName.length() > 3 &&
                    graphics.getFontMetrics().stringWidth(displayName + "..." + killText) > availableWidth) {
                displayName = displayName.substring(0, displayName.length() - 1);
            }
            displayName = displayName + "...";
        }

        Color textColor = shouldHighlight ? Color.WHITE : config.sourceNameColor();

        // If pulse mode, also pulse the text color slightly
        if (shouldHighlight && highlightMode == LootHudConfig.RareItemHighlight.PULSE) {
            int pulseAlpha = getPulseAlpha(255, config.pulseAlphaRange() / 2, position);
            textColor = new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), pulseAlpha);
        }

        OverlayUtil.renderTextLocation(graphics,
                new Point(textXOffset, textY),
                displayName + killText,
                textColor);

        // Draw value on right if enabled
        if (config.showTotalValue() && entry.getTotalValue() > 0) {
            String valueText = QuantityFormatter.quantityToStackSize(entry.getTotalValue()) + " gp";
            int textWidth = graphics.getFontMetrics().stringWidth(valueText);

            Color valueColor = shouldHighlight ? Color.WHITE : config.valueTextColor();

            if (shouldHighlight && highlightMode == LootHudConfig.RareItemHighlight.PULSE) {
                int pulseAlpha = getPulseAlpha(255, config.pulseAlphaRange() / 2, position);
                valueColor = new Color(valueColor.getRed(), valueColor.getGreen(), valueColor.getBlue(), pulseAlpha);
            }

            OverlayUtil.renderTextLocation(graphics,
                    new Point(maxWidth - PADDING - textWidth, textY),
                    valueText,
                    valueColor);
        }

        // Draw item icons and names
        if (config.showItemIcons() && !entry.getItems().isEmpty()) {
            if (config.showItemNames()) {
                drawItemsWithNames(graphics, entry, yOffset, maxWidth);
            } else {
                drawItemsInGrid(graphics, entry, yOffset, maxWidth);
            }
        }

        return entryHeight + 2;
    }

    private void drawItemsWithNames(Graphics2D graphics, LootHudEntry entry, int yOffset, int maxWidth)
    {
        int numItemsToShow = Math.min(entry.getItems().size(), config.maxIconsPerEntry());
        int startY = yOffset + HEADER_HEIGHT + PADDING;

        for (int i = 0; i < numItemsToShow; i++)
        {
            ItemStack item = entry.getItems().get(i);
            int x = PADDING;
            int y = startY + (i * (ITEM_SIZE + ITEM_GAP));

            try
            {
                ItemComposition comp = itemManager.getItemComposition(item.getId());
                String itemName = formatItemName(comp.getName(), item.getQuantity());
                int itemValue = itemManager.getItemPrice(item.getId()) * item.getQuantity();

                // Determine item color based on value thresholds
                Color itemColor = plugin.getItemValueColor(itemValue);

                // Check if this item is rare
                boolean isItemRare = false;
                String itemNameLower = comp.getName().toLowerCase().trim();
                for (String rarePattern : plugin.getRareItemNamesCache()) {
                    if (WildcardMatcher.matches(rarePattern, itemNameLower)) {
                        isItemRare = true;
                        break;
                    }
                }

                // Use highlighted color if item is rare
                if (isItemRare) {
                    itemColor = config.highlightedItemNameColor();
                }

                // Draw icon
                BufferedImage itemImage = getItemImage(item);
                if (itemImage != null)
                {
                    if (config.itemNamePosition() == LootHudConfig.ItemNamePosition.LEFT)
                    {
                        // Draw name on left, icon on right
                        int textWidth = graphics.getFontMetrics().stringWidth(itemName);
                        OverlayUtil.renderTextLocation(graphics,
                                new Point(x, y + ITEM_SIZE / 2 + 6),
                                itemName,
                                itemColor);
                        graphics.drawImage(itemImage, x + textWidth + ITEM_NAME_PADDING, y, null);
                    }
                    else if (config.itemNamePosition() == LootHudConfig.ItemNamePosition.RIGHT)
                    {
                        // Draw icon on left, name on right
                        graphics.drawImage(itemImage, x, y, null);
                        OverlayUtil.renderTextLocation(graphics,
                                new Point(x + ITEM_SIZE + ITEM_NAME_PADDING, y + ITEM_SIZE / 2 + 6),
                                itemName,
                                itemColor);
                    }
                }
            }
            catch (Exception e)
            {
                // Ignore errors and just draw the icon
                BufferedImage itemImage = getItemImage(item);
                if (itemImage != null)
                {
                    graphics.drawImage(itemImage, x, y, null);
                }
            }
        }
    }

    private void drawItemsInGrid(Graphics2D graphics, LootHudEntry entry, int yOffset, int maxWidth)
    {
        int iconsPerRow = Math.min(config.iconsPerRow(), 8);
        int numIconsToShow = Math.min(entry.getItems().size(), config.maxIconsPerEntry());
        int rows = (int) Math.ceil((double) numIconsToShow / iconsPerRow);

        int iconIndex = 0;

        for (ItemStack item : entry.getItems())
        {
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

    private Color getRainbowColor(int positionOffset)
    {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - animationStartTime;

        float speed = config.rainbowAnimationSpeed() / 10.0f;
        float hue = ((elapsedTime * 0.001f * speed) + (positionOffset * 0.2f)) % 1.0f;

        float saturation = 0.8f;
        float brightness = 0.9f;

        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        return new Color(rgb);
    }

    private int getPulseAlpha(int baseAlpha, int alphaRange, int positionOffset)
    {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - animationStartTime;

        float speedFactor = config.pulseAnimationSpeed() / 10.0f;
        long pulsePeriod = (long) (2000 / speedFactor);

        float cyclePosition = (elapsedTime % pulsePeriod) / (float) pulsePeriod;
        float sinePulse = (float) Math.sin(cyclePosition * Math.PI * 2);

        int pulseAlpha = (int) (baseAlpha + (sinePulse * alphaRange / 2));
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