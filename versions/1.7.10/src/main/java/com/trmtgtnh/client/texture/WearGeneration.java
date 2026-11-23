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
 * Composing wear sprites across the cores a machine has.
 *
 * <p>
 * Eighty gradations is five times the sprites sixteen was, which measures at twelve seconds on one
 * thread. Paying for that in parallel rather than by caching to disk was the decision, and the
 * reasoning is worth keeping where it can be found: a disk cache's key would have to cover the mod
 * list, this mod's config, the source textures' actual pixels and the rotation seed; a key too
 * narrow serves stale art in silence; and it would only ever help the second launch, being cold
 * exactly when somebody first installs the mod and forms their impression of it.
 *
 * <p>
 * The whole safety of this rests on one rule, and the rule is structural rather than defensive: <b>a
 * worker sees nothing but arrays and numbers</b>. Not the resource manager, which reads zip entries
 * and is documented nowhere as safe to share. Not a {@code Block}, whose {@code getIcon} is code
 * from any of two hundred mods and already throws often enough to have three catch blocks written
 * round it. Not another sprite, whose pixel array the atlas swaps wholesale. Not
 * {@link WearPatterns}, whose caches are plain hash maps. Not vanilla's mipmap generator, which
 * blends through one static four-element scratch buffer. Every one of those is done before or after,
 * on the thread that called in, and {@link WearSprite#compose} takes no argument that could reach
 * any of them - so the rule is checked by the compiler rather than by whoever reads this next.
 */
final class WearGeneration {

    private WearGeneration() {}

    /**
     * How many threads compose sprites.
     *
     * <p>
     * One short of the machine, because this thread is reading and installing between batches and
     * the loading screen is drawing against the same context. Capped at eight because the gain past
     * that is small and because a stitch is not only a launch-time event: a resource pack change or
     * F3+T runs one with the world loaded, the chunk mesher's own pool alive and the integrated
     * server ticking, and that is not the moment to claim a whole machine.
     */
    static ExecutorService newPool() {
        int threads = Math.max(
            1,
            Math.min(
                8,
                Runtime.getRuntime()
                    .availableProcessors() - 1));
        return Executors.newFixedThreadPool(threads, new WorkerThreads());
    }

    /**
     * Composes one batch on the pool and installs it here, then empties the batch.
     *
     * <p>
     * Installing is on this thread for the same reason reading was. It writes the sprite's own
     * fields, and it regenerates mipmaps - which goes through vanilla's {@code TextureUtil}, whose
     * blend for a frame containing a transparent pixel reads and writes one shared static array of
     * four. Two threads mipmapping a grass overlay or a fringe at once would interleave into it and
     * produce wrong colors in the lower levels, silently and non-deterministically.
     */
    static void composeAndInstall(ExecutorService pool, List<WearSprite> pending, List<WearSprite.Source> ingredients,
        int mipmapLevels, WearTextures.Tally tally) {
        composeAndInstall(pool, pending, ingredients, mipmapLevels, tally, null);
    }

    /**
     * The pictures already made, for one pass (1.0.1, W3: {@link WearPictureCache}): the cache, what every key starts
     * from (the mod's version), and each source's own key, made once a source however many sprites share it. This
     * edition keeps the composed pixels, which its install takes as it takes a composed picture's.
     */
    static final class Cached {

        final WearPictureCache cache;

        private final byte[] context;

        private final Map<WearSprite.Source, byte[]> sources = new java.util.IdentityHashMap<WearSprite.Source, byte[]>();

        /** Pictures found, and pictures made and kept. */
        int found;

        int kept;

        Cached(WearPictureCache cache, byte[] context) {
            this.cache = cache;
            this.context = context;
        }

        /**
         * One picture's key, or null for a picture the cache does not keep: everything compose reads, which is the
         * sprite's own fields and the whole of its source, deep. The render thread only.
         */
        byte[] keyOf(WearSprite sprite, WearSprite.Source source) {
            if (cache == null || !sprite.cacheable(source)) return null;
            byte[] from = sources.get(source);
            if (from == null) {
                from = new WearPictureCache.Key().add("Source")
                    .addDeep(source)
                    .finish();
                sources.put(source, from);
            }
            WearPictureCache.Key key = new WearPictureCache.Key().add("WearPicture")
                .add(context);
            sprite.addTo(key);
            return key.add(from)
                .finish();
        }
    }

    /**
     * As {@link #composeAndInstall(ExecutorService, List, List, int, WearTextures.Tally)}, taking from {@code cached}
     * the pixels of every picture it already holds, installed as composed ones are, and keeping there the pixels of
     * every one composed here. Null composes everything.
     */
    static void composeAndInstall(ExecutorService pool, List<WearSprite> pending, List<WearSprite.Source> ingredients,
        int mipmapLevels, WearTextures.Tally tally, Cached cached) {
        if (pending.isEmpty()) return;

        List<int[]> composed = cached == null ? composeOnPool(pool, pending, ingredients)
            : composeOrFind(pool, pending, ingredients, cached);

        for (int i = 0; i < pending.size(); i++) {
            WearSprite sprite = pending.get(i);
            int[] pixels = composed.get(i);
            if (pixels == null) {
                sprite.markUnusable();
                // A glow overlay with nothing to light is no failure: most gradations of the look have none.
                if (!sprite.isGlow()) tally.failed++;
                continue;
            }
            // Every picture is composed at the edge its sprite was stitched at, so this counts a fault rather than a
            // case; install still scales such a picture to fit its slot.
            int wanted = sprite.getIconWidth();
            if (pixels.length != (long) wanted * wanted) {
                tally.resized++;
                tally.resizedNames.add(sprite.originName());
            }
            if (!sprite.install(pixels, ingredients.get(i), mipmapLevels)) {
                // A sprite the atlas never sized; the pass skips those, so this too is a fault, and the sprite must
                // not go on looking usable with the placeholder it was stitched with.
                sprite.markUnusable();
                tally.failed++;
                continue;
            }
            // A glow overlay is not a wear picture and is not tallied as one.
            if (sprite.isGlow()) continue;
            tally.built++;
            if (sprite.isImprovised()) {
                tally.guessed++;
                tally.improvised.add(sprite.originName());
            }
        }

        pending.clear();
        ingredients.clear();
    }

    /** Each picture of a batch composed on the pool, or here where the pool refuses. */
    static List<int[]> composeOnPool(ExecutorService pool, List<WearSprite> pending,
        List<WearSprite.Source> ingredients) {
        List<int[]> composed;
        try {
            composed = compose(pool, pending, ingredients);
        } catch (RuntimeException poolRefused) {
            // A pool that will not take work, or one interrupted out from under this. Composing here
            // instead is slower and is exactly what this mod did before it threaded, so the worst
            // case is a longer loading screen rather than an atlas full of holes.
            Trmt.LOG.warn("Composing wear textures on the render thread; the worker pool refused", poolRefused);
            composed = new ArrayList<int[]>(pending.size());
            for (int i = 0; i < pending.size(); i++) {
                composed.add(safely(pending.get(i), ingredients.get(i)));
            }
        }

        return composed;
    }

    /**
     * Each picture of a batch from the cache where it holds it, the rest composed on the pool and kept; in the batch's
     * order. A glow overlay with nothing to light is kept as a picture of no bytes, and a failure is not kept at all.
     */
    static List<int[]> composeOrFind(ExecutorService pool, List<WearSprite> pending,
        List<WearSprite.Source> ingredients, Cached cached) {
        int[][] held = new int[pending.size()][];
        boolean[] found = new boolean[pending.size()];
        byte[][] keys = new byte[pending.size()][];
        List<WearSprite> missed = new ArrayList<WearSprite>(pending.size());
        List<WearSprite.Source> missedFrom = new ArrayList<WearSprite.Source>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            WearSprite sprite = pending.get(i);
            keys[i] = cached.keyOf(sprite, ingredients.get(i));
            WearPictureCache.Entry entry = keys[i] == null ? null : cached.cache.get(keys[i]);
            int[] pixels = entry == null ? null : WearPictureCache.bytesToPixels(entry.payload);
            boolean fits = entry != null && pixels != null
                && (pixels.length == 0 ? sprite.isGlow() : pixels.length == entry.c * entry.d);
            if (fits) {
                held[i] = pixels.length == 0 ? null : pixels;
                found[i] = true;
                sprite.composedCached(entry.b);
                cached.found++;
                continue;
            }
            missed.add(sprite);
            missedFrom.add(ingredients.get(i));
        }
        List<int[]> made = composeOnPool(pool, missed, missedFrom);
        List<int[]> out = new ArrayList<int[]>(pending.size());
        for (int i = 0, next = 0; i < pending.size(); i++) {
            if (found[i]) {
                out.add(held[i]);
                continue;
            }
            int[] pixels = made.get(next++);
            out.add(pixels);
            if (keys[i] == null) continue;
            WearSprite sprite = pending.get(i);
            if (pixels != null) {
                int edge = (int) Math.round(Math.sqrt(pixels.length));
                cached.cache.put(
                    keys[i],
                    new WearPictureCache.Entry(
                        WearPictureCache.pixelsToBytes(pixels),
                        0,
                        sprite.glowLevelOf(),
                        edge,
                        pixels.length / Math.max(1, edge)));
                cached.kept++;
            } else if (sprite.isGlow()) {
                cached.cache.put(keys[i], new WearPictureCache.Entry(new byte[0], 0, 0, 0, 0));
            }
        }
        return out;
    }

    private static List<int[]> compose(ExecutorService pool, List<WearSprite> pending,
        List<WearSprite.Source> ingredients) {
        List<Callable<int[]>> jobs = new ArrayList<Callable<int[]>>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            jobs.add(new ComposeOne(pending.get(i), ingredients.get(i)));
        }
        try {
            List<Future<int[]>> futures = pool.invokeAll(jobs);
            List<int[]> out = new ArrayList<int[]>(futures.size());
            for (Future<int[]> future : futures) {
                // Collected one by one rather than trusting invokeAll's return, because it is
                // Future.get that carries the documented guarantee that everything a worker wrote is
                // visible here. The two happen to agree in this implementation, and an invariant
                // this design rests on should not be resting on that.
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
            throw new IllegalStateException("interrupted while composing wear textures", interrupted);
        }
    }

    /**
     * One sprite's pixels, and never a thrown exception.
     *
     * <p>
     * A worker that threw would surface as an {@code ExecutionException} in the middle of a stitch
     * and take the whole atlas with it, which is exactly the outcome the catch round every harvest in
     * the sprite pass was written to prevent. One block that cannot be worn is worth one sprite, so
     * the failure comes back as a null and is dealt with where the logging happens.
     * Workers say nothing themselves: a hundred and ninety thousand stack traces is its own outage.
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
            return safely(sprite, source);
        }
    }

    private static int[] safely(WearSprite sprite, WearSprite.Source source) {
        try {
            return sprite.compose(source);
        } catch (Throwable awkwardSprite) {
            return null;
        }
    }

    /** Daemon workers, named so a profile or a crash report says whose threads these are. */
    private static final class WorkerThreads implements ThreadFactory {

        private int next;

        @Override
        public Thread newThread(Runnable job) {
            Thread thread = new Thread(job, "TRMT wear texture " + next++);
            // Daemon because a stitch that fails half way must not leave a thread holding the game
            // open, and a notch below normal because the render thread is between batches and the
            // loading screen has to go on moving.
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
