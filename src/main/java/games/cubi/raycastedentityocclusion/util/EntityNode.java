package games.cubi.raycastedentityocclusion.util;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import games.cubi.raycastedentityocclusion.RaycastedEntityOcclusion;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.UUID;

public record EntityNode(UUID uuid, Vector location, boolean light) {
    public boolean repairMeg() {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(uuid);
        if (modeled != null && modeled.getBase().getRenderRadius() <= 0) {
            modeled.getBase().setRenderRadius(48);
            return true;
        }
        return false;
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
