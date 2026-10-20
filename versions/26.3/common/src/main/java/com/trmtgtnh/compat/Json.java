package com.trmtgtnh.compat;

/**
 * Just enough JSON to write BetterQuesting's files, and no more.
 *
 * <p>
 * BetterQuesting stores NBT as JSON, which means every key carries its tag type as a suffix -
 * {@code "name:8"} is a string, {@code "index:3"} an int, {@code "tasks:9"} a list. A list is
 * written as an object with the indices as keys. That is regular enough that a node holding
 * already-serialised children and indenting them on the way out covers the whole format.
 *
 * <p>
 * Deliberately not a general serialiser and deliberately not a dependency. The mod writes JSON in
 * two places and reads it in none, so a parser would be dead weight and a library would be a
 * dependency taken for nine lines of output.
 */
final class Json {

    private final StringBuilder body = new StringBuilder();

    private boolean first = true;

    /** A raw, already-serialised value. */
    Json put(String key, String value) {
        if (!first) body.append(",\n");
        first = false;
        body.append('"')
            .append(key)
            .append("\": ")
            .append(value);
        return this;
    }

    Json put(String key, Json child) {
        return put(key, child.toString());
    }

    Json put(String key, long value) {
        return put(key, Long.toString(value));
    }

    /** A string value, quoted and escaped. */
    Json putText(String key, String value) {
        return put(key, quote(value));
    }

    /** Adds a child under the next free list index, e.g. {@code "0:10"}. */
    Json add(int index, Json child) {
        return put(index + ":10", child);
    }

    boolean isEmpty() {
        return first;
    }

    @Override
    public String toString() {
        if (first) return "{}";
        return "{\n" + indent(body.toString()) + "\n}";
    }

    private static String indent(String block) {
        return "  " + block.replace("\n", "\n  ");
    }

    /**
     * A JSON string.
     *
     * <p>
     * Everything outside printable ASCII is escaped rather than emitted directly, because these
     * files are read back by a parser whose encoding assumptions are not ours to guess - and the
     * pack's own files escape the same way.
     */
    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char letter = value.charAt(index);
            switch (letter) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (letter < 0x20 || letter > 0x7E) {
                        out.append(String.format("\\u%04x", Integer.valueOf(letter)));
                    } else {
                        out.append(letter);
                    }
            }
        }
        return out.append('"')
            .toString();
    }

    private Json() {}

    static Json obj() {
        return new Json();
    }
}
