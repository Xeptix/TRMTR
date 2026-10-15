package com.trmtgtnh.client.texture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.trmtgtnh.Trmt;

/**
 * Composing wear sprites across the cores a machine has: the 1.7.10 edition's class of the same name, carried.
 *
 * <p>
 * Eighty gradations is five times the sprites sixteen was. Paying for that in parallel rather than by caching to disk
 * was the decision in the 1.7.10 edition, and the reasoning is worth keeping where it can be found: a disk cache's key
 * would have to cover the mod list, this mod's config, the source textures' actual pixels and the rotation seed; a key
 * too narrow serves stale art in silence; and it would only ever help the second launch, being cold exactly when
 * somebody first installs the mod and forms their impression of it.
 *
 * <p>
 * The whole safety of this rests on one rule, and the rule is structural rather than defensive: <b>a worker sees
 * nothing but arrays and numbers</b>. Not the resource manager, which reads zip entries and is documented nowhere as
 * safe to share. Not a {@code Block}, whose code is any mod's. Not another sprite, nor {@link InnerLayers}, whose
 * ask, ledger and window filing are plain static fields. Not {@link WearPatterns}, whose caches are plain hash maps.
 * Not the pass's own maps of sources, nor its tally. Every one of those is done before or after, on the one thread
 * running the pass, and {@link WearSprite#compose} takes no argument that could reach any of them - so the rule is
 * checked by the compiler rather than by whoever reads this next. The other half of a worker's job here is the PNG
 * the pack serves, and that too is arrays and numbers: a {@link GeneratedPack.Sheet} is a pixel array, two sizes and
 * an {@code .mcmeta} string, and encoding it touches nothing but the JDK's own image writer, which the atlas's own
 * loading threads were already running six at a time.
 *
 * <p>
 * <strong>Where this edition differs, and why the rule needed carrying rather than only the speed.</strong> The atlas
 * here asks for its textures from vanilla's background executor - six {@code Worker-Main} threads at once on a machine
 * with more than seven cores, which a stack trace from inside the old per-load compose showed in both loaders' dev
 * runs - and until 0.9.220 each of those loads harvested its surface and counted itself into the pass with no lock at
 * all: a surface was routinely read twice (a Fabric dev run read its 77 surfaces 150 times), a count could be lost (a
 * Forge one said 22,406 composed of 22,407), and {@link InnerLayers}' open ask could be priced against another
 * thread's surface. Under OptiFabric the same log reads as one thread - every surface read exactly once, and the pass
 * more than twice as long - so there the pictures were made one at a time. Either way, as in the 1.7.10 edition, one
 * thread runs the pass now - the first of the stitch's loads to ask for a picture, holding the pass's lock while every
 * other load waits for it - and the pool does the arithmetic.
 */
final class WearGeneration {

    private WearGeneration() {}

    /**
     * How many threads compose sprites.
     *
     * <p>
     * One short of the machine, because the thread running the pass is harvesting and installing between batches.
     * Capped at eight because the gain past that is small and because a stitch is not only a launch-time event: a
     * resource pack change or F3+T runs one with the world loaded, the chunk mesher's own threads alive and the
     * integrated server ticking, and that is not the moment to claim a whole machine.
     */
    static ExecutorService newPool() {
        return newPool(
            Math.max(
                1,
                Math.min(
                    8,
                    Runtime.getRuntime()
                        .availableProcessors() - 1)));
    }

    /** A pool of exactly this many, which the test that holds the pool to the one-thread pictures uses. */
    static ExecutorService newPool(int threads) {
        return Executors.newFixedThreadPool(Math.max(1, threads), new WorkerThreads());
    }

    /**
     * Composes one batch on the pool, installs it here, encodes it on the pool, and files each finished picture for
     * its own load to collect; then empties the batch.
     *
     * <p>
     * Installing is on this thread for the same reason harvesting was. It writes the sprite's own fields, and a sprite
     * whose layer moves takes it here, which puts the picture on {@link InnerLayers}' ledger - a plain static field
     * that two threads would interleave into. The tally is this thread's for the same reason: until 0.9.220 it was
     * counted from six threads with {@code ++}, and the line that says how many pictures were composed said one fewer
     * than were.
     *
     * <p>
     * Encoding goes back out to the pool because it is work of the same kind - pixels in, bytes out, one picture at a
     * time - and a loader that asks for one texture at a time would otherwise leave all of it on the one thread. What is filed is the encoded picture, which is what the pack would have kept
     * for the rest of the stitch anyway; the pixels are let go as each batch is encoded.
     */
    static void composeAndInstall(ExecutorService pool, List<WearSprite> pending, List<WearSprite.Source> ingredients,
        WearTextures.Tally tally, Map<WearSprite, GeneratedPack.Sheet> finished) {
        if (pending.isEmpty()) return;

        List<int[]> composed;
        try {
            composed = compose(pool, pending, ingredients);
        } catch (RuntimeException poolRefused) {
            // A pool that will not take work, or one interrupted out from under this. Composing here instead is
            // slower and is exactly what this edition did before it threaded, so the worst case is a longer loading
            // screen rather than an atlas full of holes.
            Trmt.LOG.warn("Composing wear textures on the thread running the sprite pass; the worker pool refused",
                poolRefused);
            composed = new ArrayList<int[]>(pending.size());
            for (int i = 0; i < pending.size(); i++) {
                composed.add(composeSafely(pending.get(i), ingredients.get(i)));
            }
        }

        List<WearSprite> installed = new ArrayList<WearSprite>(pending.size());
        List<GeneratedPack.Sheet> sheets = new ArrayList<GeneratedPack.Sheet>(pending.size());
        int[] lumas = new int[pending.size()];
        for (int i = 0; i < pending.size(); i++) {
            WearSprite sprite = pending.get(i);
            WearSprite.Source source = ingredients.get(i);
            int[] pixels = composed.get(i);
            // Let go of as it is read, so a batch's pictures are held once, as sheets, from here on.
            composed.set(i, null);
            if (pixels == null) {
                sprite.markUnusable();
                tally.failed++;
                continue;
            }
            // Every picture is composed at the edge its sprite was planned at, so this counts a fault rather than a
            // case; install still scales such a picture to fit its slot.
            int wanted = sprite.plannedEdge();
            if (pixels.length != (long) wanted * wanted) {
                tally.resized++;
                tally.resizedNames.add(sprite.originName());
            }
            GeneratedPack.Sheet sheet;
            try {
                sheet = sprite.install(pixels, source);
            } catch (RuntimeException awkwardPicture) {
                Trmt.LOG.debug("Could not install wear texture {}", sprite.name(), awkwardPicture);
                sheet = null;
            }
            if (sheet == null) {
                // The sprite must not go on looking usable with the placeholder its load hands the atlas instead.
                sprite.markUnusable();
                tally.failed++;
                continue;
            }
            lumas[installed.size()] = WearTextures.Tally.luma(pixels);
            installed.add(sprite);
            sheets.add(sheet);
        }

        List<GeneratedPack.Sheet> encoded;
        try {
            encoded = encode(pool, sheets);
        } catch (RuntimeException poolRefused) {
            Trmt.LOG.warn("Encoding wear textures on the thread running the sprite pass; the worker pool refused",
                poolRefused);
            encoded = new ArrayList<GeneratedPack.Sheet>(sheets.size());
            for (GeneratedPack.Sheet sheet : sheets) {
                encoded.add(encodeSafely(sheet));
            }
        }
        sheets.clear();

        for (int i = 0; i < installed.size(); i++) {
            WearSprite sprite = installed.get(i);
            GeneratedPack.Sheet picture = encoded.get(i);
            if (picture == null) {
                // Its load hands the atlas the placeholder, which nothing draws.
                sprite.markUnusable();
                tally.failed++;
                continue;
            }
            finished.put(sprite, picture);
            // As the 1.7.10 edition asks it: install has just set the sprite's own mark from its source.
            tally.built(sprite, lumas[i], sprite.isImprovised());
        }

        pending.clear();
        ingredients.clear();
    }

    private static List<int[]> compose(ExecutorService pool, List<WearSprite> pending,
        List<WearSprite.Source> ingredients) {
        List<Callable<int[]>> jobs = new ArrayList<Callable<int[]>>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            jobs.add(new ComposeOne(pending.get(i), ingredients.get(i)));
        }
        return collect(pool, jobs, "composing");
    }

    private static List<GeneratedPack.Sheet> encode(ExecutorService pool, List<GeneratedPack.Sheet> sheets) {
        List<Callable<GeneratedPack.Sheet>> jobs = new ArrayList<Callable<GeneratedPack.Sheet>>(sheets.size());
        for (GeneratedPack.Sheet sheet : sheets) {
            jobs.add(new EncodeOne(sheet));
        }
        return collect(pool, jobs, "encoding");
    }

    /**
     * Every job's answer, in the order the jobs were given.
     *
     * <p>
     * Collected one by one rather than trusting invokeAll's return, because it is {@code Future.get} that carries the
     * documented guarantee that everything a worker wrote is visible here - which includes the shell a picture with a
     * moving layer leaves on its sprite for {@code install}. The two happen to agree in this implementation, and an
     * invariant this design rests on should not be resting on that.
     */
    private static <T> List<T> collect(ExecutorService pool, List<Callable<T>> jobs, String doing) {
        try {
            List<Future<T>> futures = pool.invokeAll(jobs);
            List<T> out = new ArrayList<T>(futures.size());
            for (Future<T> future : futures) {
                try {
                    out.add(future.get());
                } catch (ExecutionException impossible) {
                    out.add(null);
                }
            }
            return out;
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new IllegalStateException("interrupted while " + doing + " wear textures", interrupted);
        }
    }

    /**
     * One sprite's pixels, and never a thrown exception.
     *
     * <p>
     * A worker that threw would surface as an {@code ExecutionException} in the middle of a stitch and take the whole
     * atlas with it, which is exactly the outcome the catch round every harvest was written to prevent. One block that
     * cannot be worn is worth one sprite, so the failure comes back as a null and is dealt with where the counting
     * happens. Workers say nothing themselves: twenty thousand stack traces is its own outage.
     */
    private static final class ComposeOne implements Callable<int[]> {

        private final WearSprite sprite;

        private final WearSprite.Source source;

        ComposeOne(WearSprite sprite, WearSprite.Source source) {
            this.sprite = sprite;
            this.source = source;
        }

        @Override
        public int[] call() {
            return composeSafely(sprite, source);
        }
    }

    /** One installed picture as the PNG the pack serves, and never a thrown exception, for the same reason. */
    private static final class EncodeOne implements Callable<GeneratedPack.Sheet> {

        private final GeneratedPack.Sheet sheet;

        EncodeOne(GeneratedPack.Sheet sheet) {
            this.sheet = sheet;
        }

        @Override
        public GeneratedPack.Sheet call() {
            return encodeSafely(sheet);
        }
    }

    static int[] composeSafely(WearSprite sprite, WearSprite.Source source) {
        try {
            return sprite.compose(source);
        } catch (Throwable awkwardSprite) {
            return null;
        }
    }

    static GeneratedPack.Sheet encodeSafely(GeneratedPack.Sheet sheet) {
        try {
            return sheet.encoded();
        } catch (Throwable unencodable) {
            return null;
        }
    }

    /** Daemon workers, named so a profile or a crash report says whose threads these are. */
    private static final class WorkerThreads implements ThreadFactory {

        private int next;

        @Override
        public Thread newThread(Runnable job) {
            Thread thread = new Thread(job, "TRMT wear texture " + next++);
            // Daemon because a stitch that fails half way must not leave a thread holding the game open, and a notch
            // below normal because the loading screen has to go on moving.
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            return thread;
        }
    }

    static void shutDown(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30L, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException interrupted) {
            pool.shutdownNow();
            Thread.currentThread()
                .interrupt();
        }
    }
}
