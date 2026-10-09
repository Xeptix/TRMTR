package com.trmtgtnh.client.texture;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import com.trmtgtnh.Trmt;

/**
 * Offers {@link GeneratedPack} to the game's pack repository, always on and not in the screen.
 *
 * <p>
 * A {@code PackRepository} takes its sources when it is built and has no way to be given another
 * afterwards at this version, so one small mixin puts this in as the repository is constructed. That
 * mixin is the only thing in the whole wear texture pipeline that reaches into somebody else's class,
 * and what it assumes is checked by {@code PackRepositoryStillLooksLikeThisTest} rather than
 * discovered when the game will not start.
 *
 * <p>
 * <strong>It asks for its sheets when it is read, not when it is made, and that is what keeps it out
 * of trouble.</strong> The repository is built while the client is still starting - possibly before
 * anything has planned a single wear sprite - so a source that captured the planner at construction
 * would capture nothing and stay empty for the session. Reading it at {@code loadPacks} time means
 * the answer comes from whenever the game actually asks, which is every reload, and the first reload
 * is always after start-up.
 *
 * <p>
 * It also means a dedicated server needs no special case anywhere: nothing there ever calls
 * {@link #use}, so this contributes nothing and names no client class to find that out. The same
 * mixin can apply on both sides and simply have nothing to do on one.
 *
 * <p>
 * <strong>Required and fixed at the bottom, deliberately.</strong> Required because it is not a
 * resource pack in any sense the player cares about - it is where this mod's own textures come from,
 * and switching it off would leave worn ground drawing the missing-texture chequer rather than
 * drawing less wear. Bottom because a real resource pack should still be able to override any of it,
 * which is what a pack author would expect and costs nothing to allow.
 */
public final class WearPackSource implements RepositorySource {

    /** The id the repository files this under. Never shown, because the pack is never in the list. */
    public static final String ID = Trmt.MODID + ":wear_textures";

    private static volatile GeneratedPack.Sheets sheets;

    /** Tells this where the planned wear sprites are. Called by the client as it starts. */
    public static void use(GeneratedPack.Sheets planned) {
        sheets = planned;
    }

    /** Forgets them again. For tests, which must not leak a plan into the next one. */
    public static void forget() {
        sheets = null;
    }

    /** Whether anything has planned wear sprites yet. False on a dedicated server, always. */
    public static boolean wired() {
        return sheets != null;
    }

    /**
     * The given sources with this one added, as a set the caller can keep.
     *
     * <p>
     * Here rather than in the mixin that calls it, and that is not tidiness. Mixin reads the
     * bytecode of everything a mixin class touches through an ASM older than the JDK this workspace
     * runs on, so a {@code new HashSet} written inside the mixin sent it to read
     * {@code java.util.HashSet} from a Java 21 runtime and it died on the class file version - which
     * looks like a crash in Minecraft's bootstrap and is nothing of the sort. An ordinary class is
     * read by an ordinary classloader.
     *
     * <p>
     * A copy rather than an addition, because the set the repository was given may be immutable and
     * there is no way to ask which.
     */
    public static Set<RepositorySource> alongside(Set<RepositorySource> given) {
        Set<RepositorySource> widened = new HashSet<RepositorySource>();
        if (given != null) widened.addAll(given);
        widened.add(new WearPackSource());
        return widened;
    }

    /**
     * Equal to every other one of these, so a set keeps exactly one.
     *
     * <p>
     * The mixin that adds this runs once per constructor and {@code PackRepository} has two, one
     * calling the other - so building a repository the short way runs it twice. Rather than make the
     * mixin pick a constructor, which turned out to be the thing that broke remapping, the source
     * simply refuses to be in a set twice.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof WearPackSource;
    }

    @Override
    public int hashCode() {
        return WearPackSource.class.hashCode();
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer, Pack.PackConstructor constructor) {
        GeneratedPack.Sheets planned = sheets;
        // Not an error: this is every dedicated server, and every client repository built before the
        // first reload. Both are ordinary, and both are asked again later.
        if (planned == null) return;

        Pack pack = Pack
            .create(ID, true, () -> new GeneratedPack(planned), constructor, Pack.Position.BOTTOM, PackSource.BUILT_IN);

        if (pack == null) {
            // Loud, because the shape of this failure is the worst kind: the game carries on, every
            // other texture loads, and ground simply never shows wear. Pack.create hands back null
            // when a pack will not answer for its own pack.mcmeta, which GeneratedPack is careful to
            // do - so if this ever fires, that is where to look.
            Trmt.error(
                "The wear texture pack would not build, so no worn ground will be drawn. This "
                    + "usually means GeneratedPack stopped answering for its own pack metadata.");
            return;
        }
        consumer.accept(pack);
    }
}
