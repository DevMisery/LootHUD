package com.LootHUD;

import java.util.Set;

/**
 * Simple wildcard matcher for item/source name matching.
 * Supports '*' as wildcard character.
 */
class WildcardMatcher
{
    /**
     * Checks if a string matches a wildcard pattern.
     * @param pattern Pattern with '*' as wildcard
     * @param text Text to match against pattern
     * @return true if text matches pattern
     */
    public static boolean matches(String pattern, String text)
    {
        if (pattern == null || text == null)
        {
            return false;
        }

        // Remove leading/trailing spaces and convert to lowercase
        pattern = pattern.trim().toLowerCase();
        text = text.trim().toLowerCase();

        // If pattern is empty, it matches everything
        if (pattern.isEmpty())
        {
            return true;
        }

        // Handle exact match
        if (!pattern.contains("*"))
        {
            return text.equals(pattern);
        }

        // Handle wildcard matching
        String[] parts = pattern.split("\\*", -1); // -1 to keep empty parts

        // Empty pattern means "*" which matches everything
        if (parts.length == 0)
        {
            return true;
        }

        // Check if text starts with first part
        if (!parts[0].isEmpty() && !text.startsWith(parts[0]))
        {
            return false;
        }

        // Check if text ends with last part
        if (parts.length > 1 && !parts[parts.length - 1].isEmpty() &&
                !text.endsWith(parts[parts.length - 1]))
        {
            return false;
        }

        // Check middle parts
        int currentIndex = 0;
        for (int i = 0; i < parts.length; i++)
        {
            String part = parts[i];
            if (part.isEmpty())
            {
                continue;
            }

            int foundIndex = text.indexOf(part, currentIndex);
            if (foundIndex == -1)
            {
                return false;
            }
            currentIndex = foundIndex + part.length();
        }

        return true;
    }

    public static boolean anyMatches(Set<String> patterns, String text)
    {
        if (patterns == null || patterns.isEmpty() || text == null)
        {
            return false;
        }

        for (String pattern : patterns)
        {
            if (matches(pattern, text))
            {
                return true;
            }
        }
        return false;
    }
}