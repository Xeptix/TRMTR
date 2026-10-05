package com.trmtgtnh.client.texture;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * What a block actually draws on each side, as a texture name.
 *
 * <p>
 * Asked by {@link FaceSource} before anything is stitched, so that a worn block is made from its own
 * pixels rather than from whatever its registry name happens to suggest. A modded stone whose texture
 * is called something the convention would never guess is the case this exists for - and without it,
 * seven faces in ten were being made from a family's stock texture instead of the block's own.
 *
 * <p>
 * <strong>The models are read from their files rather than from the game.</strong> Both older
 * editions ask the model loader, which has already parsed everything by the time they look: 1.7.10
 * through its own registry, 1.12.2 through Forge's {@code ModelLoaderRegistry}. Neither route exists
 * here. Forge's is Forge's and this edition has two loaders; vanilla's {@code ModelBakery} is built
 * and discarded inside {@code ModelManager.prepare} without ever being handed out.
 *
 * <p>
 * It has to be the <em>unbaked</em> model, which is what makes this awkward rather than merely
 * different: a baked model's quads carry atlas sprites, and the atlas does not exist at the moment
 * this is asked. That is the whole ordering this pipeline lives inside.
 *
 * <p>
 * So the two files are read and walked here. A blockstate file names a model; a model file names its
 * textures and, usually, a parent that names the rest. The walk is the one the game does: merge a
 * model's textures over its parent's, then resolve a face's reference, following {@code #name}
 * indirection until it lands on something real. Vanilla's own models lean on this heavily - almost
 * every block is three levels of parent over {@code cube_all} or {@code cube_bottom_top}.
 *
 * <p>
 * <strong>Parsed rather than handed to vanilla's own parser,</strong> which can read both files but
 * links a model to its parent through a field nothing outside the loader can set. Reading the JSON
 * costs about as much and needs no reflection to make it work.
 *
 * <p>
 * A block that will not say is simply not recorded, and every caller already falls back to the
 * naming convention - which is how grass, mycelium and the path are found. What this adds is
 * everything the convention would have missed.
 */
public final class ModelFaces {

    /** How many parents deep to follow before deciding a model is pointing at itself. */
    private static final int DEEPEST = 8;

    /**
     * What each face reference is called, in the order a face asks for them.
     *
     * <p>
     * Indexed by the mod's own side numbering - 0 down, 1 up, 2-5 the flanks - which is 1.7.10's and
     * is what every caller still passes. The names are vanilla's model conventions: {@code cube_all}
     * says {@code all}, {@code cube_bottom_top} says {@code top}, {@code bottom} and {@code side},
     * and a block with a face of its own says {@code north} or {@code up}. {@code particle} is last
     * because it is the one every model has and the least specific thing it could mean.
     */
    private static final String[][] WANTED = {
        { "down", "bottom", "end", "all", "texture", "particle" },
        { "up", "top", "end", "all", "texture", "particle" },
        { "north", "side", "all", "texture", "particle" },
        { "south", "side", "all", "texture", "particle" },
        { "west", "side", "all", "texture", "particle" },
        { "east", "side", "all", "texture", "particle" },
    };

    private static volatile Map<Block, String[]> faces = new IdentityHashMap<Block, String[]>();

    private static volatile int read;

    private static volatile int refused;

    private static volatile String firstReason;

    private ModelFaces() {}

    /**
     * Reads every surface's model, once, before the plan prices anything.
     *
     * <p>
     * On whatever thread the plan runs on, which is not the render thread - and that is fine, because
     * this opens resources and nothing else. Both older editions say the same about their own survey.
     */
    public static void survey(Collection<SurfaceRegistry.SurfaceState> states) {
        Map<Block, String[]> found = new IdentityHashMap<Block, String[]>();
        int reads = 0;
        int refusals = 0;
        firstReason = null;

        ResourceManager resources = Minecraft.getInstance()
            .getResourceManager();
        if (resources == null || states == null) {
            faces = found;
            return;
        }

        for (SurfaceRegistry.SurfaceState state : states) {
            if (state == null || state.block == null || found.containsKey(state.block)) continue;
            try {
                String[] sides = read(resources, state.block);
                if (sides == null) {
                    refusals++;
                    continue;
                }
                found.put(state.block, sides);
                reads++;
            } catch (RuntimeException awkwardModel) {
                refusals++;
                note(awkwardModel.toString());
            }
        }

        faces = found;
        read = reads;
        refused = refusals;
    }

    /**
     * The texture a block draws on one side, or null when this could not say.
     *
     * <p>
     * Null is the answer both older editions give for a block that refuses, and every caller already
     * handles it by trying the naming convention next. The metadata value they also pass is gone:
     * it told one block's subtypes apart, and 1.13 made those separate blocks.
     */
    public static String faceName(Block block, int side) {
        if (block == null || side < 0 || side >= WANTED.length) return null;
        String[] sides = faces.get(block);
        return sides == null ? null : sides[side];
    }

    /** What the texture report prints about this. */
    public static String report() {
        Map<Block, String[]> current = faces;
        if (current.isEmpty() && refused == 0) return "no block models read";
        return read + " block models read, " + refused + " that would not say"
            + (firstReason == null ? "" : " (first: " + firstReason + ")");
    }

    // ------------------------------------------------------------------
    // Reading one block
    // ------------------------------------------------------------------

    private static String[] read(ResourceManager resources, Block block) {
        ResourceLocation key = Registry.BLOCK.getKey(block);
        if (key == null) return null;

        ResourceLocation model = modelOf(
            resources,
            new ResourceLocation(key.getNamespace(), "blockstates/" + key.getPath() + ".json"));
        if (model == null) return null;

        Map<String, String> textures = texturesOf(resources, model);
        if (textures == null || textures.isEmpty()) return null;

        String[] sides = new String[WANTED.length];
        boolean any = false;
        for (int side = 0; side < WANTED.length; side++) {
            sides[side] = resolve(textures, side);
            if (sides[side] != null) any = true;
        }
        return any ? sides : null;
    }

    /**
     * The first model a blockstate file names.
     *
     * <p>
     * The first, and that is a decision rather than a shortcut. A blockstate file lists a model per
     * variant, and this version has a variant for every combination of every property - every
     * rotation of a log, every age of a crop. They draw the same textures as each other; what differs
     * is how those textures are turned. So the first is as good as any, and asking for all of them
     * would be hundreds of file reads per block to learn one thing.
     *
     * <p>
     * A multipart file is read the same way: the first {@code apply} is a model like any other.
     */
    private static ResourceLocation modelOf(ResourceManager resources, ResourceLocation file) {
        JsonObject root = json(resources, file);
        if (root == null) return null;

        if (root.has("variants")) {
            JsonObject variants = root.getAsJsonObject("variants");
            for (Map.Entry<String, JsonElement> each : variants.entrySet()) {
                ResourceLocation named = modelIn(each.getValue());
                if (named != null) return named;
            }
            return null;
        }
        if (root.has("multipart")) {
            for (JsonElement each : root.getAsJsonArray("multipart")) {
                if (!each.isJsonObject()) continue;
                ResourceLocation named = modelIn(
                    each.getAsJsonObject()
                        .get("apply"));
                if (named != null) return named;
            }
        }
        return null;
    }

    /** The model a variant names, where a variant may be one object or a weighted list of them. */
    private static ResourceLocation modelIn(JsonElement variant) {
        if (variant == null) return null;
        if (variant.isJsonArray()) {
            for (JsonElement each : variant.getAsJsonArray()) {
                ResourceLocation named = modelIn(each);
                if (named != null) return named;
            }
            return null;
        }
        if (!variant.isJsonObject()) return null;
        JsonElement model = variant.getAsJsonObject()
            .get("model");
        if (model == null || !model.isJsonPrimitive()) return null;
        return modelFile(model.getAsString());
    }

    /**
     * Every texture a model declares, with its parents' underneath it.
     *
     * <p>
     * A child's names win, which is the whole mechanism vanilla's models are built on: {@code dirt}
     * is {@code cube_all} with {@code all} set to dirt, and a hundred blocks are one of five shapes
     * with the names filled in.
     */
    private static Map<String, String> texturesOf(ResourceManager resources, ResourceLocation model) {
        Map<String, String> merged = new HashMap<String, String>();
        List<ResourceLocation> seen = new ArrayList<ResourceLocation>();

        ResourceLocation at = model;
        for (int depth = 0; at != null && depth < DEEPEST; depth++) {
            if (seen.contains(at)) break; // a model that is its own ancestor, which nothing should be
            seen.add(at);

            JsonObject root = json(resources, at);
            if (root == null) break;

            if (root.has("textures")) {
                JsonObject declared = root.getAsJsonObject("textures");
                for (Map.Entry<String, JsonElement> each : declared.entrySet()) {
                    // Only where the child has not already said: the nearer model wins.
                    if (merged.containsKey(each.getKey())) continue;
                    if (!each.getValue()
                        .isJsonPrimitive()) continue;
                    merged.put(
                        each.getKey(),
                        each.getValue()
                            .getAsString());
                }
            }

            JsonElement parent = root.get("parent");
            at = parent != null && parent.isJsonPrimitive() ? modelFile(parent.getAsString()) : null;
        }
        return merged;
    }

    /**
     * The texture one side draws, following {@code #name} references to whatever they land on.
     *
     * <p>
     * A model says {@code "top": "#all"} as often as it says a real name, and the thing it points at
     * may point somewhere else again. Followed rather than resolved in one step, and bounded, because
     * a pack can write a loop and this runs over every block in the game.
     */
    private static String resolve(Map<String, String> textures, int side) {
        for (String wanted : WANTED[side]) {
            String found = textures.get(wanted);
            for (int depth = 0; found != null && found.startsWith("#") && depth < DEEPEST; depth++) {
                found = textures.get(found.substring(1));
            }
            if (found != null && !found.startsWith("#")) return found;
        }
        return null;
    }

    /** A model reference as the file it names, which may or may not say which folder. */
    private static ResourceLocation modelFile(String named) {
        ResourceLocation at = ResourceLocation.tryParse(named);
        if (at == null) return null;
        return new ResourceLocation(at.getNamespace(), "models/" + at.getPath() + ".json");
    }

    private static JsonObject json(ResourceManager resources, ResourceLocation file) {
        InputStream stream = null;
        try {
            Resource resource = resources.getResource(file);
            stream = resource.getInputStream();
            JsonElement root = new JsonParser()
                .parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (IOException missing) {
            return null;
        } catch (RuntimeException unreadable) {
            note(file + ": " + unreadable);
            return null;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // Nothing useful to do about a stream that will not close.
                }
            }
        }
    }

    /** The first reason anything refused, kept so a survey that read nothing can say why. */
    private static void note(String reason) {
        if (firstReason == null) firstReason = reason;
    }
}
