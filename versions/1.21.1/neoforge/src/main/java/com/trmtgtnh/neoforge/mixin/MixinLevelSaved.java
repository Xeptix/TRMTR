package com.trmtgtnh.neoforge.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.trmtgtnh.server.ServerEvents;

/**
 * A level has saved (wear backups, 0.9.229): the overworld's save is this mod's last, its clock written down, and the
 * wear mirror goes to disk, as 1.7.10's WorldEvent.Save handler does. At the return, once the level and its chunks are
 * written; not when saving is off ({@code save-off}), when nothing was. The same mixin is in each loader's module, so every
 * loader hears the same save at the same point.
 */
@Mixin(ServerLevel.class)
public abstract class MixinLevelSaved {

    @Inject(method = "save", at = @At("RETURN"))
    private void trmt$saved(ProgressListener progress, boolean flush, boolean skip, CallbackInfo callback) {
        if (!skip) ServerEvents.levelSaved((ServerLevel) (Object) this);
    }
}
