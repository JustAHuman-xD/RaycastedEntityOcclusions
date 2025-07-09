package games.cubi.raycastedentityocclusion.engine;

import java.util.UUID;

class RayResult {
    final UUID playerId, entityId;
    final boolean visible;

    RayResult(UUID p, UUID e, boolean vis) {
        playerId = p;
        entityId = e;
        visible = vis;
    }
}
