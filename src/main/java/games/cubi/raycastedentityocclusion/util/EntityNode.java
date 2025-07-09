package games.cubi.raycastedentityocclusion.util;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.UUID;

public record EntityNode(UUID uuid, Vector location) {
    public static EntityNode from(Entity entity) {
        double height = entity.getHeight();
        FurnitureMechanic furniture = NexoFurniture.furnitureMechanic(entity);
        if (furniture != null) {
            height = furniture.getHitbox().hitboxHeight();
        }
        return new EntityNode(entity.getUniqueId(), entity.getLocation().add(0, height / 2, 0).toVector());
    }
}
