package games.cubi.raycastedentityocclusion.util;

import games.cubi.raycastedentityocclusion.manager.ChunkSnapshotManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

public class RaycastUtil {
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.RED, 1f);
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.GREEN, 1f);

    public static boolean raycast(World world, Vector start, Vector end, int maxOccluding, boolean debug, ChunkSnapshotManager snap) {
        double totalDistanceSqr = start.distanceSquared(end);
        Location startLoc = start.toLocation(world);
        Location curr = start.toLocation(world);
        Vector dir = end.clone().subtract(start).normalize();
        while (curr.distanceSquared(startLoc) < totalDistanceSqr) {
            curr.add(dir);
            if (snap.isOccluding(curr)) {
                if (debug) {
                    world.spawnParticle(Particle.DUST, curr, 1, RED);
                }
                if (--maxOccluding < 1) {
                    return false;
                }
            } else if (debug) {
                world.spawnParticle(Particle.DUST, curr, 1, GREEN);
            }
        }
        return true;
    }
}