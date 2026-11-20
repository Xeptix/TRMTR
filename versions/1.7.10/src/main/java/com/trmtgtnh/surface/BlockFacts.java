package com.trmtgtnh.surface;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockGravel;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.BlockSand;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;

/**
 * Every input {@link Detection} has, behind one interface (W4 spec 3.2), so the rules can be asked of a live block in a
 * game or of a line of a golden file with no game.
 *
 * <p>
 * <strong>{@link #of} asks a block only what the rules reach, when they reach it.</strong> {@link Detection#classify}
 * drives the questions, in the order the rules read them, and each is put to the block at that moment and every time:
 * nothing is asked ahead and nothing is remembered. A pack's blocks throw, keep state and run slowly in these methods,
 * and one returning early from the rules has to stay a block that was never asked the rest. An eager read would give
 * the same families and still be a different program: a mod's {@code Error} from a question nobody used to ask would
 * stop a game that starts today. So no question here is guarded that was not guarded before, and what used to stop the
 * game still does.
 *
 * <p>
 * {@link #dump} is the opposite and only the census calls it: everything, of every block, written down as {@link Dump}
 * for the golden file, each question guarded because nothing else is asking it. {@link #parse} reads that back with no
 * game, which is what {@code DetectionReplayTest} replays.
 *
 * <p>
 * The word for a {@link Material} is the name of its field on that class, read from the game's own source; a material a
 * mod made for itself has none, and is {@link #OTHER}, which no rule reads.
 *
 * <p>
 * Carries (W4 T8-T11): {@link #vanillaClass} is the answer step 5 of the rules gives, not a Java class's name, so an
 * edition with other tests fits it: 26.3's {@code block == Blocks.DIRT || COARSE_DIRT || PODZOL} gives {@code dirt},
 * and
 * its {@code ColoredFallingBlock} gives {@code gravel} or {@code sand} by the block's sound. What cannot fold into a
 * method here gets a method there: 1.12.2's {@code wholeButSeenThrough} and {@code neverGround}, and the modern
 * editions' {@code neverGround}. {@link #cube} and {@link #opaque} are each edition's own two questions.
 */
public interface BlockFacts {

    String LEAVES = "leaves";
    String PLANTS = "plants";
    String VINE = "vine";
    String ICE = "ice";
    String PACKED_ICE = "packedIce";
    String GRASS = "grass";
    String GROUND = "ground";
    String CRAFTED_SNOW = "craftedSnow";
    String SNOW = "snow";
    String SAND = "sand";
    String ROCK = "rock";

    /** A material no field of {@link Material} holds: a mod's own. */
    String OTHER = "other";

    String CLASS_LEAVES = "leaves";
    String CLASS_GRASS = "grass";
    String CLASS_DIRT = "dirt";
    String CLASS_GRAVEL = "gravel";
    String CLASS_SAND = "sand";

    /** How many values a slab says apart: its upper-half bit is no part of what it is made of. */
    int SUBTYPES = 8;

    /** How many metadata values a block has, each asked on its own whether it keeps a tile entity. */
    int VALUES = 16;

    /** The name the rules read, as the registry spells it. */
    String name();

    /** What it is made of: a field name of {@link Material}, or {@link #OTHER}. */
    String material();

    /** Its shape by class alone: a data file's shape is no part of detection. */
    SurfaceShape shape();

    /** Whether it extends {@code BlockSlab}: a doubled slab is one, and a full cube, so {@link #shape} cannot say. */
    boolean slab();

    /** {@code renderAsNormalBlock}. */
    boolean cube();

    /** {@code isOpaqueCube}. */
    boolean opaque();

    /**
     * Whether it keeps state of its own at the position at this metadata value: the answer of
     * {@code SurfaceRegistry.keepsItsOwnState} for that value alone, which counts a value that cannot answer as one
     * that does (Xep, 2026-11-09, "Fix it, per-value rule").
     */
    boolean keepsStateAt(int meta);

    /**
     * What extending a vanilla class says it is, one of the {@code CLASS_} words, or null. One word holds it: these
     * classes extend nothing of each other, so a block is at most one of them.
     */
    String vanillaClass();

    /**
     * What a slab says it is made of at a metadata value, or null where it threw or said nothing, which is the rules'
     * fall back to the block as a whole.
     */
    String subtypeAt(int meta);

    /** The same block under another name: a slab's own material word is asked of the block it is. */
    BlockFacts named(String other);

    /** The live path: nothing is asked until {@link Detection} asks it. */
    static BlockFacts of(Block block, String name) {
        return new Asked(block, name);
    }

    /** By identity, as the rules always compared them: a material is an object with no name of its own. */
    static String wordFor(Material material) {
        if (material == Material.air) return "air";
        if (material == Material.grass) return GRASS;
        if (material == Material.ground) return GROUND;
        if (material == Material.wood) return "wood";
        if (material == Material.rock) return ROCK;
        if (material == Material.iron) return "iron";
        if (material == Material.anvil) return "anvil";
        if (material == Material.water) return "water";
        if (material == Material.lava) return "lava";
        if (material == Material.leaves) return LEAVES;
        if (material == Material.plants) return PLANTS;
        if (material == Material.vine) return VINE;
        if (material == Material.sponge) return "sponge";
        if (material == Material.cloth) return "cloth";
        if (material == Material.fire) return "fire";
        if (material == Material.sand) return SAND;
        if (material == Material.circuits) return "circuits";
        if (material == Material.carpet) return "carpet";
        if (material == Material.glass) return "glass";
        if (material == Material.redstoneLight) return "redstoneLight";
        if (material == Material.tnt) return "tnt";
        if (material == Material.coral) return "coral";
        if (material == Material.ice) return ICE;
        if (material == Material.packedIce) return PACKED_ICE;
        if (material == Material.snow) return SNOW;
        if (material == Material.craftedSnow) return CRAFTED_SNOW;
        if (material == Material.cactus) return "cactus";
        if (material == Material.clay) return "clay";
        if (material == Material.gourd) return "gourd";
        if (material == Material.dragonEgg) return "dragonEgg";
        if (material == Material.portal) return "portal";
        if (material == Material.cake) return "cake";
        if (material == Material.web) return "web";
        if (material == Material.piston) return "piston";
        return OTHER;
    }

    /** A live block, asked each time a rule asks. */
    final class Asked implements BlockFacts {

        private final Block block;
        private final String name;

        Asked(Block block, String name) {
            this.block = block;
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String material() {
            return wordFor(block.getMaterial());
        }

        @Override
        public SurfaceShape shape() {
            return SurfaceShape.of(block);
        }

        @Override
        public boolean slab() {
            return block instanceof BlockSlab;
        }

        @Override
        public boolean cube() {
            return block.renderAsNormalBlock();
        }

        @Override
        public boolean opaque() {
            return block.isOpaqueCube();
        }

        @Override
        public boolean keepsStateAt(int meta) {
            return SurfaceRegistry.keepsItsOwnState(block, meta);
        }

        @Override
        public String vanillaClass() {
            if (block instanceof BlockLeavesBase) return CLASS_LEAVES;
            if (block instanceof BlockGrass) return CLASS_GRASS;
            if (block instanceof BlockDirt) return CLASS_DIRT;
            if (block instanceof BlockGravel) return CLASS_GRAVEL;
            if (block instanceof BlockSand) return CLASS_SAND;
            return null;
        }

        @Override
        public String subtypeAt(int meta) {
            if (!(block instanceof BlockSlab)) return null;
            try {
                return ((BlockSlab) block).func_150002_b(meta & (SUBTYPES - 1));
            } catch (RuntimeException awkwardSlab) {
                // A slab that cannot say what it is is left as whatever its name suggested, which is
                // what every slab got before this existed.
                return null;
            }
        }

        @Override
        public BlockFacts named(String other) {
            return new Asked(block, other);
        }
    }

    /**
     * Everything about a block, asked at once. For the census alone, which asks every block of a pack by name in a run
     * of its own: each question is guarded, because here it is asked of blocks the rules would never have put it to.
     * A throw gives the answer that keeps a block out.
     */
    static Dump dump(Block block, String name) {
        BlockFacts asked = of(block, name);
        boolean cube;
        try {
            cube = asked.cube();
        } catch (RuntimeException | LinkageError awkward) {
            cube = false;
        }
        boolean opaque;
        try {
            opaque = asked.opaque();
        } catch (RuntimeException | LinkageError awkward) {
            opaque = false;
        }
        boolean[] keeps = new boolean[VALUES];
        for (int value = 0; value < VALUES; value++) {
            try {
                keeps[value] = asked.keepsStateAt(value);
            } catch (LinkageError awkward) {
                keeps[value] = true;
            }
        }
        String material;
        try {
            material = asked.material();
        } catch (RuntimeException | LinkageError awkward) {
            material = OTHER;
        }
        String[] subtypes = new String[SUBTYPES];
        for (int value = 0; value < SUBTYPES; value++) {
            try {
                subtypes[value] = asked.subtypeAt(value);
            } catch (LinkageError awkward) {
                subtypes[value] = null;
            }
        }
        return new Dump(
            name,
            material,
            asked.shape(),
            asked.slab(),
            cube,
            opaque,
            keeps,
            asked.vanillaClass(),
            subtypes);
    }

    /**
     * Reads {@link Dump#textAt} back for the name on the same line. Throws on what it cannot read: a golden line nobody
     * can replay is to be seen, not passed over.
     */
    static Dump parse(String name, String text) {
        String material = null;
        String flags = null;
        String vanillaClass = null;
        String subtype = null;
        for (String part : text.trim()
            .split(" +")) {
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            String value = part.substring(equals + 1);
            if ("m".equals(key)) material = value;
            else if ("f".equals(key)) flags = value;
            else if ("c".equals(key)) vanillaClass = value;
            else if ("s".equals(key)) subtype = Dump.unspelt(value);
            else throw new IllegalArgumentException("no such fact '" + part + "' on " + name + ": " + text);
        }
        if (material == null || flags == null) {
            throw new IllegalArgumentException("no material or flags on " + name + ": " + text);
        }
        boolean opaque = false, cube = false, keeps = false, slab = false, half = false, stair = false;
        if (!"-".equals(flags)) {
            for (String flag : flags.split(",")) {
                if ("opaque".equals(flag)) opaque = true;
                else if ("cube".equals(flag)) cube = true;
                else if ("keeps".equals(flag)) keeps = true;
                else if ("slab".equals(flag)) slab = true;
                else if ("half".equals(flag)) half = true;
                else if ("stair".equals(flag)) stair = true;
                else throw new IllegalArgumentException("no such flag '" + flag + "' on " + name + ": " + text);
            }
        }
        String[] subtypes = new String[SUBTYPES];
        Arrays.fill(subtypes, subtype);
        // A line is the values it names, so what it says of a tile entity it says of each of them.
        boolean[] keepsAt = new boolean[VALUES];
        Arrays.fill(keepsAt, keeps);
        SurfaceShape shape = half ? SurfaceShape.SLAB : stair ? SurfaceShape.STAIR : SurfaceShape.FULL;
        return new Dump(name, material, shape, slab, cube, opaque, keepsAt, vanillaClass, subtypes);
    }

    /** A block's facts as plain values: written by the census, read from a golden file, and game-free. */
    final class Dump implements BlockFacts {

        private final String name;
        private final String material;
        private final SurfaceShape shape;
        private final boolean slab;
        private final boolean cube;
        private final boolean opaque;
        private final boolean[] keepsState;
        private final String vanillaClass;
        private final String[] subtypes;

        Dump(String name, String material, SurfaceShape shape, boolean slab, boolean cube, boolean opaque,
            boolean[] keepsState, String vanillaClass, String[] subtypes) {
            this.name = name;
            this.material = material;
            this.shape = shape;
            this.slab = slab;
            this.cube = cube;
            this.opaque = opaque;
            this.keepsState = keepsState;
            this.vanillaClass = vanillaClass;
            this.subtypes = subtypes;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String material() {
            return material;
        }

        @Override
        public SurfaceShape shape() {
            return shape;
        }

        @Override
        public boolean slab() {
            return slab;
        }

        @Override
        public boolean cube() {
            return cube;
        }

        @Override
        public boolean opaque() {
            return opaque;
        }

        @Override
        public boolean keepsStateAt(int meta) {
            return keepsState[meta & (VALUES - 1)];
        }

        @Override
        public String vanillaClass() {
            return vanillaClass;
        }

        @Override
        public String subtypeAt(int meta) {
            return subtypes[meta & (SUBTYPES - 1)];
        }

        @Override
        public BlockFacts named(String other) {
            return new Dump(other, material, shape, slab, cube, opaque, keepsState, vanillaClass, subtypes);
        }

        /**
         * The facts at one metadata value as the golden file writes them: {@code m=} material, {@code f=} the flags or
         * {@code -}, {@code c=} the class word and {@code s=} the slab's subtype where there is one. A value with no
         * {@code s=} fell back on the block as a whole.
         */
        public String textAt(int meta) {
            StringBuilder flags = new StringBuilder();
            flag(flags, opaque, "opaque");
            flag(flags, cube, "cube");
            flag(flags, keepsState[meta & (VALUES - 1)], "keeps");
            flag(flags, slab, "slab");
            flag(flags, shape == SurfaceShape.SLAB, "half");
            flag(flags, shape == SurfaceShape.STAIR, "stair");
            StringBuilder out = new StringBuilder("m=").append(material)
                .append(" f=")
                .append(flags.length() == 0 ? "-" : flags.toString());
            if (vanillaClass != null) out.append(" c=")
                .append(vanillaClass);
            String subtype = subtypeAt(meta);
            if (subtype != null) out.append(" s=")
                .append(spelt(subtype));
            return out.toString();
        }

        private static void flag(StringBuilder flags, boolean set, String word) {
            if (!set) return;
            if (flags.length() > 0) flags.append(',');
            flags.append(word);
        }

        /** A subtype is a mod's own string and the line is split on spaces. */
        private static String spelt(String subtype) {
            StringBuilder out = new StringBuilder();
            for (int at = 0; at < subtype.length(); at++) {
                char each = subtype.charAt(at);
                if (each == '%' || each <= ' ' || each == 0x7f) out.append(String.format("%%%02x", (int) each));
                else out.append(each);
            }
            return out.toString();
        }

        static String unspelt(String spelt) {
            StringBuilder out = new StringBuilder();
            for (int at = 0; at < spelt.length(); at++) {
                char each = spelt.charAt(at);
                if (each == '%' && at + 2 < spelt.length()) {
                    out.append((char) Integer.parseInt(spelt.substring(at + 1, at + 3), 16));
                    at += 2;
                } else {
                    out.append(each);
                }
            }
            return out.toString();
        }
    }
}
