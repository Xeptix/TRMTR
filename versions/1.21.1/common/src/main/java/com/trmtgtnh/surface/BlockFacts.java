package com.trmtgtnh.surface;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;

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
 * <strong>The material is {@link MaterialKind}.</strong> This game has no {@code Material}: the kind is what 1.16.5's
 * material would have said, read back from the properties Mojang filled in from it (map color, sound, instrument, push
 * reaction, collision), held to 1.16.5 block for block by {@code docs/detection-parity.md}. It is asked once, where the
 * rules always asked it, and its questions reach a block's own code: its sound, its map color and its collision shape.
 * The word for a kind is the 1.7.10 edition's for the material it stands for ({@code ground}, {@code rock},
 * {@code craftedSnow}, {@code snow} for the layer, {@code vine} for a replaceable plant), so a word means one material in
 * every game's golden files; {@code carpet} is that edition's spelling too. Two words do not carry over: one kind is ice
 * of both 1.16.5 materials, so a packed ice line here says {@code ice} where 1.16.5's says {@code packedIce}; and
 * {@link #OTHER} is every material no rule reads, wood and glass as much as a mod's own, since the kind keeps no more.
 *
 * <p>
 * This edition asks a block's default state and decides the whole block from that one state, as the rules always have.
 * A slab says what it is made of by its name, as each material has had a block of its own since 1.13, so the golden line
 * has no subtype here. One fact this edition has and 1.7.10 does not, with the step that reads it: {@link #neverGround}
 * (frosted ice), read after snow and before ice, as 1.12.2's and 1.16.5's are. 1.12.2's {@code fullBlock} is not this
 * game's: its cube test asks the collision shape and nothing else, so no rule here reads it and no line writes it.
 */
public interface BlockFacts {

    String LEAVES = "leaves";
    String PLANTS = "plants";
    String VINE = "vine";
    String ICE = "ice";
    String GRASS = "grass";
    String GROUND = "ground";
    String CRAFTED_SNOW = "craftedSnow";
    String SNOW = "snow";
    String SAND = "sand";
    String ROCK = "rock";

    /** A kind no rule reads: {@link MaterialKind#OTHER}. */
    String OTHER = "other";

    String CLASS_LEAVES = "leaves";
    String CLASS_GRASS = "grass";
    String CLASS_DIRT = "dirt";
    String CLASS_GRAVEL = "gravel";
    String CLASS_SAND = "sand";

    /** The name the rules read, as the registry spells it. */
    String name();

    /** What its default state is made of: the word for its {@link MaterialKind}. */
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
     * which at this version is {@code EntityBlock}, the game's own gate on a block entity for every state.
     */
    boolean keepsState();

    /**
     * What a vanilla class or block says it is, one of the {@code CLASS_} words, or null. One word holds it: a block
     * extends at most one of {@code LeavesBlock}, {@code GrassBlock} and {@code ColoredFallingBlock}, and dirt is three
     * of the game's own blocks by identity ({@code dirt}, {@code coarse_dirt}, {@code podzol}), none of which extends
     * any of those. 1.21 folded {@code SandBlock} and {@code GravelBlock} into {@code ColoredFallingBlock}, so its word
     * is the block's own sound's, {@code gravel} for gravel's and {@code sand} for any other, as the rules always read
     * it: the one class word a block's own code answers, so {@link Detection} asks it once, where it always did.
     */
    String vanillaClass();

    /**
     * Whether it extends {@code LeavesBlock}: the class word the rules read first, at the top, before a block's shape.
     * Its own question, because the whole {@link #vanillaClass} of a falling block asks the block's sound, which the
     * rules never asked there.
     */
    boolean leavesClass();

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

    /**
     * The word for a kind: the 1.7.10 edition's name for the material it stands for, so one word is one material in
     * every game's goldens. Each kind has its own word.
     */
    static String wordFor(MaterialKind kind) {
        switch (kind) {
            case GRASS:
                return GRASS;
            case DIRT:
                return GROUND;
            case SAND:
                return SAND;
            case STONE:
                return ROCK;
            case SNOW:
                return CRAFTED_SNOW;
            case TOP_SNOW:
                return SNOW;
            case ICE:
                return ICE;
            case LEAVES:
                return LEAVES;
            case PLANT:
                return PLANTS;
            case REPLACEABLE_PLANT:
                return VINE;
            case CACTUS:
                return "cactus";
            case CLOTH_DECORATION:
                return "carpet";
            default:
                return OTHER;
        }
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
            return wordFor(MaterialKind.of(block));
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
            // Vanilla's sand, red sand and gravel and nothing else of vanilla's: the sound says which, as
            // SurfaceRegistry's classifier said it before the split.
            if (block instanceof ColoredFallingBlock) {
                return block.defaultBlockState()
                    .getSoundType() == SoundType.GRAVEL ? CLASS_GRAVEL : CLASS_SAND;
            }
            return null;
        }

        @Override
        public boolean leavesClass() {
            return block instanceof LeavesBlock;
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
     * of its own: each question that reaches a block's own code is guarded, because here it is asked of blocks the
     * rules would never have put it to. A throw gives the answer that keeps a block out. Whether it keeps state is a
     * class's answer and cannot throw at this version, so it is not guarded; the class word is guarded, as a falling
     * block's is its sound, and a sound that throws leaves the material {@link #OTHER} as well, which no rule takes.
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
        String vanillaClass;
        try {
            vanillaClass = asked.vanillaClass();
        } catch (RuntimeException | LinkageError awkward) {
            vanillaClass = null;
        }
        return new Dump(name, material, shape, asked.slab(), cube, opaque, asked.keepsState(), vanillaClass,
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
        public boolean leavesClass() {
            return CLASS_LEAVES.equals(vanillaClass);
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
