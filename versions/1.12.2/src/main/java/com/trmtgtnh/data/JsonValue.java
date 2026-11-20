package com.trmtgtnh.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One value of a parsed data file, with the line and column it starts at (W3, trmtr-api 1.0.0).
 *
 * <p>
 * Every value keeps where it was written because every refusal names a line and a column: a modder told "resistance is
 * out of range" in a file of four hundred entries has to be told which one. Objects keep their keys in the order the
 * file wrote them, and the place of each key, so a warning about an unknown key points at the key rather than at the
 * object around it.
 *
 * <p>
 * Portable: the same file in all eight trees, naming nothing from the game ({@code CoreStaysPortableTest}).
 */
public final class JsonValue {

    /** What a value is. A number keeps its text, so an integer is told from a fraction exactly. */
    public enum Kind {
        OBJECT,
        ARRAY,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    public final Kind kind;
    public final int line;
    public final int column;

    private final Object value;
    private final Map<String, JsonValue> members;
    private final Map<String, int[]> keyPlaces;
    private final List<JsonValue> items;

    private JsonValue(Kind kind, int line, int column, Object value, Map<String, JsonValue> members,
        Map<String, int[]> keyPlaces, List<JsonValue> items) {
        this.kind = kind;
        this.line = line;
        this.column = column;
        this.value = value;
        this.members = members;
        this.keyPlaces = keyPlaces;
        this.items = items;
    }

    static JsonValue scalar(Kind kind, int line, int column, Object value) {
        return new JsonValue(kind, line, column, value, null, null, null);
    }

    static JsonValue object(int line, int column, LinkedHashMap<String, JsonValue> members,
        Map<String, int[]> keyPlaces) {
        return new JsonValue(Kind.OBJECT, line, column, null, Collections.unmodifiableMap(members), keyPlaces, null);
    }

    static JsonValue array(int line, int column, List<JsonValue> items) {
        return new JsonValue(Kind.ARRAY, line, column, null, null, null, Collections.unmodifiableList(items));
    }

    /** An object's members in the file's order; empty for anything else. */
    public Map<String, JsonValue> members() {
        return members == null ? Collections.<String, JsonValue>emptyMap() : members;
    }

    /** An array's items; empty for anything else. */
    public List<JsonValue> items() {
        return items == null ? Collections.<JsonValue>emptyList() : items;
    }

    /** One member of an object, or null. */
    public JsonValue get(String key) {
        return members == null ? null : members.get(key);
    }

    /** Where an object's key was written, as {line, column}, or this object's own place. */
    public int[] placeOf(String key) {
        int[] at = keyPlaces == null ? null : keyPlaces.get(key);
        return at == null ? new int[] { line, column } : at;
    }

    /** A string's text, or null for anything else. */
    public String string() {
        return kind == Kind.STRING ? (String) value : null;
    }

    /** A boolean's value; false for anything else. */
    public boolean bool() {
        return kind == Kind.BOOLEAN && ((Boolean) value).booleanValue();
    }

    /** A number's text as the file wrote it, or null. */
    public String numberText() {
        return kind == Kind.NUMBER ? (String) value : null;
    }

    /** A number's value; NaN for anything else. */
    public double number() {
        return kind == Kind.NUMBER ? Double.parseDouble((String) value) : Double.NaN;
    }

    /**
     * Whether this is a number with a whole value that fits an int: {@code 4} and {@code 4.0} are, {@code 4.5} and
     * {@code 1e12} are not.
     */
    public boolean isWhole() {
        if (kind != Kind.NUMBER) return false;
        double d = number();
        return !Double.isInfinite(d) && d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE;
    }

    /** "line 4, column 17", the way every refusal says where. */
    public String where() {
        return where(line, column);
    }

    static String where(int line, int column) {
        return "line " + line + ", column " + column;
    }

    /** The kind as a refusal names it: "a string", "a list", "an object". */
    public String described() {
        switch (kind) {
            case OBJECT:
                return "an object";
            case ARRAY:
                return "a list";
            case STRING:
                return "a string";
            case NUMBER:
                return "a number";
            case BOOLEAN:
                return "true or false";
            default:
                return "null";
        }
    }
}
