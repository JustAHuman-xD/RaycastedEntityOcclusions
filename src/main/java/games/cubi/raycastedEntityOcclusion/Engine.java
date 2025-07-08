package games.cubi.raycastedEntityOcclusion;

import games.cubi.raycastedEntityOcclusion.util.BlockPos;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Engine {

    public static ConcurrentHashMap<BlockPos, Set<UUID>> canSeeTileEntity = new ConcurrentHashMap<>();

    private static class RayJob {
        final UUID playerId, entityId;
        final Location start, predictedStart, end;
        final boolean visible;

        RayJob(UUID p, UUID e, boolean seen, Location s, Location pred, Location t) {
            playerId = p;
            entityId = e;
            visible = seen;
            start = s;
            predictedStart = pred;
            end = t;
        }
    }

    private static class RayResult {
        final UUID playerId, entityId;
        final boolean wasVisible, nowVisible;

        RayResult(UUID p, UUID e, boolean was, boolean now) {
            playerId = p;
            entityId = e;
            wasVisible = was;
            nowVisible = now;
        }
    }

    public static void runEngine(ConfigManager cfg, ChunkSnapshotManager snapMgr, MovementTracker tracker, RaycastedEntityOcclusion plugin) {
        // ----- PHASE 1: SYNC GATHER -----
        List<RayJob> jobs = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("raycastedentityocclusions.bypass")) continue;
            Location eye = p.getEyeLocation().clone();
            Location predEye = null;
            if (cfg.engineMode == 2) {
                // getPredictedLocation returns null if insufficient data or too slow
                predEye = tracker.getPredictedLocation(p);
            }

            for (Entity e : p.getNearbyEntities(cfg.searchRadius, cfg.searchRadius, cfg.searchRadius)) {
                if (e == p) continue;
                // Cull-players logic
                boolean seen = p.canSee(e);
                if (e instanceof Player pl && (!cfg.cullPlayers || (cfg.onlyCullSneakingPlayers && !pl.isSneaking()))) {
                    if (!seen) {
                        p.showEntity(plugin, e);
                    }
                    continue;
                }

                Location target = e.getLocation().add(0, e.getHeight() / 2, 0).clone();
                double dist = eye.distance(target);
                if (dist <= cfg.alwaysShowRadius) {
                    if (!seen) {
                        p.showEntity(plugin, e);
                    }
                } else if (dist > cfg.raycastRadius) {
                    if (seen) {
                        p.hideEntity(plugin, e);
                    }
                } else if (seen && plugin.tick % cfg.recheckInterval != 0) {
                    // player can see entity, no need to raycast
                } else {
                    // schedule for async raycast (with or without predEye)
                    jobs.add(new RayJob(p.getUniqueId(), e.getUniqueId(), seen, eye, predEye, target));
                }
            }
        }

        // ----- PHASE 2: ASYNC RAYCASTS -----
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<RayResult> results = new ArrayList<>(jobs.size());
            for (RayJob job : jobs) {
                // first cast from real eye
                boolean vis = RaycastUtil.raycast(job.start, job.end, cfg.maxOccludingCount, cfg.debugMode, snapMgr);

                // if that fails, and we have a predEye, cast again from predicted
                if (!vis && job.predictedStart != null) {
                    if (cfg.debugMode) {
                        job.predictedStart.getWorld().spawnParticle(Particle.DUST, job.predictedStart, 1, new Particle.DustOptions(Color.BLUE, 1f));
                    }
                    vis = RaycastUtil.raycast(job.predictedStart, job.end, cfg.maxOccludingCount, cfg.debugMode, snapMgr);
                }

                results.add(new RayResult(job.playerId, job.entityId, job.visible, vis));
            }

            // ----- PHASE 3: SYNC APPLY -----
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (RayResult r : results) {
                    Player p = Bukkit.getPlayer(r.playerId);
                    Entity ent = Bukkit.getEntity(r.entityId);
                    if (p == null || ent == null) {
                        continue;
                    }

                    if (r.nowVisible) {
                        if (!r.wasVisible) {
                            p.showEntity(plugin, ent);
                        }
                    } else {
                        if (r.wasVisible) {
                            p.hideEntity(plugin, ent);
                        }
                    }
                }
            });
        });

    }

    public static void runTileEngine(ConfigManager cfg, ChunkSnapshotManager snapMgr, MovementTracker tracker, RaycastedEntityOcclusion plugin) {
        if (!cfg.checkTileEntities) {
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("raycastedentityocclusions.bypass")) continue;
            World world = p.getWorld();
            //async run with the world passed in
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                int chunksRadius = (cfg.searchRadius + 15) / 16;
                HashSet<BlockPos> tileEntities = new HashSet<>();
                for (int x = -chunksRadius; x <= chunksRadius; x++) {
                    for (int z = -chunksRadius; z <= chunksRadius; z++) {
                        tileEntities.addAll(snapMgr.getTileEntitiesInChunk(world, x, z));
                    }
                }

                for (BlockPos pos : tileEntities) {
                    Set<UUID> seen = canSeeTileEntity.get(pos);
                    boolean sees = seen != null && seen.contains(p.getUniqueId());
                    if (sees) {
                        if (cfg.tileEntityRecheckInterval == 0) continue;
                        if (plugin.tick % (cfg.tileEntityRecheckInterval*20) != 0) continue;
                    }

                    Location loc = pos.toLocation(world);
                    double distSquared = loc.distanceSquared(p.getLocation());
                    if (sees && distSquared > cfg.searchRadius * cfg.searchRadius) hideTileEntity(p, loc);
                    if (!sees && distSquared < cfg.alwaysShowRadius * cfg.alwaysShowRadius) showTileEntity(p, loc);

                    boolean result = RaycastUtil.raycast(p.getEyeLocation(), loc, cfg.maxOccludingCount, cfg.debugMode, snapMgr);
                    if (!result && cfg.engineMode == 2) {
                        Location predEye = tracker.getPredictedLocation(p);
                        if (predEye != null) {
                            result = RaycastUtil.raycast(predEye, loc, cfg.maxOccludingCount, cfg.debugMode, snapMgr);
                        }
                    }

                    if (sees == result) {
                        continue;
                    }

                    syncToggleTileEntity(p, loc, result, plugin);
                    if (result) {
                        canSeeTileEntity.computeIfAbsent(pos, k -> ConcurrentHashMap.newKeySet()).add(p.getUniqueId());
                    } else {
                        Set<UUID> seenPlayers = canSeeTileEntity.get(pos);
                        if (seenPlayers != null) {
                            seenPlayers.remove(p.getUniqueId());
                            if (seenPlayers.isEmpty()) {
                                canSeeTileEntity.remove(pos);
                            }
                        }
                    }
                }
            });
        }
    }

    public static void hideTileEntity(Player p, Location location) {
        if (p.hasPermission("raycastedentityocclusions.bypass")) return;
        BlockData fake;
        if (location.getBlockY() < 0) {
            fake = Material.DEEPSLATE.createBlockData();
        } else {
            fake = Material.STONE.createBlockData();
        }
        p.sendBlockChange(location, fake);
    }

    public static void showTileEntity(Player p, Location location) {
        Block block = location.getBlock();
        BlockData data = block.getBlockData();
        p.sendBlockChange(location, data);
    }

    public static void syncToggleTileEntity(Player p, Location loc, boolean bool, RaycastedEntityOcclusion plugin) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (bool) {
                showTileEntity(p, loc);
            } else {
                hideTileEntity(p, loc);
            }
        });
    }
}