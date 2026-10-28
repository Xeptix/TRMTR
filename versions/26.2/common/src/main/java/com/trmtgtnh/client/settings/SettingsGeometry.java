package com.trmtgtnh.client.settings;

/**
 * Where every part of the settings screen goes, in GUI pixels (0.9.228; docs/goals/0.9.228-settings-spec.md, 1.1 and
 * 1.2).
 *
 * <p>
 * One arithmetic for all eight editions, so the screens line up when photographed side by side and a test can hold
 * every rectangle. The screen is <b>compact</b> below 300 high or 400 wide - GUI scale 4 on a 1080p window is 480 by
 * 270 - and every band is then shorter. Top to bottom: the header, the tab strip, the Surfaces tab's family icons
 * (absent on every other tab, and everything below moves up), the search row, the banner (only while a value is
 * refused), the list panel, and the footer.
 *
 * <p>
 * A rectangle is {@code x0, y0} inclusive to {@code x1, y1} exclusive, as the edition's fills take it.
 */
public final class SettingsGeometry {

    /** The tallest and widest a screen can be and still be compact. */
    public static final int COMPACT_BELOW_HEIGHT = 300;

    public static final int COMPACT_BELOW_WIDTH = 400;

    /** The list panel's widest. */
    public static final int PANEL_MAX = 460;

    /** The scrollbar's width, and the gap between it and the rows. */
    public static final int SCROLLBAR = 6;

    public static final int SCROLLBAR_GAP = 2;

    /** A setting row's height, regular and compact; the extra line under a focused or refused control. */
    public static final int SETTING_ROW = 30;

    public static final int SETTING_ROW_COMPACT = 26;

    public static final int UNDER_LINE = 10;

    /** A folder heading, a picture row, a picker row, a dropdown's option, a kicker's extra height. */
    public static final int FOLDER_ROW = 18;

    public static final int PICTURE_ROW = 30;

    public static final int PICKER_ROW = 26;

    public static final int KICKER = 8;

    /** A setting row's control, and its sliders' and color row's halves. */
    public static final int CONTROL_WIDTH = 150;

    public static final int CONTROL_HEIGHT = 20;

    public static final int SLIDER_WIDTH = 100;

    public static final int SLIDER_FIELD = 46;

    public static final int SWATCH_WIDTH = 110;

    public static final int HEX_READOUT = 36;

    /** The search field's widest, and the "Changed only" toggle's width. */
    public static final int SEARCH_WIDTH = 220;

    public static final int CHANGED_ONLY_WIDTH = 90;

    private SettingsGeometry() {}

    /** A rectangle, {@code x0, y0} inclusive to {@code x1, y1} exclusive. */
    public static final class Rect {

        public final int x0;

        public final int y0;

        public final int x1;

        public final int y1;

        public Rect(int x0, int y0, int x1, int y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        /** A rectangle from its corner and its size. */
        public static Rect sized(int x, int y, int width, int height) {
            return new Rect(x, y, x + width, y + height);
        }

        public int width() {
            return x1 - x0;
        }

        public int height() {
            return y1 - y0;
        }

        public boolean contains(int x, int y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }

        /** Whether the two share any pixel. */
        public boolean overlaps(Rect other) {
            return other != null && x0 < other.x1 && other.x0 < x1 && y0 < other.y1 && other.y0 < y1;
        }

        /** Whether the other lies wholly inside this one. */
        public boolean holds(Rect other) {
            return other.x0 >= x0 && other.y0 >= y0 && other.x1 <= x1 && other.y1 <= y1;
        }

        public Rect moved(int dx, int dy) {
            return new Rect(x0 + dx, y0 + dy, x1 + dx, y1 + dy);
        }

        public Rect inset(int by) {
            return new Rect(x0 + by, y0 + by, x1 - by, y1 - by);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Rect)) return false;
            Rect that = (Rect) other;
            return x0 == that.x0 && y0 == that.y0 && x1 == that.x1 && y1 == that.y1;
        }

        @Override
        public int hashCode() {
            return ((x0 * 31 + y0) * 31 + x1) * 31 + y1;
        }

        @Override
        public String toString() {
            return "[" + x0 + "," + y0 + " to " + x1 + "," + y1 + "]";
        }
    }

    /** Every band and button of one screen size. A part the screen does not have is null. */
    public static final class Frame {

        public final int width;

        public final int height;

        public final boolean compact;

        public Rect header;

        /** The 1-pixel gold rule along the header's bottom edge. */
        public Rect headerRule;

        /** The wordmark, centred in the header, its own size. */
        public Rect wordmark;

        public Rect help;

        /**
         * The header's icon buttons, right to left: the toggle's slot first, then the Wear Table's, then Push's. A
         * button whose action is not offered is not drawn and the rest close up, so a screen takes the slots in order
         * for the buttons it has (spec M15).
         */
        public Rect[] headerSlots;

        public Rect tabs;

        /** Null on every tab but Surfaces. */
        public Rect subTabs;

        public Rect search;

        public Rect changedOnly;

        /** Null unless a value is refused. */
        public Rect banner;

        /** The list panel, its gold frame included. */
        public Rect panel;

        /** The rows' area: the panel 1 pixel in on every side. */
        public Rect rows;

        public Rect scrollbar;

        public Rect footer;

        public Rect done;

        public Rect cancel;

        public Rect undoAll;

        public Rect resetTab;

        Frame(int width, int height, boolean compact) {
            this.width = width;
            this.height = height;
            this.compact = compact;
        }

        /** The toggle's place: the first header slot. */
        public Rect toggle() {
            return headerSlots[0];
        }

        /** Every rectangle the frame holds, nulls left out, for a test to walk. */
        public Rect[] all() {
            Rect[] parts = { header, wordmark, help, headerSlots[0], headerSlots[1], headerSlots[2], tabs, subTabs,
                search, changedOnly, banner, panel, footer, done, cancel, undoAll, resetTab };
            int count = 0;
            for (Rect part : parts) {
                if (part != null) count++;
            }
            Rect[] out = new Rect[count];
            int at = 0;
            for (Rect part : parts) {
                if (part != null) out[at++] = part;
            }
            return out;
        }
    }

    /** Whether a screen this size is compact. */
    public static boolean compact(int width, int height) {
        return height < COMPACT_BELOW_HEIGHT || width < COMPACT_BELOW_WIDTH;
    }

    /** The list panel's width: {@code min(W - 16, 460)}. */
    public static int panelWidth(int width) {
        return Math.min(width - 16, PANEL_MAX);
    }

    /** The regular wordmark's size, and the compact one's. */
    public static final int WORDMARK_WIDTH = 80;

    public static final int WORDMARK_HEIGHT = 34;

    public static final int WORDMARK_SMALL_WIDTH = 48;

    public static final int WORDMARK_SMALL_HEIGHT = 20;

    /**
     * The screen laid out.
     *
     * @param surfaces whether the Surfaces tab is open, so its family icons take a band of their own
     * @param banner   whether a refused value has put up the banner
     */
    public static Frame of(int width, int height, boolean surfaces, boolean banner) {
        boolean compact = compact(width, height);
        Frame frame = new Frame(width, height, compact);

        int headerBottom = compact ? 24 : 40;
        frame.header = new Rect(0, 0, width, headerBottom);
        frame.headerRule = new Rect(0, headerBottom, width, headerBottom + 1);
        int markWidth = compact ? WORDMARK_SMALL_WIDTH : WORDMARK_WIDTH;
        int markHeight = compact ? WORDMARK_SMALL_HEIGHT : WORDMARK_HEIGHT;
        frame.wordmark = Rect.sized((width - markWidth) / 2, compact ? 2 : 3, markWidth, markHeight);
        frame.help = compact ? Rect.sized(4, 4, 16, 16) : Rect.sized(6, 12, 16, 16);

        int slot = compact ? 18 : 20;
        int slotRight = width - (compact ? 22 : 26);
        int slotY = compact ? 3 : 10;
        frame.headerSlots = new Rect[3];
        for (int i = 0; i < 3; i++) {
            frame.headerSlots[i] = Rect.sized(slotRight - i * (slot + 4), slotY, slot, slot);
        }

        int panelWidth = panelWidth(width);
        int panelLeft = (width - panelWidth) / 2;
        frame.tabs = compact ? new Rect(panelLeft, 26, panelLeft + panelWidth, 40)
            : new Rect(panelLeft, 42, panelLeft + panelWidth, 58);

        int searchTop;
        int searchBottom;
        if (surfaces) {
            frame.subTabs = compact ? new Rect(panelLeft, 42, panelLeft + panelWidth, 60)
                : new Rect(panelLeft, 60, panelLeft + panelWidth, 80);
            searchTop = compact ? 62 : 82;
            searchBottom = compact ? 76 : 98;
        } else {
            searchTop = compact ? 42 : 60;
            searchBottom = compact ? 56 : 76;
        }
        // The search row starts at the content column's left edge, the panel's, at every width (0.9.228; Xep, playing
        // at 1440p: it sat at the screen's left edge while the tabs, the family icons and the panel were a centred
        // column), and Changed only ends inside the column.
        int searchRight = Math.min(panelLeft + SEARCH_WIDTH, panelLeft + panelWidth - CHANGED_ONLY_WIDTH - 4);
        frame.search = new Rect(panelLeft, searchTop, searchRight, searchBottom);
        frame.changedOnly = new Rect(searchRight + 4, searchTop, searchRight + 4 + CHANGED_ONLY_WIDTH, searchBottom);

        int above = searchBottom;
        if (banner) {
            frame.banner = new Rect(panelLeft, searchBottom + 2, panelLeft + panelWidth, searchBottom + 14);
            above = frame.banner.y1;
        }
        int panelBottom = height - (compact ? 26 : 30);
        frame.panel = new Rect(panelLeft, above + 2, panelLeft + panelWidth, panelBottom);
        frame.rows = rowsArea(frame);
        frame.scrollbar = new Rect(frame.rows.x1 - SCROLLBAR, frame.rows.y0, frame.rows.x1, frame.rows.y1);

        int footerTop = height - (compact ? 24 : 26);
        frame.footer = new Rect(0, footerTop, width, footerTop + 20);
        int doneWidth = compact ? 100 : 120;
        int cancelWidth = compact ? 60 : 70;
        frame.done = Rect.sized(width - 8 - doneWidth, footerTop, doneWidth, 20);
        frame.cancel = Rect.sized(frame.done.x0 - 4 - cancelWidth, footerTop, cancelWidth, 20);
        if (compact) {
            frame.undoAll = Rect.sized(8, footerTop, 20, 20);
            frame.resetTab = Rect.sized(frame.undoAll.x1 + 4, footerTop, 20, 20);
        } else {
            frame.undoAll = Rect.sized(8, footerTop, 70, 20);
            frame.resetTab = Rect.sized(frame.undoAll.x1 + 4, footerTop, 80, 20);
        }
        return frame;
    }

    /** The rows' area: the panel 1 pixel in on every side, inside its gold frame. */
    public static Rect rowsArea(Frame frame) {
        return frame.panel.inset(1);
    }

    /** The rows' inner width {@code R}: the panel less its frame, the scrollbar and the scrollbar's gap. */
    public static int rowWidth(Frame frame) {
        return frame.panel.width() - 2 - SCROLLBAR - SCROLLBAR_GAP;
    }

    /** The parts of a setting row, each at its place from the row's left {@code L} and top (spec 1.2). */
    public static final class SettingRow {

        public Rect changedBar;

        public Rect title;

        public Rect summary;

        public Rect control;

        /** A slider row's two halves, and a color row's swatch and hex readout. */
        public Rect slider;

        public Rect sliderField;

        public Rect swatch;

        public Rect hexReadout;

        public Rect undo;

        public Rect reset;

        /** The line under the control while its field is focused or refused; the row grows by its height. */
        public Rect underLine;

        public int height;
    }

    /** A regular setting row, its top at 0. */
    public static SettingRow settingRow(int left, int r) {
        return settingRow(left, 0, r, false);
    }

    /** A setting row whose top is {@code top}, {@code r} wide from {@code left}, regular or compact. */
    public static SettingRow settingRow(int left, int top, int r, boolean compact) {
        SettingRow row = new SettingRow();
        row.height = compact ? SETTING_ROW_COMPACT : SETTING_ROW;
        row.changedBar = Rect.sized(left, top + 1, 2, row.height - 2);
        int controlLeft = left + r - 36 - CONTROL_WIDTH;
        row.control = Rect.sized(controlLeft, top + 5, CONTROL_WIDTH, CONTROL_HEIGHT);
        row.title = new Rect(left + 6, top + 5, controlLeft - 6, top + 14);
        row.summary = compact ? null : new Rect(left + 6, top + 17, controlLeft - 6, top + 24);
        row.slider = Rect.sized(controlLeft, top + 5, SLIDER_WIDTH, CONTROL_HEIGHT);
        row.sliderField = Rect.sized(controlLeft + SLIDER_WIDTH + 4, top + 5, SLIDER_FIELD, CONTROL_HEIGHT);
        row.swatch = Rect.sized(controlLeft, top + 5, SWATCH_WIDTH, CONTROL_HEIGHT);
        row.hexReadout = Rect.sized(controlLeft + SWATCH_WIDTH + 4, top + 5, HEX_READOUT, CONTROL_HEIGHT);
        row.undo = Rect.sized(left + r - 34, top + 8, 14, 14);
        row.reset = Rect.sized(left + r - 18, top + 8, 14, 14);
        row.underLine = Rect.sized(controlLeft, top + row.height, CONTROL_WIDTH, UNDER_LINE);
        return row;
    }

    /** The parts of a picture row (the spec-sheet row), at its place from the row's left and top. */
    public static final class PictureRow {

        public Rect drag;

        public Rect frame;

        public Rect picture;

        public Rect badge;

        public Rect name;

        public Rect editBox;

        /** The status column, right-aligned to {@code R - 22}. */
        public Rect status;

        /** Null unless the list has a number on each line. */
        public Rect number;

        public Rect remove;

        public Rect rule;

        public int height = PICTURE_ROW;
    }

    /**
     * A picture row.
     *
     * @param number      whether each line carries a number field (the mob list, the tamper grades' columns)
     * @param statusWidth how wide the status column's words or picture are this row
     */
    public static PictureRow pictureRow(int left, int top, int r, boolean number, int statusWidth) {
        PictureRow row = new PictureRow();
        row.drag = Rect.sized(left, top + 9, 6, 12);
        row.frame = Rect.sized(left + 8, top + 5, 20, 20);
        row.picture = Rect.sized(left + 10, top + 7, 16, 16);
        row.badge = Rect.sized(left + 22, top + 19, 8, 8);
        int statusRight = left + r - 22;
        row.status = new Rect(statusRight - statusWidth, top + 3, statusRight, top + 12);
        row.name = new Rect(left + 32, top + 3, row.status.x0 - 4, top + 12);
        int boxRight = statusRight - statusWidth;
        if (number) {
            row.number = Rect.sized(statusRight - 44, top + 14, 44, 12);
            boxRight = Math.min(boxRight, row.number.x0);
        }
        row.editBox = new Rect(left + 32, top + 14, boxRight - 4, top + 26);
        row.remove = Rect.sized(left + r - 18, top + 8, 14, 14);
        row.rule = Rect.sized(left, top + 29, r, 1);
        return row;
    }

    /** The parts of a picker row: a picture, a name and an id under it, and a status or second button at the right. */
    public static final class PickerRow {

        public Rect frame;

        public Rect picture;

        public Rect name;

        public Rect id;

        public Rect status;

        public int height = PICKER_ROW;
    }

    public static PickerRow pickerRow(int left, int top, int r, int statusWidth) {
        PickerRow row = new PickerRow();
        row.frame = Rect.sized(left + 4, top + 3, 20, 20);
        row.picture = Rect.sized(left + 6, top + 5, 16, 16);
        row.status = new Rect(left + r - 4 - statusWidth, top + 3, left + r - 4, top + 23);
        row.name = new Rect(left + 28, top + 3, row.status.x0 - 4, top + 12);
        row.id = new Rect(left + 28, top + 14, row.status.x0 - 4, top + 21);
        return row;
    }
}
