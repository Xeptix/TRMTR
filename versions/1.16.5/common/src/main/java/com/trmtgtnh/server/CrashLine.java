package com.trmtgtnh.server;

import net.minecraft.CrashReportCategory;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.util.Support;

/**
 * The line every crash report carries under this mod's name, saying where to take it (0.9.221; Xep, 2026-10-08) -
 * what FML's {@code ICrashCallable} does in the older editions. Here it is one common mixin on vanilla's crash
 * report ({@code MixinCrashReportSupport}, the same in each loader module); the mixin only calls this, because a mixin
 * body is read by an ASM older than this workspace's JDK.
 */
public final class CrashLine {

    private CrashLine() {}

    /** Adds the line to a crash report's system details. Never throws: a crash report must always be written. */
    public static void into(CrashReportCategory details) {
        try {
            details.setDetail(Support.CRASH_LABEL, Support.crashDetail(Trmt.version()));
        } catch (RuntimeException ignored) {
            // A crash report written without this line is better than none.
        }
    }
}
