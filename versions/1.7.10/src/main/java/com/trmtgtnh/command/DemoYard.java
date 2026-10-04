package com.trmtgtnh.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.entity.EntityGolemOfWays;
import com.trmtgtnh.entity.GolemUpgrade;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionChain;
import com.trmtgtnh.erosion.ErosionEngine;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.item.ItemChunkTamper;
import com.trmtgtnh.item.ModItems;
import com.trmtgtnh.item.TamperGrade;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * A row of pens behind the exhibit, one golem to each, so the whole crew can be watched at once.
 *
 * <p>
 * The platforms answer what a gradation looks like and the roads answer whether a road reads as a
 * road. Neither answers the question the golem raises, which is whether the thing actually works -
 * and that one cannot be answered by looking at a still. It needs a golem, ground worth mending,
 * the tool it spends and the material it spends, and then a while spent watching. Standing one up
 * by hand takes a few minutes; standing up all of them to compare takes an afternoon, so the demo
 * stands them up instead.
 *
 * <p>
 * One pen per upgrade, in the order the upgrades are declared, each holding the same ground and the
 * same stock, so the only thing that differs between two pens is the part fitted. That is the whole
 * design: a difference you can see is a difference the upgrade made.
 *
 * <p>
 * The ground inside each pen is laid in quarters and pre-worn across each of them, from pristine at
 * one edge to fully worn at the other, so a golem has work in both directions from its first tick -
 * something to mend and something to wear - whatever its targets happen to be set to.
 */
final class DemoYard {

    /** How much ground a golem is given, which is the size the pens were asked to be. */
    private static final int FLOOR = 6;

    /** The floor plus the wall around it. */
    private static final int FOOTPRINT = FLOOR + 2;

    /** The wall plus the walkway the sign is read from. */
    private static final int APRON = FOOTPRINT + 2;

    /** One pen to the next, leaving a visible gap between two aprons. */
    private static final int PITCH = APRON + 2;

    /**
     * How far a golem is told to work, which is inside its own walls.
     *
     * <p>
     * Deliberately short of the six squares it stands on. A golem works a circle and stands a
     * little outside the ground it keeps, so a radius equal to the pen would have it walking into
     * the wall on every stroke; three keeps the whole round comfortably in front of it.
     *
     * <p>
     * The one upgrade this cannot show, and honestly so: a radius of three sits under every
     * ceiling, so the pen for the range upgrade works exactly as hard as the pen for no upgrade at
     * all. What range buys is a bigger round, and a bigger round needs a field rather than a pen.
     */
    private static final int PEN_RADIUS = 3;

    /** How much air a golem is given above its floor, which is more than it is tall. */
    private static final int HEADROOM = 4;

    /** The quarters of ground, one to each corner of the floor. */
    private static final SurfaceFamily[] QUARTERS = { SurfaceFamily.GRASS, SurfaceFamily.GRAVEL, SurfaceFamily.COBBLE,
        SurfaceFamily.SAND };

    private DemoYard() {}

    /** Which upgrades get a pen: all of them where a pack allows upgrades, otherwise the plain one. */
    static GolemUpgrade[] penned() {
        if (!TrmtConfig.golemUpgrades) return new GolemUpgrade[] { GolemUpgrade.NONE };
        return GolemUpgrade.values();
    }

    /** How wide the whole row is, so the clearing and the chunk loading can cover it. */
    static int width() {
        return penned().length * PITCH;
    }

    /** How deep one pen is, front apron to back apron. */
    static int depth() {
        return APRON;
    }

    /**
     * Stands the row up.
     *
     * @param tally filled in with pens built, golems stood up and squares pre-worn
     * @return how many entities were cleared away first
     */
    static int build(World world, int originX, int originZ, int baseY, int[] tally) {
        GolemUpgrade[] wanted = penned();

        // Everything left over from a previous run goes first, in one sweep across the whole row.
        // Per-pen would miss whatever has wandered between two of them, and a golem is a thing
        // that wanders.
        int swept = sweep(world, originX, originZ, baseY, true);

        Set<Long> painted = new HashSet<Long>();
        for (int i = 0; i < wanted.length; i++) {
            int penX = originX + i * PITCH + 1;
            int penZ = originZ + 1;
            if (!world.blockExists(penX, baseY, penZ)) continue;
            buildPen(world, penX, baseY, penZ, wanted[i], tally, painted);
            tally[0]++;
        }

        // The wear the pens were laid with, one packet to the chunk. A ghost is client-side and
        // nothing else here tells a client about it, so without this the pens read as pristine
        // ground until something else in the chunk happens to be sent.
        for (Long key : painted) {
            int chunkX = (int) (key.longValue() >> 32);
            int chunkZ = (int) key.longValue();
            ChunkErosionData data = ErosionStore.get()
                .getChunk(world.provider.dimensionId, chunkX, chunkZ);
            if (data != null) TrmtNetwork.sendChunkToWatchers(world, chunkX, chunkZ, data, true);
        }

        // And a second sweep, because putting a chest where a chest already stood breaks the old
        // one and a chest breaking empties itself onto the floor - drops the first sweep could not
        // see, because they did not exist yet. Litter only this time: the only golems in the box
        // now are the ones three lines above, and a pass that took those would leave ten empty
        // pens and report that it had stood ten golems up in them.
        return swept + sweep(world, originX, originZ, baseY, false);
    }

    /** What the demonstration's own golems are signed as, which is how a sweep tells them apart. */
    private static final String DEMO_CREW = "demonstrate";

    /**
     * The whole row, as a box, so the two sweeps and the survey cannot describe different ground.
     */
    private static int[] rowBox(int originX, int originZ, int baseY) {
        return new int[] { originX - 2, baseY - 2, originZ - 2, originX + width() + 2, baseY + 8,
            originZ + depth() + 2 };
    }

    /** Clears the row, taking the previous run's crew with it or leaving this run's alone. */
    private static int sweep(World world, int originX, int originZ, int baseY, boolean golems) {
        int[] box = rowBox(originX, originZ, baseY);
        return sweep(world, box[0], box[1], box[2], box[3], box[4], box[5], golems);
    }

    /**
     * Takes away what a previous run left standing.
     *
     * <p>
     * Three things and no others. The golems, because otherwise a second run stands a second crew
     * beside the first and by the third there are thirty of them treading the same ground. The
     * dropped items, because clearing the ground breaks the chests and a chest breaking empties
     * itself onto the floor - which is a great many stacks a run, all of them lag. And the
     * experience, which comes from the same place and costs the same to leave.
     *
     * <p>
     * Killed with {@code setDead} rather than by damage, because damage is exactly what makes a
     * golem drop the inventory this is here to stop accumulating.
     */
    static int sweep(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return sweep(world, minX, minY, minZ, maxX, maxY, maxZ, true);
    }

    /**
     * @param golems whether the crew goes too, which is true of a run clearing what a previous one
     *               left and false of a pass tidying up after itself
     */
    static int sweep(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean golems) {
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(minX, Math.max(0, minY), minZ, maxX + 1, Math.min(256, maxY + 1), maxZ + 1);
        @SuppressWarnings("unchecked")
        List<Entity> found = world.getEntitiesWithinAABB(Entity.class, box);
        int removed = 0;
        for (Entity entity : found) {
            // Already taken by an earlier pass over overlapping ground. The world hands a dead
            // entity back until the tick ends, and counting it twice would say two golems were
            // cleared where there was one.
            if (entity.isDead) continue;
            boolean litter = entity instanceof EntityItem || entity instanceof EntityXPOrb;
            // Only a golem this command stood up. A player's own golem inside the box is somebody's
            // road and somebody's stock, and setDead is chosen above precisely so that nothing
            // drops - which, pointed at a golem the demonstration did not make, deleted everything
            // it carried without a word.
            boolean crew = golems && entity instanceof EntityGolemOfWays
                && DEMO_CREW.equals(((EntityGolemOfWays) entity).summonedBy());
            if (litter || crew) {
                entity.setDead();
                removed++;
            }
        }
        return removed;
    }

    /**
     * How many positions the row would have to build over, so the command can refuse the way it
     * refuses for the platforms rather than punching through whatever is there.
     */
    static int survey(World world, int originX, int originZ, int baseY) {
        int taken = 0;
        for (int i = 0; i < penned().length; i++) {
            int penX = originX + i * PITCH + 1;
            int penZ = originZ + 1;
            for (int dx = -1; dx <= FOOTPRINT; dx++) {
                for (int dz = -1; dz <= FOOTPRINT; dz++) {
                    for (int y = baseY; y <= baseY + 1 + HEADROOM; y++) {
                        int x = penX + dx;
                        int z = penZ + dz;
                        if (!world.blockExists(x, y, z)) {
                            taken++;
                            continue;
                        }
                        if (world.isAirBlock(x, y, z)) continue;
                        if (!world.getBlock(x, y, z)
                            .isReplaceable(world, x, y, z)) {
                            taken++;
                        }
                    }
                }
            }
        }
        return taken;
    }

    private static void buildPen(World world, int penX, int baseY, int penZ, GolemUpgrade upgrade, int[] tally,
        Set<Long> painted) {
        // The apron first, one block proud of the wall all round, so there is somewhere to stand
        // and read the sign from without being in the pen.
        for (int dx = -1; dx <= FOOTPRINT; dx++) {
            for (int dz = -1; dz <= FOOTPRINT; dz++) {
                set(world, penX + dx, baseY, penZ + dz, Blocks.stone, 0);
            }
        }

        layFloor(world, penX, baseY, penZ, tally, painted);
        layWall(world, penX, baseY, penZ);
        signPost(world, penX + FOOTPRINT / 2, baseY + 1, penZ - 1, upgrade);

        if (TrmtConfig.golemEnabled && standGolem(world, penX, baseY, penZ, upgrade)) tally[1]++;
    }

    /**
     * The ground a golem is given: four quarters, each running pristine to worn across its width.
     *
     * <p>
     * Four materials rather than one because a golem carries stock for what it mends, and a pen of
     * a single ground would never show it running out of one thing while it still has plenty of
     * another - which is the difficulty the storage upgrade exists to answer.
     */
    private static void layFloor(World world, int penX, int baseY, int penZ, int[] tally, Set<Long> painted) {
        int half = FLOOR / 2;
        for (int dx = 0; dx < FLOOR; dx++) {
            for (int dz = 0; dz < FLOOR; dz++) {
                SurfaceFamily family = QUARTERS[(dx < half ? 0 : 1) + (dz < half ? 0 : 2)];
                Block ground = CommandTrmt.roadBlock(family);
                int x = penX + 1 + dx;
                int y = baseY + 1;
                int z = penZ + 1 + dz;
                set(world, x, y, z, ground, 0);
                // Headroom, cleared rather than assumed. A golem is nearly three blocks tall and
                // suffocates in anything it is standing inside, and a pen built without 'cleararea'
                // is built in whatever happened to be there.
                for (int up = 1; up <= HEADROOM; up++) {
                    set(world, x, y + up, z, Blocks.air, 0);
                }

                int length = ErosionChain.length(family);
                if (length <= 1) continue;
                // Across the quarter rather than along it, so each strip of three reads as one run
                // from untouched to gone and the four runs sit side by side.
                int within = dx % half;
                int index = Math.round((within / (float) Math.max(1, half - 1)) * (length - 1));
                if (index <= 0) continue;
                // Never pinned, whatever the exhibit beside it is doing. The platforms are a still
                // and are frozen so they stay one; a pen is the opposite, and ground a golem cannot
                // change is a pen with nothing to watch in it.
                if (ErosionEngine.get()
                    .forceStage(world, x, y, z, family, index, false, false)) {
                    tally[2]++;
                    painted.add(Long.valueOf(((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL)));
                }
            }
        }
    }

    /**
     * The wall, which is a kerb, a course to lean on, and a fence above that.
     *
     * <p>
     * Two and a half blocks above the floor in all, which is more than a golem can step up and
     * short of hiding it. The fence is what makes the difference: a wall tall enough to hold one
     * in is a wall tall enough to stop anybody watching, and a fence is neither.
     */
    private static void layWall(World world, int penX, int baseY, int penZ) {
        for (int dx = 0; dx < FOOTPRINT; dx++) {
            for (int dz = 0; dz < FOOTPRINT; dz++) {
                boolean edge = dx == 0 || dz == 0 || dx == FOOTPRINT - 1 || dz == FOOTPRINT - 1;
                if (!edge) continue;
                int x = penX + dx;
                int z = penZ + dz;
                set(world, x, baseY + 1, z, Blocks.cobblestone, 0);
                set(world, x, baseY + 2, z, Blocks.cobblestone, 0);
                set(world, x, baseY + 3, z, Blocks.fence, 0);
            }
        }

        // Set into the wall rather than standing in the pen, because six squares of ground is what
        // was asked for and a chest in the corner of it is five and a half. Reachable from the
        // apron, and each pair is a double chest because vanilla joins two that touch. The fence
        // above them stays: it is not a solid cube, so it does not stop a chest opening.
        int at = FOOTPRINT / 2 - 1;
        stock(world, penX + at, baseY + 2, penZ, toolChest());
        stock(world, penX + at, baseY + 2, penZ + FOOTPRINT - 1, healingChest());
    }

    /** Puts a pair of containers into two squares of the wall and fills them. */
    private static void stock(World world, int x, int y, int z, List<ItemStack> contents) {
        if (!world.blockExists(x, y, z) || !world.blockExists(x + 1, y, z)) return;
        int placed = CommandTrmt.placeContainer(world, x, y, z, contents, 0);
        if (placed < contents.size()) CommandTrmt.placeContainer(world, x + 1, y, z, contents, placed);
    }

    /**
     * The sign, which is the only thing that says which pen is which.
     *
     * <p>
     * The golem's own name says it too, but only to somebody already standing in front of that one
     * and looking straight at it - and the whole point of a row of them is walking the row.
     */
    private static void signPost(World world, int x, int y, int z, GolemUpgrade upgrade) {
        if (!world.blockExists(x, y, z)) return;
        // Meta eight is the rotation a sign gets when it is placed by somebody looking along
        // positive Z, which is the way the row is walked: from the exhibit, towards the pens.
        set(world, x, y, z, Blocks.standing_sign, 8);
        TileEntity holder = world.getTileEntity(x, y, z);
        if (!(holder instanceof TileEntitySign)) return;
        TileEntitySign sign = (TileEntitySign) holder;
        String[] wrapped = wrap(StatCollector.translateToLocal(upgrade.nameKey()));
        sign.signText[0] = wrapped[0];
        sign.signText[1] = wrapped[1];
        sign.signText[2] = wrapped[2];
        sign.signText[3] = upgrade == GolemUpgrade.NONE ? "no upgrade" : upgrade.key;
        sign.markDirty();
    }

    /**
     * Stands one golem up in its pen, armed and stocked.
     *
     * <p>
     * The upgrade goes in before anything else, because the storage upgrade decides how many slots
     * count as the golem's own. Filled afterwards, a plain golem gets its sixteen slots filled and
     * a deep one its sixty-four, which is the difference that pen exists to show.
     */
    private static boolean standGolem(World world, int penX, int baseY, int penZ, GolemUpgrade upgrade) {
        EntityGolemOfWays golem = new EntityGolemOfWays(world);
        if (upgrade != GolemUpgrade.NONE && TrmtConfig.golemUpgrades) {
            Item part = ModItems.upgrade(upgrade);
            if (part != null) golem.setInventorySlotContents(golem.upgradeSlot(), new ItemStack(part, 1, 0));
        }

        int centre = FOOTPRINT / 2;
        golem.setLocationAndAngles(penX + centre, baseY + 2, penZ + centre, 0f, 0f);
        golem.setAnchor(penX + centre, baseY + 1, penZ + centre);
        golem.setWorkRadius(PEN_RADIUS);
        golem.setSummonedBy(DEMO_CREW);

        // Held at the middle of each ground the pen is laid with, which is what makes a quartered
        // floor work in both directions: the fresh half gets worn down to the middle and the spent
        // half gets mended up to it. Not decoration - a golem with no target for anything has no
        // orders at all, and one with no orders never lifts its tool.
        for (SurfaceFamily family : QUARTERS) {
            golem.setTargetFor(family, 50);
        }

        // The Wayfarer, because a demo that runs out of tool in ten minutes is a demo of a golem
        // standing still. Everything after it is what the golem mends with.
        if (ModItems.magicTamper() != null) {
            golem.setInventorySlotContents(0, new ItemStack(ModItems.magicTamper(), 1, 0));
        }
        // Two slots left empty on purpose. A golem puts what it finds into storage and can only
        // stack onto something it is already holding, so a golem stocked to the last slot has
        // nowhere to put the first sapling it turns up and drops it on the floor - which is the
        // one behaviour the storage upgrade's pen is there to show off.
        List<ItemStack> stock = healingBlocks();
        int last = Math.max(1, golem.slotCount() - 2);
        for (int slot = 1; slot < last; slot++) {
            golem.setInventorySlotContents(
                slot,
                stock.get((slot - 1) % stock.size())
                    .copy());
        }
        return world.spawnEntityInWorld(golem);
    }

    /** One stack of each ground a pen is laid with, plus the two the roads add. */
    private static List<ItemStack> healingBlocks() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        out.add(new ItemStack(Blocks.dirt, 64, 0));
        out.add(new ItemStack(Blocks.grass, 64, 0));
        out.add(new ItemStack(Blocks.gravel, 64, 0));
        out.add(new ItemStack(Blocks.cobblestone, 64, 0));
        out.add(new ItemStack(Blocks.sand, 64, 0));
        out.add(new ItemStack(Blocks.stone, 64, 0));
        return out;
    }

    /** What the pen's first chest holds: a few tools, and a way to mend by hand beside them. */
    private static List<ItemStack> toolChest() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (ModItems.magicTamper() != null) out.add(new ItemStack(ModItems.magicTamper(), 1, 0));
        if (ModItems.chunkTamper() != null) {
            for (TamperGrade grade : TamperGrade.available()) {
                ItemStack graded = new ItemStack(ModItems.chunkTamper(), 1, 0);
                ItemChunkTamper.setGrade(graded, grade);
                out.add(graded);
            }
        }
        if (ModItems.gradedTamper() != null) out.add(new ItemStack(ModItems.gradedTamper(), 1, 0));
        out.add(new ItemStack(Items.dye, 64, 15));
        return out;
    }

    /** What the pen's second chest holds: more of everything the golem spends. */
    private static List<ItemStack> healingChest() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (int repeat = 0; repeat < 4; repeat++) {
            for (ItemStack stack : healingBlocks()) {
                out.add(stack.copy());
            }
        }
        out.add(new ItemStack(Items.dye, 64, 15));
        return out;
    }

    /**
     * A golem's name laid across three lines of a sign.
     *
     * <p>
     * Fifteen characters is what the game lets anybody type onto a line and what its renderer lays
     * out for, and every one of these names but the plainest is longer than that. Wrapped at the
     * spaces rather than cut, because "Golem of Sparin" is a name nobody would recognise and it is
     * a pack's lang file that decides how long these get, not this.
     */
    private static String[] wrap(String text) {
        String[] lines = { "", "", "" };
        if (text == null) return lines;
        int at = 0;
        for (String word : text.trim()
            .split("\\s+")) {
            if (word.isEmpty()) continue;
            if (lines[at].isEmpty()) {
                lines[at] = word.length() <= 15 ? word : word.substring(0, 15);
            } else if (lines[at].length() + 1 + word.length() <= 15) {
                lines[at] = lines[at] + " " + word;
            } else if (at < lines.length - 1) {
                lines[++at] = word.length() <= 15 ? word : word.substring(0, 15);
            }
        }
        return lines;
    }

    /** Places a block only where it is not already what it should be, which makes a re-run cheap. */
    private static void set(World world, int x, int y, int z, Block block, int meta) {
        if (!world.blockExists(x, y, z)) return;
        if (world.getBlock(x, y, z) == block && world.getBlockMetadata(x, y, z) == meta) return;
        world.setBlock(x, y, z, block, meta, 2);
    }
}
