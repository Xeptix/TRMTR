package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationFrame;
import net.minecraft.client.resources.data.AnimationMetadataSection;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.surface.SurfaceRegistry;

/**
 * Which texture belongs behind a block whose own is cut away to show a second layer.
 *
 * <p>
 * Data rather than a dependency, and that is the whole design. A block is named by a string a player
 * can read in the config file, resolved through the registry, and the texture behind it is named the
 * way any resource pack names one. No class of the mod that owns the block is ever mentioned, loaded
 * or reflected on - which is what lets this serve Chisel today and whatever somebody asks for
 * tomorrow with one line in a config file and no release of this mod.
 *
 * <p>
 * Render thread only, end to end. {@link #prime} is called beside the other tables the sprite pass fills before it
 * hands any work out, {@link #openAsk} and {@link #closeAsk} round each surface's harvest, {@link #publishWindows} as
 * that pass ends and {@link #reportAnimation} after it; {@link #textureFor} from the pass's count of pictures and from
 * {@code WearSprite.harvest}, and {@link #animationOf} and {@link #mayMove} only from that harvest, which runs only
 * inside the pass and is the half of it allowed to ask a block or a pack anything; {@link #noteAnimated} from
 * {@code WearSprite.adoptLayer}, as a picture takes its layer; {@link #isWindow} only from the painter; and
 * {@link #newTick} and {@link #mayUpload} only from the client tick and the atlas's own animation tick. Nothing here is
 * ever read from a worker, so nothing here needs to be synchronised.
 */
public final class InnerLayers {

    private static Map<String, String> table = Collections.emptyMap();

    /** Names asked for that no texture could be found under, said once rather than once a sprite. */
    private static final Set<String> unresolved = new LinkedHashSet<String>();

    private InnerLayers() {}

    /** Reads the config list into a lookup. Render thread, once, before any sprite is harvested. */
    public static void prime() {
        unresolved.clear();
        // A fresh set to note into, rather than the painter's emptied in place: what is drawn goes on
        // reading the last stitch's set until this one publishes its own.
        windowsBuilding = StateFiling.<Block, Boolean>builder();
        // The budget and the switch read once, as the pass begins, so every surface of one stitch is priced against the
        // same figure and counted under the same answer.
        ledger = new MovingLayerLedger(MovingLayerLedger.budgetBytes(TrmtConfig.innerLayerAnimationBudgetMb));
        animating = TrmtConfig.animateInnerLayers;
        unpriced = 0;
        closeAsk();
        uploads.forget();
        String[] lines = TrmtConfig.innerLayerTextures;
        if (lines == null || lines.length == 0) {
            table = Collections.emptyMap();
            return;
        }
        Map<String, String> built = new HashMap<String, String>();
        for (String line : lines) {
            if (line == null) continue;
            int split = line.indexOf('=');
            if (split <= 0) continue;
            String block = line.substring(0, split)
                .trim();
            String texture = line.substring(split + 1)
                .trim();
            if (block.isEmpty() || texture.isEmpty()) continue;
            built.put(block.toLowerCase(Locale.ROOT), texture);
        }
        table = built;
    }

    /**
     * The texture behind this block, or null when it has none.
     *
     * <p>
     * An entry naming a metadata wins over one that does not, so a pack can give one variation its
     * own layer and leave the rest alone. Both spellings are looked up exactly rather than parsed,
     * which sidesteps the trap {@link com.trmtgtnh.util.BlockEntry} exists for: a block genuinely
     * called {@code mod:thing:5} is found under its own name, a metadata five of {@code mod:thing}
     * is found under that name with the number appended, and neither can be mistaken for the other.
     */
    public static String textureFor(Block origin, int meta) {
        if (table.isEmpty() || origin == null) return null;
        String name = SurfaceRegistry.registryName(origin);
        if (name == null) return null;
        String key = name.toLowerCase(Locale.ROOT);
        String exact = table.get(key + ":" + meta);
        return exact != null ? exact : table.get(key);
    }

    // ------------------------------------------------------------------
    // Layers you can see through
    // ------------------------------------------------------------------

    /**
     * Covered block states with something see-through behind them, by the covered block object and its
     * metadata, which Forge never renumbers.
     *
     * <p>
     * Filled while the sprites are being built, from the pixels rather than from the config, because
     * whether a layer is see-through is a property of the texture a pack supplied and not of the
     * name somebody typed. Lava has no transparency in it at all, so a lavastone never enters this
     * set and nothing about it changes; a pack that shipped clear lava would put it in, and a pack
     * that shipped solid water would leave waterstone out. Read by the painter, which turns the origin
     * it remembers for a position back into a block under the ids in force before it asks.
     *
     * <p>
     * By the object and no longer by the block's id packed with its metadata. A stitch filed under the ids
     * of whatever world or server was open when it ran, and Forge takes the ids of a mod's blocks from each
     * world's save and from each server joined, so a world whose numbers came out in another order from the
     * stitch's drew the see-through ghost over the wrong block, and the right one solid, and a stitch
     * made inside such a world carried that order into the next.
     *
     * <p>
     * Deliberately independent of whether the setting is on. What a block has behind it is a fact,
     * and holding it either way is what lets the setting be turned on and off without a rebuild of
     * anything but the sprites.
     *
     * <p>
     * Replaced whole at the end of a stitch rather than filled in place, so the painter reads the last
     * stitch's set or this one's and never one half built. Volatile for that one publication, not for any
     * worker, none of which reads it.
     */
    private static volatile StateFiling<Block, Boolean> windows = StateFiling.<Block, Boolean>empty();

    /**
     * The set the current stitch notes into, taken fresh by {@link #prime} and copied out by
     * {@link #publishWindows}. Render thread.
     */
    private static StateFiling.Builder<Block, Boolean> windowsBuilding = StateFiling.<Block, Boolean>builder();

    /** Notes that this covered state has something see-through behind it. Render thread. */
    static void noteWindow(Block origin, int meta) {
        if (origin != null) windowsBuilding.file(origin, meta, Boolean.TRUE);
    }

    /**
     * Makes what this stitch noted the set the painter reads. Render thread, as the sprite pass ends.
     *
     * <p>
     * A filing built whole rather than the builder handed over, so the painter reads one finished set and nothing noted
     * afterwards can reach it.
     */
    static void publishWindows() {
        windows = windowsBuilding.build();
    }

    /**
     * Whether this covered state has anything see-through behind it. A null block, which is a position with
     * nothing recorded under it, has not.
     */
    public static boolean isWindow(Block origin, int meta) {
        return origin != null && windows.has(origin, meta);
    }

    /**
     * Says whether anything will actually be drawn through. Render thread, at the end of a stitch.
     *
     * <p>
     * Two of these are warnings rather than notes, and both name a setting somewhere else that makes
     * this one do nothing at all. That is the shape of failure this mod has shipped twice: a feature
     * switched on, quietly impossible, and no line anywhere saying so.
     */
    public static void reportWindows() {
        if (!TrmtConfig.seeThroughInnerLayers) return;
        if (!TrmtConfig.perSurfaceTextures) {
            Trmt.LOG.warn(
                "seeThroughInnerLayers is on but perSurfaceTextures is off, so no worn block is drawn from its own pixels and there is no layer behind any of them. Nothing will be drawn through.");
            return;
        }
        StateFiling<Block, Boolean> published = windows;
        if (published.isEmpty()) {
            Trmt.LOG.warn(
                "seeThroughInnerLayers is on but nothing named in innerLayerTextures has a layer with any transparency in it, so nothing will be drawn through. Lava has none at all, so a block with only lava behind it is expected here.");
            return;
        }
        Trmt.LOG.info("{} covered block states will be drawn through", Integer.valueOf(published.states()));
    }

    // ------------------------------------------------------------------
    // Layers that move
    // ------------------------------------------------------------------

    /**
     * What this stitch has granted moving layers, what they turned out to hold and what was kept still, against the
     * budget as it stood when the pass began. Replaced whole by {@link #prime}. It used to be one figure, the bytes of
     * each surface's frames, while the budget promised the shells as well; at the settings shipped that was one byte in
     * thirty of what Chisel's fifteen faces hold.
     */
    private static MovingLayerLedger ledger = new MovingLayerLedger(0L);

    /** Whether layers may move this stitch, as {@code client.animateInnerLayers} stood when the pass began. */
    private static boolean animating;

    /**
     * The pictures of the surface being harvested while the pass holds an ask open, or null where the count found
     * none.
     */
    private static MovingLayerLedger.Pictures asking;

    /** Whether the pass holds an ask open, which a null count alone cannot say. */
    private static boolean askOpen;

    /** The atlas's mip levels this stitch, which decide how much of each still picture it keeps. */
    private static int askingMipLevels;

    /**
     * Asks for a moving layer with no count of pictures behind them. Nought unless the count and the build have come
     * apart.
     */
    private static int unpriced;

    /** This tick's uploads, and whether this stitch has yet said that their ceiling turned pictures away. */
    private static final MovingLayerLedger.UploadTally uploads = new MovingLayerLedger.UploadTally();

    /**
     * The animation the pack gives this texture, normalised, or null when it does not move.
     *
     * <p>
     * Normalised because it has to be. Both the game's own advance and the modern chunk builder's
     * work out how many frames there are by asking the metadata first and falling back on how many
     * frames the sprite holds - and a sprite here holds exactly one, being a single composed
     * picture. Vanilla's water declares only a frame time and no list, so left as it comes it would
     * report no frames, the fallback would say one, and the sprite would sit on the first frame for
     * ever. Writing the list out makes both callers count the pack's frames instead.
     */
    public static AnimationMetadataSection animationOf(IResourceManager resources, String iconName) {
        if (!TrmtConfig.animateInnerLayers || iconName == null || resources == null) return null;
        try {
            // The file the block atlas loads for this name, its domain lower-cased as the atlas lower-cases
            // it: split here by hand, a domain registered in capitals named a file no resource pack holds,
            // and the layer never moved.
            IResource resource = resources.getResource(WearPatterns.blockTextureFile(iconName));
            AnimationMetadataSection declared = (AnimationMetadataSection) resource.getMetadata("animation");
            if (declared == null) return null;
            if (declared.getFrameCount() > 0) return declared;

            BufferedImage image = WearPatterns.readIcon(resources, iconName);
            int count = WearPatterns.frameCount(image, declared.getFrameHeight());
            if (count <= 1) return null;
            List<AnimationFrame> frames = new ArrayList<AnimationFrame>(count);
            for (int i = 0; i < count; i++) frames.add(new AnimationFrame(i));
            // The plain frame time, not the one asked per frame. Asking per frame reaches into the
            // list of frames to see whether that frame overrides it - and the whole reason this
            // branch is running is that the list is empty, so it throws. Water is the case: its
            // metadata declares a frame time and nothing else, where lava's writes all
            // thirty-eight of its frames out by hand, which is why lava moved and water did not.
            return new AnimationMetadataSection(
                frames,
                declared.getFrameWidth(),
                declared.getFrameHeight(),
                declared.getFrameTime());
        } catch (IOException missing) {
            return null;
        } catch (RuntimeException broken) {
            // Said aloud rather than at debug, because this is a feature quietly not happening and
            // the last time it did the only trace was a line nobody had a reason to look for.
            Trmt.LOG.warn("Could not read the animation of {}; its layer will not move", iconName, broken);
            return null;
        }
    }

    /** Whether any layer can move this stitch, so the pass need not count pictures when nothing can ask. */
    static boolean layersMayMove() {
        return animating && !table.isEmpty();
    }

    /**
     * Holds this surface's count of pictures open for the harvest that follows. Render thread, from the sprite pass.
     *
     * <p>
     * The pass's count rather than anything the harvest knows, because a harvest is one sprite and a price is every
     * picture that sprite's surface will have. Null where the count found no layer behind the surface.
     */
    static void openAsk(MovingLayerLedger.Pictures pictures, int mipLevels) {
        asking = pictures;
        askingMipLevels = mipLevels;
        askOpen = true;
    }

    /**
     * Closes it again, whatever became of the harvest, so a sprite harvested later is never priced as another
     * surface.
     */
    static void closeAsk() {
        asking = null;
        askOpen = false;
    }

    /**
     * Whether this surface's layer may move, priced whole against what has been granted already.
     *
     * <p>
     * It prices everything moving the layer makes the game keep: a shell for every picture of the surface, the still
     * picture and its smaller copies that the atlas keeps for anything that moves and lets go of for everything else,
     * one copy of the frames where any picture shares them, and a copy at a picture's own size wherever a picture was
     * stitched at another size from the frames, which the sprite pass no longer makes. Until 0.9.211 it charged the
     * frames alone, which at the settings shipped was one byte in thirty of what Chisel's fifteen faces hold, and until
     * 0.9.212 it was named for them. The arithmetic is {@link MovingLayerLedger}'s.
     *
     * <p>
     * Whole or not at all, in the order surfaces are built, and a refusal does not stop a cheaper surface after it.
     *
     * <p>
     * Only inside an ask the sprite pass holds open round a surface's harvest, because only the pass knows how many
     * pictures will share the layer and at what size. An ask with none open, or with no count behind it, is declined
     * and counted, because a layer that quietly never moves is the failure this mod has shipped twice. No harvest runs
     * outside the pass any more, so either is a fault in this mod.
     */
    public static boolean mayMove(String iconName, int frameCount, int frameEdge) {
        if (!TrmtConfig.animateInnerLayers || !animating) return false;
        if (!askOpen || asking == null) {
            unpriced++;
            return false;
        }
        MovingLayerLedger.Price price = MovingLayerLedger.price(asking, frameCount, frameEdge, askingMipLevels);
        return ledger.ask(iconName, price) == MovingLayerLedger.Answer.GRANTED;
    }

    /**
     * Counts what one picture keeps as it takes its layer, measured from the arrays rather than priced again, so the
     * end
     * of the stitch can hold the two against each other. Render thread, from {@code WearSprite.adoptLayer}.
     */
    static void noteAnimated(int[] shell, int[][] keptPicture, int[][] frames, boolean ownCopy) {
        ledger.took(shell, keptPicture, frames, ownCopy);
    }

    /**
     * A new client tick and a fresh allowance of uploads; and, the first time since the last stitch that a tick turned
     * pictures away, a line saying so with the figures that tick measured.
     */
    public static void newTick() {
        if (!uploads.newTick(TrmtConfig.innerLayerUploadsPerTick)) return;
        Trmt.LOG.info(
            LINE_CEILING,
            new Object[] { Integer.valueOf(uploads.lastAsked()), Integer.valueOf(uploads.lastAllowed()),
                Integer.valueOf(uploads.lastTurnedAway()) });
    }

    /**
     * Whether another layer may be redrawn this tick.
     *
     * <p>
     * Every picture of a lavastone wants its lava laid again on the same tick, because they all follow one clock, so
     * on a plain client the atlas asks every moving picture on it each tick, whether or not it is in view, and without
     * a
     * ceiling Chisel's faces alone would send several thousand small uploads to the card in one tick and drop a frame
     * doing it. What goes over is not redrawn late. The clock it follows has already moved
     * on, so the picture keeps the frame it shows until its layer next moves and asks again, and since the atlas asks
     * its
     * pictures in the same order every tick, past the ceiling it is the same pictures that stand still. The first tick
     * of a stitch that turns any away is said.
     */
    public static boolean mayUpload() {
        return uploads.take();
    }

    /**
     * Says what moves, what it holds against what it was granted, and what was kept still and why. Render thread, as
     * the
     * sprite pass ends and just before the atlas goes to the stitcher.
     *
     * <p>
     * Every branch that moves nothing says so, because the budget can mean never without a word, and a feature switched
     * on and quietly doing nothing is the failure this mod has shipped twice. What the pictures hold is measured before
     * the atlas decides to keep their still pictures, which it does for every sprite whose animation {@code adoptLayer}
     * has already declared. Once said, the frames counted are let go of and any ask left open is closed.
     */
    public static void reportAnimation() {
        MovingLayerLedger book = ledger;
        long budgetMb = book.budget() / MovingLayerLedger.MEBIBYTE;
        if (unpriced > 0) {
            Trmt.LOG.warn(LINE_UNPRICED, Integer.valueOf(unpriced), unpriced == 1 ? "ask" : "asks");
        }
        if (book.declinedAtNought() > 0) {
            Trmt.LOG.info(LINE_NOUGHT, Integer.valueOf(book.declinedAtNought()), surfaces(book.declinedAtNought()));
        }
        if (book.surfacesGranted() > 0 || book.picturesHolding() > 0) {
            Trmt.LOG.info(
                LINE_MOVING,
                new Object[] { Integer.valueOf(book.picturesHolding()), Integer.valueOf(book.surfacesGranted()),
                    surfaces(book.surfacesGranted()), Long.valueOf(MovingLayerLedger.kibibytes(book.held())),
                    Long.valueOf(budgetMb), Long.valueOf(MovingLayerLedger.kibibytes(book.shellsHeld())),
                    Long.valueOf(MovingLayerLedger.kibibytes(book.keptHeld())),
                    Long.valueOf(MovingLayerLedger.kibibytes(book.framesHeld())),
                    Long.valueOf(MovingLayerLedger.kibibytes(book.copiesHeld())) });
        }
        if (book.overdrawn()) {
            Trmt.LOG.warn(LINE_OVERDRAWN, Long.valueOf(MovingLayerLedger.kibibytes(book.held() - book.granted())));
        } else if (book.givenBack() > 0L) {
            Trmt.LOG.info(
                LINE_GIVEN_BACK,
                Long.valueOf(MovingLayerLedger.kibibytes(book.givenBack())),
                Integer.valueOf(book.picturesNotTaken()));
        }
        if (book.surfacesRefused() > 0) {
            Long cheapest = Long.valueOf(MovingLayerLedger.kibibytes(book.cheapestRefused()));
            Long everything = Long.valueOf(book.everythingAskedMebibytes());
            if (book.surfacesGranted() == 0) {
                Trmt.LOG.warn(
                    LINE_NONE_FIT,
                    new Object[] { Long.valueOf(budgetMb), book.describeRefusals(), cheapest, everything });
            } else {
                Trmt.LOG.info(
                    LINE_REFUSED,
                    new Object[] { Integer.valueOf(book.surfacesRefused()), surfaces(book.surfacesRefused()),
                        Long.valueOf(budgetMb), book.describeRefusals(), cheapest, everything });
            }
        }
        book.closeBook();
        closeAsk();
    }

    private static String surfaces(int count) {
        return count == 1 ? "surface" : "surfaces";
    }

    // Word for word what the stitch and the tick write about moving layers, kept in one place so a line found in
    // latest.log leads straight back to the branch that wrote it.
    private static final String LINE_MOVING = "{} worn pictures of {} {} take a moving layer, holding {} KiB against the {} MiB client.innerLayerAnimationBudgetMb allows: {} KiB of worn shells, {} KiB of the still pictures and smaller copies the atlas keeps for anything that moves, {} KiB of the layers' own frames and {} KiB of frames copied for pictures drawn at another size from them";
    private static final String LINE_OVERDRAWN = "Moving layers hold {} KiB more than client.innerLayerAnimationBudgetMb granted them, so the budget is not the ceiling it says it is: what MovingLayerLedger prices a picture at and what WearSprite keeps for it have come apart";
    private static final String LINE_GIVEN_BACK = "{} KiB granted to moving layers is not held, because {} of the pictures it was priced for never took their layer";
    private static final String LINE_UNPRICED = "{} {} for a moving layer came with no count of the pictures that would share it, and each was answered with a still layer rather than priced at a guess. The sprite pass holds a count open round every harvest it makes, so this is a fault in this mod and not in any setting: a harvest ran outside that count, or the count and the pass filed a surface under different keys";
    private static final String LINE_NOUGHT = "client.innerLayerAnimationBudgetMb is nought, so none of the {} {} whose layer moves was given movement, the same picture as turning client.animateInnerLayers off";
    private static final String LINE_NONE_FIT = "No layer moves: client.innerLayerAnimationBudgetMb is {} MiB and no surface asking for a moving layer fitted in it, so each keeps its layer's first frame: {}. The cheapest needed {} KiB, and moving every one would need a budget of {} MiB";
    private static final String LINE_REFUSED = "Kept a still layer on {} {} for want of room under client.innerLayerAnimationBudgetMb of {} MiB, each surface priced whole: {}. The cheapest needed {} KiB, and moving every surface asked for would need a budget of {} MiB";
    private static final String LINE_CEILING = "On one tick {} worn pictures asked to have their moving layer redrawn and client.innerLayerUploadsPerTick allows {}, so {} were turned away. A picture turned away keeps the frame it shows until its layer next moves and asks again, and the atlas asks in the same order every tick, so while this lasts it is the same pictures that stand still";

    /** Notes that a named texture could not be read, so the stitch can say so once at the end. */
    static void couldNotRead(String texture) {
        unresolved.add(texture);
    }

    /** Says what was asked for and not found. Render thread, at the end of a stitch. */
    public static void report() {
        if (unresolved.isEmpty()) return;
        Trmt.LOG.warn(
            "innerLayerTextures names {} texture(s) that could not be read, so those blocks keep their holes: {}",
            Integer.valueOf(unresolved.size()),
            com.trmtgtnh.util.LogSample.of(unresolved));
        unresolved.clear();
    }
}
