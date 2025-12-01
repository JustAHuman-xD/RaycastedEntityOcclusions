package games.cubi.raycastedentityocclusion.manager;

import games.cubi.raycastedentityocclusion.engine.Engine;
import io.papermc.paper.math.Rotation;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConfigManager {
    private final JavaPlugin plugin;

    public boolean debugMode;

    public int snapshotRefreshInterval;
    public int engineRate;
    public int recheckInterval;

    public int megRepairInterval;
    public boolean logMegRepairs;
    public int megMinRenderRadius;
    public boolean megRotationLocked;
    public Map<UUID, Float> megRotations;

    public int alwaysShowRadius;
    public int raycastRadius;
    public int maxOccludingCount;
    public int searchRadius;

    public int maxOctreeEntities;
    public int maxOctreeDepth;
    public World octreeWorld;
    public BoundingBox octreeBounds;

    public FileConfiguration cfg;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        cfg = plugin.getConfig();

        debugMode = cfg.getBoolean("debug-mode", false);

        snapshotRefreshInterval = cfg.getInt("snapshot-refresh-interval", 12000);
        engineRate = cfg.getInt("engine-rate", 1);
        recheckInterval = cfg.getInt("recheck-interval", 20);

        megRepairInterval = cfg.getInt("meg-repair-interval", 1200);
        logMegRepairs = cfg.getBoolean("log-meg-repairs", false);
        megMinRenderRadius = cfg.getInt("minimum-meg-radius", 48);
        megRotationLocked = cfg.getBoolean("meg-rotation-locked", true);
        megRotations = new HashMap<>();
        ConfigurationSection rotSection = cfg.getConfigurationSection("meg-rotations");
        if (rotSection == null) {
            plugin.getLogger().info("No MEG rotations found in config.");
        } else {
            for (String key : rotSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    if (rotSection.isDouble(key)) {
                        megRotations.put(uuid, (float) rotSection.getDouble(key));
                    } else {
                        plugin.getLogger().warning("Invalid rotation data for MEG UUID: " + key + ". Expected 2 values (yaw, pitch).");
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID format in MEG rotations: " + key);
                }
            }
            plugin.getLogger().info("Loaded " + megRotations.size() + " MEG rotations from config.");
        }

        alwaysShowRadius = cfg.getInt("always-show-radius", 8);
        raycastRadius = cfg.getInt("raycast-radius", 48);
        maxOccludingCount = cfg.getInt("max-occluding-count", 3);
        searchRadius = cfg.getInt("search-radius", 48);


        maxOctreeEntities = cfg.getInt("octree-max-entities", 16);
        maxOctreeDepth = cfg.getInt("octree-max-depth", 4);
        octreeWorld = Bukkit.getWorld(cfg.getString("octree-world", "world"));
        octreeBounds = BoundingBox.of(getVector("octree-min"), getVector("octree-max"));
        if (octreeWorld == null || octreeBounds.getVolume() == 0 || maxOctreeEntities <= 0 || maxOctreeDepth <= 0) {
            plugin.getLogger().severe("Invalid octree specified in config, plugin will not function.");
        }

        Engine.reconstructOctree(plugin, this);

        // Write defaults if missing
        cfg.addDefault("debug-mode", false);
        cfg.addDefault("snapshot-refresh-interval", 60);
        cfg.addDefault("engine-rate", 1);
        cfg.addDefault("recheck-interval", 20);
        cfg.addDefault("always-show-radius", 8);
        cfg.addDefault("raycast-radius", 48);
        cfg.addDefault("max-occluding-count", 3);
        cfg.addDefault("search-radius", 48);
        cfg.addDefault("octree-max-entities", 16);
        cfg.addDefault("octree-max-depth", 4);
        cfg.addDefault("octree-world", "world");
        cfg.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public int setConfigValue(String path, String rawValue) {
        if (!cfg.contains(path)) return -1;
        Object current = cfg.get(path);
        Object parsed;
        if (current instanceof Boolean) {
            String lower = rawValue.toLowerCase();
            if (!lower.equals("true") && !lower.equals("false")) return -1;
            parsed = Boolean.parseBoolean(lower);
        } else if (current instanceof Number) {
            int intVal;
            try {
                intVal = Integer.parseInt(rawValue);
            } catch (NumberFormatException e) {
                return -1;
            }
            if (intVal < 0 || intVal > 256) return 0;
            parsed = intVal;
        } else {
            return -1;
        }
        cfg.set(path, parsed);
        plugin.saveConfig();
        load();
        return 1;
        /*
        -1 = invalid input
        0 = out of range
        1 = success
         */
    }

    public Vector getVector(String key) {
        if (cfg.isVector(key)) {
            return cfg.getVector(key);
        } else if (cfg.isInt(key + ".x") && cfg.isInt(key + ".y") && cfg.isInt(key + ".z")) {
            return new Vector(cfg.getInt(key + ".x"), cfg.getInt(key + ".y"), cfg.getInt(key + ".z"));
        } else {
            return new Vector(0, 0, 0);
        }
    }
}