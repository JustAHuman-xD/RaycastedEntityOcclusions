package games.cubi.raycastedentityocclusion.listener;

import games.cubi.raycastedentityocclusion.manager.ConfigManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.MobExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.UUID;

public class RepairListener implements Listener {
    private final ConfigManager cfg;

    public RepairListener(ConfigManager cfg) {
        this.cfg = cfg;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        MobExecutor mobs = MythicBukkit.inst().getMobManager();
        Entity entity = event.getRightClicked();
        UUID uuid = entity.getUniqueId();
        if ((cfg.megPositions.containsKey(uuid) || cfg.megRotations.containsKey(uuid)) && !mobs.isActiveMob(uuid)) {
            mobs.loadMythicMob(entity);
        }
    }
}
