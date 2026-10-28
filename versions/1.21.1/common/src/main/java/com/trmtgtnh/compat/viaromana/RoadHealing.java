package com.trmtgtnh.compat.viaromana;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * Which squares of a chunk lie on a charted Via Romana road, so they heal at integration.viaRomanaRoadHealing's speed:
 * the third part of the support (0.9.228).
 *
 * <p>
 * Asked once per chunk by ErosionEngine's heal, before it walks the chunk's records: the nodes of every road near the
 * chunk are read from Via Romana's graph, each joined pair drawn back into squares ({@link RoadSquares}), and each
 * record is then asked of that. With Via Romana absent, the speed at one, or no road near, the answer is null and the
 * heal is exactly what it is without this mod's support at all.
 */
public final class RoadHealing {

    /**
     * How far from a chunk's middle a node may be and still begin a road that crosses it: half the chunk's diagonal,
     * and the longest join Via Romana's own settings allow - charting.node_distance_maximum, eight at its shipped
     * settings, held well above that here, since its file can raise it.
     */
    static final double REACH = 12.0D + 32.0D;

    private static volatile boolean said;

    private RoadHealing() {}

    /**
     * The roads crossing this chunk, or null where nothing in it heals other than at the ordinary speed.
     *
     * @param lowest  the lowest of the chunk's records, and {@code highest} the highest: Via Romana's query is a sphere,
     *                so it is centred between them and made tall enough to reach both
     */
    public static RoadSquares inChunk(Level world, int chunkX, int chunkZ, int lowest, int highest) {
        if (TrmtConfig.viaRomanaRoadHealing >= 1d || !(world instanceof ServerLevel) || !ViaRomana.ready()) return null;
        Object graph = ViaRomana.graph((ServerLevel) world);
        if (graph == null) return null;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        RoadSquares road = new RoadSquares();
        double half = (Math.max(lowest, highest) - Math.min(lowest, highest)) / 2.0D + RoadSquares.SLACK + 1;
        BlockPos middle = new BlockPos(minX + 8, (lowest + highest) / 2, minZ + 8);
        for (Object node : ViaRomana.nearby(graph, middle, Math.sqrt(REACH * REACH + half * half))) {
            BlockPos a = ViaRomana.posOf(node);
            if (a == null) continue;
            for (long packed : ViaRomana.linksOf(node)) {
                BlockPos b = BlockPos.of(packed);
                // Only joins that come within a square of the chunk: the band is one square wide either side.
                if (Math.max(a.getX(), b.getX()) < minX - 1 || Math.min(a.getX(), b.getX()) > minX + 16) continue;
                if (Math.max(a.getZ(), b.getZ()) < minZ - 1 || Math.min(a.getZ(), b.getZ()) > minZ + 16) continue;
                road.addSegment(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
            }
        }
        if (road.isEmpty()) return null;
        if (!said) {
            said = true;
            Trmt.LOG.info(
                "Via Romana: a charted road crosses chunk {}, {}, and its squares heal at {} of the ordinary speed "
                    + "(integration.viaRomanaRoadHealing). Said once a session.",
                Integer.valueOf(chunkX),
                Integer.valueOf(chunkZ),
                Double.valueOf(TrmtConfig.viaRomanaRoadHealing));
        }
        return road;
    }
}
