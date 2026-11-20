package com.trmtgtnh.data;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A block or entity selector from a data file (FORMAT.md, "Block selectors"; W3 spec 2.1).
 *
 * <p>
 * Blocks: {@code ns:name} (every state), {@code ns:name:3} or {@code ns:name:*} (metadata, 1.7.10 and 1.12.2),
 * {@code ns:name[half=bottom]} (state properties, 1.12.2 on), {@code #ns:tag} (a block tag, 1.16.5 on). Entities: an id
 * in the edition's own spelling ({@code Zombie}, {@code modid.Name} on 1.7.10; {@code ns:name} from 1.12.2), or
 * {@code #ns:tag} where entity tags exist.
 *
 * <p>
 * Parsing is the same everywhere, so a pack written for several editions is read alike by each; whether this edition
 * can use a form is {@link Forms}, and a selector it cannot use is skipped and counted, never an error - a file names
 * {@code minecraft:grass} and {@code minecraft:grass_block} side by side and each edition reads its own. A string that
 * is
 * no selector at all is malformed, which is the file's mistake and refused.
 *
 * <p>
 * Portable: the same file in all eight trees. Matching takes plain strings, so the adapter turns a block state into its
 * id, metadata, properties and tags, and nothing here names the game.
 */
public final class Selector {

    /** The selector forms an edition reads. Each tree's adapter passes its own. */
    public static final class Forms {

        public final boolean meta;
        public final boolean states;
        public final boolean tags;
        public final boolean entityTags;

        public Forms(boolean meta, boolean states, boolean tags, boolean entityTags) {
            this.meta = meta;
            this.states = states;
            this.tags = tags;
            this.entityTags = entityTags;
        }

        /** 1.7.10: metadata, no block states, no tags. */
        public static final Forms LEGACY = new Forms(true, false, false, false);
    }

    private static final Pattern BLOCK = Pattern.compile(
        "^(#)?([-A-Za-z0-9_.]+):([-A-Za-z0-9_./]+)(?::([0-9]+|\\*))?(?:\\[([a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*)\\])?$");
    private static final Pattern ENTITY = Pattern.compile("^#?[-A-Za-z0-9_.:/]+$");

    /** No metadata named: every value. */
    public static final int ANY_META = -1;

    public final boolean tag;
    public final boolean entity;
    /** The namespace, or for a 1.7.10 entity without one, the empty string. */
    public final String namespace;
    /** The id without its tag mark: {@code ns:name}, or the entity's id as written. */
    public final String id;
    public final int meta;
    public final Map<String, String> properties;
    /** The selector as written. */
    public final String text;

    private Selector(String text, boolean tag, boolean entity, String namespace, String id, int meta,
        Map<String, String> properties) {
        this.text = text;
        this.tag = tag;
        this.entity = entity;
        this.namespace = namespace;
        this.id = id;
        this.meta = meta;
        this.properties = properties;
    }

    /** A block selector, or null when the text is no selector (the file's mistake). */
    public static Selector block(String text) {
        if (text == null) return null;
        Matcher m = BLOCK.matcher(text);
        if (!m.matches()) return null;
        boolean tag = m.group(1) != null;
        int meta = ANY_META;
        String metaText = m.group(4);
        Map<String, String> properties = Collections.emptyMap();
        if (tag && (metaText != null || m.group(5) != null)) return null;
        if (metaText != null && !"*".equals(metaText)) {
            if (metaText.length() > 2) return null;
            meta = Integer.parseInt(metaText);
            if (meta > 15) return null;
        }
        if (m.group(5) != null) {
            TreeMap<String, String> props = new TreeMap<String, String>();
            for (String pair : m.group(5)
                .split(",")) {
                int eq = pair.indexOf('=');
                if (props.put(pair.substring(0, eq), pair.substring(eq + 1)) != null) return null;
            }
            properties = Collections.unmodifiableMap(props);
        }
        return new Selector(text, tag, false, m.group(2), m.group(2) + ":" + m.group(3), meta, properties);
    }

    /** An entity selector, or null when the text is no selector. */
    public static Selector entity(String text) {
        if (text == null || !ENTITY.matcher(text)
            .matches()) return null;
        boolean tag = text.startsWith("#");
        String id = tag ? text.substring(1) : text;
        if (id.isEmpty() || (tag && id.indexOf(':') <= 0)) return null;
        // 1.7.10 spells a mod's entity modid.Name, so the namespace (which decides who owns it) is before the dot.
        int colon = id.indexOf(':');
        int dot = id.indexOf('.');
        String namespace = colon > 0 ? id.substring(0, colon) : dot > 0 ? id.substring(0, dot) : "";
        return new Selector(text, tag, true, namespace, id, ANY_META, Collections.<String, String>emptyMap());
    }

    /** Whether this edition can use this selector; one it cannot is skipped and counted, never refused. */
    public boolean readableIn(Forms forms) {
        if (entity) return !tag || forms.entityTags;
        if (tag) return forms.tags;
        if (meta != ANY_META && !forms.meta) return false;
        if (!properties.isEmpty() && !forms.states) return false;
        return true;
    }

    /**
     * Whether this block selector takes one block state, given as plain strings by the adapter.
     *
     * @param blockId     the block's id, {@code ns:name}
     * @param stateMeta   its metadata, or {@link #ANY_META} on an edition without
     * @param state       its properties, or null on an edition without
     * @param tagsOfBlock the tags it is in ({@code ns:tag}, without the mark), or null on an edition without
     */
    public boolean matches(String blockId, int stateMeta, Map<String, String> state, Collection<String> tagsOfBlock) {
        if (entity) return false;
        if (tag) return tagsOfBlock != null && tagsOfBlock.contains(id);
        if (!id.equals(blockId)) return false;
        if (meta != ANY_META && meta != stateMeta) return false;
        if (!properties.isEmpty()) {
            if (state == null) return false;
            for (Map.Entry<String, String> wanted : properties.entrySet()) {
                if (!wanted.getValue()
                    .equals(state.get(wanted.getKey()))) return false;
            }
        }
        return true;
    }

    /** Whether this entity selector takes an entity, by its id and (where the edition has them) its tags. */
    public boolean matchesEntity(String entityId, Collection<String> tagsOfEntity) {
        if (!entity) return false;
        if (tag) return tagsOfEntity != null && tagsOfEntity.contains(id);
        return id.equals(entityId);
    }

    /**
     * The selector in one spelling, so that {@code ns:name:*} and {@code ns:name} are one target and properties are in
     * one order: what the merge keys its targets by.
     */
    public String canonical() {
        StringBuilder out = new StringBuilder();
        if (tag) out.append('#');
        out.append(id);
        if (meta != ANY_META) out.append(':')
            .append(meta);
        if (!properties.isEmpty()) {
            out.append('[');
            boolean first = true;
            for (Map.Entry<String, String> each : properties.entrySet()) {
                if (!first) out.append(',');
                out.append(each.getKey())
                    .append('=')
                    .append(each.getValue());
                first = false;
            }
            out.append(']');
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return text;
    }
}
