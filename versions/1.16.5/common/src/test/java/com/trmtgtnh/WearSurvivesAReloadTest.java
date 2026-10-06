package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Wear has to be written down, and it has to be read back.
 *
 * <p>
 * <strong>Written after a session in which every road vanished on reload.</strong>
 * {@code ErosionStore} has four hooks - a chunk's bytes arriving, a chunk arriving, a chunk being
 * written, a chunk going - and its own javadoc names the events each loader should call them from.
 * Neither loader called two of them. Wear accumulated in memory for one session, was handed out
 * correctly all the while, and was dropped on unload with nothing ever reaching the disk. Every save
 * opened as new ground.
 *
 * <p>
 * This is the third gap of exactly this shape in this edition: the common module complete, the loader
 * wiring absent, every test green because every test reads the common module. Detection was the
 * first - see {@code DetectionRunsOnAServerTest} - the render pass and the tint were the second and
 * third. A missing call compiles, so what is held here is not behaviour but the call itself.
 *
 * <p>
 * The first test below is the general one and is the one that would have caught all three: a hook
 * this store documents as the loader's to call has to actually be called by somebody who is not this
 * store. The rest name the particular events, because which event is right took a probe to settle and
 * is worth recording where it will be read.
 */
class WearSurvivesAReloadTest {

    /** The four hooks, exactly as {@code ErosionStore} names them. */
    private static final String[] HOOKS = { "chunkDataLoaded", "chunkLoaded", "chunkDataSaving", "chunkUnloaded" };

    private static final String STORE = "com/trmtgtnh/erosion/ErosionStore.java";

    @Test
    void every_hook_a_loader_is_meant_to_call_has_a_caller() throws IOException {
        List<String> orphaned = new ArrayList<String>();
        for (String hook : HOOKS) {
            List<String> callers = new ArrayList<String>();
            for (File root : sourceRoots()) {
                gather(root, hook, callers);
            }
            if (callers.isEmpty()) orphaned.add(hook);
        }

        assertTrue(
            orphaned.isEmpty(),
            "ErosionStore hooks that nothing calls: " + orphaned
                + ". A hook with no caller is a feature that compiles and does nothing - this is how "
                + "wear stopped surviving a reload, and the store's own javadoc says which event each "
                + "of these belongs to.");
    }

    @Test
    void both_loaders_read_and_write_the_chunk_tag() throws IOException {
        for (String module : new String[] { "forge", "fabric" }) {
            File mixin = new File(
                SourceTree.repoRoot(),
                module + "/src/main/java/com/trmtgtnh/" + module + "/mixin/MixinChunkSerializer.java");
            String source = read(mixin);

            assertTrue(
                source.contains("@Mixin(ChunkSerializer.class)"),
                module + " has to go through ChunkSerializer, which is the only place either loader can "
                    + "see the level and the tag at once");
            assertTrue(
                source.contains("chunkDataLoaded(") && source.contains("chunkDataSaving("),
                module + "'s mixin has to do both halves; one of them alone is a save that never loads "
                    + "or a load that never saves");

            // A mixin that is not in the config is a file that compiles and never applies, which
            // looks exactly like a mixin that does not work.
            String config = read(
                new File(SourceTree.repoRoot(), module + "/src/main/resources/trmtgtnh-" + module + ".mixins.json"));
            assertTrue(
                config.contains("\"MixinChunkSerializer\""),
                module + " declares the mixin but never lists it in trmtgtnh-"
                    + module
                    + ".mixins.json, so it would never apply");
        }
    }

    @Test
    void both_loaders_hear_a_chunk_arrive_and_leave() throws IOException {
        String forge = read(new File(SourceTree.repoRoot(), "forge/src/main/java/com/trmtgtnh/forge/ForgeEvents.java"));
        assertTrue(
            forge.contains("ChunkEvent.Load") && forge.contains("chunkLoaded("),
            "Forge has to promote a chunk's wear when the chunk arrives");
        assertTrue(
            forge.contains("ChunkEvent.Unload") && forge.contains("chunkUnloaded("),
            "and set it aside when the chunk goes, because the save comes after the unload");

        String fabric = read(
            new File(SourceTree.repoRoot(), "fabric/src/main/java/com/trmtgtnh/fabric/FabricEvents.java"));
        assertTrue(
            fabric.contains("ServerChunkEvents.CHUNK_LOAD") && fabric.contains("chunkLoaded("),
            "Fabric has to do the same from its own event");
        assertTrue(
            fabric.contains("ServerChunkEvents.CHUNK_UNLOAD") && fabric.contains("chunkUnloaded("),
            "and the same on the way out");
    }

    @Test
    void a_chunk_going_does_not_take_the_record_with_it() throws IOException {
        String store = String.join("\n", SourceTree.lines(STORE));
        int at = store.indexOf("public void chunkUnloaded(");
        assertTrue(at > 0, "the store has to have an unload hook");
        String body = store.substring(at, Math.min(store.length(), at + 2600));

        assertTrue(
            body.contains("unloading.put("),
            "an unloading chunk's record has to be kept: Minecraft unloads a chunk and then saves it, "
                + "so dropping the record here throws it away a moment before its one chance to persist");
        assertTrue(
            body.contains("Weather.forget(") && body.contains("SnowCover.forget("),
            "and the per-chunk meters have to go with it, or they are held for every chunk ever walked "
                + "on rather than for every chunk in memory");
        assertTrue(
            body.contains("markModified(level, chunkX, chunkZ)"),
            "a chunk leaving with unwritten wear has to be told it changed, or the save that follows "
                + "the unload is skipped and the record is stranded");
    }

    @Test
    void a_stranded_record_beats_the_copy_on_disk() throws IOException {
        String store = String.join("\n", SourceTree.lines(STORE));
        int at = store.indexOf("public void chunkLoaded(");
        assertTrue(at > 0, "the store has to have an arrival hook");
        String body = store.substring(at, Math.min(store.length(), at + 1800));

        // The record is newer than anything on disk by construction: it was in memory when the chunk
        // left and was never written. Reading the chunk back in used to promote the older disk copy
        // straight over the top of it, which is wear going backwards with every byte of the newer
        // version still in memory at the time.
        assertTrue(
            body.contains("unloading.remove("),
            "arriving has to claim any record this chunk left stranded on its way out");
        assertTrue(body.contains("data = stranded"), "and prefer it to what was read off disk, which is older");
    }

    @Test
    void a_stopped_server_lets_go_of_the_world() throws IOException {
        String events = String.join("\n", SourceTree.lines("com/trmtgtnh/server/ServerEvents.java"));
        int at = events.indexOf("public static void serverStopped(");
        assertTrue(at > 0, "there has to be a moment where a stopped server's state is let go");
        String body = events.substring(at, Math.min(events.length(), at + 900));

        // Single player stops a server every time a world is left and starts another for the next
        // one, so anything kept here is handed to the next world rather than merely being stale.
        for (String letGo : new String[] { "clearMemory()", "clearOrphans()", "Weather.reset()",
            "SnowCover.reset()" }) {
            assertTrue(
                body.contains(letGo),
                "serverStopped has to call " + letGo + ", or one save's roads turn up in the next one");
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static List<File> sourceRoots() {
        List<File> roots = new ArrayList<File>();
        roots.add(SourceTree.mainJava());
        roots.add(new File(SourceTree.repoRoot(), "forge/src/main/java"));
        roots.add(new File(SourceTree.repoRoot(), "fabric/src/main/java"));
        return roots;
    }

    private static String read(File file) throws IOException {
        assertFalse(!file.isFile(), file.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Every file that calls {@code hook} on the store, ignoring the store itself and any comment.
     *
     * <p>
     * The receiver is part of what is looked for, and that is the whole difference between this test
     * and a weaker one that would have passed throughout the bug. {@code Worlds.chunkLoaded} exists,
     * is called twice, and is a different question entirely - "is that chunk in memory" rather than
     * "a chunk has arrived". A search for the bare name finds it and reports the store's hook as
     * wired. The first draft of this test did exactly that, and so did the tool that found the gap.
     *
     * <p>
     * Whitespace is dropped before looking, because every call site in this codebase is written
     * across two lines by the formatter.
     */
    private static void gather(File at, String hook, List<String> into) throws IOException {
        File[] children = at.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                gather(child, hook, into);
                continue;
            }
            String name = child.getName();
            if (!name.endsWith(".java") || name.equals("ErosionStore.java")) continue;

            StringBuilder code = new StringBuilder();
            for (String line : Files.readAllLines(child.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) continue;
                code.append(trimmed);
            }
            String squashed = code.toString()
                .replaceAll("\\s+", "");
            if (squashed.contains("ErosionStore.get()." + hook + "(")) into.add(child.getPath());
        }
    }
}
