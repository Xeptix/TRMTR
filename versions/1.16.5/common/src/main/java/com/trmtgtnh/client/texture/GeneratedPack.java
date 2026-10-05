package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

import javax.imageio.ImageIO;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;

import com.trmtgtnh.Trmt;

/**
 * A resource pack that holds no files, and answers for the wear sprites by drawing them.
 *
 * <p>
 * <strong>This is the pipeline inverted, and the inversion is the whole port of this layer.</strong>
 * Both older editions compose a wear sprite's pixels and then push them into a
 * {@code TextureAtlasSprite} by hand, at stitch time, through {@code setFramesTextureData}. 1.15
 * replaced a sprite's {@code int[][]} frames with {@code NativeImage} and there is nothing left to
 * push into - so instead of handing pixels to the atlas, this hands the atlas a <em>pack</em>, and
 * the atlas loads our sprites the way it loads everybody else's.
 *
 * <p>
 * That is better than the arrangement it replaces in two ways worth having. Nothing has to run at
 * stitch time any more: the pack answers when asked, so the atlas decides when, and a resource
 * reload regenerates everything without this mod having to notice one happened. And an animated wear
 * sprite keeps the one thing the direct route gave it for free - the {@code .mcmeta} that declares it
 * animated is served from here too, as an ordinary sibling file, which is exactly how the game
 * expects to find one.
 *
 * <p>
 * <strong>Nothing is cached.</strong> The atlas asks for each sprite once per reload, so a cache
 * would hold megabytes of encoded PNG for the rest of the session to save work that is not going to
 * be repeated. The composing itself is the older editions' own code, unchanged, and it was fast
 * enough to run inside a stitch there.
 *
 * <p>
 * The pixels come from {@link Sheets}, which is where the planning lives. This class knows how to be
 * a pack and nothing at all about wear.
 */
public final class GeneratedPack implements PackResources {

    /** One generated image, as the compositor leaves it. */
    public static final class Sheet {

        /** ARGB, {@code width * height} of them, which is what the compositor already works in. */
        public final int[] pixels;

        public final int width;

        public final int height;

        /** The {@code .mcmeta} this sprite wants, or null for a still one. */
        public final String mcmeta;

        public Sheet(int[] pixels, int width, int height, String mcmeta) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.mcmeta = mcmeta;
        }
    }

    /** Where the pack gets its answers. Implemented by whatever is planning the wear sprites. */
    public interface Sheets {

        /** Every sprite this pack can answer for. Asked when the atlas lists what a pack holds. */
        Set<ResourceLocation> names();

        /** The image for one name, drawn now, or null if that name is not ours. */
        Sheet sheet(ResourceLocation name);
    }

    /** Where this mod's generated sprites live, under this mod's own namespace. */
    public static final String FOLDER = "textures/block/wear";

    private static final String METADATA_SUFFIX = ".mcmeta";

    private final Sheets sheets;

    public GeneratedPack(Sheets sheets) {
        this.sheets = sheets;
    }

    @Override
    public String getName() {
        return Trmt.NAME + " generated wear textures";
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Collections.singleton(Trmt.MODID)
            : Collections.<String>emptySet();
    }

    /**
     * The last sheet drawn on this thread, so the three questions about one sprite cost one drawing.
     *
     * <p>
     * The resource manager asks about a texture in a run: has it got the metadata, then take the
     * metadata, then take the image. Answering the first honestly means knowing whether the sprite
     * is animated, and the only way to know that is to draw it - so without this, every sprite is
     * drawn two or three times, and the atlas loads sprites on worker threads so a shared slot would
     * need a lock. One slot per thread needs none, and the run is always on one thread.
     *
     * <p>
     * Still not a cache in the sense rejected earlier: it holds one sheet, and the next name
     * replaces it. Nothing is retained after a reload.
     */
    private final ThreadLocal<Drawn> recent = new ThreadLocal<Drawn>();

    private static final class Drawn {

        final ResourceLocation name;

        final Sheet sheet;

        Drawn(ResourceLocation name, Sheet sheet) {
            this.name = name;
            this.sheet = sheet;
        }
    }

    /** The sheet for this image, drawing it only if it is not the one just drawn on this thread. */
    private Sheet drawn(ResourceLocation image) {
        Drawn held = recent.get();
        if (held != null && held.name.equals(image)) return held.sheet;
        Sheet made = sheets.sheet(image);
        recent.set(new Drawn(image, made));
        return made;
    }

    @Override
    public boolean hasResource(PackType type, ResourceLocation location) {
        ResourceLocation image = wanted(type, location);
        if (image == null) return false;
        if (!location.getPath()
            .endsWith(METADATA_SUFFIX)) return true;

        // A still sprite has no metadata file, and saying it has one is not a small lie: the manager
        // asks this before it takes, and a yes it cannot honour comes back as "using missing
        // texture" for the sprite itself. Which is what it did.
        Sheet sheet = drawn(image);
        return sheet != null && sheet.mcmeta != null;
    }

    @Override
    public InputStream getResource(PackType type, ResourceLocation location) throws IOException {
        ResourceLocation image = wanted(type, location);
        if (image == null) {
            // The manager asks before it takes, so this should not happen - but a pack that answers
            // null here is a crash inside the loader rather than a missing texture, so it is said
            // plainly instead.
            throw new IOException(location + " is not one of this mod's generated textures");
        }

        Sheet sheet = drawn(image);
        if (sheet == null) throw new IOException(image + " could not be drawn");

        boolean metadata = location.getPath()
            .endsWith(METADATA_SUFFIX);
        if (metadata) {
            if (sheet.mcmeta == null) throw new IOException(image + " is not animated");
            return new ByteArrayInputStream(sheet.mcmeta.getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(encode(sheet));
    }

    @Override
    public Collection<ResourceLocation> getResources(PackType type, String namespace, String path, int depth,
        Predicate<String> filter) {
        // Only ever this mod's own folder, which is also the only thing this pack claims a namespace
        // for. A pack that answered more widely would be offering to supply files it cannot draw.
        if (type != PackType.CLIENT_RESOURCES || !Trmt.MODID.equals(namespace)) {
            return Collections.emptyList();
        }
        // Either way round: the caller may be listing a folder above ours or ours exactly.
        if (!FOLDER.startsWith(path) && !path.startsWith(FOLDER)) return Collections.emptyList();

        Collection<ResourceLocation> found = new ArrayList<ResourceLocation>();
        for (ResourceLocation each : sheets.names()) {
            if (filter == null || filter.test(each.getPath())) found.add(each);
        }
        return found;
    }

    /**
     * Nothing, and that is the point.
     *
     * <p>
     * A root resource is {@code pack.png} or {@code pack.mcmeta} - the things a pack shows in the
     * resource pack screen. This one is never in that screen: it is handed to the atlas directly by
     * the loader module rather than offered to the player, because a wear texture nobody can switch
     * off is not a texture pack, it is part of the mod.
     */
    @Override
    public InputStream getRootResource(String name) {
        return null;
    }

    /**
     * The {@code pack.mcmeta} this pack would have had, had it been a folder.
     *
     * <p>
     * <strong>Not optional, and the reason is worth writing down.</strong> {@code Pack.create} asks
     * every pack for this before it will build anything, and hands back null if it does not get one
     * - so a pack that answered null here would simply never be added, with one line in the log and
     * no wear textures at all. That is precisely the silent failure this project keeps being caught
     * by, and it is invisible from the outside because everything else still works.
     *
     * <p>
     * The pack format is taken from the running game rather than written down, so this pack is never
     * the one that reports itself as made for a different version.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer == PackMetadataSection.SERIALIZER) {
            return (T) new PackMetadataSection(
                new TextComponent(getName()),
                SharedConstants.getCurrentVersion()
                    .getPackVersion());
        }
        return null;
    }

    @Override
    public void close() {}

    /**
     * The image a request is for, whether it asked for the image or for its metadata, or null when
     * the request is not this pack's at all.
     */
    private ResourceLocation wanted(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES || location == null) return null;
        if (!Trmt.MODID.equals(location.getNamespace())) return null;

        String path = location.getPath();
        if (!path.startsWith(FOLDER)) return null;

        ResourceLocation image = path.endsWith(METADATA_SUFFIX)
            ? new ResourceLocation(
                location.getNamespace(),
                path.substring(0, path.length() - METADATA_SUFFIX.length()))
            : location;

        return sheets.names()
            .contains(image) ? image : null;
    }

    /**
     * ARGB pixels to the bytes of a PNG.
     *
     * <p>
     * Through {@code BufferedImage} and {@code ImageIO}, which are in every JDK and take an
     * {@code int[]} of ARGB directly - which is exactly what the compositor has been producing in
     * both older editions all along. {@code NativeImage} would do it too and is what the atlas
     * decodes into, but going through it here would mean composing into one and the compositor is
     * portable code that names nothing from the game.
     */
    static byte[] encode(Sheet sheet) throws IOException {
        BufferedImage image = new BufferedImage(sheet.width, sheet.height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, sheet.width, sheet.height, sheet.pixels, 0, sheet.width);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", out)) {
            throw new IOException("no PNG writer, which no JDK should be without");
        }
        return out.toByteArray();
    }
}
