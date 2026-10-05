package com.trmtgtnh.client.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.PackType;

import com.trmtgtnh.Trmt;

/**
 * The generated pack, which is the whole of how wear sprites reach the atlas on this version.
 *
 * <p>
 * Worth testing without the game running, because everything it does is answerable on its own: given
 * some pixels, does it hand back a PNG of those pixels, does it own up to the names it can draw, and
 * does it keep its hands off everything else. If it gets any of that wrong the symptom is a missing
 * texture inside a resource reload, which is a slow and unpleasant place to debug.
 */
class GeneratedPackTest {

    private static final ResourceLocation MINE =
        new ResourceLocation(Trmt.MODID, GeneratedPack.FOLDER + "/grass_worn_3.png");

    private static final ResourceLocation ITS_METADATA =
        new ResourceLocation(Trmt.MODID, GeneratedPack.FOLDER + "/grass_worn_3.png.mcmeta");

    private static final String ANIMATION = "{\"animation\":{\"frametime\":2}}";

    /** Two by two, with one of every awkward kind of pixel. */
    private static final int[] PIXELS = {
        0xFFFF0000, // opaque red
        0x80008000, // half-transparent green
        0x00000000, // fully clear, and black underneath it
        0xFF0000FF, // opaque blue
    };

    private static GeneratedPack packOf(final boolean animated) {
        return new GeneratedPack(new GeneratedPack.Sheets() {

            @Override
            public Set<ResourceLocation> names() {
                return Collections.singleton(MINE);
            }

            @Override
            public GeneratedPack.Sheet sheet(ResourceLocation name) {
                if (!MINE.equals(name)) return null;
                return new GeneratedPack.Sheet(PIXELS, 2, 2, animated ? ANIMATION : null);
            }
        });
    }

    @Test
    void it_owns_the_names_it_can_draw_and_nothing_else() {
        GeneratedPack pack = packOf(false);

        assertTrue(pack.hasResource(PackType.CLIENT_RESOURCES, MINE), "its own sprite");

        assertFalse(
            pack.hasResource(
                PackType.CLIENT_RESOURCES,
                new ResourceLocation(Trmt.MODID, GeneratedPack.FOLDER + "/never_planned.png")),
            "a name in our folder that was never planned");
        assertFalse(
            pack.hasResource(
                PackType.CLIENT_RESOURCES,
                new ResourceLocation("minecraft", "textures/block/dirt.png")),
            "somebody else's texture, which this pack must never claim");
        assertFalse(
            pack.hasResource(PackType.SERVER_DATA, MINE),
            "the same name asked of the server side, where this pack holds nothing");
    }

    @Test
    void the_pixels_survive_the_round_trip_including_their_alpha() throws IOException {
        GeneratedPack pack = packOf(false);

        BufferedImage read;
        try (InputStream in = pack.getResource(PackType.CLIENT_RESOURCES, MINE)) {
            read = ImageIO.read(in);
        }

        assertEquals(2, read.getWidth());
        assertEquals(2, read.getHeight());

        int[] back = new int[4];
        read.getRGB(0, 0, 2, 2, back, 0, 2);
        // Alpha above all: a wear sprite is an overlay, and a PNG writer that flattened the
        // half-transparent pixel or filled in the clear one would draw a solid square over the
        // ground rather than a worn patch - which would look like a compositing bug for days.
        assertArrayEquals(PIXELS, back, "the pixels, their order and their alpha");
    }

    @Test
    void an_animated_sprite_serves_its_own_mcmeta_as_a_sibling_file() throws IOException {
        GeneratedPack pack = packOf(true);

        assertTrue(pack.hasResource(PackType.CLIENT_RESOURCES, ITS_METADATA));
        try (InputStream in = pack.getResource(PackType.CLIENT_RESOURCES, ITS_METADATA)) {
            assertEquals(ANIMATION, read(in));
        }
    }

    @Test
    void a_still_sprite_says_it_has_no_mcmeta_rather_than_refusing_one() {
        GeneratedPack pack = packOf(false);

        // This test used to assert the opposite - that hasResource said yes and getResource then
        // threw - on the reasoning that the manager asks before it takes. It does ask, and a yes it
        // cannot honour is not a small lie: the game logged "using missing texture" for the sprite
        // itself, not for its metadata, so every worn block drew the chequer. Found by running it.
        assertFalse(
            pack.hasResource(PackType.CLIENT_RESOURCES, ITS_METADATA),
            "a still sprite has no animation file and must say so");

        assertThrows(
            IOException.class,
            () -> pack.getResource(PackType.CLIENT_RESOURCES, ITS_METADATA),
            "and must still refuse to invent one if asked anyway");
    }

    @Test
    void it_lists_what_it_holds_when_the_atlas_asks() {
        GeneratedPack pack = packOf(false);

        Collection<ResourceLocation> found =
            pack.getResources(PackType.CLIENT_RESOURCES, Trmt.MODID, GeneratedPack.FOLDER, 99, name -> true);
        assertEquals(Collections.singletonList(MINE), found);

        assertTrue(
            pack.getResources(PackType.CLIENT_RESOURCES, "minecraft", "textures", 99, name -> true)
                .isEmpty(),
            "another namespace's listing must come back empty");
    }

    @Test
    void it_answers_for_its_own_pack_metadata_or_it_is_never_added_at_all() {
        GeneratedPack pack = packOf(false);

        // Pack.create asks every pack for this before it will build anything and hands back null if
        // it does not get one. A pack that answered null here would never be added, with one line in
        // a log and no wear textures at all - the game running perfectly and the mod doing nothing.
        PackMetadataSection declared = pack.getMetadataSection(PackMetadataSection.SERIALIZER);
        assertNotNull(declared, "without this the pack is silently dropped and nothing draws wear");

        assertEquals(
            SharedConstants.getCurrentVersion()
                .getPackVersion(),
            declared.getPackFormat(),
            "taken from the running game, so this pack is never the one reporting a wrong version");

        assertNull(
            pack.getMetadataSection(AnimationMetadataSection.SERIALIZER),
            "a section this pack has no business answering for");
    }

    @Test
    void it_claims_one_namespace_on_the_client_and_none_on_the_server() {
        GeneratedPack pack = packOf(false);

        assertEquals(Collections.singleton(Trmt.MODID), pack.getNamespaces(PackType.CLIENT_RESOURCES));
        assertTrue(pack.getNamespaces(PackType.SERVER_DATA).isEmpty());
    }

    private static String read(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int read;
        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
