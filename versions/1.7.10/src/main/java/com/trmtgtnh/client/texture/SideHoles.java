package com.trmtgtnh.client.texture;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Whether a flank's picture has any pixel less than opaque, asked of each picture once a stitch (1.0.1 W2b; Codex's
 * review of W2, 2026-11-21).
 *
 * <p>
 * The mend asks it of every grass state's flanks, and every state of a block, every metadata value and every flank
 * that draws one picture would read and scan the whole of it again: at a high-resolution pack's 512 pixels a side,
 * that is a quarter of a million pixels a time. So one of these is made for each walk of the states, and each picture,
 * by its name, is read and scanned the first time it is asked about and answered from what was found after that. A
 * walk is once a stitch, so a pack switch, which can change any picture, starts afresh.
 *
 * <p>
 * The same class in all eight trees.
 */
public final class SideHoles implements FaceMends.Holes {

    /** Reads a picture by its name, or null where it cannot be read. */
    public interface Reader {

        BufferedImage read(String name);
    }

    private final Reader reader;

    private final Map<String, Boolean> found = new HashMap<String, Boolean>();

    private int scans;

    public SideHoles(Reader reader) {
        this.reader = reader;
    }

    @Override
    public boolean holed(String name) {
        if (name == null) return false;
        Boolean known = found.get(name);
        if (known != null) return known.booleanValue();
        scans++;
        boolean holed = hasHoles(reader.read(name));
        found.put(name, Boolean.valueOf(holed));
        return holed;
    }

    /** How many pictures were read and scanned: one per name asked about. */
    public int scans() {
        return scans;
    }

    /**
     * Whether any pixel of a texture's first frame is less than opaque.
     *
     * <p>
     * The first frame only: an animated texture is a column of frames, and the frames below the first are not what a
     * still side draws. Square is assumed, as everything in this pipeline assumes, so the frame is the width.
     */
    public static boolean hasHoles(BufferedImage image) {
        if (image == null) return false;
        int width = image.getWidth();
        int height = Math.min(width, image.getHeight());
        if (width <= 0 || height <= 0) return false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) < 255) return true;
            }
        }
        return false;
    }
}
