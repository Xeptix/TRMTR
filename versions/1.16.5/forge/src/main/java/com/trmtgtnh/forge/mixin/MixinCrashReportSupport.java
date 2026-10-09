package com.trmtgtnh.forge.mixin;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.server.CrashLine;

/**
 * Every crash report says where to take a crash that names this mod (0.9.221): one line in its system details,
 * as {@code ICrashCallable} adds in 1.7.10 and 1.12.2. Vanilla's own report, so the same mixin in each loader module -
 * common's refmap cannot serve a Forge development run, and Forge's {@code CrashReportExtender} would cover
 * only one loader.
 */
@Mixin(CrashReport.class)
public abstract class MixinCrashReportSupport {

    @Shadow
    @Final
    private CrashReportCategory systemDetails;

    @Inject(method = "initDetails", at = @At("RETURN"))
    private void trmt$supportLine(CallbackInfo info) {
        CrashLine.into(systemDetails);
    }
}
