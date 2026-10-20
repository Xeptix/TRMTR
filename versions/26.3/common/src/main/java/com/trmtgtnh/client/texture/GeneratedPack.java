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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import net.minecraft.network.chat.Component;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;

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
 * <strong>Each picture is drawn once a stitch, and kept only for that stitch.</strong> This said, until
 * 0.9.220, that nothing was cached because the atlas asks for each sprite once per reload. It asks twice -
 * once to learn its size, once to load it - and OptiFine three times, so every wear sprite was drawn two or
 * three times over: 44,174 pictures for 22,080 sprites, 66,261 under OptiFabric, each under a log line
 * saying "once each". See {@link #answers}. The composing itself is the older editions' own code, unchanged, and it was fast
 * enough to run inside a stitch there.
 *
 * <p>
 * The pixels come from {@link Sheets}, which is where the planning lives. This class knows how to be
 * a pack and nothing at all about wear.
 */
public final class GeneratedPack implements PackResources {

    /** One generated image, as the compositor leaves it, or already encoded as the PNG this pack serves. */
    public static final class Sheet {

        /**
         * ARGB, {@code width * height} of them, which is what the compositor already works in; null once the picture
         * is {@link #encoded}, when the bytes are all anybody reads and the pixels are let go.
         */
        public final int[] pixels;

        public final int width;

        public final int height;

        /** The {@code .mcmeta} this sprite wants, or null for a still one. */
        public final String mcmeta;

        /** The PNG, where the picture was encoded before it was asked for, or null for this pack to encode on its read. */
        final byte[] png;

        public Sheet(int[] pixels, int width, int height, String mcmeta) {
            this(pixels, width, height, mcmeta, null);
        }

        private Sheet(int[] pixels, int width, int height, String mcmeta, byte[] png) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
            this.mcmeta = mcmeta;
            this.png = png;
        }

        /**
         * The same picture as the PNG this pack would make of it, with the pixels let go.
         *
         * <p>
         * Safe on any thread: pixels, two sizes and a string in, bytes out, through nothing but the JDK's own writer -
         * which is how the sprite pass's pool can encode for the pack. The bytes are exactly the ones a read of the
         * unencoded sheet would produce, because they are made by the same method.
         */
        public Sheet encoded() throws IOException {
            if (png != null) return this;
            return new Sheet(null, width, height, mcmeta, encode(this));
        }
    }

    /** Where the pack gets its answers. Implemented by whatever is planning the wear sprites. */
    public interface Sheets {

        /** Every sprite this pack can answer for. Asked when the atlas lists what a pack holds. */
        Set<Identifier> names();

        /** The image for one name, drawn now, or null if that name is not ours. */
        Sheet sheet(Identifier name);
    }

    /** Where this mod's generated sprites live, under this mod's own namespace. */
    public static final String FOLDER = "textures/block/wear";

    private static final String METADATA_SUFFIX = ".mcmeta";

    private final Sheets sheets;

    public GeneratedPack(Sheets sheets) {
        this.sheets = sheets;
        OPEN.add(this);
    }

    /**
     * Where the pack came from and what it is called, which 1.20.5 asks every pack for where 1.20 asked only its id:
     * the id it was offered under, its title and its source - the same three WearPackSource offers it with.
     */
    @Override
    public net.minecraft.server.packs.PackLocationInfo location() {
        return WearPackSource.LOCATION;
    }

    /** What the pack is called where it is named to a person. */
    static String title() {
        return Trmt.NAME + " generated wear textures";
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Collections.singleton(Trmt.MODID)
            : Collections.<String>emptySet();
    }

    /**
     * Each sprite's answer - its PNG and its {@code .mcmeta} - from the first time the atlas asks for it until the
     * stitch ends.
     *
     * <p>
     * Vanilla's atlas opens every texture once to learn its size (getBasicSpriteInfos) and once more to load it
     * (getLoadedSprites), OptiFine's opens it a third time, and each opening asks about the metadata before it
     * takes the picture. Answering honestly about the metadata means knowing whether the sprite is animated, which
     * is known only once it is drawn - a sprite moves, and its sheet is a strip, only if its moving layer is
     * adopted while composing - so the size cannot be worked out ahead and the picture itself is what is kept:
     * encoded, which is a fraction of the pixels, and only for the one stitch. Until 0.9.220 one sheet was kept per
     * thread instead, which saved the questions inside one opening and none across them.
     *
     * <p>
     * Held to the end of the stitch rather than let go at some count of reads, because the count is the
     * renderer's: the first version let go at the second and still drew everything twice under OptiFabric. Let go
     * wholly at the end of every stitch ({@link #forgetAll}) and when the pack is closed, so nothing outlives the
     * stitch it was drawn for; a read after that draws it again, as before. The atlas reads on worker threads - six at
     * once, vanilla's background executor - hence the concurrent map; two threads asking for one name at once is not
     * something it does, and if it did the second answer would simply be thrown away. A wear sprite's answer is the
     * same either way, since the sprite pass keeps what it filed until the stitch ends.
     */
    private final Map<Identifier, Answer> answers = new ConcurrentHashMap<Identifier, Answer>();

    /**
     * Every pack this class has made and not closed, whose answers the end of a stitch lets go.
     *
     * <p>
     * Every one, not the last made (0.9.224). The game makes a pack from its supplier each time the pack list is read,
     * and the resource manager goes on reading through the one it opened while newer ones are made beside it - so a
     * single "latest" could be a pack nothing reads, and the one being read kept the last stitch's pictures. The second
     * rebuild of the wear pictures in a session then had the atlas served those kept pictures without asking for new
     * ones, every wear sprite was left unusable, and no worn ground showed until the game was restarted: the narrow
     * rebuild of 0.9.223 asked twice in a row did it every time, found by the harness's atlas part. Weak, so a pack the
     * game has let go is not held here.
     */
    private static final Set<GeneratedPack> OPEN = Collections
        .synchronizedSet(Collections.newSetFromMap(new java.util.WeakHashMap<GeneratedPack, Boolean>()));

    private static final class Answer {

        final byte[] png;

        final String mcmeta;

        Answer(byte[] png, String mcmeta) {
            this.png = png;
            this.mcmeta = mcmeta;
        }
    }

    /**
     * The answer for one image: kept from an earlier read in this stitch, or drawn now - and encoded now, unless it
     * arrived encoded, which every wear sprite's does: the sprite pass encodes on its pool.
     */
    private Answer answer(Identifier image) throws IOException {
        Answer kept = answers.get(image);
        if (kept != null) return kept;
        Sheet sheet = sheets.sheet(image);
        if (sheet == null) return null;
        Answer made = new Answer(sheet.png != null ? sheet.png : encode(sheet), sheet.mcmeta);
        Answer first = answers.putIfAbsent(image, made);
        return first != null ? first : made;
    }

    /** Lets go of every answer the current reload's pack still holds - the end of a stitch. */
    public static void forgetAll() {
        synchronized (OPEN) {
            for (GeneratedPack pack : OPEN) pack.answers.clear();
        }
    }

    /**
     * One file of this pack, as 1.20 asks for one: a supplier of its bytes, or null where the pack has no such file -
     * which is how a pack says what it has, from 1.19.3, where 1.16.5 asked hasResource first.
     *
     * <p>
     * A still sprite has no metadata file, and saying it has one is not a small lie: the manager reads the metadata
     * before it takes the picture, and a file it cannot honour comes back as "using missing texture" for the sprite
     * itself. Which is what it did. So whether the {@code .mcmeta} is there is asked now, and the picture is drawn
     * when the atlas opens it.
     */
    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        final Identifier image = wanted(type, location);
        if (image == null) return null;

        boolean metadata = location.getPath()
            .endsWith(METADATA_SUFFIX);
        if (metadata) {
            Answer answer;
            try {
                answer = answer(image);
            } catch (IOException unencoded) {
                // Said by the picture's own read, which tries again and throws.
                return null;
            }
            if (answer == null || answer.mcmeta == null) return null;
            final byte[] bytes = answer.mcmeta.getBytes(StandardCharsets.UTF_8);
            return () -> new ByteArrayInputStream(bytes);
        }
        return () -> {
            Answer answer = answer(image);
            if (answer == null) throw new IOException(image + " could not be drawn");
            return new ByteArrayInputStream(answer.png);
        };
    }

    /**
     * Every wear sprite, to whoever lists this pack's textures.
     *
     * <p>
     * <strong>This is how the wear sprites reach the block atlas from 1.19.3</strong>, and the reason no stitch event
     * is needed any more: the atlas collects its sprites through its sources, and vanilla's own blocks source lists
     * every pack's {@code textures/block}, where these are. Only ever this mod's own folder, which is also the only
     * thing this pack claims a namespace for: a pack that answered more widely would be offering to supply files it
     * cannot draw. Either way round, as before: the caller may be listing a folder above ours or ours exactly.
     */
    @Override
    public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !Trmt.MODID.equals(namespace)) return;
        if (!FOLDER.startsWith(path) && !path.startsWith(FOLDER)) return;
        for (Identifier each : sheets.names()) {
            if (!each.getPath()
                .startsWith(path + "/")) continue;
            IoSupplier<InputStream> file = getResource(type, each);
            if (file != null) output.accept(each, file);
        }
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
    public IoSupplier<InputStream> getRootResource(String... name) {
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
    public <T> T getMetadataSection(MetadataSectionType<T> serializer) {
        // By 26.1 a metadata section is asked by its MetadataSectionType (MetadataSectionSerializer is gone), and the
        // pack section has one type per pack kind plus a fallback the game tries when the first will not parse: this
        // pack answers the client's and the fallback, which are both the "pack" section 1.21.1 answered as TYPE. The
        // format is a PackFormat, and the range of formats is a range of one: this pack is made by the game it runs in.
        if (serializer == PackMetadataSection.CLIENT_TYPE || serializer == PackMetadataSection.FALLBACK_TYPE) {
            return (T) new PackMetadataSection(
                Component.literal(title()),
                new net.minecraft.util.InclusiveRange<net.minecraft.server.packs.metadata.pack.PackFormat>(
                    SharedConstants.getCurrentVersion()
                        .packVersion(PackType.CLIENT_RESOURCES)));
        }
        return null;
    }

    @Override
    public void close() {
        answers.clear();
        OPEN.remove(this);
    }

    /**
     * The image a request is for, whether it asked for the image or for its metadata, or null when
     * the request is not this pack's at all.
     */
    private Identifier wanted(PackType type, Identifier location) {
        if (type != PackType.CLIENT_RESOURCES || location == null) return null;
        if (!Trmt.MODID.equals(location.getNamespace())) return null;

        String path = location.getPath();
        if (!path.startsWith(FOLDER)) return null;

        Identifier image = path.endsWith(METADATA_SUFFIX)
            ? Identifier.fromNamespaceAndPath(
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
     *
     * <p>
     * <strong>Written through a stream held in memory, never the one ImageIO picks.</strong> Handed an
     * {@code OutputStream}, ImageIO wraps it in a {@code FileCacheImageOutputStream} wherever the
     * temporary folder is writable - a file created in it, written, read back and deleted, for every
     * picture. Until 0.9.220 that was every wear picture of every stitch: measured on 2026-10-08, the
     * encoding took ninety seconds of thread time across the atlas's six loading threads, where the
     * composing took two, and a pool of eight encoding it was no faster, because what it was waiting
     * on was the disk. The writer, the
     * image and the bytes it writes are the same either way - only where the writer keeps them while
     * it goes back to fill in each chunk's length differs - which {@code PoolComposesWhatOneThreadDoesTest}
     * holds against the old route byte for byte. The JDK's own switch for this, {@code ImageIO.setUseCache},
     * is global to the game and every other mod in it, so it is left alone.
     */
    static byte[] encode(Sheet sheet) throws IOException {
        BufferedImage image = new BufferedImage(sheet.width, sheet.height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, sheet.width, sheet.height, sheet.pixels, 0, sheet.width);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageOutputStream stream = new MemoryCacheImageOutputStream(out);
        try {
            if (!ImageIO.write(image, "PNG", stream)) {
                throw new IOException("no PNG writer, which no JDK should be without");
            }
        } finally {
            // Flushes what the cache still holds into the bytes, and leaves them open; there is nothing to close.
            stream.close();
        }
        return out.toByteArray();
    }
}
