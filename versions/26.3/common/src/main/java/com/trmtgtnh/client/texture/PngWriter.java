package com.trmtgtnh.client.texture;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * ARGB pixels to the bytes of a PNG, with one compressor a thread for as long as the thread lives (1.0.1, W3: the
 * freeze).
 *
 * <p>
 * <strong>Why not ImageIO, which this used until 1.0.1.</strong> Its PNG writer makes a new {@link Deflater} for every
 * picture and ends it after, and a Deflater is zlib's own state, a quarter of a megabyte allocated from the process's
 * native heap and freed again. A stitch encodes ninety to a hundred and ten thousand pictures in a large pack, and a
 * game that has run for a while has a native heap holding tens of thousands of blocks of every size: measured on
 * 2026-11-21 (the W3 section of 1.0.1-found), forty thousand sixteen-pixel pictures on eight threads took 614 ms
 * through ImageIO in a fresh JVM and 38,674 ms once 20,000 mixed native blocks were held, while a compressor kept per
 * thread took 250 ms and 310 ms. A dev run's flight recording showed the same: zlib's {@code init} and {@code end}
 * a sixth of the pool's samples at startup and more than half after two pack switches. That is the whole of why the
 * same pictures took 24 s at startup on the rig's Prominence copy and 141 to 254 s after a pack switch, in 0.9.228 and
 * 1.0.0 alike.
 *
 * <p>
 * The picture is the same: eight bits a channel with alpha (color type 6), not interlaced, every row unfiltered, one
 * IDAT, so any reader decodes exactly the pixels it was given; {@code PngWriterTest} holds that against ImageIO's own
 * reader and against what ImageIO's writer wrote. Fastest compression, because the pictures are small and are read once.
 * Portable code: it names nothing from the game.
 */
final class PngWriter {

    private PngWriter() {}

    private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

    /** How many compressors have been made, for the test that holds this to one a thread. */
    private static final AtomicInteger MADE = new AtomicInteger();

    private static final ThreadLocal<Deflater> COMPRESSOR = new ThreadLocal<Deflater>() {

        @Override
        protected Deflater initialValue() {
            MADE.incrementAndGet();
            return new Deflater(Deflater.BEST_SPEED);
        }
    };

    static int compressorsMade() {
        return MADE.get();
    }

    /**
     * The PNG of {@code width * height} ARGB pixels, row by row.
     *
     * @throws IllegalArgumentException where the sizes do not match the pixels
     */
    static byte[] encode(int[] argb, int width, int height) {
        if (argb == null || width <= 0 || height <= 0 || (long) width * height != argb.length) {
            throw new IllegalArgumentException("no picture of " + width + " by " + height + " in "
                + (argb == null ? "nothing" : argb.length + " pixels"));
        }
        int stride = 1 + 4 * width;
        byte[] raw = new byte[stride * height];
        int at = 0;
        int pixel = 0;
        for (int y = 0; y < height; y++) {
            raw[at++] = 0; // the row's filter: none
            for (int x = 0; x < width; x++) {
                int p = argb[pixel++];
                raw[at++] = (byte) (p >> 16);
                raw[at++] = (byte) (p >> 8);
                raw[at++] = (byte) p;
                raw[at++] = (byte) (p >>> 24);
            }
        }
        byte[] packed = compress(raw);

        ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length + 64);
        out.write(SIGNATURE, 0, SIGNATURE.length);
        byte[] header = new byte[13];
        putInt(header, 0, width);
        putInt(header, 4, height);
        header[8] = 8; // bits a channel
        header[9] = 6; // red, green, blue and alpha
        header[10] = 0; // deflate
        header[11] = 0; // adaptive filtering, each row naming its own
        header[12] = 0; // not interlaced
        chunk(out, "IHDR", header, header.length);
        chunk(out, "IDAT", packed, packed.length);
        chunk(out, "IEND", new byte[0], 0);
        return out.toByteArray();
    }

    /** One zlib stream of the rows, through this thread's own compressor, reset rather than made again. */
    private static byte[] compress(byte[] raw) {
        Deflater compressor = COMPRESSOR.get();
        compressor.reset();
        compressor.setInput(raw);
        compressor.finish();
        byte[] out = new byte[Math.max(64, raw.length / 2 + 64)];
        int length = 0;
        while (!compressor.finished()) {
            if (length == out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
            length += compressor.deflate(out, length, out.length - length);
        }
        // The input is let go of here, so a thread's compressor never holds a picture between pictures.
        compressor.reset();
        return java.util.Arrays.copyOf(out, length);
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data, int length) {
        byte[] head = new byte[8];
        putInt(head, 0, length);
        for (int i = 0; i < 4; i++) head[4 + i] = (byte) type.charAt(i);
        out.write(head, 0, 8);
        out.write(data, 0, length);
        CRC32 crc = new CRC32();
        crc.update(head, 4, 4);
        crc.update(data, 0, length);
        byte[] tail = new byte[4];
        putInt(tail, 0, (int) crc.getValue());
        out.write(tail, 0, 4);
    }

    private static void putInt(byte[] into, int at, int value) {
        into[at] = (byte) (value >>> 24);
        into[at + 1] = (byte) (value >>> 16);
        into[at + 2] = (byte) (value >>> 8);
        into[at + 3] = (byte) value;
    }
}
