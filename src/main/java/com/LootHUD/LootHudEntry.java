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
}