package games.cubi.raycastedentityocclusion.manager;

import games.cubi.raycastedentityocclusion.RaycastedEntityOcclusion;
import games.cubi.raycastedentityocclusion.util.ChunkData;
import games.cubi.raycastedentityocclusion.util.ChunkPos;
import org.bukkit.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkSnapshotManager {

    private final Map<ChunkPos, ChunkData> dataMap = new ConcurrentHashMap<>();
    private final ConfigManager cfg;

    public ChunkSnapshotManager(RaycastedEntityOcclusion plugin) {
        cfg = plugin.getConfigManager();

        for (World w : plugin.getServer().getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                takeSnapshot(c);
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                int chunksRefreshed = 0;
                int chunksToRefreshMaximum = dataMap.size() / 3;
                for (Map.Entry<ChunkPos, ChunkData> e : dataMap.entrySet()) {
                    if (now - e.getValue().timestamp >= cfg.snapshotRefreshInterval * 1000L) {
                        ChunkPos pos = e.getKey();
                        World w = Bukkit.getWorld(pos.world());
                        if (w == null) {
                            plugin.getLogger().warning("ChunkSnapshotManager: World " + pos.world() + " not found. Please report this on our discord (discord.cubi.games)'");
                            continue;
                        }
                        e.setValue(makeSnapshot(w.getChunkAt(pos.chunk()), now));

                        if (++chunksRefreshed >= chunksToRefreshMaximum) {
                            if (cfg.debugMode) {
                                plugin.getLogger().info("ChunkSnapshotManager: Reached maximum chunks to refresh (" + chunksToRefreshMaximum + "). Stopping refresh.");
                            }
                            break; // Stop refreshing if we reached the maximum
                        }
                    }
                }
                if (cfg.debugMode) {
                    plugin.getLogger().info("ChunkSnapshotManager: Refreshed " + chunksRefreshed + " chunks out of " + chunksToRefreshMaximum + " maximum.");
                }
            }
        }.runTaskTimerAsynchronously(plugin, cfg.snapshotRefreshInterval, cfg.snapshotRefreshInterval);
    }

    public void onChunkLoad(Chunk c) {
        takeSnapshot(c);
    }

    public void onChunkUnload(Chunk c) {
        dataMap.remove(key(c));
    }

    private void takeSnapshot(Chunk c) {
        takeSnapshot(c, System.currentTimeMillis());
    }

    private void takeSnapshot(Chunk c, long now) {
        dataMap.put(key(c), makeSnapshot(c, now));
    }

    private ChunkData makeSnapshot(Chunk c, long now) {
        ChunkSnapshot snapshot = c.getChunkSnapshot(true, false, false, false);
        return new ChunkData(snapshot, now);
    }

    public boolean isOccluding(Location loc) {
        ChunkData d = dataMap.get(key(loc));
        return d == null
                ? loc.getBlock().getBlockData().isOccluding()
                : d.isOccluding(loc);
    }

    private ChunkPos key(Location loc) {
        return new ChunkPos(loc.getWorld().getUID(), Chunk.getChunkKey(loc));
    }

    private ChunkPos key(Chunk c) {
        return new ChunkPos(c.getWorld().getUID(), c.getChunkKey());
    }
}
