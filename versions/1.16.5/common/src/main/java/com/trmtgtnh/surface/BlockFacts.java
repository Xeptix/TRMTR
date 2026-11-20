package com.trmtgtnh.surface;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.GravelBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.material.Material;

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
 * This edition asks a block's default state for its material, its opacity and its collision shape, and decides the
 * whole block from that one state, as the rules always have. Of those, only the collision shape and the loader's word
 * on a tile entity reach a block's own code: the material and the opacity are what the block's properties said when
 * it was made, kept on the state. A slab says what it is made of by its name, as each material has had a block of its
 * own since 1.13, so the golden line has no subtype here. The word for a {@link Material} is the name of its field as
 * the 1.7.10 edition spells it, where that edition has the material ({@code ground}, {@code rock}, {@code packedIce},
 * {@code craftedSnow} and {@code snow} for the layer), so a word means one material in every game's golden files; the
 * materials 1.7.10 has not got take this game's field name in the same spelling ({@code waterPlant},
 * {@code netherWood}). A material a mod made for itself has none, and is {@link #OTHER}, which no rule reads.
 *
 * <p>
 * One fact this edition has and 1.7.10 does not, with the step that reads it: {@link #neverGround} (frosted ice), read
 * after snow and before ice, as 1.12.2's is. Without it frosted ice would silently move. 1.12.2's other,
 * {@code fullBlock}, is not this game's: its cube test asks the collision shape and nothing else, so no rule here
 * reads it and no line writes it.
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

    /** The name the rules read, as the registry spells it. */
    String name();

    /** What its default state is made of: a word for a field of {@link Material}, or {@link #OTHER}. */
    String material();

    /** Its shape by class alone ({@code SurfaceShape.ofClass}): a data file's shape is no part of detection. */
    SurfaceShape shape();

    /**
     * Whether it extends {@code SlabBlock}: a slab whose default state is doubled is one, and a full cube, so
     * {@link #shape} cannot say.
     */
    boolean slab();

    /** {@code Worlds.fullCube} of the default state: its collision shape fills the cell, and a throw says no. */
    boolean cube();

    /** {@code canOcclude} of the default state. */
    boolean opaque();

    /**
     * Whether it keeps state of its own at the position: the one answer of {@code SurfaceRegistry.keepsItsOwnState},
     * which asks {@code EntityBlock} and the loader's own word, and counts a block that cannot answer as one that does.
     */
    boolean keepsState();

    /**
     * What a vanilla class or block says it is, one of the {@code CLASS_} words, or null. One word holds it: a block
     * extends at most one of {@code LeavesBlock}, {@code GrassBlock}, {@code GravelBlock} and {@code SandBlock}, and
     * dirt is three of the game's own blocks by identity ({@code dirt}, {@code coarse_dirt}, {@code podzol}), none of
     * which extends any of those.
     */
    String vanillaClass();

    /**
     * Whether it is the one block that is never ground however it is named or listed: frosted ice, which is ice by
     * material and melts within seconds ({@code SurfaceRegistry.neverGround}).
     */
    boolean neverGround();

    /** The same block under another name: a slab's own material word is asked of the block it is. */
    BlockFacts named(String other);

    /** The live path: nothing is asked until {@link Detection} asks it. */
    static BlockFacts of(Block block, String name) {
        return new Asked(block, name);
    }

    /** By identity, as the rules always compared them: a material is an object with no name of its own. */
    static String wordFor(Material material) {
        if (material == Material.AIR) return "air";
        if (material == Material.STRUCTURAL_AIR) return "structureVoid";
        if (material == Material.PORTAL) return "portal";
        if (material == Material.CLOTH_DECORATION) return "carpet";
        if (material == Material.PLANT) return PLANTS;
        if (material == Material.WATER_PLANT) return "waterPlant";
        if (material == Material.REPLACEABLE_PLANT) return VINE;
        if (material == Material.REPLACEABLE_FIREPROOF_PLANT) return "replaceableFireproofPlant";
        if (material == Material.REPLACEABLE_WATER_PLANT) return "replaceableWaterPlant";
        if (material == Material.WATER) return "water";
        if (material == Material.BUBBLE_COLUMN) return "bubbleColumn";
        if (material == Material.LAVA) return "lava";
        if (material == Material.TOP_SNOW) return SNOW;
        if (material == Material.FIRE) return "fire";
        if (material == Material.DECORATION) return "circuits";
        if (material == Material.WEB) return "web";
        if (material == Material.BUILDABLE_GLASS) return "redstoneLight";
        if (material == Material.CLAY) return "clay";
        if (material == Material.DIRT) return GROUND;
        if (material == Material.GRASS) return GRASS;
        if (material == Material.ICE_SOLID) return PACKED_ICE;
        if (material == Material.SAND) return SAND;
        if (material == Material.SPONGE) return "sponge";
        if (material == Material.SHULKER_SHELL) return "shulkerShell";
        if (material == Material.WOOD) return "wood";
        if (material == Material.NETHER_WOOD) return "netherWood";
        if (material == Material.BAMBOO_SAPLING) return "bambooSapling";
        if (material == Material.BAMBOO) return "bamboo";
        if (material == Material.WOOL) return "cloth";
        if (material == Material.EXPLOSIVE) return "tnt";
        if (material == Material.LEAVES) return LEAVES;
        if (material == Material.GLASS) return "glass";
        if (material == Material.ICE) return ICE;
        if (material == Material.CACTUS) return "cactus";
        if (material == Material.STONE) return ROCK;
        if (material == Material.METAL) return "iron";
        if (material == Material.SNOW) return CRAFTED_SNOW;
        if (material == Material.HEAVY_METAL) return "anvil";
        if (material == Material.BARRIER) return "barrier";
        if (material == Material.PISTON) return "piston";
        if (material == Material.CORAL) return "coral";
        if (material == Material.VEGETABLE) return "gourd";
        if (material == Material.EGG) return "dragonEgg";
        if (material == Material.CAKE) return "cake";
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
            return wordFor(
                block.defaultBlockState()
                    .getMaterial());
        }

        @Override
        public SurfaceShape shape() {
            // By class alone: detection classifies before any data is published (W3 T6, as 1.12.2 and 1.7.10).
            return SurfaceShape.ofClass(block.defaultBlockState());
        }

        @Override
        public boolean slab() {
            return block instanceof SlabBlock;
        }

        @Override
        public boolean cube() {
            return com.trmtgtnh.util.Worlds.fullCube(block.defaultBlockState());
        }

        @Override
        public boolean opaque() {
            return block.defaultBlockState()
                .canOcclude();
        }

        @Override
        public boolean keepsState() {
            return SurfaceRegistry.keepsItsOwnState(block);
        }

        @Override
        public String vanillaClass() {
            if (block instanceof LeavesBlock) return CLASS_LEAVES;
            if (block instanceof GrassBlock) return CLASS_GRASS;
            if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.PODZOL) return CLASS_DIRT;
            if (block instanceof GravelBlock) return CLASS_GRAVEL;
            if (block instanceof SandBlock) return CLASS_SAND;
            return null;
        }

        @Override
        public boolean neverGround() {
            return SurfaceRegistry.neverGround(block);
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
        boolean keeps;
        try {
            keeps = asked.keepsState();
        } catch (LinkageError awkward) {
            keeps = true;
        }
        String material;
        try {
            material = asked.material();
        } catch (RuntimeException | LinkageError awkward) {
            material = OTHER;
        }
        SurfaceShape shape;
        try {
            shape = asked.shape();
        } catch (RuntimeException | LinkageError awkward) {
            shape = SurfaceShape.FULL;
        }
        return new Dump(name, material, shape, asked.slab(), cube, opaque, keeps, asked.vanillaClass(),
            asked.neverGround());
    }

    /**
     * Reads {@link Dump#text} back for the name on the same line. Throws on what it cannot read: a golden line nobody
     * can replay is to be seen, not passed over. This game writes no {@code s=}, and 1.12.2's {@code fullblock} is no
     * flag of its own, so both are refused here as any unknown word is.
     */
    static Dump parse(String name, String text) {
        String material = null;
        String flags = null;
        String vanillaClass = null;
        for (String part : text.trim()
            .split(" +")) {
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            String value = part.substring(equals + 1);
            if ("m".equals(key)) material = value;
            else if ("f".equals(key)) flags = value;
            else if ("c".equals(key)) vanillaClass = value;
            else throw new IllegalArgumentException("no such fact '" + part + "' on " + name + ": " + text);
        }
        if (material == null || flags == null) {
            throw new IllegalArgumentException("no material or flags on " + name + ": " + text);
        }
        boolean opaque = false, cube = false, keeps = false, slab = false, half = false, stair = false, never = false;
        if (!"-".equals(flags)) {
            for (String flag : flags.split(",")) {
                if ("opaque".equals(flag)) opaque = true;
                else if ("cube".equals(flag)) cube = true;
                else if ("keeps".equals(flag)) keeps = true;
                else if ("slab".equals(flag)) slab = true;
                else if ("half".equals(flag)) half = true;
                else if ("stair".equals(flag)) stair = true;
                else if ("neverground".equals(flag)) never = true;
                else throw new IllegalArgumentException("no such flag '" + flag + "' on " + name + ": " + text);
            }
        }
        SurfaceShape shape = half ? SurfaceShape.SLAB : stair ? SurfaceShape.STAIR : SurfaceShape.FULL;
        return new Dump(name, material, shape, slab, cube, opaque, keeps, vanillaClass, never);
    }

    /** A block's facts as plain values: written by the census, read from a golden file, and game-free. */
    final class Dump implements BlockFacts {

        private final String name;
        private final String material;
        private final SurfaceShape shape;
        private final boolean slab;
        private final boolean cube;
        private final boolean opaque;
        private final boolean keepsState;
        private final String vanillaClass;
        private final boolean neverGround;

        Dump(String name, String material, SurfaceShape shape, boolean slab, boolean cube, boolean opaque,
            boolean keepsState, String vanillaClass, boolean neverGround) {
            this.name = name;
            this.material = material;
            this.shape = shape;
            this.slab = slab;
            this.cube = cube;
            this.opaque = opaque;
            this.keepsState = keepsState;
            this.vanillaClass = vanillaClass;
            this.neverGround = neverGround;
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
        public boolean keepsState() {
            return keepsState;
        }

        @Override
        public String vanillaClass() {
            return vanillaClass;
        }

        @Override
        public boolean neverGround() {
            return neverGround;
        }

        @Override
        public BlockFacts named(String other) {
            return new Dump(other, material, shape, slab, cube, opaque, keepsState, vanillaClass, neverGround);
        }

        /**
         * The facts as the golden file writes them: {@code m=} material, {@code f=} the flags or {@code -} and
         * {@code c=} the class word. One state speaks for the whole block here, so a slab's two forms share these.
         * This edition's flag of its own is {@code neverground}.
         */
        public String text() {
            StringBuilder flags = new StringBuilder();
            flag(flags, opaque, "opaque");
            flag(flags, cube, "cube");
            flag(flags, keepsState, "keeps");
            flag(flags, slab, "slab");
            flag(flags, shape == SurfaceShape.SLAB, "half");
            flag(flags, shape == SurfaceShape.STAIR, "stair");
            flag(flags, neverGround, "neverground");
            StringBuilder out = new StringBuilder("m=").append(material)
                .append(" f=")
                .append(flags.length() == 0 ? "-" : flags.toString());
            if (vanillaClass != null) out.append(" c=")
                .append(vanillaClass);
            return out.toString();
        }

        private static void flag(StringBuilder flags, boolean set, String word) {
            if (!set) return;
            if (flags.length() > 0) flags.append(',');
            flags.append(word);
        }
    }
}
