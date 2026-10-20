package com.trmtgtnh.client.texture;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;

/**
 * A texture's animation, as the wear pictures read it: its frames in order with their times, the frame size its
 * metadata declares, its default frame time and whether it interpolates.
 *
 * <p>
 * 1.16.5 read all of that straight off {@code AnimationMetadataSection}. From 1.19.3 the section keeps its frames to
 * itself and hands them out one at a time ({@code forEachFrame}), and answers the frame size only for an image it is
 * given ({@code calculateFrameSize}): what the metadata declared, or the image's own size where it declared nothing.
 * This asks it once, the way the wear pictures need it, and answers the questions they ask in 1.16.5's words - so
 * InnerLayers, WearPatterns and WearSprite read as they do there.
 */
public final class AnimationFrames {

    private final int[] index;
    private final int[] time;
    private final int frameWidth;
    private final int frameHeight;
    private final int defaultFrameTime;
    private final boolean interpolated;

    private AnimationFrames(int[] index, int[] time, int frameWidth, int frameHeight, int defaultFrameTime,
        boolean interpolated) {
        this.index = index;
        this.time = time;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.defaultFrameTime = defaultFrameTime;
        this.interpolated = interpolated;
    }

    /** A texture's own animation, read off its metadata section. */
    public static AnimationFrames of(AnimationMetadataSection section) {
        final List<int[]> frames = new ArrayList<int[]>();
        // Each frame with its time already resolved: the section hands out the default where a frame names none.
        // By 26.1 the section is a record with no forEachFrame: its frames are an optional list, absent where the file
        // lists none - as 1.21.1's empty list was - and each frame resolves its own time against the default.
        if (section.frames()
            .isPresent()) {
            for (net.minecraft.client.resources.metadata.animation.AnimationFrame frame : section.frames()
                .get()) {
                frames.add(new int[] { frame.index(), frame.timeOr(section.defaultFrameTime()) });
            }
        }
        int[] index = new int[frames.size()];
        int[] time = new int[frames.size()];
        for (int i = 0; i < index.length; i++) {
            index[i] = frames.get(i)[0];
            time[i] = frames.get(i)[1];
        }
        // What the metadata declared, read by asking about an image of impossible size: the section answers its
        // declared width or height where it has one and falls back on the image's otherwise, so a negative answer
        // is "not declared" - which 1.16.5's getFrameWidth(-1) and getFrameHeight(-1) said as -1.
        FrameSize declared = section.calculateFrameSize(UNSET_WIDTH, UNSET_HEIGHT);
        int width = declared.width() < 0 ? -1 : declared.width();
        int height = declared.height() < 0 ? -1 : declared.height();
        // getDefaultFrameTime and isInterpolatedFrames are the record's defaultFrameTime and interpolatedFrames by 26.1.
        return new AnimationFrames(index, time, width, height, section.defaultFrameTime(), section.interpolatedFrames());
    }

    /** An animation of `count` frames in order, each at the default time - for a texture whose metadata lists none. */
    public static AnimationFrames listed(int count, int frameWidth, int frameHeight, int defaultFrameTime,
        boolean interpolated) {
        int[] index = new int[count];
        int[] time = new int[count];
        for (int i = 0; i < count; i++) {
            index[i] = i;
            time[i] = defaultFrameTime;
        }
        return new AnimationFrames(index, time, frameWidth, frameHeight, defaultFrameTime, interpolated);
    }

    private static final int UNSET_WIDTH = -7;
    private static final int UNSET_HEIGHT = -9;

    public int getFrameCount() {
        return index.length;
    }

    public int getFrameIndex(int at) {
        return index[at];
    }

    public int getFrameTime(int at) {
        return time[at];
    }

    /** The frame width the metadata declared, or `unset` where it declared none. */
    public int getFrameWidth(int unset) {
        return frameWidth < 0 ? unset : frameWidth;
    }

    /** The frame height the metadata declared, or `unset` where it declared none. */
    public int getFrameHeight(int unset) {
        return frameHeight < 0 ? unset : frameHeight;
    }

    public int getDefaultFrameTime() {
        return defaultFrameTime;
    }

    public boolean isInterpolatedFrames() {
        return interpolated;
    }
}
