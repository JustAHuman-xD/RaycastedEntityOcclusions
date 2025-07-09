package games.cubi.raycastedentityocclusion.listener;

import games.cubi.raycastedentityocclusion.engine.Engine;
import games.cubi.raycastedentityocclusion.manager.ChunkSnapshotManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class CacheListener implements Listener {
    private final ChunkSnapshotManager manager;

    public CacheListener(ChunkSnapshotManager mgr) {
        this.manager = mgr;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Engine.unCachePlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        manager.onChunkLoad(e.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent e) {
        manager.onChunkUnload(e.getChunk());
    }

}