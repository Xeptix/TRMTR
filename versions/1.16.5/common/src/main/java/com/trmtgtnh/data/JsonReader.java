package com.trmtgtnh.data;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TRMTR's own small JSON reader, for data files (W3 spec 2.1 and open question 7, trmtr-api 1.0.0).
 *
 * <p>
 * Not Gson, on purpose. 1.7.10 has Gson 2.2.4 from the game and the modern editions a much newer one, with different
 * entry points and different messages; a reader of our own is one file in all eight trees, and every refusal it makes
 * gives the line and the column, which is what a modder needs to mend a file.
 *
 * <p>
 * Strict JSON (RFC 8259): no comments, no trailing commas, no single quotes, and a key written twice in one object is
 * refused rather than quietly letting the later one win, because a file that says two things about one key does not say
 * which it meant. A UTF-8 byte-order mark is allowed, since Windows editors write one. The input must be UTF-8.
 *
 * <p>
 * Bounded, so no file can take the game down: a file over {@link #MAX_BYTES} is refused before it is decoded, and
 * nesting deeper than {@link #MAX_DEPTH} is refused rather than recursed into. Nothing here throws to its caller; a
 * refusal is a {@link Result} with its message and place.
 */
public final class JsonReader {

    /** The largest file read: 1 MB (FORMAT.md, "Every file"). */
    public static final int MAX_BYTES = 1024 * 1024;

    /** The deepest nesting read. Format 1's deepest file is four levels; this leaves room and bounds the stack. */
    public static final int MAX_DEPTH = 64;

    /** What reading a file came to: a value, or a refusal with its place. */
    public static final class Result {

        public final JsonValue value;
        public final String problem;
        public final int line;
        public final int column;

        Result(JsonValue value, String problem, int line, int column) {
            this.value = value;
            this.problem = problem;
            this.line = line;
            this.column = column;
        }

        public boolean ok() {
            return value != null;
        }

        /** "line 4, column 17: expected ','" - or "file over 1 MB" for a refusal with no place. */
        public String described() {
            if (ok()) return "read";
            return line > 0 ? JsonValue.where(line, column) + ": " + problem : problem;
        }
    }

    /** A refusal while parsing; caught inside this class only. */
    private static final class Refused extends Exception {

        private static final long serialVersionUID = 1L;
        final int line;
        final int column;

        Refused(String message, int line, int column) {
            super(message, null, false, false);
            this.line = line;
            this.column = column;
        }
    }

    private final String text;
    private int at;
    private int line = 1;
    private int lineStart;

    private JsonReader(String text) {
        this.text = text;
    }

    /** Reads a file's bytes. Never throws. */
    public static Result read(byte[] bytes) {
        if (bytes == null) return new Result(null, "no file", 0, 0);
        if (bytes.length > MAX_BYTES) {
            return new Result(null, "the file is " + bytes.length + " bytes, over the 1 MB a data file may be", 0, 0);
        }
        String text;
        try {
            CharsetDecoder decoder = Charset.forName("UTF-8")
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer chars = decoder.decode(ByteBuffer.wrap(bytes));
            text = chars.toString();
        } catch (CharacterCodingException notUtf8) {
            return new Result(null, "the file is not UTF-8 text", 0, 0);
        }
        return read(text);
    }

    /** Reads a file's text (as a plugin's {@code Registrar.data} hands it over). Never throws. */
    public static Result read(String text) {
        if (text == null) return new Result(null, "no file", 0, 0);
        if (text.length() > MAX_BYTES) {
            return new Result(null, "the file is over the 1 MB a data file may be", 0, 0);
        }
        JsonReader reader = new JsonReader(text);
        try {
            if (text.length() > 0 && text.charAt(0) == '﻿') {
                reader.at = 1;
                reader.lineStart = 1;
            }
            reader.blank();
            if (reader.at >= text.length()) throw reader.refuse("the file is empty");
            JsonValue value = reader.value(0);
            reader.blank();
            if (reader.at < text.length()) throw reader.refuse("unexpected " + reader.shown() + " after the end");
            return new Result(value, null, 0, 0);
        } catch (Refused refused) {
            return new Result(null, refused.getMessage(), refused.line, refused.column);
        }
    }

    private Refused refuse(String message) {
        return new Refused(message, line, at - lineStart + 1);
    }

    private Refused refuseAt(String message, int atLine, int atColumn) {
        return new Refused(message, atLine, atColumn);
    }

    /** The character at the cursor as a refusal names it. */
    private String shown() {
        if (at >= text.length()) return "end of file";
        char c = text.charAt(at);
        if (c == '\n' || c == '\r') return "end of line";
        if (c < 0x20) return "control character " + (int) c;
        return "'" + c + "'";
    }

    private void blank() {
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == '\n') {
                at++;
                line++;
                lineStart = at;
            } else if (c == '\r') {
                at++;
                if (at < text.length() && text.charAt(at) == '\n') at++;
                line++;
                lineStart = at;
            } else if (c == ' ' || c == '\t') {
                at++;
            } else {
                return;
            }
        }
    }

    private JsonValue value(int depth) throws Refused {
        if (depth > MAX_DEPTH) throw refuse("nested deeper than " + MAX_DEPTH + " levels");
        if (at >= text.length()) throw refuse("expected a value, found the end of file");
        int startLine = line;
        int startColumn = at - lineStart + 1;
        char c = text.charAt(at);
        switch (c) {
            case '{':
                return object(depth, startLine, startColumn);
            case '[':
                return array(depth, startLine, startColumn);
            case '"':
                return JsonValue.scalar(JsonValue.Kind.STRING, startLine, startColumn, string());
            case 't':
                word("true");
                return JsonValue.scalar(JsonValue.Kind.BOOLEAN, startLine, startColumn, Boolean.TRUE);
            case 'f':
                word("false");
                return JsonValue.scalar(JsonValue.Kind.BOOLEAN, startLine, startColumn, Boolean.FALSE);
            case 'n':
                word("null");
                return JsonValue.scalar(JsonValue.Kind.NULL, startLine, startColumn, null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return JsonValue.scalar(JsonValue.Kind.NUMBER, startLine, startColumn, number());
                }
                throw refuse("expected a value, found " + shown());
        }
    }

    private void word(String word) throws Refused {
        if (!text.startsWith(word, at)) throw refuse("expected a value, found " + shown());
        at += word.length();
    }

    private JsonValue object(int depth, int startLine, int startColumn) throws Refused {
        at++;
        LinkedHashMap<String, JsonValue> members = new LinkedHashMap<String, JsonValue>();
        Map<String, int[]> places = new HashMap<String, int[]>();
        blank();
        if (at < text.length() && text.charAt(at) == '}') {
            at++;
            return JsonValue.object(startLine, startColumn, members, places);
        }
        while (true) {
            blank();
            if (at >= text.length() || text.charAt(at) != '"') {
                throw refuse("expected a key in double quotes, found " + shown());
            }
            int keyLine = line;
            int keyColumn = at - lineStart + 1;
            String key = string();
            if (members.containsKey(key)) {
                throw refuseAt("the key \"" + key + "\" is written twice in one object", keyLine, keyColumn);
            }
            blank();
            if (at >= text.length() || text.charAt(at) != ':') throw refuse("expected ':', found " + shown());
            at++;
            blank();
            members.put(key, value(depth + 1));
            places.put(key, new int[] { keyLine, keyColumn });
            blank();
            if (at >= text.length()) throw refuse("expected ',' or '}', found the end of file");
            char c = text.charAt(at);
            if (c == ',') {
                at++;
                continue;
            }
            if (c == '}') {
                at++;
                return JsonValue.object(startLine, startColumn, members, places);
            }
            throw refuse("expected ',' or '}', found " + shown());
        }
    }

    private JsonValue array(int depth, int startLine, int startColumn) throws Refused {
        at++;
        List<JsonValue> items = new ArrayList<JsonValue>();
        blank();
        if (at < text.length() && text.charAt(at) == ']') {
            at++;
            return JsonValue.array(startLine, startColumn, items);
        }
        while (true) {
            blank();
            items.add(value(depth + 1));
            blank();
            if (at >= text.length()) throw refuse("expected ',' or ']', found the end of file");
            char c = text.charAt(at);
            if (c == ',') {
                at++;
                continue;
            }
            if (c == ']') {
                at++;
                return JsonValue.array(startLine, startColumn, items);
            }
            throw refuse("expected ',' or ']', found " + shown());
        }
    }

    private String string() throws Refused {
        at++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (at >= text.length()) throw refuse("a string is not closed before the end of file");
            char c = text.charAt(at);
            if (c == '"') {
                at++;
                return out.toString();
            }
            if (c == '\\') {
                at++;
                if (at >= text.length()) throw refuse("a string is not closed before the end of file");
                char e = text.charAt(at);
                switch (e) {
                    case '"':
                    case '\\':
                    case '/':
                        out.append(e);
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (at + 4 >= text.length()) throw refuse("expected four hex digits after \\u");
                        int code = 0;
                        for (int i = 1; i <= 4; i++) {
                            int digit = Character.digit(text.charAt(at + i), 16);
                            if (digit < 0) {
                                at += i;
                                throw refuse("expected four hex digits after \\u, found " + shown());
                            }
                            code = code * 16 + digit;
                        }
                        out.append((char) code);
                        at += 4;
                        break;
                    default:
                        throw refuse("unknown escape \\" + e);
                }
                at++;
                continue;
            }
            if (c < 0x20) throw refuse("a string runs into " + shown() + " - close it with '\"'");
            out.append(c);
            at++;
        }
    }

    private String number() throws Refused {
        int start = at;
        if (text.charAt(at) == '-') at++;
        if (at >= text.length() || !digit(text.charAt(at))) throw refuse("expected a digit, found " + shown());
        if (text.charAt(at) == '0') {
            at++;
            if (at < text.length() && digit(text.charAt(at))) throw refuse("a number may not start with 0");
        } else {
            while (at < text.length() && digit(text.charAt(at))) at++;
        }
        if (at < text.length() && text.charAt(at) == '.') {
            at++;
            if (at >= text.length() || !digit(text.charAt(at))) throw refuse("expected a digit after '.'");
            while (at < text.length() && digit(text.charAt(at))) at++;
        }
        if (at < text.length() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
            at++;
            if (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) at++;
            if (at >= text.length() || !digit(text.charAt(at))) throw refuse("expected a digit in the exponent");
            while (at < text.length() && digit(text.charAt(at))) at++;
        }
        return text.substring(start, at);
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }
}
