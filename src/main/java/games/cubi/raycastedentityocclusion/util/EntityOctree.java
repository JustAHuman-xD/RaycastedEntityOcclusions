package games.cubi.raycastedentityocclusion.util;

import games.cubi.raycastedentityocclusion.RaycastedEntityOcclusion;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EntityOctree {

    private final int maxOctreeEntities;
    private final int maxOctreeDepth;

    private final BoundingBox bounds;
    private final Set<ChunkPos> cachedChunks = new HashSet<>();
    private final int depth;
    private final List<EntityNode> nodes = new ArrayList<>();
    private EntityOctree[] children = null;

    private int entities = 0;
    private int lit = 0;
    private int skippedPlayers = 0;
    private int skippedInvisible = 0;
    private int repairedMeg = 0;

    public EntityOctree(int maxOctreeEntities, int maxOctreeDepth, BoundingBox bounds, int depth) {
        this.maxOctreeEntities = maxOctreeEntities;
        this.maxOctreeDepth = maxOctreeDepth;
        this.bounds = bounds;
        this.depth = depth;
    }

    public void insert(Chunk chunk) {
        if (!cachedChunks.add(new ChunkPos(null, Chunk.getChunkKey(chunk.getX(), chunk.getZ())))) return;

        repairedMeg = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player || !entity.isVisibleByDefault()) {
                if (entity instanceof Player) skippedPlayers++;
                else skippedInvisible++;
                continue;
            }
            EntityNode node = EntityNode.from(entity);
            if (node.light()) lit++;
            if (node.repairMeg()) repairedMeg++;
            insert(node);
            entities++;
        }

        if (repairedMeg > 0) {
            RaycastedEntityOcclusion.instance.getLogger().warning("Repaired ModelEngine render radius for " + repairedMeg + " entities in chunk [" + chunk.getX() + ", " + chunk.getZ() + "].");
        }
    }

    public void insert(EntityNode node) {
        if (!bounds.contains(node.location())) return;

        if (children != null) {
            for (EntityOctree child : children) {
                if (child.bounds.contains(node.location())) {
                    child.insert(node);
                    return;
                }
            }
        }

        if (nodes.size() < maxOctreeEntities || depth >= maxOctreeDepth) {
            nodes.add(node);
        } else {
            subdivide();
            insert(node);
        }
    }

    private void subdivide() {
        children = new EntityOctree[8];
        Vector min = bounds.getMin();
        Vector max = bounds.getMax();
        Vector center = min.clone().add(max).multiply(0.5);

        int i = 0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    Vector childMin = new Vector(
                            dx == 0 ? min.getX() : center.getX(),
                            dy == 0 ? min.getY() : center.getY(),
                            dz == 0 ? min.getZ() : center.getZ()
                    );
                    Vector childMax = new Vector(
                            dx == 0 ? center.getX() : max.getX(),
                            dy == 0 ? center.getY() : max.getY(),
                            dz == 0 ? center.getZ() : max.getZ()
                    );
                    children[i++] = new EntityOctree(maxOctreeEntities, maxOctreeDepth, BoundingBox.of(childMin, childMax), depth + 1);
                }
            }
        }

        for (EntityNode node : nodes) {
            for (EntityOctree child : children) {
                if (child.bounds.contains(node.location())) {
                    child.insert(node);
                    break;
                }
            }
        }
        nodes.clear();
    }

    public List<EntityNode> query(BoundingBox area) {
        return query(area, new ArrayList<>());
    }

    public List<EntityNode> query(BoundingBox area, List<EntityNode> result) {
        if (!bounds.overlaps(area)) return result;

        for (EntityNode node : nodes) {
            if (area.contains(node.location())) {
                result.add(node);
            }
        }

        if (children != null) {
            for (EntityOctree child : children) {
                child.query(area, result);
            }
        }
        return result;
    }

    public List<EntityNode> getAllNodes() {
        List<EntityNode> result = new ArrayList<>(nodes);
        if (children != null) {
            for (EntityOctree child : children) {
                result.addAll(child.getAllNodes());
            }
        }
        return result;
    }

    public int maxDepth() {
        if (children == null) return depth;
        int maxDepth = depth;
        for (EntityOctree child : children) {
            maxDepth = Math.max(maxDepth, child.maxDepth());
        }
        return maxDepth;
    }

    public int getChunks() {
        return cachedChunks.size();
    }

    public int getEntities() {
        return entities;
    }

    public int getLit() {
        return lit;
    }

    public int getSkippedPlayers() {
        return skippedPlayers;
    }

    public int getSkippedInvisible() {
        return skippedInvisible;
    }

    public int getRepairedMeg() {
        return repairedMeg;
    }

    public void repairMegEntities() {
        repairedMeg = 0;
        List<EntityNode> allNodes = getAllNodes();
        for (EntityNode node : allNodes) {
            if (node.repairMeg()) {
                repairedMeg++;
            }
        }
        if (repairedMeg > 0) {
            RaycastedEntityOcclusion.instance.getLogger().warning("Repaired ModelEngine render radius for " + repairedMeg + " entities in the octree.");
        }
    }
}