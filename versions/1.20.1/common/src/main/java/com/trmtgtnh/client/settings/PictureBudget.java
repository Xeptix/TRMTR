package com.trmtgtnh.client.settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * How many pictures may be tried for the first time in one frame, and which are never tried again (spec Part 6).
 *
 * <p>
 * A picture is drawn by somebody else's code - a mod's block renderer, its entity model, its item - and the first time
 * is the dear one: an entity is made, a model baked, a texture looked up. A long list opened at once would make every
 * one of them in the same frame, so a frame takes at most {@code newPerFrame} first tries (2 mobs on 1.7.10 and 1.12.2,
 * 4 from 1.16.5, 24 blocks or items everywhere) and the rest draw their empty frame until a later one. Once a picture
 * has drawn without throwing it is {@link State#FINE} and drawn every frame, costing nothing here. Once it throws it is
 * {@link State#FAILED}, falls back, and is never asked again: a renderer that threw once will throw again, and a list
 * scrolled over it would otherwise throw every frame.
 *
 * <p>
 * Kept by name for the game session, not per screen, so the edition holds one budget per kind of picture and reopening
 * the screen costs nothing.
 */
public final class PictureBudget {

    /** Where a picture stands. */
    public enum State {
        /** Not drawn yet: draws its empty frame until a frame has room to try it. */
        UNTRIED,
        /** Drawn once without throwing: drawn every frame. */
        FINE,
        /** Threw: drawn by its fallback, never asked again. */
        FAILED
    }

    private final int newPerFrame;

    private final Map<Object, State> states = new HashMap<Object, State>();

    /** The pictures given a first try this frame, so asking twice in one frame is one try. */
    private final Set<Object> triedThisFrame = new HashSet<Object>();

    /** @param newPerFrame the most first tries one frame may make; 0 or less makes none */
    public PictureBudget(int newPerFrame) {
        this.newPerFrame = Math.max(0, newPerFrame);
    }

    /** Call at the start of every frame: the frame's room for first tries is full again. */
    public void frame() {
        triedThisFrame.clear();
    }

    /**
     * Whether this picture may be drawn now: always for a fine one, never for a failed one, and for one not yet tried
     * only while this frame has room - which this call then takes. Report the outcome with {@link #fine} or
     * {@link #failed}; a try never reported is tried again in a later frame.
     */
    public boolean mayTry(Object key) {
        State state = state(key);
        if (state == State.FINE) return true;
        if (state == State.FAILED) return false;
        if (triedThisFrame.contains(key)) return true;
        if (triedThisFrame.size() >= newPerFrame) return false;
        triedThisFrame.add(key);
        return true;
    }

    /** The picture drew without throwing. A failed picture stays failed. */
    public void fine(Object key) {
        if (state(key) != State.FAILED) states.put(key, State.FINE);
    }

    /** The picture threw: it is never asked again this session. */
    public void failed(Object key) {
        states.put(key, State.FAILED);
    }

    /** Where the picture stands. */
    public State state(Object key) {
        State state = states.get(key);
        return state == null ? State.UNTRIED : state;
    }

    /** How many pictures have failed, for the {@code settingsperf} line. */
    public int failedCount() {
        int count = 0;
        for (State state : states.values()) {
            if (state == State.FAILED) count++;
        }
        return count;
    }
}
