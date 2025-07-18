package games.cubi.raycastedentityocclusion.util;

import org.bukkit.Location;

public record BlockPos(int x, int y, int z) {
    public static BlockPos fromLocation(Location loc) {
        return new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
