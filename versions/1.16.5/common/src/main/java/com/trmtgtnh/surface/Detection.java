package com.trmtgtnh.surface;

import java.util.Locale;

/**
 * The rules of detection, with no game in them: which family a block's facts make it (W4 spec 3.2).
 *
 * <p>
 * {@link SurfaceRegistry#resolve} walks the block registry and asks {@link BlockFacts#of} what a block is, then asks
 * this class what follows. Split so the census can write those facts down and every tree's suite can put each pack's
 * whole registry through the rules again in seconds, with no pack on disk and no game ({@code DetectionReplayTest}):
 * a change here shows as the lines that moved, which is what makes reviewing each pack's diff before a change lands
 * affordable (D34).
 *
 * <p>
 * Material leads, name only excludes. An earlier version required the name to say "grass" or "dirt" as well, and that
 * quietly missed things: Biomes O' Plenty's mud registers as {@code mud} on the <em>sand</em> material, so it matched
 * neither the dirt rule nor the sand one. Asking what a block is made of and then ruling out the near-misses by name
 * catches modded terrain whatever its author decided to call it.
 *
 * <p>
 * Leading with material is only safe because detection writes what it decided into the config. Anything it over-reaches
 * on is visible in a list and one edit away, which is a far better failure than terrain that silently never wears.
 *
 * <p>
 * <strong>Every step reads {@link BlockFacts} and nothing else</strong>, and in order: this class is what decides which
 * questions a live block is asked, so an earlier return still means the rest are never put to it. A rule that wants a
 * new fact adds a method there and a word to its line in the golden file, or the replay stops being able to answer for
 * that rule.
 *
 * <p>
 * The same steps as the 1.7.10 edition's, in its order, with this edition's own where it always stood: {@code
 * neverGround} after snow and before ice. Dirt is three of the game's blocks by identity rather than a class, and a
 * slab says what it is made of by its own name; both arrive here as facts like any other.
 */
public final class Detection {

    private Detection() {}

    /**
     * Which family these facts make a block, or null for one that does not wear. Asked of the name as the facts carry
     * it, so a slab's own material word is asked by {@link #refineSlab} through the same steps.
     */
    public static SurfaceFamily classify(BlockFacts facts) {
        String lower = facts.name()
            .toLowerCase(Locale.ROOT);
        String simple = lower.substring(lower.indexOf(':') + 1);
        String material = facts.material();
        SurfaceShape shape = facts.shape();
        // A slab is named for the block it was cut from with the shape tacked on, and every rule
        // below that reads a name wants the material rather than the shape: "stone_slab" must
        // reach the same answer "stone" does, and "sandstone_slab" must be refused for the same
        // reason sandstone is. Stripping the word is what makes one set of rules serve both.
        //
        // Asked of the class rather than of the shape, because a doubled slab is a full cube and
        // still a slab by name - and "slab" is on the decorative-stonework list, so leaving the
        // word on refused every doubled slab in the game before its metadata was ever consulted.
        // A stair is always partial by its class (SurfaceShape.ofClass), so the shape speaks for it.
        if (shape.isPartial() || facts.slab()) {
            simple = withoutShapeWords(simple);
        }

        if (BlockFacts.CLASS_LEAVES.equals(facts.vanillaClass()) || BlockFacts.LEAVES.equals(material)) {
            return SurfaceFamily.LEAVES;
        }

        if ((BlockFacts.PLANTS.equals(material) || BlockFacts.VINE.equals(material)) && !facts.cube()) {
            return SurfaceFamily.VEGETATION;
        }

        // Anything that is not a full opaque cube cannot carry a path overlay: a slab or a
        // fence would show the ghost block's geometry rather than its own. This is also what
        // keeps farmland and grass paths out, both of which stand a pixel short.
        // Ice is the exception to opacity, not to shape: it fills its space exactly, it is
        // simply clear. Asking for a full cube by sight would refuse every frozen surface in
        // the game, so this asks whether the block fills its space instead.
        boolean clearButSolid = BlockFacts.ICE.equals(material) || BlockFacts.PACKED_ICE.equals(material);
        // A slab is the one thing that is not a full cube and is still plainly ground: you walk on
        // it, and the top of it is the same sixteen by sixteen picture the block it was cut from
        // has. Whether it counts is the family's own decision, taken once the family is known, so
        // the shape is let past here and refused further down.
        if (!shape.isPartial() && (!facts.cube() || (!facts.opaque() && !clearButSolid))) {
            return null;
        }

        if (facts.keepsState()) return null;

        // Extending the vanilla class is the strongest signal there is, and costs one check.
        if (BlockFacts.CLASS_GRASS.equals(facts.vanillaClass())) return SurfaceFamily.GRASS;
        if (BlockFacts.CLASS_DIRT.equals(facts.vanillaClass())) return SurfaceFamily.DIRT;
        if (BlockFacts.CLASS_GRAVEL.equals(facts.vanillaClass())) return SurfaceFamily.GRAVEL;
        if (BlockFacts.CLASS_SAND.equals(facts.vanillaClass())) return SurfaceFamily.SAND;

        if (isWorkedGround(simple)) return null;

        if (BlockFacts.GRASS.equals(material)) return SurfaceFamily.GRASS;
        if (BlockFacts.GROUND.equals(material)) return SurfaceFamily.DIRT;

        // Snow and ice are led by material rather than by name, and for once that is the safe
        // way round: these materials name exactly the blocks meant, where "ice" as a word turns
        // up inside pumice, lattice and half the pack's chemistry.
        if (BlockFacts.CRAFTED_SNOW.equals(material) || BlockFacts.SNOW.equals(material)) return SurfaceFamily.SNOW;
        // Except frosted ice, which is ice by material and not ground at all: Frost Walker lays it and
        // the game melts it again within seconds. Counted, it put a platform of it in every
        // demonstrate yard, which melted and poured off the edge - found by the 0.9.219 photograph
        // pass, as a waterfall the 1.7.10 yard does not have. That edition has no such block, so
        // leaving it out is what its rule, the ground of frozen water, means here.
        if (facts.neverGround()) return null;
        if (BlockFacts.ICE.equals(material) || BlockFacts.PACKED_ICE.equals(material)) return SurfaceFamily.ICE;

        if (BlockFacts.SAND.equals(material)) {
            if (contains(simple, "soul")) return null;
            if (contains(simple, "gravel")) return SurfaceFamily.GRAVEL;
            // Mud, silt and clay are earth that happens to be built on sand's material; they
            // should wear like ground rather than like a beach.
            if (contains(simple, "mud", "loam", "silt", "clay", "earth")) return SurfaceFamily.DIRT;
            if (contains(simple, "stone")) return null; // sandstone and its many cousins
            return SurfaceFamily.SAND;
        }

        boolean rock = BlockFacts.ROCK.equals(material);

        // The nether and the end are checked before the decorative-stonework filter rather
        // than after, because their brick and tile forms are simply the ground of those places
        // rather than something a builder laid, and that filter would throw them out on the
        // word "brick" alone. Ores are still excluded: a road worn across an ore seam should
        // wear the seam's surroundings, not the seam.
        if (rock && !contains(simple, "ore")) {
            if (contains(simple, "nether") && !contains(simple, "quartz")) return SurfaceFamily.NETHER;
            if (contains(simple, "endstone", "end_stone", "purpur", "endbrick", "end_brick")) {
                return SurfaceFamily.END;
            }
        }

        // Rock stays name-led: the material covers ores, casings and half the decorative blocks
        // in the pack, so leading with it here would sweep up far more than ground.
        if (rock && !isDecorativeStonework(simple)) {
            if (contains(simple, "cobble")) return SurfaceFamily.COBBLE;
            if (contains(simple, "stone", "granite", "diorite", "andesite", "marble", "basalt", "limestone", "slate")) {
                return SurfaceFamily.STONE;
            }
        }

        return null;
    }

    /**
     * Which family a slab belongs to, given what the block as a whole came to.
     *
     * <p>
     * A slab block was once several materials wearing one name: 1.12.2 packs stone, sandstone, wood, cobble, brick,
     * stone brick, nether brick and quartz into a single id and says per metadata value which is which. 1.13 gave each
     * material a slab block of its own, so {@code granite_slab} says granite in the one place this was already looking:
     * the name. The last segment after a dot is the word, with the shape words taken off it for the same reason they come
     * off a registry name, and that word then goes through the ordinary rules, so a slab is classified by exactly what a
     * whole block of the same stuff would be. Every value of the block asks the same here, as this game has none.
     *
     * <p>
     * A name that is empty, or that leaves nothing once its shape is off, is left as whatever the block's name
     * suggested, which is what every slab got before this existed.
     */
    public static SurfaceFamily refineSlab(BlockFacts facts, SurfaceFamily whole, int meta) {
        if (!facts.slab()) return whole;

        String subtype = facts.name();
        if (subtype == null || subtype.isEmpty()) return whole;

        String word = withoutShapeWords(
            subtype.substring(subtype.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT));
        if (word.isEmpty()) return whole;

        // Whatever the word says, including nothing. A slab of stone brick is stone brick, and a
        // whole block of stone brick is decorative stonework this mod leaves alone, so a slab of it
        // is left alone too - falling back on the block's name here would have wandered off in the
        // other direction and worn it as plain stone.
        return classify(facts.named("minecraft:" + word));
    }

    /**
     * Whether a family lets this shape wear, once the family is known.
     *
     * <p>
     * The shape is asked last rather than first. What a slab is made of is the same question as
     * what a whole block of it is made of, and it is answered by the same rules; whether a family
     * lets its slabs wear is a separate decision that cannot be taken until the family is known.
     *
     * @param slabs  whether the family lets its slabs wear
     * @param stairs whether the family lets its stairs wear
     */
    public static SurfaceFamily letIn(SurfaceFamily family, SurfaceShape shape, boolean slabs, boolean stairs) {
        if (family == null || !shape.isPartial()) return family;
        if (shape == SurfaceShape.SLAB && !slabs) return null;
        if (shape == SurfaceShape.STAIR && !stairs) return null;
        return family;
    }

    /**
     * A shaped block's name with the shape taken off it.
     *
     * <p>
     * Only ever applied to a block whose class already said what shape it is, so this is not
     * guessing from a name - it is removing a word the name is known to contain. That matters,
     * because "step" and "slab" turn up inside plenty of blocks that are neither.
     */
    static String withoutShapeWords(String simple) {
        String out = simple;
        for (String word : SHAPE_WORDS) {
            if (out.endsWith(word)) {
                out = out.substring(0, out.length() - word.length());
                break;
            }
        }
        while (out.endsWith("_") || out.endsWith(".")) out = out.substring(0, out.length() - 1);
        // And the front, because two slabs stacked are a block of their own and vanilla names it
        // by saying so: double_stone_slab has to reach the same answer stone does.
        if (out.startsWith("double_")) out = out.substring("double_".length());
        else if (out.startsWith("double")) out = out.substring("double".length());
        while (out.startsWith("_") || out.startsWith(".")) out = out.substring(1);
        return out.isEmpty() ? simple : out;
    }

    /** Longest first, so "_slabs" is not left with a trailing s by the shorter match. */
    private static final String[] SHAPE_WORDS = { "_halfslab", "halfslab", "_slabs", "_slab", "slabs", "slab",
        "_stairs", "_stair", "stairs", "stair", "_steps", "_step", "steps" };

    /**
     * Ground somebody has already made something of, which should stay as they left it.
     *
     * <p>
     * The bale entries are not tilled ground at all - a hay bale is built on the grass material,
     * so leading with material sweeps it in as turf. It is a stored crop sitting in a barn, not
     * a field anyone walks a path across.
     */
    static boolean isWorkedGround(String simple) {
        return contains(
            simple,
            "path",
            "farmland",
            "tilled",
            "soil",
            "crop",
            "garden",
            "planter",
            "hay",
            "bale",
            "thatch",
            "straw");
    }

    /**
     * Filters out worked stone and everything wearing a stone material for other reasons.
     * GregTech alone contributes a great many rock-material blocks that are machinery,
     * casings or ores rather than ground you could wear a track into.
     */
    static boolean isDecorativeStonework(String simple) {
        return contains(
            simple,
            "ore",
            "brick",
            "casing",
            "machine",
            "wall",
            "stair",
            "slab",
            "chiseled",
            "carved",
            "pillar",
            "sandstone",
            "redstone",
            "lodestone",
            "grindstone",
            "gemstone",
            "furnace",
            "block_of",
            "polished",
            "tile",
            "pressure",
            "button",
            "monster",
            "spawner",
            "obsidian",
            "netherrack",
            "endstone",
            "end_stone");
    }

    static boolean contains(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }
}
