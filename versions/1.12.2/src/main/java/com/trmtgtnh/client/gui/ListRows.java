package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.trmtgtnh.client.settings.Canvas;
import com.trmtgtnh.client.settings.Edits;
import com.trmtgtnh.client.settings.ListEdits;
import com.trmtgtnh.client.settings.ListLine;
import com.trmtgtnh.client.settings.NameStatus;
import com.trmtgtnh.client.settings.NumberWords;
import com.trmtgtnh.client.settings.SettingsGeometry;
import com.trmtgtnh.client.settings.SettingsGeometry.Rect;
import com.trmtgtnh.client.settings.SettingsKeys;

/**
 * A list's lines as rows, shared by a list's own page ({@link WornPathListScreen}) and a short list opened in place on
 * the settings screen (spec S1): every line read by {@link ListLine#parse} and judged by {@link NameStatus#of} against
 * what the game has registered, and drawn as the spec sheet's picture row - its picture, its own display name or
 * "Not in this game" in red, the line as written in its edit box, its status, and its remove (M3, M18).
 *
 * <p>
 * <b>Every list says what a line is</b> (Xep, in GT New Horizons: "I go into dimensions, see a weird table, click
 * 'add' then type what? I put 'overworld' and nothing happens."): its add buttons name their kind, an empty list shows
 * a
 * hint row saying what to add, each kind of line has the row that fits it, and a dimension typed by name is
 * understood. The kinds:
 * <ul>
 * <li>blocks, items, fluids, mobs, enchantments, dimensions and free words: one picture row a line, with the picker
 * for its kind (M4) and suggestions while typing (M5);</li>
 * <li>{@code innerLayerTextures}: a block and the texture shown inside it, two pictures and two pickers a row
 * (M18.2);</li>
 * <li>{@code tamperGrades}: the table of C2, the ore's first item, the grade, the ore, its uses and its reach;</li>
 * <li>{@code wearsThroughTo}: chips in order, each with its family's picture, dragged to reorder (C1).</li>
 * </ul>
 *
 * <p>
 * <b>Red is a warning only</b> (Xep, 2026-10-21): no line's status holds Save. A number that does not read - a mob's
 * multiplier, a grade's uses or reach - is refused red in its field and counted by {@link #refusals()}, and the line
 * keeps the last number that read, so a refusal never reaches the file.
 *
 * <p>
 * The lines live in the {@link Edits} the rows are given, under their setting's id, joined by line ends; every change
 * here is one step there, so Ctrl+Z, the undo arrow and Undo all reach the rows' edits as any other. A line added and
 * not yet typed into is held by the rows alone, since the file has no use for a blank line. Everything the game knows
 * comes through {@link Host}, so a test can stand in for it and hold every rule here.
 */
public final class ListRows {

    /** What the rows need from the game: what is registered, pictures, names, pickers, the clipboard, the time. */
    interface Host {

        NameStatus.Lookup lookup();

        /** A line's picture, as a ref for {@link Canvas#picture}, or null for an empty frame. */
        Object picture(ListLine line, NameStatus.Status status);

        /** The thing's own display name, or null where it has none to give. */
        String name(ListLine line, NameStatus.Status status);

        /** A block atlas sprite's picture, for a texture pair's second half. */
        Object sprite(String name);

        /** A surface family's picture: its sub-tab's. */
        Object family(String key);

        /** What a picker of this kind offers, as gathered so far, for the suggestions. */
        List<PickerList.Entry> entries(PickerList.Kind kind);

        /** Opens the picker of this kind; {@code chosen} is told each line picked. */
        void pick(PickerList.Kind kind, Collection<String> held, Consumer<String> chosen);

        String clipboard();

        void clipboard(String text);

        long now();

        boolean ctrl();

        boolean shift();

        boolean alt();
    }

    /** The action bar's height: its buttons, 20 high, and a gap. */
    static final int BAR = 24;

    /** A row: the spec sheet's picture row (1.2). */
    static final int ROW = SettingsGeometry.PICTURE_ROW;

    /** The column headers of the texture pairs and the grades. */
    static final int HEAD = 14;

    /** A chip's size and the gap between chips (C1). */
    static final int CHIP_WIDTH = 100;

    static final int CHIP = 20;

    static final int CHIP_GAP = 4;

    /** A row of the menu of families to add. */
    static final int MENU_ROW = 16;

    /** How long a paste's note stays in the bar (C4): two seconds. */
    static final long NOTE_MILLIS = 2000L;

    // The keys, as LWJGL 2 numbers them.
    static final int KEY_ESCAPE = 1;

    static final int KEY_TAB = 15;

    static final int KEY_RETURN = 28;

    static final int KEY_ENTER = 156;

    static final int KEY_UP = 200;

    static final int KEY_DOWN = 208;

    static final int KEY_C = 46;

    static final int KEY_V = 47;

    private final ListLine.Kind kind;

    private final Edits edits;

    private final String id;

    private final String category;

    private final String key;

    private final int left;

    private final int width;

    private final Host host;

    private final boolean ordered;

    private final List<Row> rows = new ArrayList<Row>();

    /**
     * What the rows last wrote into the edits, so a change made elsewhere - an undo - is seen and the rows read again.
     */
    private String written;

    private int top;

    private int visibleTop = Integer.MIN_VALUE;

    private int visibleBottom = Integer.MAX_VALUE;

    private Runnable resized;

    private Row focusRow;

    private int focusBox;

    private final Suggestions suggestions = new Suggestions();

    private Row dragging;

    private int dragTo = -1;

    private boolean menuOpen;

    private String note;

    private long noteAt;

    /** The bar's buttons as last drawn, for the pointer. */
    private final List<Button> buttons = new ArrayList<Button>();

    /** Judgements by line, made again only when a line's text changes (Part 6). */
    private final Map<String, NameStatus.Status> judged = new HashMap<String, NameStatus.Status>();

    private Canvas seen;

    /** The rows of one list, as the settings screen and the list page lay them out: the game's own host. */
    public ListRows(ListLine.Kind kind, List<String> lines, Edits edits, String id, int left, int width) {
        this(kind, lines, edits, id, left, width, SettingsPictures.HOST);
    }

    ListRows(ListLine.Kind kind, List<String> lines, Edits edits, String id, int left, int width, Host host) {
        this.kind = kind == null ? ListLine.Kind.TEXT : kind;
        this.edits = edits;
        this.id = id;
        int dot = id.lastIndexOf('.');
        this.category = dot > 0 ? id.substring(0, dot) : id;
        this.key = dot > 0 ? id.substring(dot + 1) : id;
        this.left = left;
        this.width = width;
        this.host = host;
        this.ordered = ConfigLayout.ordered(category, key);
        List<String> start = lines == null ? SettingsModel.lines(edits.value(id)) : lines;
        for (String line : start) rows.add(new Row(line));
        written = join(start);
        sync();
    }

    // ------------------------------------------------------------------
    // The seam the settings screen and the list page use (spec Part 7)
    // ------------------------------------------------------------------

    public void layout(int top) {
        this.top = top;
    }

    /** The rows' whole height: the action bar, then the rows, the hint row, or the chips. */
    public int height() {
        sync();
        int height = BAR;
        if (kind == ListLine.Kind.FAMILY) {
            if (menuOpen) height += menuFamilies().size() * MENU_ROW + 4;
            height += rows.isEmpty() ? ROW : chipLines() * (CHIP + CHIP_GAP);
        } else if (rows.isEmpty()) {
            height += ROW;
        } else {
            if (headed()) height += HEAD;
            height += rows.size() * ROW;
        }
        return height + 2;
    }

    /** The lines as held now, blank ones left out. */
    public List<String> lines() {
        List<String> out = new ArrayList<String>();
        for (Row row : rows) {
            String line = row.line();
            if (!line.trim()
                .isEmpty()) out.add(line);
        }
        return out;
    }

    /** Told when the rows grow or shrink, so whoever placed them can lay out again. */
    public void onResize(Runnable whenResized) {
        resized = whenResized;
    }

    /** The band of the screen the rows are seen through: rows outside it are not drawn, nor their pictures (Part 6). */
    public void visible(int fromY, int toY) {
        visibleTop = fromY;
        visibleBottom = toY;
    }

    /** Whether one of the rows' boxes has the keyboard. */
    public boolean focused() {
        return focusRow != null;
    }

    public void blur() {
        setFocus(null, 0);
    }

    /** How many numbers typed into the rows do not read: these, and only these, hold a page's Done (M3.3, M6.5). */
    public int refusals() {
        int count = 0;
        for (Row row : rows) {
            if (row.refused != null) count++;
        }
        return count;
    }

    ListLine.Kind kind() {
        return kind;
    }

    // ------------------------------------------------------------------
    // The lines, as the edits hold them
    // ------------------------------------------------------------------

    private static String join(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.trim()
                .isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        return out.toString();
    }

    /** The rows read again where the edits were changed by something else: an undo, a revert, a reset. */
    private void sync() {
        String now = edits.value(id);
        if (now == null) now = "";
        if (now.equals(written)) return;
        rows.clear();
        for (String line : SettingsModel.lines(now)) rows.add(new Row(line));
        written = now;
        focusRow = null;
        changedSize();
    }

    /** Every row's line into the edits, as one step. */
    private void write() {
        List<String> lines = new ArrayList<String>();
        for (Row row : rows) lines.add(row.line());
        String joined = join(lines);
        if (!joined.equals(edits.value(id))) edits.set(id, joined);
        written = joined;
    }

    private void changedSize() {
        if (resized != null) resized.run();
    }

    // ------------------------------------------------------------------
    // What a line is
    // ------------------------------------------------------------------

    private NameStatus.Status judge(String text) {
        NameStatus.Status status = judged.get(text);
        if (status == null) {
            if (judged.size() > 512) judged.clear();
            try {
                status = NameStatus.of(ListLine.parse(text, kind), host.lookup());
            } catch (RuntimeException hostile) {
                status = NameStatus.of(ListLine.parse("", kind), host.lookup());
            }
            judged.put(text, status);
        }
        return status;
    }

    /** The picker a list of this kind adds through, or null where it has none (M4.1). */
    static PickerList.Kind pickerOf(ListLine.Kind kind) {
        switch (kind) {
            case BLOCK:
            case BLOCK_OR_ORE:
            case SPRITE_PAIR:
                return PickerList.Kind.BLOCK;
            case BLOCK_NO_META:
                return PickerList.Kind.BLOCK_NO_META;
            case ITEM:
            case ITEM_OR_FLUID:
                return PickerList.Kind.ITEM;
            case MOB:
                return PickerList.Kind.MOB;
            case ENCHANT:
                return PickerList.Kind.ENCHANTMENT;
            case DIMENSION:
                return PickerList.Kind.DIMENSION;
            default:
                return null;
        }
    }

    /** The words of an add button that opens a picker, by what it picks (M18.1). */
    static String addKey(PickerList.Kind picker) {
        switch (picker) {
            case BLOCK:
            case BLOCK_NO_META:
                return SettingsKeys.ADD_BLOCK;
            case ITEM:
                return SettingsKeys.ADD_ITEM;
            case FLUID:
                return SettingsKeys.ADD_FLUID;
            case MOB:
                return SettingsKeys.ADD_MOB;
            case ENCHANTMENT:
                return SettingsKeys.ADD_ENCHANTMENT;
            case DIMENSION:
                return SettingsKeys.ADD_DIMENSION;
            case SPRITE:
                return SettingsKeys.PICK_TEXTURE;
            default:
                return SettingsKeys.ADD;
        }
    }

    /** What an empty list's hint row says (M18.4). */
    String hintKey() {
        switch (kind) {
            case BLOCK:
            case BLOCK_OR_ORE:
            case BLOCK_NO_META:
                return SettingsKeys.HINT_BLOCK;
            case ITEM:
            case ITEM_OR_FLUID:
                return SettingsKeys.HINT_ITEM;
            case MOB:
                return SettingsKeys.HINT_MOB;
            case ENCHANT:
                return SettingsKeys.HINT_ENCHANTMENT;
            case DIMENSION:
                return host.lookup()
                    .writesDimensionNames() ? SettingsKeys.HINT_DIMENSION_NAMED : SettingsKeys.HINT_DIMENSION;
            case SPRITE_PAIR:
                return SettingsKeys.HINT_TEXTURE;
            case FAMILY:
                return SettingsKeys.HINT_FAMILY;
            case GRADE:
                return SettingsKeys.HINT_GRADE;
            default:
                return SettingsKeys.HINT_TEXT;
        }
    }

    /** The texture pairs and the grades head their rows with the names of their columns. */
    private boolean headed() {
        return kind == ListLine.Kind.SPRITE_PAIR || kind == ListLine.Kind.GRADE;
    }

    /** What a box's suggestions complete from (M5.3): the picker of its kind, or none. */
    private PickerList.Kind suggestedFrom(Row row, int box) {
        if (kind == ListLine.Kind.SPRITE_PAIR) return box == 0 ? PickerList.Kind.BLOCK : PickerList.Kind.SPRITE;
        if (kind == ListLine.Kind.GRADE) return box == 1 ? PickerList.Kind.ORE : null;
        if (kind == ListLine.Kind.MOB && box != 0) return null;
        if (kind == ListLine.Kind.ITEM_OR_FLUID) {
            return row.box.text()
                .trim()
                .regionMatches(true, 0, ListLine.FLUID_PREFIX, 0, ListLine.FLUID_PREFIX.length())
                    ? PickerList.Kind.FLUID
                    : PickerList.Kind.ITEM;
        }
        if (kind == ListLine.Kind.TEXT) return "loot.wayfarerCategories".equals(id) ? PickerList.Kind.POOL : null;
        return pickerOf(kind);
    }

    // ------------------------------------------------------------------
    // One row
    // ------------------------------------------------------------------

    /** One line: its edit boxes, by kind, and what it wrote when last it was edited. */
    final class Row {

        /** The line as the rows were given it, kept byte for byte until the row is edited. */
        private final String original;

        private boolean dirty;

        /** The block, the name, the mob, the grade's name - or the whole line. */
        final LineBox box;

        /** A pair's texture, a mob's number, a grade's ore; null otherwise. */
        final LineBox second;

        /** A grade's uses and reach; null otherwise. */
        final LineBox third;

        final LineBox fourth;

        /** The last number that read, kept in the line while the field holds one that does not. */
        private String goodNumber;

        private String goodUses;

        private String goodReach;

        /** Why a number typed here does not read, or null. */
        String refused;

        int top;

        Row(String line) {
            original = line == null ? "" : line;
            switch (kind) {
                case MOB:
                    box = new LineBox(MobRows.nameOf(original));
                    second = new LineBox(MobRows.numberOf(original));
                    third = null;
                    fourth = null;
                    goodNumber = second.text();
                    break;
                case SPRITE_PAIR: {
                    int split = original.indexOf('=');
                    box = new LineBox(
                        split < 0 ? original.trim()
                            : original.substring(0, split)
                                .trim());
                    second = new LineBox(
                        split < 0 ? ""
                            : original.substring(split + 1)
                                .trim());
                    third = null;
                    fourth = null;
                    break;
                }
                case GRADE: {
                    String[] parts = original.trim()
                        .split(":", -1);
                    box = new LineBox(parts[0].trim());
                    second = new LineBox(parts.length > 1 ? parts[1].trim() : "");
                    third = new LineBox(parts.length > 2 ? parts[2].trim() : "");
                    StringBuilder rest = new StringBuilder();
                    for (int i = 3; i < parts.length; i++) {
                        if (i > 3) rest.append(':');
                        rest.append(parts[i].trim());
                    }
                    fourth = new LineBox(rest.toString());
                    goodUses = third.text();
                    goodReach = fourth.text();
                    break;
                }
                default:
                    box = new LineBox(original);
                    second = null;
                    third = null;
                    fourth = null;
            }
        }

        LineBox box(int at) {
            switch (at) {
                case 0:
                    return box;
                case 1:
                    return second;
                case 2:
                    return third;
                case 3:
                    return fourth;
                default:
                    return null;
            }
        }

        int boxes() {
            return fourth != null ? 4 : second != null ? 2 : 1;
        }

        /** The text the row is judged by: a dimension's as typed, so a name typed is understood (M18.3). */
        String judgedText() {
            return dirty
                || kind != ListLine.Kind.SPRITE_PAIR && kind != ListLine.Kind.GRADE && kind != ListLine.Kind.MOB
                    ? typedLine()
                    : original;
        }

        /** The line the boxes spell, before a dimension's name is written as its id. */
        private String typedLine() {
            switch (kind) {
                case MOB:
                    return MobRows.line(box.text(), goodNumber);
                case SPRITE_PAIR:
                    if (box.text()
                        .trim()
                        .isEmpty()
                        && second.text()
                            .trim()
                            .isEmpty())
                        return "";
                    return ListLine.pair(
                        box.text()
                            .trim(),
                        second.text()
                            .trim());
                case GRADE: {
                    boolean blank = box.text()
                        .trim()
                        .isEmpty()
                        && second.text()
                            .trim()
                            .isEmpty()
                        && goodUses.trim()
                            .isEmpty()
                        && goodReach.trim()
                            .isEmpty();
                    if (blank) return "";
                    return ListLine.grade(
                        box.text()
                            .trim(),
                        second.text()
                            .trim(),
                        goodUses.trim(),
                        goodReach.trim()
                            .isEmpty() ? null : goodReach.trim());
                }
                default:
                    return box.text();
            }
        }

        /**
         * The line this row writes: as it was given until it is edited, then what its boxes say (M18.3: a dimension
         * name the game knows is written as its id).
         */
        String line() {
            if (!dirty) return original;
            String typed = typedLine();
            if (kind == ListLine.Kind.DIMENSION) {
                NameStatus.Status status = judge(typed);
                if (status.writtenAs != null) return status.writtenAs.toString();
            }
            return typed;
        }

        /** A box was typed into: its number checked where it is one, and the line written. */
        void edited(int at) {
            dirty = true;
            if (kind == ListLine.Kind.MOB && at == 1) {
                refused = refusalOf(second.text(), 'D', "0");
                if (refused == null) goodNumber = second.text()
                    .trim();
            } else if (kind == ListLine.Kind.GRADE && (at == 2 || at == 3)) {
                String uses = refusalOf(third.text(), 'I', "1");
                String reach = fourth.text()
                    .trim()
                    .isEmpty() ? null : refusalOf(fourth.text(), 'I', "0");
                if (uses == null) goodUses = third.text()
                    .trim();
                if (reach == null) goodReach = fourth.text()
                    .trim();
                refused = uses != null ? uses : reach;
            }
            colors();
        }

        /** The red of a refused number (M6.5); every other box in its body color. */
        void colors() {
            if (second != null && kind == ListLine.Kind.MOB) second.color(refused != null ? Canvas.RED : Canvas.BODY);
            if (third != null) {
                third.color(refusalOf(third.text(), 'I', "1") != null ? Canvas.RED : Canvas.BODY);
                fourth.color(
                    !fourth.text()
                        .trim()
                        .isEmpty() && refusalOf(fourth.text(), 'I', "0") != null ? Canvas.RED : Canvas.BODY);
            }
        }
    }

    /** Why a typed number does not read, in the screen's own refusal words' key, or null where it reads. */
    private static String refusalOf(String text, char kindOfNumber, String least) {
        NumberWords.Refusal refusal = NumberWords.check(text, kindOfNumber, least, null);
        return refusal == null ? null : refusal.key;
    }

    // ------------------------------------------------------------------
    // Where everything is
    // ------------------------------------------------------------------

    private int rowsTop() {
        return top + BAR + (headed() && !rows.isEmpty() ? HEAD : 0);
    }

    private void place() {
        int y = rowsTop();
        for (Row row : rows) {
            row.top = y;
            y += ROW;
        }
    }

    private int chipsPerLine() {
        return Math.max(1, (width - 8 + CHIP_GAP) / (CHIP_WIDTH + CHIP_GAP));
    }

    private int chipLines() {
        return Math.max(1, (rows.size() + chipsPerLine() - 1) / chipsPerLine());
    }

    private int chipsTop() {
        return top + BAR + (menuOpen ? menuFamilies().size() * MENU_ROW + 4 : 0);
    }

    private Rect chip(int index) {
        int per = chipsPerLine();
        int x = left + 4 + index % per * (CHIP_WIDTH + CHIP_GAP);
        int y = chipsTop() + index / per * (CHIP + CHIP_GAP) + 2;
        return Rect.sized(x, y, CHIP_WIDTH, CHIP);
    }

    private static Rect chipCross(Rect chip) {
        return Rect.sized(chip.x1 - 12, chip.y0 + 6, 8, 8);
    }

    /** The families the menu offers: the ten that wear in stages, less those already in the list. */
    private List<String> menuFamilies() {
        List<String> out = new ArrayList<String>();
        for (String family : NameStatus.FAMILIES) {
            boolean held = false;
            for (Row row : rows) {
                if (row.box.text()
                    .trim()
                    .equalsIgnoreCase(family)) held = true;
            }
            if (!held) out.add(family);
        }
        return out;
    }

    private Rect hintRow() {
        return Rect.sized(left, top + BAR, width, ROW);
    }

    /** One of the action bar's buttons. */
    private static final class Button {

        final Rect at;

        final String action;

        final String tip;

        Button(Rect at, String action, String tip) {
            this.at = at;
            this.action = action;
            this.tip = tip;
        }
    }

    /** The bar's buttons, left to right: Add a line, the pickers by kind, then Copy and Paste at the right. */
    private List<String[]> barActions() {
        List<String[]> out = new ArrayList<String[]>();
        if (kind == ListLine.Kind.FAMILY) {
            out.add(new String[] { "menu", SettingsKeys.CHIPS_ADD, SettingsKeys.CHIPS_TIP });
            return out;
        }
        out.add(new String[] { "line", SettingsKeys.ADD, null });
        PickerList.Kind picker = pickerOf(kind);
        if (picker != null) out.add(new String[] { "pick:" + picker.name(), addKey(picker), pickWords(picker) });
        if (kind == ListLine.Kind.ITEM_OR_FLUID) {
            out.add(
                new String[] { "pick:" + PickerList.Kind.FLUID.name(), SettingsKeys.ADD_FLUID,
                    SettingsKeys.PICK_FLUID });
        }
        return out;
    }

    /** A picker's title (M4.1), as its button's hover. */
    static String pickWords(PickerList.Kind picker) {
        switch (picker) {
            case BLOCK:
            case BLOCK_NO_META:
                return SettingsKeys.PICK_BLOCK;
            case ITEM:
                return SettingsKeys.PICK_ITEM;
            case FLUID:
                return SettingsKeys.PICK_FLUID;
            case MOB:
                return SettingsKeys.PICK_MOB;
            case ENCHANTMENT:
                return SettingsKeys.PICK_ENCHANTMENT;
            case DIMENSION:
                return SettingsKeys.PICK_DIMENSION;
            case SPRITE:
                return SettingsKeys.PICK_TEXTURE;
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    public void draw(Canvas canvas, int mouseX, int mouseY) {
        seen = canvas;
        sync();
        place();
        long now = host.now();
        List<String> hover = new ArrayList<String>();
        int[] hoverAt = { mouseX, mouseY };
        drawBar(canvas, mouseX, mouseY, now, hover);
        if (kind == ListLine.Kind.FAMILY) {
            drawFamilies(canvas, mouseX, mouseY, hover);
        } else if (rows.isEmpty()) {
            drawHint(canvas, mouseX, mouseY);
        } else {
            if (headed()) drawHeads(canvas);
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row.top + ROW < visibleTop || row.top > visibleBottom) continue;
                if (kind == ListLine.Kind.SPRITE_PAIR) drawPair(canvas, row, mouseX, mouseY, now, hover);
                else if (kind == ListLine.Kind.GRADE) drawGrade(canvas, row, mouseX, mouseY, now, hover);
                else drawRow(canvas, row, mouseX, mouseY, now, hover);
            }
            if (dragging != null && dragTo >= 0) {
                int y = rowsTop() + dragTo * ROW;
                canvas.fill(left, y - 1, left + width, y + 1, Canvas.GOLD);
            }
        }
        if (focusRow != null) {
            LineBox box = focusRow.box(focusBox);
            if (box != null && box.at != null) {
                suggestions.draw(
                    canvas,
                    box.at,
                    Math.max(box.at.width(), 200),
                    visibleTop == Integer.MIN_VALUE ? top : visibleTop,
                    visibleBottom == Integer.MAX_VALUE ? top + height() + 300 : visibleBottom,
                    mouseX,
                    mouseY);
            }
        }
        if (!hover.isEmpty() && !suggestions.holds(mouseX, mouseY)) canvas.hover(hover, hoverAt[0], hoverAt[1]);
    }

    private void drawBar(Canvas canvas, int mouseX, int mouseY, long now, List<String> hover) {
        buttons.clear();
        int x = left;
        int y = top + 2;
        for (String[] action : barActions()) {
            String label = canvas.words(action[1]);
            Rect at = Rect.sized(x, y, canvas.width(label) + 12, 20);
            buttons.add(new Button(at, action[0], action[2]));
            wordButton(canvas, at, label, mouseX, mouseY);
            if (action[2] != null && at.contains(mouseX, mouseY)) hover.add(canvas.words(action[2]));
            x = at.x1 + 4;
        }
        int right = left + width;
        String[][] edges = { { "paste", SettingsKeys.PASTE, SettingsKeys.PASTE_TIP },
            { "copy", SettingsKeys.COPY, SettingsKeys.COPY_TIP } };
        for (String[] action : edges) {
            String label = canvas.words(action[1]);
            Rect at = Rect.sized(right - canvas.width(label) - 12, y, canvas.width(label) + 12, 20);
            if (at.x0 < x) break;
            buttons.add(new Button(at, action[0], action[2]));
            wordButton(canvas, at, label, mouseX, mouseY);
            if (at.contains(mouseX, mouseY)) hover.add(canvas.words(action[2]));
            right = at.x0 - 4;
        }
        if (note != null && now - noteAt < NOTE_MILLIS && right - x > 8) {
            canvas.text(canvas.trim(noteWords(canvas), right - x - 4), x + 2, y + 6, Canvas.GOLD, true);
        }
    }

    /** A button with words, as the screen's own: dark, a brown frame, gold under the pointer. */
    private static void wordButton(Canvas canvas, Rect at, String label, int mouseX, int mouseY) {
        boolean under = at.contains(mouseX, mouseY);
        canvas.fill(at.x0, at.y0, at.x1, at.y1, under ? Canvas.TAB_IDLE : Canvas.TAB_OPEN);
        Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, under ? Canvas.GOLD : Canvas.RULE);
        canvas.text(label, at.x0 + 6, at.y0 + 6, under ? Canvas.GOLD : Canvas.WHITE, true);
    }

    /** An empty list's hint row (M18.4): the add icon in the picture frame and the kind's words, the row a button. */
    private void drawHint(Canvas canvas, int mouseX, int mouseY) {
        Rect at = hintRow();
        boolean under = at.contains(mouseX, mouseY);
        if (under) canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.TAB_IDLE);
        canvas.fill(at.x0 + 8, at.y0 + 5, at.x0 + 28, at.y0 + 25, Canvas.TAB_IDLE);
        canvas.icon("add", at.x0 + 10, at.y0 + 7);
        canvas.text(
            canvas.trim(canvas.words(hintKey()), width - 40),
            at.x0 + 32,
            at.y0 + 11,
            under ? Canvas.GOLD : Canvas.BODY,
            true);
        canvas.fill(at.x0, at.y1 - 1, at.x1, at.y1, Canvas.RULE);
    }

    /** The column names over the texture pairs and the grades, a dark rule under them (C2). */
    private void drawHeads(Canvas canvas) {
        int y = top + BAR + 2;
        if (kind == ListLine.Kind.SPRITE_PAIR) {
            canvas.text(canvas.words(SettingsKeys.TEXTURE_BLOCK), left + 32, y, Canvas.GOLD, 0.75F);
            canvas.text(canvas.words(SettingsKeys.TEXTURE_SHOWS), pairMid() + 32, y, Canvas.GOLD, 0.75F);
        } else {
            int[] at = gradeColumns();
            canvas.text(canvas.words(SettingsKeys.GRADE_NAME), at[0], y, Canvas.GOLD, 0.75F);
            canvas.text(canvas.words(SettingsKeys.GRADE_ORE), at[1], y, Canvas.GOLD, 0.75F);
            canvas.text(canvas.words(SettingsKeys.GRADE_USES), at[3], y, Canvas.GOLD, 0.75F);
            canvas.text(canvas.words(SettingsKeys.GRADE_REACH), at[4], y, Canvas.GOLD, 0.75F);
        }
        canvas.fill(left, top + BAR + HEAD - 2, left + width, top + BAR + HEAD - 1, Canvas.RULE_DARK);
    }

    /** The drag handle of an ordered list's row (S7). */
    private void drawHandle(Canvas canvas, Row row, int mouseX, int mouseY, List<String> hover) {
        if (!ordered) return;
        canvas.icon("drag", left - 5, row.top + 7);
        if (Rect.sized(left, row.top + 9, 6, 12)
            .contains(mouseX, mouseY)) hover.add(canvas.words(SettingsKeys.DRAG_TIP));
    }

    private void drawRemove(Canvas canvas, Row row, int mouseX, int mouseY, List<String> hover) {
        Rect at = removeOf(row);
        canvas.icon("remove", at.x0 - 1, at.y0 - 1);
        if (at.contains(mouseX, mouseY)) hover.add(canvas.words(SettingsKeys.REMOVE_TIP));
    }

    private Rect removeOf(Row row) {
        return Rect.sized(left + width - 18, row.top + 8, 14, 14);
    }

    /** A picture's frame, and the picture in it where there is one. */
    private static void frame(Canvas canvas, int x, int y, Object picture) {
        canvas.fill(x, y, x + 20, y + 20, Canvas.TAB_IDLE);
        if (picture == null) return;
        // Held inside its frame: a mob turned side on (spec C6) is longer than it is tall, and would spill over the
        // row's name.
        canvas.pushClip(x, y, x + 20, y + 20);
        canvas.picture(picture, x + 2, y + 2);
        canvas.popClip();
    }

    private String ghostFor(Row row, int box) {
        return row == focusRow && box == focusBox ? suggestions.ghost() : null;
    }

    /** The common row: picture, name or "Not in this game", the line in its box, the status, and remove. */
    private void drawRow(Canvas canvas, Row row, int mouseX, int mouseY, long now, List<String> hover) {
        String text = row.judgedText();
        NameStatus.Status status = judge(text);
        ListLine line = ListLine.parse(text, kind);
        boolean red = status.red();
        boolean number = kind == ListLine.Kind.MOB;
        String said = statusWords(canvas, status, line);
        int statusWidth = red ? 16 : said == null ? 0 : canvas.width(said);
        SettingsGeometry.PictureRow at = SettingsGeometry.pictureRow(left, row.top, width, number, statusWidth);
        drawHandle(canvas, row, mouseX, mouseY, hover);
        // A dimension's row is pictured as the picker's is (0.9.228; Xep, 2026-10-26, asked whether the list rows should
        // show the dimension pictures too: "Rows too"): its ground for the vanilla three, the portal for any other.
        boolean pictured = kind != ListLine.Kind.TEXT;
        if (pictured) {
            frame(canvas, at.frame.x0, at.frame.y0, red || line.blank ? null : host.picture(line, status));
            if (status.state == NameStatus.State.WILDCARD) canvas.icon("every", at.badge.x0, at.badge.y0);
        }
        int nameLeft = pictured ? at.name.x0 : left + 8;
        if (!line.blank) {
            String name;
            int color;
            if (status.state == NameStatus.State.BAD_LINE) {
                name = text.trim();
                color = Canvas.RED;
            } else if (red) {
                name = canvas.words(SettingsKeys.STATUS_NOT_HERE);
                color = Canvas.RED;
            } else {
                name = displayName(canvas, line, status);
                color = Canvas.WHITE;
            }
            if (name != null) {
                String shown = canvas.trim(name, at.name.x1 - nameLeft);
                canvas.text(shown, nameLeft, at.name.y0, color, true);
                if (status.writtenAs != null) {
                    // A dimension typed by name on an edition that writes ids (M18.3): written as its number.
                    String byName = canvas.words(SettingsKeys.DIMENSION_BY_NAME, status.writtenAs);
                    int x = nameLeft + canvas.width(shown) + 6;
                    if (x < at.name.x1) {
                        canvas.text(
                            canvas.trim(byName, (int) ((at.name.x1 - x) / 0.75F)),
                            x,
                            at.name.y0 + 1,
                            Canvas.GREY,
                            0.75F);
                    }
                }
            }
            if (red) {
                canvas.icon("warning", at.status.x1 - 16, row.top + 7);
                if (Rect.sized(at.status.x1 - 16, row.top + 7, 16, 16)
                    .contains(mouseX, mouseY)
                    || Rect.sized(nameLeft, at.name.y0, Math.max(1, at.name.x1 - nameLeft), 10)
                        .contains(mouseX, mouseY))
                    hover.add(reason(canvas, status));
            } else if (said != null) {
                canvas.text(said, at.status.x1 - canvas.width(said), at.status.y0, Canvas.BODY, true);
            }
        }
        Rect box = new Rect(nameLeft, at.editBox.y0, at.editBox.x1, at.editBox.y1);
        row.box.color(red ? Canvas.RED : Canvas.BODY);
        row.box.draw(canvas, box, ghostFor(row, 0), now);
        if (number) {
            row.second.draw(canvas, at.number, null, now);
            if (at.number.contains(mouseX, mouseY)) {
                hover.add(row.refused != null ? canvas.words(row.refused, "0") : canvas.words(SettingsKeys.MOB_NUMBER));
            }
        }
        drawRemove(canvas, row, mouseX, mouseY, hover);
        canvas.fill(at.rule.x0, at.rule.y0, at.rule.x1, at.rule.y1, Canvas.RULE);
    }

    /** A line's own display name, as the game gives it, by its kind. */
    private String displayName(Canvas canvas, ListLine line, NameStatus.Status status) {
        switch (status.state) {
            case ANY_MOB:
                return canvas.words(SettingsKeys.STATUS_ANY_MOB);
            case ENCHANTMENTS: {
                StringBuilder out = new StringBuilder();
                for (String one : status.names) {
                    if (out.length() > 0) out.append(", ");
                    out.append(one);
                }
                return out.toString();
            }
            case ORE_GROUP:
            case TAG:
                return line.ore;
            case DIMENSION:
                return status.dimensionName;
            case PLAIN:
                return null;
            default:
                String name = host.name(line, status);
                return name != null ? name : line.id;
        }
    }

    /** The status column's words (M3.2), or null where the row has none. */
    private static String statusWords(Canvas canvas, NameStatus.Status status, ListLine line) {
        // A red row says why in its warning's hover; '*' says what it is as its name, and its status column is empty.
        if (status.red() || status.state == NameStatus.State.ANY_MOB) return null;
        if (status.state == NameStatus.State.DIMENSION) {
            Integer number = line.dimension != null ? line.dimension : status.writtenAs;
            return number == null ? null : number.toString();
        }
        String key = status.statusKey();
        if (key == null) return null;
        if (status.state == NameStatus.State.ORE_GROUP || status.state == NameStatus.State.TAG) {
            return status.count == 1 ? canvas.words(key) : canvas.words(key, status.count);
        }
        if (status.state == NameStatus.State.ENCHANTMENTS) {
            return status.names.size() == 1 ? canvas.words(key) : canvas.words(key, status.names.size());
        }
        return canvas.words(key);
    }

    /** Why a red row is red: its reason's words, with the part that does not read where it is a bad line. */
    private static String reason(Canvas canvas, NameStatus.Status status) {
        String key = status.reasonKey();
        if (key == null) return "";
        return status.reasonArg != null ? canvas.words(key, status.reasonArg) : canvas.words(key);
    }

    private int pairMid() {
        return left + (width - 22) / 2;
    }

    /** A texture pair (M18.2): the block and its picker, then the texture shown inside it and its picker. */
    private void drawPair(Canvas canvas, Row row, int mouseX, int mouseY, long now, List<String> hover) {
        int y = row.top;
        int mid = pairMid();
        String block = row.box.text()
            .trim();
        ListLine blockLine = ListLine.parse(block, ListLine.Kind.BLOCK);
        NameStatus.Status blockStatus = judgeBlock(block);
        boolean blockRed = blockStatus.red() || blockLine.blank;
        drawHandle(canvas, row, mouseX, mouseY, hover);
        frame(canvas, left + 8, y + 5, blockRed ? null : host.picture(blockLine, blockStatus));
        String blockName = blockLine.blank ? null
            : blockRed ? canvas.words(SettingsKeys.STATUS_NOT_HERE) : host.name(blockLine, blockStatus);
        if (blockName != null) {
            canvas.text(
                canvas.trim(blockName, mid - 22 - left - 32),
                left + 32,
                y + 3,
                blockRed ? Canvas.RED : Canvas.WHITE,
                true);
        }
        if (blockRed && !blockLine.blank
            && Rect.sized(left + 32, y + 3, mid - 22 - left - 32, 10)
                .contains(mouseX, mouseY))
            hover.add(reason(canvas, blockStatus));
        row.box.color(blockRed && !blockLine.blank ? Canvas.RED : Canvas.BODY);
        row.box.draw(canvas, new Rect(left + 32, y + 14, mid - 22, y + 26), ghostFor(row, 0), now);
        pickIcon(canvas, mid - 20, y, mouseX, mouseY, hover, SettingsKeys.PICK_BLOCK);

        String texture = row.second.text()
            .trim();
        boolean textureKnown = !texture.isEmpty() && spriteKnown(texture);
        frame(canvas, mid + 4, y + 5, textureKnown ? host.sprite(texture) : null);
        int textLeft = mid + 28;
        int textRight = left + width - 22 - 20;
        if (!texture.isEmpty()) {
            canvas.text(
                canvas.trim(textureKnown ? texture : canvas.words(SettingsKeys.STATUS_NOT_HERE), textRight - textLeft),
                textLeft,
                y + 3,
                textureKnown ? Canvas.WHITE : Canvas.RED,
                true);
            if (!textureKnown && Rect.sized(textLeft, y + 3, textRight - textLeft, 10)
                .contains(mouseX, mouseY)) hover.add(canvas.words(SettingsKeys.STATUS_UNKNOWN_SPRITE));
        }
        row.second.color(!texture.isEmpty() && !textureKnown ? Canvas.RED : Canvas.BODY);
        row.second.draw(canvas, new Rect(textLeft, y + 14, textRight, y + 26), ghostFor(row, 1), now);
        pickIcon(canvas, textRight + 2, y, mouseX, mouseY, hover, SettingsKeys.PICK_TEXTURE);
        drawRemove(canvas, row, mouseX, mouseY, hover);
        canvas.fill(left, y + ROW - 1, left + width, y + ROW, Canvas.RULE);
    }

    private NameStatus.Status judgeBlock(String block) {
        try {
            return NameStatus.of(ListLine.parse(block, ListLine.Kind.BLOCK), host.lookup());
        } catch (RuntimeException hostile) {
            return NameStatus.of(ListLine.parse("", ListLine.Kind.BLOCK), host.lookup());
        }
    }

    private boolean spriteKnown(String texture) {
        try {
            return host.lookup()
                .sprite(texture);
        } catch (RuntimeException hostile) {
            return false;
        }
    }

    private static void pickIcon(Canvas canvas, int x, int rowTop, int mouseX, int mouseY, List<String> hover,
        String words) {
        canvas.icon("pick", x, rowTop + 7);
        if (Rect.sized(x, rowTop + 7, 16, 16)
            .contains(mouseX, mouseY)) hover.add(canvas.words(words));
    }

    /** The x of each grade column: name, ore, ore's end, uses, reach. */
    private int[] gradeColumns() {
        int reach = left + width - 22 - 44;
        int uses = reach - 4 - 44;
        int room = uses - 4 - (left + 32);
        int name = left + 32;
        int ore = name + room * 2 / 5;
        return new int[] { name, ore, uses - 4, uses, reach };
    }

    /** A tamper grade as the table's row (C2): the ore's first item, the grade, the ore, its uses and its reach. */
    private void drawGrade(Canvas canvas, Row row, int mouseX, int mouseY, long now, List<String> hover) {
        int y = row.top;
        String text = row.judgedText();
        NameStatus.Status status = judge(text);
        ListLine line = ListLine.parse(text, kind);
        boolean red = status.red() && !line.blank;
        int[] at = gradeColumns();
        drawHandle(canvas, row, mouseX, mouseY, hover);
        frame(canvas, left + 8, y + 5, red || line.blank ? null : host.picture(line, status));
        if (red) {
            canvas.text(canvas.words(SettingsKeys.STATUS_NOT_HERE), at[0], y + 3, Canvas.RED, true);
            canvas.icon("warning", at[3] - 20, y + 2);
            if (Rect.sized(at[0], y + 3, at[3] - at[0], 10)
                .contains(mouseX, mouseY)) hover.add(reason(canvas, status));
        } else if (!line.blank) {
            String name = host.name(line, status);
            if (name != null) canvas.text(canvas.trim(name, at[3] - at[0] - 4), at[0], y + 3, Canvas.WHITE, true);
        }
        row.box.draw(canvas, new Rect(at[0], y + 14, at[1] - 4, y + 26), ghostFor(row, 0), now);
        row.second.color(red ? Canvas.RED : Canvas.BODY);
        row.second.draw(canvas, new Rect(at[1], y + 14, at[2], y + 26), ghostFor(row, 1), now);
        row.third.draw(canvas, new Rect(at[3], y + 14, at[3] + 44, y + 26), null, now);
        row.fourth.draw(canvas, new Rect(at[4], y + 14, at[4] + 44, y + 26), null, now);
        if (row.refused != null && Rect.sized(at[3], y + 14, at[4] + 44 - at[3], 12)
            .contains(mouseX, mouseY)) hover.add(canvas.words(row.refused, "0"));
        drawRemove(canvas, row, mouseX, mouseY, hover);
        canvas.fill(left, y + ROW - 1, left + width, y + ROW, Canvas.RULE);
    }

    /** The surfaces a family wears through to, as chips (C1), and the menu of those it could add. */
    private void drawFamilies(Canvas canvas, int mouseX, int mouseY, List<String> hover) {
        if (menuOpen) {
            int y = top + BAR;
            for (String family : menuFamilies()) {
                Rect at = Rect.sized(left + 4, y, Math.min(width - 8, 160), MENU_ROW);
                boolean under = at.contains(mouseX, mouseY);
                canvas.fill(at.x0, at.y0, at.x1, at.y1, under ? Canvas.TAB_IDLE : Canvas.TAB_OPEN);
                canvas.picture(host.family(family), at.x0, at.y0);
                canvas
                    .text(familyName(canvas, family), at.x0 + 20, at.y0 + 4, under ? Canvas.GOLD : Canvas.WHITE, true);
                y += MENU_ROW;
            }
        }
        if (rows.isEmpty()) {
            Rect at = Rect.sized(left, chipsTop(), width, ROW);
            canvas.fill(at.x0 + 8, at.y0 + 5, at.x0 + 28, at.y0 + 25, Canvas.TAB_IDLE);
            canvas.icon("add", at.x0 + 10, at.y0 + 7);
            canvas.text(canvas.trim(canvas.words(hintKey()), width - 40), at.x0 + 32, at.y0 + 11, Canvas.BODY, true);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            Rect at = chip(i);
            String key = row.box.text()
                .trim();
            NameStatus.Status status = judge(key);
            boolean red = status.red();
            boolean lit = row == dragging || at.contains(mouseX, mouseY);
            canvas.fill(at.x0, at.y0, at.x1, at.y1, Canvas.TAB_IDLE);
            Canvas.frame(canvas, at.x0, at.y0, at.x1, at.y1, lit ? Canvas.GOLD : red ? Canvas.RED : Canvas.RULE);
            if (!red) canvas.picture(host.family(key.toLowerCase(Locale.ROOT)), at.x0 + 2, at.y0 + 2);
            String name = red ? key : familyName(canvas, key);
            canvas
                .text(canvas.trim(name, CHIP_WIDTH - 36), at.x0 + 20, at.y0 + 6, red ? Canvas.RED : Canvas.WHITE, true);
            Rect cross = chipCross(at);
            canvas.icon("remove", cross.x0 - 4, cross.y0 - 4);
            if (cross.contains(mouseX, mouseY)) hover.add(canvas.words(SettingsKeys.REMOVE_TIP));
            else if (at.contains(mouseX, mouseY)) {
                hover
                    .add(red ? canvas.words(SettingsKeys.STATUS_UNKNOWN_FAMILY) : canvas.words(SettingsKeys.CHIPS_TIP));
            }
        }
        if (dragging != null && dragTo >= 0) {
            Rect at = chip(Math.min(dragTo, rows.size() - 1));
            canvas.fill(at.x0 - 3, at.y0, at.x0 - 1, at.y1, Canvas.GOLD);
        }
    }

    private static String familyName(Canvas canvas, String key) {
        return canvas.words(SettingsKeys.OWN + "family." + key.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // The pointer
    // ------------------------------------------------------------------

    /** Whether a point is on the rows at all. */
    boolean holds(int x, int y) {
        return x >= left && x < left + width && y >= top && y < top + height();
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        sync();
        place();
        if (focusRow != null) {
            String taken = suggestions.click(mouseX, mouseY);
            if (taken != null) {
                take(taken);
                return true;
            }
        }
        if (!holds(mouseX, mouseY)) {
            blur();
            menuOpen = false;
            return false;
        }
        if (button != 0) return true;
        for (Button pressed : buttons) {
            if (pressed.at.contains(mouseX, mouseY)) {
                barPressed(pressed.action);
                return true;
            }
        }
        if (kind == ListLine.Kind.FAMILY) return familyClicked(mouseX, mouseY);
        if (rows.isEmpty()) {
            if (hintRow().contains(mouseX, mouseY)) hintPressed();
            return true;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (mouseY < row.top || mouseY >= row.top + ROW) continue;
            if (removeOf(row).contains(mouseX, mouseY)) {
                remove(row);
                return true;
            }
            if (ordered && Rect.sized(left, row.top + 9, 6, 12)
                .contains(mouseX, mouseY)) {
                dragging = row;
                dragTo = i;
                return true;
            }
            if (kind == ListLine.Kind.SPRITE_PAIR) {
                if (Rect.sized(pairMid() - 20, row.top + 7, 16, 16)
                    .contains(mouseX, mouseY)) {
                    pickInto(row, 0, PickerList.Kind.BLOCK);
                    return true;
                }
                if (Rect.sized(left + width - 22 - 18, row.top + 7, 16, 16)
                    .contains(mouseX, mouseY)) {
                    pickInto(row, 1, PickerList.Kind.SPRITE);
                    return true;
                }
            }
            for (int at = 0; at < row.boxes(); at++) {
                LineBox box = row.box(at);
                if (box.at != null && box.at.contains(mouseX, mouseY)) {
                    setFocus(row, at);
                    if (seen != null) box.click(seen, mouseX);
                    return true;
                }
            }
            return true;
        }
        blur();
        return true;
    }

    private boolean familyClicked(int mouseX, int mouseY) {
        if (menuOpen) {
            int y = top + BAR;
            for (String family : menuFamilies()) {
                if (Rect.sized(left + 4, y, Math.min(width - 8, 160), MENU_ROW)
                    .contains(mouseX, mouseY)) {
                    menuOpen = false;
                    add(family);
                    return true;
                }
                y += MENU_ROW;
            }
        }
        if (rows.isEmpty()) {
            menuOpen = true;
            changedSize();
            return true;
        }
        for (int i = 0; i < rows.size(); i++) {
            Rect at = chip(i);
            if (!at.contains(mouseX, mouseY)) continue;
            if (chipCross(at).contains(mouseX, mouseY)) remove(rows.get(i));
            else {
                dragging = rows.get(i);
                dragTo = i;
            }
            return true;
        }
        return true;
    }

    /** A drag in progress: where the line being moved would land (S7, C1). */
    public void mouseDragged(int mouseX, int mouseY) {
        if (dragging == null) return;
        if (kind == ListLine.Kind.FAMILY) {
            int per = chipsPerLine();
            int column = Math.max(0, Math.min(per - 1, (mouseX - left - 4) / (CHIP_WIDTH + CHIP_GAP)));
            int line = Math.max(0, (mouseY - chipsTop()) / (CHIP + CHIP_GAP));
            dragTo = Math.min(rows.size() - 1, line * per + column);
        } else {
            dragTo = Math.max(0, Math.min(rows.size() - 1, (mouseY - rowsTop()) / ROW));
        }
    }

    /** The drag let go: the line moved, one step (S7). */
    public void mouseReleased(int mouseX, int mouseY) {
        if (dragging == null) return;
        int from = rows.indexOf(dragging);
        int to = dragTo;
        dragging = null;
        dragTo = -1;
        if (from >= 0 && to >= 0 && from != to) move(from, to);
    }

    // ------------------------------------------------------------------
    // The keyboard
    // ------------------------------------------------------------------

    /**
     * A key: into the focused box, its suggestions or its row; Copy and Paste of the whole list with no box focused
     * (C4). True when the rows took it.
     */
    public boolean keyTyped(char typed, int code) {
        sync();
        boolean ctrl = host.ctrl();
        if (focusRow == null) {
            if (ctrl && code == KEY_C) {
                copy();
                return true;
            }
            if (ctrl && code == KEY_V) {
                paste();
                return true;
            }
            return false;
        }
        LineBox box = focusRow.box(focusBox);
        if (suggestions.open()) {
            if (code == KEY_UP || code == KEY_DOWN) {
                suggestions.move(code == KEY_UP ? -1 : 1);
                return true;
            }
            if (code == KEY_RETURN || code == KEY_ENTER) {
                take(suggestions.taken());
                return true;
            }
            if (code == KEY_ESCAPE) {
                suggestions.close();
                return true;
            }
        }
        if (code == KEY_ESCAPE) {
            blur();
            return true;
        }
        if (code == KEY_TAB) {
            if (!host.shift() && box.caretAtEnd() && suggestions.completed() != null) {
                take(suggestions.completed());
                return true;
            }
            return stepFocus(host.shift() ? -1 : 1);
        }
        if (host.alt() && ordered && (code == KEY_UP || code == KEY_DOWN)) {
            int from = rows.indexOf(focusRow);
            int to = from + (code == KEY_UP ? -1 : 1);
            if (to >= 0 && to < rows.size()) {
                int keep = focusBox;
                move(from, to);
                setFocus(rows.get(to), keep);
            }
            return true;
        }
        if (ctrl && code == KEY_V) {
            String clip = host.clipboard();
            if (box.insert(clip) == LineBox.Did.CHANGED) edited(focusRow, focusBox);
            return true;
        }
        if (box.key(typed, code) == LineBox.Did.CHANGED) edited(focusRow, focusBox);
        return true;
    }

    /** Tab and Shift+Tab within the rows: the next box, then the next row's; past the last, the rows let it go. */
    private boolean stepFocus(int direction) {
        int index = rows.indexOf(focusRow);
        int box = focusBox + direction;
        while (index >= 0 && index < rows.size()) {
            Row row = rows.get(index);
            if (box >= 0 && box < row.boxes()) {
                setFocus(row, box);
                return true;
            }
            index += direction;
            if (index < 0 || index >= rows.size()) break;
            box = direction > 0 ? 0
                : rows.get(index)
                    .boxes() - 1;
        }
        blur();
        return false;
    }

    private void setFocus(Row row, int box) {
        if (focusRow != null) {
            for (int i = 0; i < focusRow.boxes(); i++) focusRow.box(i)
                .focus(false);
        }
        focusRow = row;
        focusBox = box;
        if (row != null) {
            row.box(box)
                .focus(true);
            suggest();
        }
    }

    /** A box typed into: the line written, its suggestions asked again. */
    private void edited(Row row, int box) {
        row.edited(box);
        write();
        suggest();
    }

    private void suggest() {
        if (focusRow == null) return;
        PickerList.Kind from = suggestedFrom(focusRow, focusBox);
        List<PickerList.Entry> entries = from == null ? null : host.entries(from);
        suggestions.update(
            focusRow.box(focusBox)
                .text(),
            entries);
    }

    /** A suggestion taken into the focused box. */
    private void take(String line) {
        if (line == null || focusRow == null) return;
        LineBox box = focusRow.box(focusBox);
        box.setText(line);
        edited(focusRow, focusBox);
        suggestions.close();
    }

    // ------------------------------------------------------------------
    // What the buttons do
    // ------------------------------------------------------------------

    private void barPressed(String action) {
        if ("line".equals(action)) {
            Row row = new Row("");
            row.dirty = true;
            rows.add(row);
            setFocus(row, 0);
            changedSize();
        } else if ("menu".equals(action)) {
            menuOpen = !menuOpen;
            changedSize();
        } else if ("copy".equals(action)) {
            copy();
        } else if ("paste".equals(action)) {
            paste();
        } else if (action.startsWith("pick:")) {
            PickerList.Kind picker = PickerList.Kind.valueOf(action.substring("pick:".length()));
            host.pick(picker, lines(), new Consumer<String>() {

                @Override
                public void accept(String line) {
                    add(line);
                }
            });
        }
    }

    /** The hint row pressed (M18.4): the add button's picker, or a new line where the list has none. */
    private void hintPressed() {
        PickerList.Kind picker = pickerOf(kind);
        if (kind == ListLine.Kind.SPRITE_PAIR) {
            Row row = new Row("");
            row.dirty = true;
            rows.add(row);
            changedSize();
            pickInto(row, 0, PickerList.Kind.BLOCK);
        } else if (picker != null) {
            barPressed("pick:" + picker.name());
        } else {
            barPressed("line");
        }
    }

    /** A picker whose choice goes into one box of one row: a pair's block or texture (M18.2). */
    private void pickInto(final Row row, final int box, PickerList.Kind picker) {
        host.pick(picker, lines(), new Consumer<String>() {

            @Override
            public void accept(String line) {
                row.box(box)
                    .setText(line);
                edited(row, box);
            }
        });
    }

    /** A line added - picked, or a family from the menu - unless the list holds it already (M4.4). */
    void add(String line) {
        if (line == null || line.trim()
            .isEmpty()) return;
        for (String held : lines()) {
            if (PickerList.held(held)
                .equals(PickerList.held(line))) return;
        }
        Row row = new Row(line);
        row.dirty = true;
        rows.add(row);
        write();
        changedSize();
    }

    private void remove(Row row) {
        if (row == focusRow) blur();
        rows.remove(row);
        write();
        changedSize();
    }

    /** A line moved (S7), by the shared rule, as one step. */
    private void move(int from, int to) {
        List<String> all = new ArrayList<String>();
        for (Row row : rows) all.add(row.line());
        List<String> moved = ListEdits.move(all, from, to);
        Row shifted = rows.remove(from);
        rows.add(to, shifted);
        String joined = join(moved);
        if (!joined.equals(edits.value(id))) edits.set(id, joined);
        written = joined;
    }

    /** Every line onto the clipboard, one per line (C4). */
    private void copy() {
        host.clipboard(ListEdits.copy(lines()));
    }

    /** The clipboard's lines after the list's own, by the shared rule, as one step, and a note of what it did (C4). */
    private void paste() {
        ListEdits.PasteResult result = ListEdits.paste(lines(), host.clipboard());
        String joined = join(result.lines);
        if (!joined.equals(edits.value(id))) edits.set(id, joined);
        note = result.added == 0 ? SettingsKeys.PASTE_NONE
            : result.added == 1 ? SettingsKeys.PASTE_ADDED_ONE : SettingsKeys.PASTE_ADDED_MANY;
        noteCount = result.added;
        noteAt = host.now();
        sync();
    }

    /** How many lines the last paste added, for {@code paste.added.many}. */
    private int noteCount;

    private String noteWords(Canvas canvas) {
        if (SettingsKeys.PASTE_ADDED_MANY.equals(note)) return canvas.words(note, noteCount);
        return canvas.words(note);
    }
}
