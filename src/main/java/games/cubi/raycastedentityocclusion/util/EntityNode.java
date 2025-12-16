package games.cubi.raycastedentityocclusion.util;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.nms.entity.wrapper.BodyRotationController;
import games.cubi.raycastedentityocclusion.RaycastedEntityOcclusion;
import games.cubi.raycastedentityocclusion.engine.Engine;
import games.cubi.raycastedentityocclusion.manager.ConfigManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.MobExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.UUID;

public record EntityNode(UUID uuid, Vector location, boolean light) {
    public boolean[] repairMeg(ConfigManager cfg) {
        boolean[] repaired = new boolean[6];
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(uuid);
        if (modeled != null) {
            if (!modeled.shouldBeSaved()) {
                modeled.setSaved(true);
                repaired[0] = true;
            }
            if (modeled.getBase().getRenderRadius() < cfg.megMinRenderRadius) {
                modeled.getBase().setRenderRadius(cfg.megMinRenderRadius);
                repaired[1] = true;
            }
            if (modeled.isModelRotationLocked() != cfg.megRotationLocked) {
                modeled.setModelRotationLocked(cfg.megRotationLocked);
                repaired[2] = true;
            }
            if (cfg.megPositions.containsKey(uuid)) {
                Engine.scheduleSyncTask(() -> {
                    Entity entity = Engine.getEntity(uuid);
                    if (entity != null) {
                        Vector desired = cfg.megPositions.get(uuid);
                        Vector current = entity.getLocation().toVector();
                        // account for floating point precision issues
                        if (desired.distanceSquared(current) > 0.01f) {
                            entity.teleport(desired.toLocation(entity.getWorld()));
                            Vector after = entity.getLocation().toVector();
                            repaired[5] = after.distanceSquared(desired) <= 0.01f;
                            if (!repaired[5]) {
                                RaycastedEntityOcclusion.instance.getLogger().warning("Failed to repair ModelEngine position for entity " + uuid + ". Wanted " + desired + " but got " + after + ".");
                            }
                        }
                    }
                });
            }
            if (cfg.megRotations.containsKey(uuid)) {
                BodyRotationController controller = modeled.getBase().getBodyRotationController();
                float desired = cfg.megRotations.get(uuid);
                float current = controller.getYBodyRot();
                // account for floating point precision issues
                if (Math.abs(current - desired) > 0.01f) {
                    controller.setBodyClampUneven(false);
                    controller.setMaxBodyAngle(desired);
                    controller.setYBodyRot(desired);
                    repaired[3] = Math.abs(controller.getYBodyRot() - desired) <= 0.01f;
                    if (!repaired[3]) {
                        RaycastedEntityOcclusion.instance.getLogger().warning("Failed to repair ModelEngine rotation for entity " + uuid + ". Wanted " + desired + " but got " + controller.getYBodyRot() + ".");
                    }
                }
            }
        }
        MobExecutor mobs = MythicBukkit.inst().getMobManager();
        if ((cfg.megPositions.containsKey(uuid) || cfg.megRotations.containsKey(uuid)) && !mobs.isActiveMob(uuid)) {
            Engine.scheduleSyncTask(() -> {
                try {
                    Entity entity = Engine.getEntity(uuid);
                    if (entity != null && mobs.isMythicMob(entity) && mobs.loadMythicMob(entity).isPresent()) {
                        repaired[4] = true;
                    }
                } catch (Throwable t) {
                    RaycastedEntityOcclusion.instance.getLogger().severe("Failed to repair MythicMob for entity " + uuid + ": " + t.getMessage());
                    t.printStackTrace();
                }
            });
        }
        return repaired;
    }

    public static EntityNode from(Entity entity) {
        double height = entity.getHeight();
        boolean light = false;
        FurnitureMechanic furniture = NexoFurniture.furnitureMechanic(entity);
        if (furniture != null) {
            height = furniture.getHitbox().hitboxHeight();
            light = !furniture.getLight().isEmpty();
        }
        return new EntityNode(entity.getUniqueId(), entity.getLocation().add(0, height / 2, 0).toVector(), light);
    }
}
