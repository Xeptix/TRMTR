package com.trmtgtnh.client.texture;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import com.trmtgtnh.Trmt;

/**
 * Composing wear sprites across the cores a machine has - the 1.7.10 edition's class of the same name, carried, with
 * the one thing this edition needs that one did not: composing ahead of the loads that ask for the pictures.
 *
 * <p>
 * Eighty gradations is five times the sprites sixteen was, which measures at twelve seconds on one
 * thread. Paying for that in parallel rather than by caching to disk was the decision, and the
 * reasoning is worth keeping where it can be found: a disk cache's key would have to cover the mod
 * list, this mod's config, the source textures' actual pixels and the rotation seed; a key too
 * narrow serves stale art in silence; and it would only ever help the second launch, being cold
 * exactly when somebody first installs the mod and forms their impression of it. Until 0.9.220 this edition composed
 * every picture on one thread, inside each sprite's own load, and with Chisel installed that was a stitch of three and
 * a half minutes - 133,467 pictures in 202,640 ms - where the other edition composes 108,162 in under twenty seconds.
 *
 * <p>
 * The whole safety of this rests on one rule, and the rule is structural rather than defensive: <b>a
 * worker sees nothing but arrays and numbers</b>. Not the resource manager, which reads zip entries
 * and is documented nowhere as safe to share. Not a {@code Block}, whose model, state and texture
 * answers are code from any mod in the pack. Not another sprite, whose frames the atlas loads, mipmaps
 * and swaps wholesale on its own thread. Not {@link FaceSource}, whose caches of faces are plain hash
 * maps, nor {@link WearPatterns}, whose caches are too. Not {@link InnerLayers}, whose animation budget
 * is spent first come, first served, so that the order surfaces are read in is part of the result. Not
 * the pass's own maps of sources and its tally. Not vanilla's mipmap generator, which blends through
 * one static four-element scratch buffer. Every one of those is done before or after, on the thread
 * loading the atlas, and {@link WearSprite#compose} takes no argument that could reach any of them -
 * so the rule is checked by the compiler rather than by whoever reads this next, and
 * {@code WearGenerationTest} holds the one argument it takes to arrays, numbers and strings.
 */
final class WearGeneration {

    private WearGeneration() {}

    /** How long an idle worker waits for more work before it ends. */
    private static final long IDLE_SECONDS = 30L;

    /**
     * How many threads compose sprites.
     *
     * <p>
     * One short of the machine, because the thread loading the atlas is reading, installing and mipmapping between
     * batches and the loading screen is drawing against the same context. Capped at eight because the gain past that
     * is small and because a stitch is not only a launch-time event: a resource pack change or F3+T runs one with the
     * world loaded, the chunk renderer's own threads alive and the integrated server ticking, and that is not the
     * moment to claim a whole machine.
     */
    static int threads() {
        return Math.max(
            1,
            Math.min(
                8,
                Runtime.getRuntime()
                    .availableProcessors() - 1));
    }

    static ExecutorService newPool() {
        return newPool(threads());
    }

    /**
     * A fixed pool of that many workers whose threads end themselves when idle.
     *
     * <p>
     * The ending is the one thing added to the other edition's pool. There the whole pass runs inside one method and
     * the pool is shut in its {@code finally}. Here the pass is spread across the atlas's own loading, from the stitch
     * event to the event after it, and is shut from the second of those, or from the next stitch's first should a
     * stitch die between them; so that a pool nothing could reach any more still holds no thread, its workers leave
     * once they have waited this long for work. A wait that long falls between two batches of one stitch only if the
     * atlas spends half a minute loading other mods' textures between them, and then the next batch simply starts its
     * threads again.
     */
    static ExecutorService newPool(int threads) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
            threads,
            threads,
            IDLE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new WorkerThreads());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /**
     * One sprite's picture as composing left it: its pixels, or nothing, and what stopped it where something threw.
     *
     * <p>
     * The throwable comes back to the thread loading the atlas rather than being said where it happened, so the
     * pass's line about a picture that could not be composed is written where it always was, by that thread, at that
     * sprite's own load. Workers say nothing themselves: a hundred and thirty thousand stack traces is its own outage.
     */
    static final class Picture {

        final int[] pixels;

        final Throwable failure;

        private Picture(int[] pixels, Throwable failure) {
            this.pixels = pixels;
            this.failure = failure;
        }
    }

    /**
     * Composes a batch on the pool and collects it here, one picture per sprite, in the batch's order.
     *
     * @throws RuntimeException when the pool will not take the work, or this thread is interrupted while it waits
     */
    static Picture[] onPool(ExecutorService pool, List<WearSprite> sprites, List<WearSprite.Source> sources) {
        List<Callable<Picture>> jobs = new ArrayList<Callable<Picture>>(sprites.size());
        for (int i = 0; i < sprites.size(); i++) {
            jobs.add(new ComposeOne(sprites.get(i), sources.get(i)));
        }
        try {
            List<Future<Picture>> futures = pool.invokeAll(jobs);
            Picture[] out = new Picture[futures.size()];
            for (int i = 0; i < out.length; i++) {
                // Collected one by one rather than trusting invokeAll's return, because it is
                // Future.get that carries the documented guarantee that everything a worker wrote is
                // visible here - the picture, and the shell a sprite with a moving layer keeps for its
                // install. The two happen to agree in this implementation, and an invariant this design
                // rests on should not be resting on that.
                try {
                    out[i] = futures.get(i)
                        .get();
                } catch (ExecutionException impossible) {
                    out[i] = new Picture(null, impossible.getCause());
                }
            }
            return out;
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
            throw new IllegalStateException("interrupted while composing wear textures", interrupted);
        }
    }

    /** Composes a batch on this thread, which is all the pass did before it threaded. */
    static Picture[] here(List<WearSprite> sprites, List<WearSprite.Source> sources) {
        Picture[] out = new Picture[sprites.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = safely(sprites.get(i), sources.get(i));
        }
        return out;
    }

    /**
     * One sprite's picture, and never a thrown exception.
     *
     * <p>
     * A worker that threw would surface as an {@code ExecutionException} in the middle of a stitch
     * and take the whole atlas with it, which is exactly the outcome the catch round every harvest in
     * the sprite pass was written to prevent. One block that cannot be worn is worth one sprite, so
     * the failure comes back with the picture and is dealt with where the logging happens. Every
     * throwable, as the other edition catches it, and on this thread as well as on a worker, so that a
     * picture costs the same whichever thread happened to compose it.
     */
    private static Picture safely(WearSprite sprite, WearSprite.Source source) {
        try {
            return new Picture(sprite.compose(source), null);
        } catch (Throwable awkwardSprite) {
            return new Picture(null, awkwardSprite);
        }
    }

    private static final class ComposeOne implements Callable<Picture> {

        private final WearSprite sprite;

        private final WearSprite.Source source;

        ComposeOne(WearSprite sprite, WearSprite.Source source) {
            this.sprite = sprite;
            this.source = source;
        }

        @Override
        public Picture call() {
            return safely(sprite, source);
        }
    }

    /** Daemon workers, named so a profile or a crash report says whose threads these are. */
    private static final class WorkerThreads implements ThreadFactory {

        private int next;

        @Override
        public synchronized Thread newThread(Runnable job) {
            Thread thread = new Thread(job, "TRMT wear texture " + next++);
            // Daemon because a stitch that fails half way must not leave a thread holding the game
            // open, and a notch below normal because the thread loading the atlas is between batches
            // and the loading screen has to go on moving.
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

    /**
     * Composes the pictures of one stitch ahead of the loads that ask for them, a batch at a time, in the order the
     * atlas will ask.
     *
     * <p>
     * The other edition composes in one place: after every sprite has loaded and before any is stitched, it reads
     * every surface, composes everything on the pool a batch at a time and installs it. This edition has no such
     * window and does not need one - the atlas loads a wear sprite's faces before the sprite itself - so each sprite
     * asks for its picture from inside its own load, one at a time, on the thread loading the atlas, and the atlas
     * mipmaps whatever the load installed the moment it returns. A picture therefore has to be finished by the time
     * its sprite asks, and the only way to compose many at once is to compose them before they are asked for.
     *
     * <p>
     * <b>What may be composed ahead is only a sprite whose surface has already been read.</b> Reading stays exactly
     * where it was, in the first load of each surface, because the moment of that read is part of the picture. The
     * atlas has loaded a sprite's faces only by the time that sprite loads; read any earlier, a face the block draws
     * with may not be loaded yet, comes back with no pixels, is read from its file instead - a different picture -
     * and that answer is kept for the rest of the pass. And {@link InnerLayers} grants its animation budget in the
     * order surfaces are read. So a surface is read in the load that would have read it anyway, and every other
     * picture of that surface can then be composed from what that read made, whenever and wherever is convenient,
     * because composing reads nothing else and writes nothing it was handed.
     *
     * <p>
     * <b>Which pictures are composed ahead is a guess at the order the atlas loads in, and only how fast this goes
     * rides on it.</b> When a sprite asks and its picture is not waiting, it is composed with every sprite of the
     * next {@code batch} places in the order that is not yet composed, whose surface has been read, as far as there
     * is room. With the order right, each of those is then waiting when it asks, and a stitch composes in a batch per
     * surface first read and then a batch for every {@code batch} sprites after. With it wrong, pictures wait longer
     * and the room runs out, and the pass falls back to composing in each load, which is what it did before it
     * threaded. Either way every sprite is handed exactly the picture its own load would have composed: the batch a
     * picture is composed in, the thread it is composed on and how long it waits change nothing in it.
     *
     * <p>
     * <b>At most {@code batch} pictures wait at once</b>, counting the one being asked for, which is the bound
     * {@code WearTextures.COMPOSE_BATCH} describes. A waiting picture is the array its sprite will install and keep
     * until the end of the stitch, not a copy of it, so composing ahead moves when a picture is held rather than adding
     * a second one.
     *
     * <p>
     * Everything here runs on the thread loading the atlas but the composing itself. The order, the waiting pictures,
     * the record of what has been composed, and the look-up of a surface's source - which is the pass's own maps - are
     * that thread's alone, and what a worker is handed is a sprite to call {@code compose} on and its source.
     */
    static final class Ahead {

        /** The sprites in the order the atlas is expected to load them. */
        private final List<WearSprite> order;

        /** Each sprite's place in that order, the first where one is listed twice. */
        private final Map<WearSprite, Integer> places;

        /** By place: asked for by its own load, or composed ahead and waiting to be. */
        private final boolean[] handled;

        /** Pictures composed ahead, waiting for the loads that will install them. */
        private final Map<WearSprite, Picture> waiting = new IdentityHashMap<WearSprite, Picture>();

        private final int batch;

        private final Supplier<ExecutorService> pools;

        /** Made at the first batch of more than one picture, and shut by {@link #close}. */
        private ExecutorService pool;

        /** Set when the pool would not take a batch; every later batch is then composed on this thread. */
        private boolean refused;

        private boolean closed;

        /** How many threads the pool was made with, or nought where none was made. */
        private int threads;

        /** Pictures composed ahead of the load that asked for them. */
        private int composedAhead;

        /** Pictures composed in the load that asked for them, with or without others beside them. */
        private int composedInLoad;

        /** Batches that went to the pool. */
        private int batches;

        /** The most pictures that were ever waiting at once. */
        private int mostWaiting;

        /**
         * @param order the sprites in the order the atlas is expected to load them, which only decides how many are
         *              ready when they ask
         * @param batch the most pictures composed at once, the one asked for included, and so the most that wait
         * @param pools makes the pool, at the first batch that wants one
         */
        Ahead(List<WearSprite> order, int batch, Supplier<ExecutorService> pools) {
            this.order = new ArrayList<WearSprite>(order);
            this.places = new IdentityHashMap<WearSprite, Integer>(this.order.size() * 2);
            for (int i = 0; i < this.order.size(); i++) {
                WearSprite sprite = this.order.get(i);
                if (!places.containsKey(sprite)) places.put(sprite, Integer.valueOf(i));
            }
            this.handled = new boolean[this.order.size()];
            this.batch = Math.max(1, batch);
            this.pools = pools;
        }

        /**
         * The picture for a sprite that is loading, composed now if it is not already waiting, and with it as many of
         * the sprites expected to load next as are ready and fit.
         *
         * @param source    the sprite's own source, read in this load or an earlier one of its surface
         * @param harvested a surface's source where it has already been read this pass, or null; never reads one
         */
        Picture take(WearSprite sprite, WearSprite.Source source, Function<WearSprite, WearSprite.Source> harvested) {
            Integer at = places.get(sprite);
            if (at != null) handled[at.intValue()] = true;
            Picture ready = waiting.remove(sprite);
            if (ready != null) return ready;

            List<WearSprite> sprites = new ArrayList<WearSprite>();
            List<WearSprite.Source> sources = new ArrayList<WearSprite.Source>();
            sprites.add(sprite);
            sources.add(source);
            if (at != null && !closed) gather(at.intValue(), harvested, sprites, sources);

            Picture[] made = compose(sprites, sources);
            for (int i = 1; i < made.length; i++) {
                waiting.put(sprites.get(i), made[i]);
            }
            composedInLoad++;
            composedAhead += made.length - 1;
            mostWaiting = Math.max(mostWaiting, waiting.size() + 1);
            return made[0];
        }

        /**
         * Adds to the batch every sprite of the next places in the order that has not been composed or asked for, will
         * ask, and whose surface has been read, nearest first, while there is room.
         */
        private void gather(int at, Function<WearSprite, WearSprite.Source> harvested, List<WearSprite> sprites,
            List<WearSprite.Source> sources) {
            int room = batch - waiting.size();
            int end = (int) Math.min(order.size(), (long) at + batch);
            for (int i = at + 1; i < end && sprites.size() < room; i++) {
                if (handled[i]) continue;
                WearSprite next = order.get(i);
                // A sprite with no edge never asks: its load sizes nothing and composes nothing.
                if (next.plannedEdge() <= 0) continue;
                WearSprite.Source theirs = harvested.apply(next);
                if (theirs == null || theirs.isUnreadable()) continue;
                handled[i] = true;
                sprites.add(next);
                sources.add(theirs);
            }
        }

        private Picture[] compose(List<WearSprite> sprites, List<WearSprite.Source> sources) {
            if (sprites.size() > 1 && !refused) {
                try {
                    if (pool == null) {
                        pool = pools.get();
                        threads = pool instanceof ThreadPoolExecutor ? ((ThreadPoolExecutor) pool).getMaximumPoolSize()
                            : -1;
                    }
                    Picture[] made = onPool(pool, sprites, sources);
                    batches++;
                    return made;
                } catch (Throwable poolRefused) {
                    // A pool that will not take work, one that could not start its threads, or one interrupted
                    // out from under this. Composing here instead is slower and is exactly what this edition did
                    // before it threaded, so the worst case is a longer loading screen rather than an atlas full of
                    // holes. Said once, and the rest of the stitch composed here, so a broken pool is not asked
                    // again for every batch.
                    refused = true;
                    Trmt.LOG.warn(
                        "Composing the rest of this stitch's wear textures on the thread loading the atlas; the worker pool refused",
                        poolRefused);
                }
            }
            return here(sprites, sources);
        }

        /**
         * Shuts the pool and lets go of any picture still waiting, whose sprite then never asked. Safe to call more
         * than once.
         *
         * @return how many pictures were still waiting
         */
        int close() {
            closed = true;
            int left = waiting.size();
            for (WearSprite sprite : waiting.keySet()) {
                // Composing may have kept a shell on the sprite for an install that will now never come.
                sprite.forgetComposed();
            }
            waiting.clear();
            ExecutorService shutting = pool;
            pool = null;
            if (shutting != null) shutDown(shutting);
            return left;
        }

        /** Pictures waiting for their loads now. */
        int waiting() {
            return waiting.size();
        }

        /** The most pictures composed and not yet installed at any one time, the one being asked for included. */
        int mostWaiting() {
            return mostWaiting;
        }

        int composedAhead() {
            return composedAhead;
        }

        int composedInLoad() {
            return composedInLoad;
        }

        int batches() {
            return batches;
        }

        int threads() {
            return threads;
        }
    }
}
