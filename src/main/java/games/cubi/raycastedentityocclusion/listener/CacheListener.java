package games.cubi.raycastedentityocclusion.listener;

import games.cubi.raycastedentityocclusion.engine.Engine;
import games.cubi.raycastedentityocclusion.manager.ChunkSnapshotManager;
import games.cubi.raycastedentityocclusion.manager.ConfigManager;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.util.BoundingBox;

public class CacheListener implements Listener {
    private final ConfigManager cfg;
    private final ChunkSnapshotManager manager;

    public CacheListener(ConfigManager cfg, ChunkSnapshotManager mgr) {
        this.cfg = cfg;
        this.manager = mgr;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Engine.unCachePlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();
        if (e.getWorld() == cfg.octreeWorld && Engine.getOctree() != null && cfg.octreeBounds.overlaps(new BoundingBox(
                chunk.getX() * 16.0, cfg.octreeWorld.getMinHeight(), chunk.getZ() * 16.0,
                chunk.getX() * 16.0 + 16.0, cfg.octreeWorld.getMaxHeight(), chunk.getZ() * 16.0 + 16.0
        ))) {
            Engine.getOctree().insert(cfg, chunk);
        }
        manager.onChunkLoad(chunk);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent e) {
        manager.onChunkUnload(e.getChunk());
    }

}