package com.trmtgtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

import com.trmtgtnh.client.gui.SavingIndicator;

/**
 * The game's "Saving world" is left out while a screen of this mod's that pauses the game is open (1.0.0): see
 * {@link SavingIndicator}. From 26.2 the words are drawn by {@code Hud.extractSavingIndicator}, which Gui calls after
 * the open screen is drawn; asked first, it draws nothing over such a screen and everything as the game has it
 * otherwise. The save, and the words over play and every other screen, are the game's.
 */
@Mixin(Hud.class)
public abstract class MixinSavingIndicator implements SavingIndicator.Lit {

    @Shadow
    private float autosaveIndicatorValue;

    @Shadow
    private float lastAutosaveIndicatorValue;

    @Inject(method = "extractSavingIndicator", at = @At("HEAD"), cancellable = true)
    private void trmt$leftOutOverOurScreens(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (SavingIndicator.hiddenOver(Minecraft.getInstance().gui.screen())) ci.cancel();
    }

    @Override
    public void trmt$lightSavingIndicator() {
        autosaveIndicatorValue = 1F;
        lastAutosaveIndicatorValue = 1F;
    }
}
