package games.cubi.raycastedEntityOcclusion;

import games.cubi.raycastedEntityOcclusion.util.BlockPos;
import games.cubi.raycastedEntityOcclusion.util.ChunkPos;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkSnapshotManager {
    public static class Data {
        public final Set<BlockPos> occluding = ConcurrentHashMap.newKeySet();
        public final Set<BlockPos> tileEntities = ConcurrentHashMap.newKeySet();
        public long lastRefresh;

        public Data(long time) {
            this.lastRefresh = time;
        }
    }

    private final RaycastedEntityOcclusion plugin;
    private final Map<Byte, UUID> worlds = new ConcurrentHashMap<>();
    private final Map<UUID, Byte> worldIds = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Data> dataMap = new ConcurrentHashMap<>();
    private final ConfigManager cfg;

    public ChunkSnapshotManager(RaycastedEntityOcclusion plugin) {
        cfg = plugin.getConfigManager();
        this.plugin = plugin;

        //get loaded chunks and add them to dataMap
        for (World w : plugin.getServer().getWorlds()) {
            UUID worldId = w.getUID();
            worldIds.put(worldId, (byte) worlds.size());
            worlds.put(worldIds.get(worldId), worldId);
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
                for (Map.Entry<ChunkPos, Data> e : dataMap.entrySet()) {
                    if (now - e.getValue().lastRefresh >= cfg.snapshotRefreshInterval * 1000L && chunksRefreshed < chunksToRefreshMaximum) {
                        chunksRefreshed++;
                        ChunkPos pos = e.getKey();
                        UUID worldId = worlds.get(pos.world());
                        World w = Bukkit.getWorld(worldId);
                        if (w == null) {
                            plugin.getLogger().warning("ChunkSnapshotManager: World " + worldId + " not found. Please report this on our discord (discord.cubi.games)'");
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

        Data d = dataMap.get(key(loc.getChunk()));
        if (d != null) {
            BlockPos pos = BlockPos.fromLocation(loc);
            boolean occluding = m.isOccluding();
            if (occluding) {
                d.occluding.add(pos);
            } else {
                d.occluding.remove(pos);
            }

            if (cfg.checkTileEntities) {
                // Check if the block is a tile entity
                if (loc.getBlock().getState() instanceof TileState) {
                    if (cfg.debugMode){
                        Bukkit.getLogger().info("ChunkSnapshotManager: Tile entity at " + pos);
                    }
                    d.tileEntities.add(pos);
                } else {
                    d.tileEntities.remove(pos);
                }
            }
        }
    }

    private void takeSnapshot(Chunk c) {
        takeSnapshot(c, System.currentTimeMillis());
    }

    private void takeSnapshot(Chunk c, long now) {
        World w = c.getWorld();
        ChunkSnapshot snapshot = c.getChunkSnapshot(true, false, false, false);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Data data = new Data(now);
            int chunkX = c.getX() * 16;
            int chunkZ = c.getZ() * 16;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int max = snapshot.getHighestBlockYAt(x, z) + 1;
                    for (int y = w.getMinHeight(); y < max; y++) {
                        BlockData blockData = snapshot.getBlockData(x, y, z);
                        BlockPos pos = new BlockPos(x + chunkX, y, z + chunkZ);
                        if (blockData.isOccluding()) {
                            data.occluding.add(pos);
                        }
                        if (cfg.checkTileEntities) {
                            Material mat = blockData.getMaterial();
                            if (!mat.isEmpty() && mat != Material.BEACON) {
                                data.tileEntities.add(pos);
                            }
                        }
                    }
                }
            }
        });
    }

    private ChunkPos key(Chunk c) {
        return new ChunkPos(worldIds.get(c.getWorld().getUID()), c.getChunkKey());
    }

    public boolean isOccluding(Location loc) {
        Data d = dataMap.get(key(loc.getChunk()));
        if (d == null) {
            Chunk c = loc.getChunk();
            //dataMap.put(key(c), takeSnapshot(c, System.currentTimeMillis())); infinite loop
            System.err.println("ChunkSnapshotManager: No snapshot for " + loc.getChunk()+ " Please report this on our discord (discord.cubi.games)'");
            return loc.getBlock().getBlockData().isOccluding();
        }
        return d.occluding.contains(BlockPos.fromLocation(loc));
    }

    //get TileEntity Locations in chunk
    public Set<BlockPos> getTileEntitiesInChunk(World world, int x, int z) {
        Data d = dataMap.get(new ChunkPos(worldIds.get(world.getUID()), Chunk.getChunkKey(x, z)));
        if (d == null) {
            return Collections.emptySet();
        }
        return d.tileEntities;
    }

    public int getNumberOfCachedChunks() {
        return dataMap.size();
        //created to use in a debug command maybe
    }

}
