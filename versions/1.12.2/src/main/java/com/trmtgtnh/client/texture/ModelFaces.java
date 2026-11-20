package com.trmtgtnh.client.texture;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockPart;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.surface.SurfaceRegistry;
import com.trmtgtnh.surface.SurfaceShape;

/**
 * Which texture a block draws on each of its faces, read out of its model before the model is baked.
 *
 * <p>
 * The other edition asks {@code Block.getIcon(side, meta)}, which 1.7.10 answers from a table the block
 * filled in while the atlas was being gathered. 1.12.2 has no such question: a block's faces are its
 * model's, and the model is baked only after the atlas is stitched - too late for a wear sprite that
 * has to say, while the atlas is being filled, which texture it is worn from. But the model is
 * <em>loaded</em> before then. {@code ModelLoader} loads every blockstate and every model, then fills the
 * atlas, then bakes; the stitch event fires in the middle of that. So the answer is read from the
 * unbaked model, which is what this class does, once a stitch, for every surface that has wear
 * textures.
 *
 * <p>
 * The road through the model is short. The block's state mapper names the model each state uses. For
 * a vanilla blockstate file that is a weighted list, which does not unwrap itself but does name the
 * models it is made of, and the first of those unwraps into the vanilla model: elements, each with
 * faces, each face naming a texture variable the model resolves. The top is taken from the element that
 * reaches highest, so a block built of a slab and a rim reports the surface a foot lands on; every
 * other side from the first element that has one.
 *
 * <p>
 * Two things on that road were found on 2026-10-08, with Chisel's own jar open, and both are why every Chisel
 * stone wore its family's stand-in until then while the log said all of them had been read. <strong>A Forge
 * blockstate ({@code "forge_marker": 1}) keeps its textures in its variants</strong>, and Forge applies them only
 * to the models it makes of each variant and keeps to itself; the models the weighted list names are the bases,
 * whose faces name variables ({@code #all}, {@code #top}) that resolve to nothing there. So the variant's own
 * models are read first - see {@link #variantModels}. <strong>And a block drawn as a shell over a second layer
 * - Chisel's lavastone and waterstone, the liquid as one full cube and the carved stone as a second at the same
 * place - is the shell</strong>, which is what the 1.7.10 edition reads: there {@code getIcon} answers the
 * shell and Chisel's renderer draws the liquid under it. The liquid is the first element, so a face that draws
 * exactly the texture {@code client.innerLayerTextures} names behind its block is taken for that layer and
 * passed over, wherever anything else draws that side. See {@link #sidesOf(ModelBlock, ResourceLocation)}.
 *
 * <p>
 * One limit is known and accepted: a variant turned on its side - a log lying down - reports the face
 * that was on top before the turn, because the turn is applied when the model is baked. No surface that
 * wears in vanilla is one, and a modded one that is will be worn from its end grain rather than its bark.
 *
 * <p>
 * Asked on the render thread while the atlas is filled, and read on mesher threads afterwards; the
 * table is built whole and published by one write, so a reader sees one stitch's answers or the next's.
 */
@SideOnly(Side.CLIENT)
public final class ModelFaces {

    /** Per block, per meta, per side (the 1.7.10 numbering, which is EnumFacing's index), a sprite name. */
    private static volatile Map<Block, String[][]> faces = new IdentityHashMap<Block, String[][]>();

    /**
     * Per block, per meta, the side its snowy variant draws - the picture a turf shows on its flanks with snow on top
     * (0.9.222, spec SC20). The 1.7.10 edition asks the covered block for it through a view of the world; a mesher
     * thread may not ask another mod's model, so it is read here, with the rest of the survey.
     */
    private static volatile Map<Block, String[]> snowed = new IdentityHashMap<Block, String[]>();

    private static volatile int surveyed;

    private static volatile int unread;

    private ModelFaces() {}

    /**
     * Reads the faces of every block that has wear textures, from its unbaked model.
     *
     * <p>
     * Called at the start of each stitch, where the models are loaded and not yet baked. A block whose
     * model cannot be read answers nothing, and the chain in {@link FaceSource} goes on to the file named
     * after it and then to its family's vanilla texture, which is what the other edition does for a block
     * whose {@code getIcon} refuses.
     */
    public static void survey(Collection<SurfaceRegistry.SurfaceState> states) {
        List<Block> blocks = new ArrayList<Block>(states.size());
        for (SurfaceRegistry.SurfaceState surface : states) {
            blocks.add(surface.block);
        }
        survey(blocks, GameModels.INSTANCE);
    }

    /** {@link #survey(Collection)}, of these blocks, with their models asked of {@code models}. */
    static void survey(List<Block> blocks, Models models) {
        Map<Block, String[][]> built = new IdentityHashMap<Block, String[][]>();
        Map<Block, String[]> snowedBuilt = new IdentityHashMap<Block, String[]>();
        firstReason = null;
        passedOverBuilt = new LinkedHashSet<String>();
        int read = 0;
        int missed = 0;
        for (Block block : blocks) {
            if (built.containsKey(block)) continue;
            String[][] byMeta = new String[16][];
            String[] snowedByMeta = new String[16];
            Map<IBlockState, ModelResourceLocation> variants;
            try {
                variants = models.variants(block);
                if (variants == null) {
                    noteReason("the game's block state mapper could not be reached");
                    missed++;
                    continue;
                }
            } catch (RuntimeException awkwardBlock) {
                noteReason("its state mapper threw " + awkwardBlock);
                missed++;
                continue;
            }
            for (Map.Entry<IBlockState, ModelResourceLocation> entry : variants.entrySet()) {
                IBlockState state = entry.getKey();
                int meta;
                try {
                    meta = block.getMetaFromState(state);
                } catch (RuntimeException awkwardState) {
                    continue;
                }
                if (meta < 0 || meta > 15) continue;
                // The snowy variant is read for its flank alone, and never stands for the meta (SC20).
                if (isSnowy(state)) {
                    String[] snowySides = sidesOf(models, block, entry.getValue(), layerBehind(block, meta));
                    if (snowySides != null && snowySides[2] != null) snowedByMeta[meta] = snowySides[2];
                    continue;
                }
                // Several states share a meta - grass snowy and not - and the one the meta itself stands for
                // is the one to read, since that is the state a record's origin is filed under.
                boolean canonical = sameState(block, meta, state);
                if (byMeta[meta] != null && !canonical) continue;
                String[] sides = sidesOf(models, block, entry.getValue(), layerBehind(block, meta));
                if (sides == null) continue;
                if (byMeta[meta] == null || canonical) {
                    byMeta[meta] = lawnFaces(sides, SurfaceShape.of(block, meta) != SurfaceShape.STAIR);
                }
            }
            boolean any = false;
            for (String[] sides : byMeta) {
                if (sides != null) any = true;
            }
            for (String side : snowedByMeta) {
                if (side != null) {
                    snowedBuilt.put(block, snowedByMeta);
                    break;
                }
            }
            if (any) {
                built.put(block, byMeta);
                read++;
            } else {
                if (variants.isEmpty()) noteReason(block.getRegistryName() + " has no model variants");
                missed++;
            }
        }
        faces = built;
        snowed = snowedBuilt;
        surveyed = read;
        unread = missed;
        passedOver = Collections.unmodifiableList(new ArrayList<String>(passedOverBuilt));
        // Said once a survey, bounded, and only where the loader itself would not answer: see vanillaModel.
        if (!passedOver.isEmpty()) {
            Trmt.LOG.warn(
                "Wear texture faces: the model loader could not read the models of {} surface block(s), which wear the "
                    + "texture named after them or their family's instead: {}",
                Integer.valueOf(passedOver.size()),
                com.trmtgtnh.util.LogSample.of(passedOver));
        }
    }

    /**
     * Where the survey's models come from: the game's state mapper and Forge's model loader in a game, a stand-in in a
     * test, since neither can be reached without a running client.
     */
    interface Models {

        /** Which model each of a block's states draws with, or null where the game's state mapper cannot be reached. */
        Map<IBlockState, ModelResourceLocation> variants(Block block);

        /** The unbaked model at a location, or the missing model, as {@code ModelLoaderRegistry.getModelOrMissing}. */
        IModel model(ResourceLocation location);
    }

    /** The game's own: kept in a class of its own so that a test which never surveys the game never loads it. */
    private static final class GameModels implements Models {

        static final GameModels INSTANCE = new GameModels();

        @Override
        public Map<IBlockState, ModelResourceLocation> variants(Block block) {
            net.minecraft.client.renderer.block.statemap.BlockStateMapper mapper = stateMapper();
            return mapper == null ? null : mapper.getVariants(block);
        }

        @Override
        public IModel model(ResourceLocation location) {
            return ModelLoaderRegistry.getModelOrMissing(location);
        }
    }

    /** The blocks the last survey asked the loader about and could not read, each with what it threw. */
    private static volatile List<String> passedOver = Collections.emptyList();

    /** The same, while a survey is being taken; only the survey's own thread touches it. */
    private static Set<String> passedOverBuilt = new LinkedHashSet<String>();

    /** {@link #passedOver}, as the last survey left it. */
    static List<String> passedOver() {
        return passedOver;
    }

    /** The side a block's snowy variant draws, or null where it has none or its model did not say (SC20). */
    public static String snowedSideName(Block block, int meta) {
        if (block == null || meta < 0 || meta > 15) return null;
        String[] byMeta = snowed.get(block);
        return byMeta == null ? null : byMeta[meta];
    }

    /** Whether a state is a snowy variant: a property named snowy, set. Vanilla grass's, mycelium's and a mod's alike. */
    private static boolean isSnowy(IBlockState state) {
        for (net.minecraft.block.properties.IProperty<?> property : state.getPropertyKeys()) {
            if ("snowy".equals(property.getName()) && Boolean.TRUE.equals(state.getValue(property))) return true;
        }
        return false;
    }

    /** The sprite name a block draws on one side, or null where its model did not say. */
    public static String faceName(Block block, int meta, int side) {
        if (block == null || meta < 0 || meta > 15 || side < 0 || side > 5) return null;
        String[][] byMeta = faces.get(block);
        if (byMeta == null) return null;
        String[] sides = byMeta[meta];
        return sides == null ? null : sides[side];
    }

    /**
     * Why the first model that would not say, would not - kept so a survey that read nothing can say so rather than
     * leave every surface quietly wearing its file. The first run of this read none of twenty-eight and said nothing.
     */
    private static volatile String firstReason;

    private static void noteReason(String reason) {
        if (firstReason == null) firstReason = reason;
    }

    /** Blocks whose faces were read on the last survey, and blocks whose model would not say, and why. */
    public static String report() {
        String line = surveyed + " surface block(s) read from their models, " + unread + " not";
        return unread > 0 && firstReason != null ? line + "; the first would not because " + firstReason : line;
    }

    /**
     * The state mapper the game's models are named through, with every mod's own mappers in it.
     *
     * <p>
     * Asked of the block renderer when there is one. At start-up there is not: 1.12.2 builds the block atlas in the
     * model manager's first reload and creates the renderer a dozen lines later, so the first survey found it null
     * and read none of twenty-eight surfaces. The model manager itself exists by then - it is what is reloading - but
     * the game keeps it in a private field with no getter, so it is found by its type. By type rather than by name
     * because a field's name is obfuscated in a released game and its type is not.
     */
    private static net.minecraft.client.renderer.block.statemap.BlockStateMapper stateMapper() {
        Minecraft game = Minecraft.getMinecraft();
        if (game.getBlockRendererDispatcher() != null) {
            return game.getBlockRendererDispatcher()
                .getBlockModelShapes()
                .getBlockStateMapper();
        }
        net.minecraft.client.renderer.block.model.ModelManager manager = modelManager();
        if (manager == null) {
            noteReason("the model manager could not be reached");
            return null;
        }
        return manager.getBlockModelShapes()
            .getBlockStateMapper();
    }

    /**
     * The game's model manager, which it keeps in a private field with no getter.
     *
     * <p>
     * Found by its type rather than by its name, because a field's name is obfuscated in a released game and
     * its type is not. Wanted twice: at start-up, where the block renderer this would otherwise be asked of
     * does not exist yet, and by the rebuild, which asks the manager to reload so that the pictures are
     * stitched and the models baked against them again.
     */
    public static net.minecraft.client.renderer.block.model.ModelManager modelManager() {
        try {
            for (java.lang.reflect.Field field : Minecraft.class.getDeclaredFields()) {
                if (field.getType() != net.minecraft.client.renderer.block.model.ModelManager.class) continue;
                field.setAccessible(true);
                return (net.minecraft.client.renderer.block.model.ModelManager) field.get(Minecraft.getMinecraft());
            }
        } catch (ReflectiveOperationException | RuntimeException unreachable) {
            Trmt.LOG.debug("The model manager could not be reached", unreachable);
        }
        return null;
    }

    private static boolean sameState(Block block, int meta, IBlockState state) {
        try {
            return block.getStateFromMeta(meta) == state;
        } catch (RuntimeException awkwardBlock) {
            return false;
        }
    }

    /**
     * The texture {@code client.innerLayerTextures} names behind this block, as the atlas names it, or null.
     *
     * <p>
     * Read from {@link InnerLayers}, whose table the stitch reads from the config before this survey runs
     * ({@code WearTextures.registerAll}), so the face read and the layer laid behind it are the same entry.
     */
    private static ResourceLocation layerBehind(Block block, int meta) {
        String named = InnerLayers.textureFor(block, meta);
        if (named == null) return null;
        try {
            return FaceSource.spriteLocation(named);
        } catch (RuntimeException unreadableName) {
            return null;
        }
    }

    /**
     * A lawn whose model draws its top on a face keeps a lawn's face, so everything downstream treats it as the lawn it
     * is: see {@link FaceRules#lawnFace}. Here rather than in {@code GhostSides} because the wall made at the stitch and
     * the flank drawn on a mesher thread have to read the same name, and this is the one table both read.
     *
     * <p>
     * Never a stair: the 1.7.10 edition's stair stand-in is never grass's, every face of a worn stair takes the covered
     * block's own tint ({@code GhostSides.stair}), and on that face the top is green and vanilla's flank would not be.
     *
     * @param sides      the six faces a model draws, changed in place
     * @param lawnShaped whether this block can be a lawn: anything but a stair
     */
    static String[] lawnFaces(String[] sides, boolean lawnShaped) {
        if (!lawnShaped) return sides;
        for (int side = 0; side < sides.length; side++) {
            String lawn = FaceRules.lawnFace(sides[1], sides[side], side);
            if (lawn != null) sides[side] = "minecraft:blocks/" + lawn;
        }
        return sides;
    }

    /** The six faces a model draws, by side, or null where it cannot be read as a vanilla model. */
    private static String[] sidesOf(Models models, Block block, ModelResourceLocation location, ResourceLocation behind) {
        ModelBlock model = vanillaModel(models, block, location);
        if (model == null) return null;
        String[] sides = sidesOf(model, behind);
        // Said rather than counted as read. Six sides that are all null used to count, and that is how every
        // Chisel stone wore its family's stand-in while the survey said it had read every surface it was given.
        if (sides == null) noteReason("its model " + location + " names no texture that resolves");
        return sides;
    }

    /**
     * The six faces a vanilla model draws, by side, or null where not one of its faces names a texture that
     * resolves.
     *
     * <p>
     * The top is the top of whichever element reaches highest, the surface a foot lands on; every other side is
     * the first element's that draws one. Except the layer the config names behind this block ({@code behind},
     * or null where it names none): a face drawing exactly that texture is the layer, seen only through the holes
     * in the face in front of it, and is passed over wherever any other element draws that side. Chisel's
     * lavastone and waterstone are the case - the liquid is their first element and their carved shell the second,
     * at the same place - and the 1.7.10 edition wears the shell, because there {@code getIcon} answers the shell
     * and the liquid is drawn under it by Chisel's renderer. Read by the texture and not by the order of elements,
     * because the order is a model's own business: vanilla grass draws its tinted overlay as a second element too,
     * and its side is the first element's, as it always was here.
     */
    static String[] sidesOf(ModelBlock model, ResourceLocation behind) {
        List<Part> parts = new ArrayList<Part>();
        try {
            for (BlockPart part : model.getElements()) {
                String[] drawn = new String[6];
                for (Map.Entry<EnumFacing, BlockPartFace> face : part.mapFaces.entrySet()) {
                    drawn[face.getKey()
                        .getIndex()] = resolve(model, face.getValue().texture);
                }
                parts.add(new Part(part.positionTo.y, drawn));
            }
        } catch (RuntimeException awkwardModel) {
            return null;
        }
        return sidesOf(parts, behind);
    }

    /**
     * One element of a model, as the reading of its faces needs it: how high it reaches, and the texture it draws on
     * each side by the 1.7.10 numbering, null where it draws none or names one that does not resolve.
     */
    static final class Part {

        final float reach;

        final String[] drawn;

        Part(float reach, String[] drawn) {
            this.reach = reach;
            this.drawn = drawn;
        }
    }

    /** {@link #sidesOf(ModelBlock, ResourceLocation)}, from the elements once they are read. */
    static String[] sidesOf(List<Part> parts, ResourceLocation behind) {
        String[] sides = new String[6];
        String[] layer = new String[6];
        float highest = Float.NEGATIVE_INFINITY;
        float layerHighest = Float.NEGATIVE_INFINITY;
        boolean any = false;
        for (Part part : parts) {
            for (int side = 0; side < sides.length && side < part.drawn.length; side++) {
                String texture = part.drawn[side];
                if (texture == null) continue;
                any = true;
                boolean isLayer = behind != null && behind.equals(new ResourceLocation(texture));
                String[] into = isLayer ? layer : sides;
                if (side == EnumFacing.UP.getIndex()) {
                    // The top of whichever element reaches highest: the surface a foot lands on.
                    if (part.reach > (isLayer ? layerHighest : highest)) {
                        if (isLayer) layerHighest = part.reach;
                        else highest = part.reach;
                        into[side] = texture;
                    }
                } else if (into[side] == null) {
                    into[side] = texture;
                }
            }
        }
        if (!any) return null;
        // The layer only where nothing else draws that side, so a block whose whole face is the texture named behind
        // it still reads as that texture rather than as nothing.
        for (int side = 0; side < sides.length; side++) {
            if (sides[side] == null) sides[side] = layer[side];
        }
        return sides;
    }

    /** A texture variable resolved through the model's own chain, as a sprite name, or null where it is missing. */
    private static String resolve(ModelBlock model, String variable) {
        if (variable == null) return null;
        String resolved = model.resolveTextureName(variable);
        if (resolved == null || resolved.isEmpty() || resolved.startsWith("#") || "missingno".equals(resolved)) {
            return null;
        }
        return new ResourceLocation(resolved).toString();
    }

    /**
     * The vanilla model a state draws with, unwrapped.
     *
     * <p>
     * A vanilla blockstate file loads as a weighted list of variants, which does not unwrap into a vanilla
     * model itself but names the models it is made of among its dependencies; the first of those that
     * does unwrap is taken. A model some mod builds in code unwraps into nothing, and answers nothing.
     *
     * <p>
     * The list's own variants come first, each the model as the blockstate made it - its textures applied - and
     * only then the bases it names. For a vanilla blockstate the two are the same model; for a Forge one they are
     * not, and only the first can say what a face draws. See {@link #variantModels}.
     */
    private static ModelBlock vanillaModel(Models models, Block block, ModelResourceLocation location) {
        try {
            IModel model = load(models, block, location);
            if (model == null) return null;
            Optional<ModelBlock> direct = model.asVanillaModel();
            if (direct.isPresent()) return direct.get();
            for (IModel variant : variantModels(model)) {
                Optional<ModelBlock> made = variant.asVanillaModel();
                if (made.isPresent()) return made.get();
            }
            for (ResourceLocation part : model.getDependencies()) {
                IModel dependency = load(models, block, part);
                if (dependency == null) return null;
                Optional<ModelBlock> inner = dependency.asVanillaModel();
                if (inner.isPresent()) return inner.get();
            }
            noteReason(
                "its model " + location
                    + " is a "
                    + model.getClass()
                        .getName()
                    + " naming "
                    + model.getDependencies()
                    + ", none of which unwraps into a vanilla model");
        } catch (RuntimeException unreadable) {
            passOver(block, location, unreadable);
        }
        return null;
    }

    /**
     * A model asked of the loader, or null where the loader could not read it, the block recorded as passed over.
     *
     * <p>
     * A LinkageError is passed over here and only here (E2E, 2026-11-11): VintageFix's overwrite of
     * VariantLoader.loadModel throws a MissingVariantException through a constructor the game's own jar keeps protected,
     * so for a variant its blockstate does not name - Extra Utilities 2 ships none - the throw fails as
     * IllegalAccessError. VintageFix's own loading catches Throwable there; StellarCore's getModel and Forge's
     * getModelOrMissing catch Exception, and the error took the stitch down. The same error from reading a model the
     * loader did hand back is a binary fault in this mod or the model API, and goes on up, as does any other Error.
     */
    private static IModel load(Models models, Block block, ResourceLocation location) {
        try {
            return models.model(location);
        } catch (LinkageError unlinkable) {
            passOver(block, location, unlinkable);
            return null;
        }
    }

    /** Notes a block whose model could not be read, once, with what it threw. */
    private static void passOver(Block block, ResourceLocation location, Throwable unreadable) {
        noteReason("reading its model " + location + " threw " + unreadable);
        passedOverBuilt.add(block.getRegistryName() + " (" + unreadable.getClass()
            .getSimpleName() + ")");
        Trmt.LOG.debug("Could not read the model {} for its faces", location, unreadable);
    }

    /** The field each model class keeps its variants' models in, found once per class; empty where it has none. */
    private static final Map<Class<?>, Optional<Field>> VARIANT_FIELDS = new ConcurrentHashMap<Class<?>, Optional<Field>>();

    /**
     * The models a weighted list of variants was made into, each with its blockstate's textures already applied, or
     * none.
     *
     * <p>
     * Forge's {@code ModelLoader.WeightedRandomModel} makes one model per variant as it loads - the base model put
     * through the variant's own {@code process}, which for a Forge blockstate is where its {@code "textures"} are
     * applied - and keeps them in a private list with no getter, naming only the bases among its dependencies. Those
     * bases are what this read before 2026-10-08, and a base names {@code #top} and {@code #all} and nothing they
     * resolve to. The list is found by its type, a {@code List<IModel>}, rather than by its name: the class has three
     * lists, and the other two hold the variants and their locations. Forge's own classes are not renamed in a
     * released game, so either would hold; the type says why it is the one.
     */
    private static List<IModel> variantModels(IModel model) {
        Class<?> type = model.getClass();
        Optional<Field> found = VARIANT_FIELDS.get(type);
        if (found == null) {
            found = Optional.ofNullable(variantModelsField(type));
            VARIANT_FIELDS.put(type, found);
        }
        if (!found.isPresent()) return Collections.emptyList();
        try {
            Field field = found.get();
            field.setAccessible(true);
            Object held = field.get(model);
            if (!(held instanceof List)) return Collections.emptyList();
            List<IModel> made = new ArrayList<IModel>();
            for (Object each : (List<?>) held) {
                if (each instanceof IModel) made.add((IModel) each);
            }
            return made;
        } catch (ReflectiveOperationException | RuntimeException unreachable) {
            noteReason("the models its blockstate made of " + type.getName() + " could not be reached: " + unreachable);
            return Collections.emptyList();
        }
    }

    /** A class's own {@code List<IModel>} field, or null where it declares none. */
    static Field variantModelsField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getType() != List.class) continue;
            Type held = field.getGenericType();
            if (!(held instanceof ParameterizedType)) continue;
            Type[] of = ((ParameterizedType) held).getActualTypeArguments();
            if (of.length == 1 && of[0] == IModel.class) return field;
        }
        return null;
    }
}
