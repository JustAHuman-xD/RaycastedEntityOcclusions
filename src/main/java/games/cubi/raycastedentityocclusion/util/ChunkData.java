package games.cubi.raycastedentityocclusion.util;

import org.bukkit.ChunkSnapshot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkData {
    public final ChunkSnapshot snapshot;
    public final Map<BlockPos, Boolean> occluding = new ConcurrentHashMap<>();
    public long lastRefresh;

    public ChunkData(ChunkSnapshot snapshot, long time) {
        this.snapshot = snapshot;
        this.lastRefresh = time;
    }
}
