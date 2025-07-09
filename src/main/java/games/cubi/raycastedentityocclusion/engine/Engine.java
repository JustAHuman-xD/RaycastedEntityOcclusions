package games.cubi.raycastedentityocclusion.engine;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import games.cubi.raycastedentityocclusion.manager.ChunkSnapshotManager;
import games.cubi.raycastedentityocclusion.manager.ConfigManager;
import games.cubi.raycastedentityocclusion.util.EntityNode;
import games.cubi.raycastedentityocclusion.util.EntityOctree;
import games.cubi.raycastedentityocclusion.util.RaycastUtil;
import games.cubi.raycastedentityocclusion.RaycastedEntityOcclusion;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;

public class Engine {

    private static final VarHandle INVERTED_VISIBILITY;
    static {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(craftPlayerClass, MethodHandles.lookup());
            Field invertedVisibilityField = craftPlayerClass.getDeclaredField("invertedVisibilityEntities");
            INVERTED_VISIBILITY = lookup.unreflectVarHandle(invertedVisibilityField);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get the Inverted Visibility Field", e);
        }
    }

    private static final Map<UUID, WeakReference<Player>> PLAYER_CACHE = new HashMap<>();
    private static final Map<UUID, Map<UUID, ?>> INVERTED_VISIBILITY_CACHE = new HashMap<>();
    private static final Map<UUID, WeakReference<Entity>> ENTITY_CACHE = new HashMap<>();

    private static EntityOctree octree;


    public static void reconstructOctree(JavaPlugin plugin, ConfigManager cfg) {
        if (cfg.octreeWorld == null || cfg.octreeBounds.getVolume() == 0 || cfg.maxOctreeEntities == 0 || cfg.maxOctreeDepth == 0) {
            octree = null;
            return;
        }

        octree = new EntityOctree(cfg.maxOctreeEntities, cfg.maxOctreeDepth, cfg.octreeBounds, 0);
        ENTITY_CACHE.clear();

        int minChunkX = (int) (cfg.octreeBounds.getMinX() / 16);
        int minChunkZ = (int) (cfg.octreeBounds.getMinZ() / 16);
        int maxChunkX = (int) (cfg.octreeBounds.getMaxX() / 16);
        int maxChunkZ = (int) (cfg.octreeBounds.getMaxZ() / 16);

        int chunks = 0;
        int entities = 0;
        int skippedPlayers = 0;
        int skippedInvisible = 0;
        int skippedLights = 0;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                Chunk chunk = cfg.octreeWorld.getChunkAt(x, z);
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Player || !entity.isVisibleByDefault()) {
                        if (entity instanceof Player) skippedPlayers++;
                        else skippedInvisible++;
                        continue;
                    }
                    FurnitureMechanic furniture = NexoFurniture.furnitureMechanic(entity);
                    if (furniture != null && !furniture.getLight().isEmpty()) {
                        skippedLights++;
                        continue;
                    }
                    octree.insert(entity);
                    entities++;
                }
                chunks++;
            }
        }

        plugin.getLogger().info("Reconstructed octree with " + entities + " entities in " + chunks + " chunks.");
        plugin.getLogger().info("Skipped " + skippedPlayers + " players, " + skippedInvisible + " invisible entities, and " + skippedLights + " lighted furniture.");
    }

    public static void runEngine(ConfigManager cfg, ChunkSnapshotManager snapMgr, RaycastedEntityOcclusion plugin) {
        if (octree == null) {
            return;
        }

        // ----- PHASE 1: ASYNC COMPUTE -----
        List<RayResult> results = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("raycastedentityocclusions.bypass")) continue;
            World world = p.getWorld();
            Vector eye = p.getEyeLocation().toVector();
            for (EntityNode node : octree.query(BoundingBox.of(eye, cfg.searchRadius, cfg.searchRadius, cfg.searchRadius))) {
                boolean seen = canSee(p, node.uuid());
                double distSqr = eye.distanceSquared(node.location());
                if (distSqr <= cfg.alwaysShowRadius * cfg.alwaysShowRadius) {
                    if (!seen) {
                        results.add(new RayResult(p.getUniqueId(), node.uuid(), true));
                    }
                } else if (cfg.raycastRadius > 0 && distSqr > cfg.raycastRadius * cfg.raycastRadius) {
                    if (seen) {
                        results.add(new RayResult(p.getUniqueId(), node.uuid(), false));
                    }
                } else if (seen && plugin.tick % cfg.recheckInterval != 0) {
                    // player can see entity, no need to raycast
                } else {
                    // schedule for async raycast (with or without predEye)
                    boolean visible = RaycastUtil.raycast(world, eye, node.location(), cfg.maxOccludingCount, cfg.debugMode, snapMgr);
                    if (visible != seen) {
                        results.add(new RayResult(p.getUniqueId(), node.uuid(), visible));
                    }
                }
            }
        }

        // ----- PHASE 3: SYNC APPLY -----
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (RayResult r : results) {
                Player p = getPlayer(r.playerId);
                Entity ent = getEntity(r.entityId);
                if (p == null || ent == null) {
                    continue;
                }

                if (r.visible) {
                    p.showEntity(plugin, ent);
                } else {
                    p.hideEntity(plugin, ent);
                }
            }
        });
    }

    public static void unCachePlayer(UUID uuid) {
        PLAYER_CACHE.remove(uuid);
    }

    public static Player getPlayer(UUID uuid) {
        return getCached(uuid, PLAYER_CACHE, () -> {
            INVERTED_VISIBILITY_CACHE.remove(uuid);
            return Bukkit.getPlayer(uuid);
        });
    }

    public static Entity getEntity(UUID uuid) {
        return getCached(uuid, ENTITY_CACHE, () -> Bukkit.getEntity(uuid));
    }

    public static <E> E getCached(UUID uuid, Map<UUID, WeakReference<E>> cache, Supplier<E> supplier) {
        WeakReference<E> ref = cache.get(uuid);
        if (ref != null) {
            E entity = ref.get();
            if (entity != null) {
                return entity;
            }
        }
        E entity = supplier.get();
        if (entity != null) {
            cache.put(uuid, new WeakReference<>(entity));
        }
        return entity;
    }

    public static boolean canSee(Player player, UUID entity) {
        Map<UUID, ?> cached = INVERTED_VISIBILITY_CACHE.get(player.getUniqueId());
        if (cached != null) {
            return !cached.containsKey(entity);
        }

        try {
            Map<UUID, ?> invertedVisibility = (Map<UUID, ?>) INVERTED_VISIBILITY.get(player);
            INVERTED_VISIBILITY_CACHE.put(player.getUniqueId(), invertedVisibility);
            return !invertedVisibility.containsKey(entity);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to access inverted visibility for player " + player.getName(), e);
        }
    }
}