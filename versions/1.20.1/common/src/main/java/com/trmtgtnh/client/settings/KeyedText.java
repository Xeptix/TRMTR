package com.trmtgtnh.client.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The forms {@code presets.remembered} holds its memory in (M16.2), read and written so the preset machinery reads
 * what the screen writes exactly as it reads what it wrote itself.
 *
 * <p>
 * {@code Presets.encode} writes two forms. A family axis is keyed text, {@code grass=0;dirt=8;sand=8}, each value
 * either one number ({@code grass=1.5625}) or a pair joined by a comma ({@code grass=16,8}); the quality axis is five
 * numbers joined by commas, {@code 80,4,262144,1024,1}. {@code Presets.restore} reads them back by splitting on
 * {@code ;}, then at the first {@code =} of each piece, skipping a piece with nothing before its {@code =} or none at
 * all, then each value on {@code ,}. This class reads by the same rule and writes the form {@code encode} writes, so
 * every text {@code encode} can write comes back through {@link #keyed(String)} and {@link #keyed(Map)}, or through
 * {@link #commas(String)} and {@link #commas(List)}, byte for byte - and an edit made on the Advanced fold is written
 * in the file's own form.
 *
 * <p>
 * Keys and values are kept as written, untrimmed, because {@code restore} trims the numbers itself and a text that
 * comes back changed is an edit nobody made. What a map cannot hold - a stray {@code ;}, a piece without {@code =} -
 * {@code restore} skips, and so does this, so it is dropped on the next write: the only texts that do not come back
 * byte for byte are ones the game was already ignoring part of.
 */
public final class KeyedText {

    private KeyedText() {}

    /** The pieces of {@code k=v;k=v}, in the order written. A later piece with the same key wins, as in restore. */
    public static Map<String, String> keyed(String text) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (text == null || text.isEmpty()) return out;
        for (String piece : text.split(";", -1)) {
            int split = piece.indexOf('=');
            if (split <= 0) continue;
            out.put(piece.substring(0, split), piece.substring(split + 1));
        }
        return out;
    }

    /** {@code k=v;k=v}, in the map's own order, as {@code Presets.encode} writes it. */
    public static String keyed(Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        if (values == null) return "";
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (out.length() > 0) out.append(';');
            out.append(entry.getKey())
                .append('=')
                .append(entry.getValue() == null ? "" : entry.getValue());
        }
        return out.toString();
    }

    /**
     * The parts of a comma list, every one kept as written - an empty text is no parts, and an empty part between two
     * commas or at the end is kept, so writing the parts back gives the same text.
     */
    public static List<String> commas(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        Collections.addAll(out, text.split(",", -1));
        return out;
    }

    /** The parts joined by commas. */
    public static String commas(List<String> parts) {
        StringBuilder out = new StringBuilder();
        if (parts == null) return "";
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(',');
            String part = parts.get(i);
            out.append(part == null ? "" : part);
        }
        return out.toString();
    }

    /**
     * One family's value replaced, or added at the end where the text has none for it, the rest kept as written: what
     * an edit on one family's row of the Advanced fold writes.
     */
    public static String with(String text, String key, String value) {
        Map<String, String> values = keyed(text);
        values.put(key, value);
        return keyed(values);
    }

    /** One part of a comma list replaced, the list widened with empty parts where it is too short. */
    public static String withPart(String text, int index, String value) {
        List<String> parts = new ArrayList<String>(commas(text));
        while (parts.size() <= index) parts.add("");
        parts.set(index, value);
        return commas(parts);
    }
}
