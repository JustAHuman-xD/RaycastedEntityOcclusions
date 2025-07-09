package games.cubi.raycastedentityocclusion.listener;

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nexomc.nexo.api.events.NexoMechanicsRegisteredEvent;
import games.cubi.raycastedentityocclusion.engine.Engine;
import games.cubi.raycastedentityocclusion.manager.ConfigManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class NexoListener implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager cfg;

    public NexoListener(JavaPlugin plugin, ConfigManager cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    @EventHandler
    public void onNexoMechanicsRegistered(NexoMechanicsRegisteredEvent event) {
        plugin.getLogger().info("Nexo Mechanics Registered, reconstructing octree...");
        Engine.reconstructOctree(plugin, cfg);
    }

    @EventHandler
    public void onNexoItemsLoaded(NexoItemsLoadedEvent event) {
        plugin.getLogger().info("Nexo Items Loaded, reconstructing octree...");
        Engine.reconstructOctree(plugin, cfg);
    }
}
