package games.cubi.raycastedentityocclusion.util;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkData {
    public final ChunkSnapshot snapshot;
    public final long timestamp;

    public final Map<BlockPos, Boolean> occluding = new ConcurrentHashMap<>();

    public ChunkData(ChunkSnapshot snapshot, long timestamp) {
        this.snapshot = snapshot;
        this.timestamp = timestamp;
    }

    public boolean isOccluding(Location loc) {
        BlockPos pos = BlockPos.fromLocation(loc);
        Boolean occlude = occluding.get(pos);
        if (occlude != null) {
            return occlude;
        }

        occlude = snapshot.getBlockData(loc.getBlockX() & 0xF, loc.getBlockY(), loc.getBlockZ() & 0xF).isOccluding();
        occluding.put(pos, occlude);
        return occlude;
    }
}
