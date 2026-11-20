package com.trmtgtnh.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One entry of a data file, read and validated: the selectors this edition can use, the values it sets, and where it
 * was written (W3 spec 2.1).
 *
 * <p>
 * The values are typed by the key table: a {@link String}, a {@link Double} (numbers), an {@link Integer} (integers), a
 * {@link Boolean}, a {@code List<String>} (a list of strings or texture names), a {@code List<Selector>}
 * ({@code repairWith}), a {@code List<int[]>} ({@code boxes}) or a {@code Map<String, String>} ({@code state}). A
 * shapes entry's {@code meta} and {@code state} are not values but part of which states it is about, so they are kept
 * as {@link #meta} and {@link #state} and not merged.
 *
 * <p>
 * Portable: the same file in all eight trees.
 */
public final class DataEntry {

    public final DataKind kind;
    public final DataSource source;
    public final String path;
    /** The namespace of the file ({@code assets/<ns>/...}), which owns the blocks of its own namespace. */
    public final String namespace;
    /** The entry's number in its file, from 1. */
    public final int index;
    public final int line;
    /** The selectors this edition can use; those it cannot are counted, not kept. */
    public final List<Selector> selectors;
    /** The keys it sets, in the file's order, typed as the class comment says. */
    public final Map<String, Object> values;
    /** For a shapes entry, the metadata it is about, or {@link Selector#ANY_META}. */
    public final int meta;
    /** For a shapes entry, the properties it is about; empty for every state. */
    public final Map<String, String> state;

    DataEntry(DataKind kind, DataSource source, String path, String namespace, int index, int line,
        List<Selector> selectors, Map<String, Object> values, int meta, Map<String, String> state) {
        this.kind = kind;
        this.source = source;
        this.path = path;
        this.namespace = namespace;
        this.index = index;
        this.line = line;
        this.selectors = Collections.unmodifiableList(selectors);
        this.values = Collections.unmodifiableMap(values);
        this.meta = meta;
        this.state = Collections.unmodifiableMap(state);
    }

    /** "mod example, assets/example/trmtr/families/loam.json entry 2 (line 4)": where a value came from. */
    public String origin() {
        return source.who() + ", " + path + " entry " + index + " (line " + line + ")";
    }
}
