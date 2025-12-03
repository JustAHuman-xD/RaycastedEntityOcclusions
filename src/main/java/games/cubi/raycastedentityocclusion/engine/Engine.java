package games.cubi.raycastedentityocclusion.engine;

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

    private static final List<Runnable> SYNC_TASKS = new ArrayList<>();

    private static EntityOctree octree;

    public static void reconstructOctree(JavaPlugin plugin, ConfigManager cfg) {
        if (cfg.octreeWorld == null || cfg.octreeBounds.getVolume() == 0 || cfg.maxOctreeEntities == 0 || cfg.maxOctreeDepth == 0) {
            octree = null;
            return;
        }

        octree = new EntityOctree(cfg.maxOctreeEntities, cfg.maxOctreeDepth, cfg.octreeBounds, 0);
        ENTITY_CACHE.clear();

        for (Chunk chunk : cfg.octreeWorld.getLoadedChunks()) {
            octree.insert(cfg, chunk);
        }

        plugin.getLogger().info("Reconstructed octree[max_depth=" + octree.maxDepth() + "] with " + octree.getEntities() + " entities[lit=" + octree.getLit() + "] in " + octree.getChunks() + " loaded chunks. (More will be added as chunks are loaded)");
        plugin.getLogger().info("Skipped " + octree.getSkippedPlayers() + " players and " + octree.getSkippedInvisible() + " invisible entities.");
    }

    public static void repairMegEntities(ConfigManager cfg) {
        if (octree != null) {
            octree.repairMegEntities(cfg);
        }
    }

    public static void runEngine(ConfigManager cfg, ChunkSnapshotManager snapMgr, RaycastedEntityOcclusion plugin) {
        if (octree == null) {
            return;
        }

        // ----- PHASE 1: ASYNC COMPUTE -----
        List<RayResult> results = new ArrayList<>();
        for (Player p : cfg.octreeWorld.getPlayers()) {
            if (p.hasPermission("raycastedentityocclusions.bypass")) continue;
            World world = p.getWorld();
            Vector eye = p.getEyeLocation().toVector();
            // TODO: Don't use searchRadius if the player's view distance is smaller (or the servers, or etc)
            for (EntityNode node : octree.query(BoundingBox.of(eye, cfg.searchRadius, cfg.searchRadius, cfg.searchRadius))) {
                boolean seen = canSee(p, node.uuid());
                double distSqr = eye.distanceSquared(node.location());
                if (distSqr <= cfg.alwaysShowRadius * cfg.alwaysShowRadius) {
                    if (!seen) {
                        results.add(new RayResult(p.getUniqueId(), node.uuid(), true));
                    }
                } else if (!node.light() && cfg.raycastRadius > 0 && distSqr > cfg.raycastRadius * cfg.raycastRadius) {
                    if (seen) {
                        results.add(new RayResult(p.getUniqueId(), node.uuid(), false));
                    }
                } else if (seen && RaycastedEntityOcclusion.tick % cfg.recheckInterval != 0) {
                    // player can see entity, no need to raycast
                } else {
                    // schedule for async raycast (with or without predEye)
                    boolean visible = RaycastUtil.raycast(world, eye, node.location(), (node.light() ? 2 : 1) * cfg.maxOccludingCount, cfg.debugMode, snapMgr);
                    if (visible != seen) {
                        results.add(new RayResult(p.getUniqueId(), node.uuid(), visible));
                    }
                }
            }
        }

        // ----- PHASE 3: SYNC APPLY -----
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!SYNC_TASKS.isEmpty()) {
                for (Runnable task : SYNC_TASKS) {
                    task.run();
                }
                SYNC_TASKS.clear();
            }

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
            RaycastedEntityOcclusion.running.set(false);
        });
    }

    public static void scheduleSyncTask(Runnable task) {
        SYNC_TASKS.add(task);
    }

    public static EntityOctree getOctree() {
        return octree;
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