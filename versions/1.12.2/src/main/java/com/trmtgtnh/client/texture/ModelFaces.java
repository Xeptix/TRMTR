package com.trmtgtnh.client.texture;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

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
        Map<Block, String[][]> built = new IdentityHashMap<Block, String[][]>();
        firstReason = null;
        int read = 0;
        int missed = 0;
        for (SurfaceRegistry.SurfaceState surface : states) {
            Block block = surface.block;
            if (built.containsKey(block)) continue;
            String[][] byMeta = new String[16][];
            Map<IBlockState, ModelResourceLocation> variants;
            try {
                net.minecraft.client.renderer.block.statemap.BlockStateMapper mapper = stateMapper();
                if (mapper == null) {
                    noteReason("the game's block state mapper could not be reached");
                    missed++;
                    continue;
                }
                variants = mapper.getVariants(block);
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
                // Several states share a meta - grass snowy and not - and the one the meta itself stands for
                // is the one to read, since that is the state a record's origin is filed under.
                boolean canonical = sameState(block, meta, state);
                if (byMeta[meta] != null && !canonical) continue;
                String[] sides = sidesOf(entry.getValue());
                if (sides == null) continue;
                if (byMeta[meta] == null || canonical) byMeta[meta] = sides;
            }
            boolean any = false;
            for (String[] sides : byMeta) {
                if (sides != null) any = true;
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
        surveyed = read;
        unread = missed;
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

    /** The six faces a model draws, by side, or null where it cannot be read as a vanilla model. */
    private static String[] sidesOf(ModelResourceLocation location) {
        ModelBlock model = vanillaModel(location);
        if (model == null) return null;
        String[] sides = new String[6];
        float highest = Float.NEGATIVE_INFINITY;
        try {
            for (BlockPart part : model.getElements()) {
                for (Map.Entry<EnumFacing, BlockPartFace> face : part.mapFaces.entrySet()) {
                    int side = face.getKey()
                        .getIndex();
                    String texture = resolve(model, face.getValue().texture);
                    if (texture == null) continue;
                    if (side == EnumFacing.UP.getIndex()) {
                        // The top of whichever element reaches highest: the surface a foot lands on.
                        if (part.positionTo.y > highest) {
                            highest = part.positionTo.y;
                            sides[side] = texture;
                        }
                    } else if (sides[side] == null) {
                        sides[side] = texture;
                    }
                }
            }
        } catch (RuntimeException awkwardModel) {
            return null;
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
     */
    private static ModelBlock vanillaModel(ModelResourceLocation location) {
        try {
            IModel model = ModelLoaderRegistry.getModelOrMissing(location);
            Optional<ModelBlock> direct = model.asVanillaModel();
            if (direct.isPresent()) return direct.get();
            for (ResourceLocation part : model.getDependencies()) {
                Optional<ModelBlock> inner = ModelLoaderRegistry.getModelOrMissing(part)
                    .asVanillaModel();
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
            noteReason("reading its model " + location + " threw " + unreadable);
            Trmt.LOG.debug("Could not read the model {} for its faces", location, unreadable);
        }
        return null;
    }
}
