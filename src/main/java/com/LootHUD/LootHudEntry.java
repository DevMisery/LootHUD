package com.LootHUD;

import java.time.Instant;
import java.util.List;
import lombok.Data;
import net.runelite.client.game.ItemStack;
import net.runelite.http.api.loottracker.LootRecordType;

@Data
class LootHudEntry
{
    private final String sourceName;
    private final List<ItemStack> items;
    private final int killCount;
    private final Instant expirationTime;
    private final long totalValue;
    private final LootRecordType type;
    private final boolean isRare;
    private final boolean isGrouped;

    // Animation state
    private float currentAlpha = 1.0f;

    public LootHudEntry(String sourceName, List<ItemStack> items, int killCount, Instant expirationTime, long totalValue, LootRecordType type, boolean isRare, boolean isGrouped)
    {
        this.sourceName = sourceName;
        this.items = items;
        this.killCount = killCount;
        this.expirationTime = expirationTime;
        this.totalValue = totalValue;
        this.type = type;
        this.isRare = isRare;
        this.isGrouped = isGrouped;
    }

    /**
     * Checks if this loot entry has expired.
     * @return true if the current time is after the expiration time, false otherwise
     */
    public boolean isExpired()
    {
        return Instant.now().isAfter(expirationTime);
    }

    /**
     * Gets the remaining display time in milliseconds.
     * @return milliseconds remaining before expiration
     */
    public long getRemainingMillis()
    {
        Instant now = Instant.now();
        if (now.isAfter(expirationTime)) {
            return 0;
        }
        return expirationTime.toEpochMilli() - now.toEpochMilli();
    }

    /**
     * Updates fade-out animation based on current time.
     * @param fadeDurationMillis how long the fade-out animation should last (milliseconds)
     * @return current alpha value (1.0 = fully opaque, 0.0 = fully transparent)
     */
    public float updateFadeAnimation(long fadeDurationMillis)
    {
        if (fadeDurationMillis <= 0)
        {
            currentAlpha = 1.0f;
            return currentAlpha;
        }

        long remaining = getRemainingMillis();

        // If we have more time than the fade duration, stay fully opaque
        if (remaining > fadeDurationMillis)
        {
            currentAlpha = 1.0f;
            return currentAlpha;
        }

        // If we're already expired
        if (remaining <= 0)
        {
            currentAlpha = 0.0f;
            return currentAlpha;
        }

        // Calculate what percentage of the fade duration remains
        float fadeProgress = remaining / (float) fadeDurationMillis;

        // Use this directly for alpha (1.0 at start of fade, 0.0 at end)
        currentAlpha = fadeProgress;
        return currentAlpha;
    }

    /**
     * Gets the current alpha for rendering (updated by updateFadeAnimation).
     * @return current alpha value (1.0 = fully opaque, 0.0 = fully transparent)
     */
    public float getCurrentAlpha()
    {
        return currentAlpha;
    }
}