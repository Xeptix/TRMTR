package com.trmtgtnh.original;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the original mod's {@code config/trmt.json}, and nothing else.
 *
 * <p>
 * Its own small reader rather than the game's Gson, because this package is the same file in all eight editions and
 * the game's Gson is not the same library in them: 1.7.10 carries 2.2.4 and the modern editions one ten years newer,
 * with other entry points and other words when a file is refused. A refusal here gives a line and a column, which is
 * what a player needs to mend a file by hand, and it says the same thing in every edition. When the shared reader of
 * {@code com.trmtgtnh.data} lands it can take this one's place; nothing outside this package sees which reader ran.
 *
 * <p>
 * As forgiving as the original was. It read the file through Gson's lenient reader, so a file someone wrote by hand
 * with
 * a comment in it, a name without quotes or a string in single quotes loaded there, and it loads here: refusing a file
 * the original accepted would leave a player's own numbers behind for a reason they could not see.
 */
final class OriginalJson {

    /** What a value is. */
    enum Type {
        OBJECT,
        ARRAY,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    /** One value, with where it began in the file, so a value of the wrong kind can be pointed at. */
    static final class Node {

        final Type type;
        final String text;
        final Map<String, Node> fields;
        final List<Node> items;
        final int line;
        final int column;

        Node(Type type, String text, Map<String, Node> fields, List<Node> items, int line, int column) {
            this.type = type;
            this.text = text;
            this.fields = fields;
            this.items = items;
            this.line = line;
            this.column = column;
        }

        /** A field of an object, or null when this is not an object or has no such field. */
        Node field(String name) {
            return fields == null ? null : fields.get(name);
        }
    }

    /** Why a file could not be read, and where. */
    static final class Refused extends Exception {

        private static final long serialVersionUID = 1L;

        final int line;
        final int column;
        final String why;

        Refused(String why, int line, int column) {
            super("line " + line + ", column " + column + ": " + why);
            this.why = why;
            this.line = line;
            this.column = column;
        }
    }

    private final String text;
    private int at;
    private int line = 1;
    private int lineStart;

    private OriginalJson(String text) {
        this.text = text;
    }

    /** The file's top-level object, or a refusal saying where reading stopped. */
    static Node read(String text) throws Refused {
        OriginalJson reader = new OriginalJson(text == null ? "" : text);
        reader.skipSpace();
        if (reader.at >= reader.text.length()) throw reader.refuse("the file is empty");
        if (reader.peek() != '{') throw reader.refuse("expected the file to start with '{'");
        Node root = reader.value();
        reader.skipSpace();
        if (reader.at < reader.text.length()) throw reader.refuse("expected the end of the file after the closing '}'");
        return root;
    }

    private Refused refuse(String why) {
        return new Refused(why, line, at - lineStart + 1);
    }

    private char peek() {
        return text.charAt(at);
    }

    private void advance() {
        if (text.charAt(at) == '\n') {
            line++;
            lineStart = at + 1;
        }
        at++;
    }

    private void skipSpace() throws Refused {
        while (at < text.length()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '﻿') {
                advance();
            } else if (c == '#' || (c == '/' && at + 1 < text.length() && text.charAt(at + 1) == '/')) {
                while (at < text.length() && peek() != '\n') advance();
            } else if (c == '/' && at + 1 < text.length() && text.charAt(at + 1) == '*') {
                int end = text.indexOf("*/", at + 2);
                if (end < 0) throw refuse("a comment is never closed");
                while (at < end + 2) advance();
            } else {
                return;
            }
        }
    }

    private Node value() throws Refused {
        skipSpace();
        if (at >= text.length()) throw refuse("the file ends where a value was expected");
        int startLine = line;
        int startColumn = at - lineStart + 1;
        char c = peek();
        if (c == '{') return object(startLine, startColumn);
        if (c == '[') return array(startLine, startColumn);
        if (c == '"' || c == '\'') return new Node(Type.STRING, quoted(), null, null, startLine, startColumn);
        String word = bare();
        if (word.isEmpty()) throw refuse("expected a value");
        if ("true".equals(word) || "false".equals(word)) {
            return new Node(Type.BOOLEAN, word, null, null, startLine, startColumn);
        }
        if ("null".equals(word)) return new Node(Type.NULL, word, null, null, startLine, startColumn);
        if (isNumber(word)) return new Node(Type.NUMBER, word, null, null, startLine, startColumn);
        return new Node(Type.STRING, word, null, null, startLine, startColumn);
    }

    private Node object(int startLine, int startColumn) throws Refused {
        advance();
        Map<String, Node> fields = new LinkedHashMap<String, Node>();
        skipSpace();
        if (at < text.length() && peek() == '}') {
            advance();
            return new Node(Type.OBJECT, null, fields, null, startLine, startColumn);
        }
        while (true) {
            skipSpace();
            if (at >= text.length()) throw refuse("the file ends inside an object; a '}' is missing");
            String name;
            if (peek() == '"' || peek() == '\'') {
                name = quoted();
            } else {
                name = bare();
                if (name.isEmpty()) throw refuse("expected a name in quotes");
            }
            skipSpace();
            if (at >= text.length() || peek() != ':') throw refuse("expected ':' after the name \"" + name + "\"");
            advance();
            fields.put(name, value());
            skipSpace();
            if (at >= text.length()) throw refuse("the file ends inside an object; a '}' is missing");
            char c = peek();
            if (c == ',') {
                advance();
            } else if (c == '}') {
                advance();
                return new Node(Type.OBJECT, null, fields, null, startLine, startColumn);
            } else {
                throw refuse("expected ',' or '}'");
            }
        }
    }

    private Node array(int startLine, int startColumn) throws Refused {
        advance();
        List<Node> items = new ArrayList<Node>();
        skipSpace();
        if (at < text.length() && peek() == ']') {
            advance();
            return new Node(Type.ARRAY, null, null, items, startLine, startColumn);
        }
        while (true) {
            items.add(value());
            skipSpace();
            if (at >= text.length()) throw refuse("the file ends inside a list; a ']' is missing");
            char c = peek();
            if (c == ',') {
                advance();
            } else if (c == ']') {
                advance();
                return new Node(Type.ARRAY, null, null, Collections.unmodifiableList(items), startLine, startColumn);
            } else {
                throw refuse("expected ',' or ']'");
            }
        }
    }

    private String quoted() throws Refused {
        char quote = peek();
        advance();
        StringBuilder out = new StringBuilder();
        while (true) {
            if (at >= text.length()) throw refuse("a string is never closed");
            char c = peek();
            if (c == quote) {
                advance();
                return out.toString();
            }
            if (c == '\n') throw refuse("a string runs past the end of its line");
            if (c != '\\') {
                out.append(c);
                advance();
                continue;
            }
            advance();
            if (at >= text.length()) throw refuse("a string is never closed");
            char e = peek();
            advance();
            switch (e) {
                case 'n':
                    out.append('\n');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'u':
                    if (at + 4 > text.length()) throw refuse("a \\u escape is cut short");
                    try {
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    } catch (NumberFormatException bad) {
                        throw refuse("a \\u escape is not four hexadecimal digits");
                    }
                    for (int i = 0; i < 4; i++) advance();
                    break;
                default:
                    out.append(e);
            }
        }
    }

    /** A run of characters that is not punctuation: a number, a word, or a name without quotes. */
    private String bare() {
        int start = at;
        while (at < text.length()) {
            char c = peek();
            if (c == ',' || c == ':'
                || c == '{'
                || c == '}'
                || c == '['
                || c == ']'
                || c == '"'
                || c == '\''
                || c == ' '
                || c == '\t'
                || c == '\r'
                || c == '\n'
                || c == '#'
                || c == '/') {
                break;
            }
            advance();
        }
        return text.substring(start, at);
    }

    private static boolean isNumber(String word) {
        return word.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    }
}
