package games.cubi.raycastedentityocclusion.util;

import org.bukkit.ChunkSnapshot;

import java.util.HashMap;
import java.util.Map;

public class ChunkData {
    public final ChunkSnapshot snapshot;
    public final Map<BlockPos, Boolean> occluding = new HashMap<>();
    public long lastRefresh;

    public ChunkData(ChunkSnapshot snapshot, long time) {
        this.snapshot = snapshot;
        this.lastRefresh = time;
    }
}
