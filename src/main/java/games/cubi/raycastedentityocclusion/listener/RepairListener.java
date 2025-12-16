package games.cubi.raycastedentityocclusion.listener;

import games.cubi.raycastedentityocclusion.RaycastedEntityOcclusion;
import games.cubi.raycastedentityocclusion.manager.ConfigManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.MobExecutor;
import org.bukkit.ChatColor;
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
        try {
            if ((cfg.megPositions.containsKey(uuid) || cfg.megRotations.containsKey(uuid)) && !mobs.isActiveMob(uuid) && mobs.isMythicMob(entity)) {
                mobs.loadMythicMob(entity);
            }
        } catch (Throwable t) {
            RaycastedEntityOcclusion.instance.getLogger().severe("Failed to repair MythicMob for entity " + uuid + ": " + t.getMessage());
            t.printStackTrace();
            event.getPlayer().sendMessage(ChatColor.RED + "An error has occured, please notify an administrator, they should check the log.");
        }
    }
}
