package games.cubi.raycastedentityocclusion.manager;

import games.cubi.raycastedentityocclusion.RaycastedEntityOcclusion;
import games.cubi.raycastedentityocclusion.util.BlockPos;
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

        //get loaded chunks and add them to dataMap
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
                int chunksToRefreshMaximum = getNumberOfCachedChunks() / 3;
                for (Map.Entry<ChunkPos, ChunkData> e : dataMap.entrySet()) {
                    if (now - e.getValue().lastRefresh >= cfg.snapshotRefreshInterval * 1000L && chunksRefreshed < chunksToRefreshMaximum) {
                        chunksRefreshed++;
                        ChunkPos pos = e.getKey();
                        World w = Bukkit.getWorld(pos.world());
                        if (w == null) {
                            plugin.getLogger().warning("ChunkSnapshotManager: World " + pos.world() + " not found. Please report this on our discord (discord.cubi.games)'");
                            continue;
                        }
                        takeSnapshot(w.getChunkAt(pos.chunk()), now);
                    }
                }
                if (cfg.debugMode) {
                    plugin.getLogger().info("ChunkSnapshotManager: Refreshed " + chunksRefreshed + " chunks out of " + chunksToRefreshMaximum + " maximum.");
                }
            }
        }.runTaskTimerAsynchronously(plugin, cfg.snapshotRefreshInterval * 2L, cfg.snapshotRefreshInterval * 2L /* This runs 10 times per refreshInterval, spreading out the refreshes */);
    }

    public void onChunkLoad(Chunk c) {
        takeSnapshot(c);
    }

    public void onChunkUnload(Chunk c) {
        dataMap.remove(key(c));
    }

    // Used by SnapshotListener to update the delta map when a block is placed or broken
    public void onBlockChange(Location loc, Material m) {
        if (cfg.debugMode) {
            Bukkit.getLogger().info("ChunkSnapshotManager: Block change at " + loc + " to " + m);
        }

        ChunkData d = dataMap.get(key(loc.getChunk()));
        if (d != null) {
            BlockPos pos = BlockPos.fromLocation(loc);
            boolean occluding = m.isOccluding();
            d.occluding.put(pos, occluding);
        }
    }

    private void takeSnapshot(Chunk c) {
        takeSnapshot(c, System.currentTimeMillis());
    }

    private void takeSnapshot(Chunk c, long now) {
        ChunkSnapshot snapshot = c.getChunkSnapshot(true, false, false, false);
        ChunkData chunkData = new ChunkData(snapshot, now);
        dataMap.put(key(c), chunkData);
    }

    private ChunkPos key(Chunk c) {
        return new ChunkPos(c.getWorld().getUID(), c.getChunkKey());
    }

    public boolean isOccluding(Location loc) {
        Chunk c = loc.getChunk();
        ChunkData d = dataMap.get(key(c));
        if (d == null) {
            //dataMap.put(key(c), takeSnapshot(c, System.currentTimeMillis())); infinite loop
            System.err.println("ChunkSnapshotManager: No snapshot for " + c + " Please report this on our discord (discord.cubi.games)'");
            return loc.getBlock().getBlockData().isOccluding();
        }

        BlockPos pos = BlockPos.fromLocation(loc);
        Boolean occluding = d.occluding.get(pos);
        if (occluding != null) {
            return occluding;
        }

        occluding = d.snapshot.getBlockData(loc.getBlockX() & 0xF, loc.getBlockY(), loc.getBlockZ() & 0xF).isOccluding();
        d.occluding.put(pos, occluding);
        return occluding;
    }

    public int getNumberOfCachedChunks() {
        return dataMap.size();
        //created to use in a debug command maybe
    }

}
