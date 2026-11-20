package com.trmtgtnh.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

import com.trmtgtnh.client.gui.SavingIndicator;

/**
 * The game's "Saving world" is left out while a screen of this mod's that pauses the game is open (1.0.0): see
 * {@link SavingIndicator}. At 1.21.1 the words are drawn by {@code Gui.renderSavingIndicator}, which GameRenderer calls
 * after the open screen is drawn, on every loader; asked first, it draws nothing over such a screen and everything as
 * the game has it otherwise. The save, and the words over play and every other screen, are the game's.
 */
@Mixin(Gui.class)
public abstract class MixinSavingIndicator implements SavingIndicator.Lit {

    @Shadow
    private float autosaveIndicatorValue;

    @Shadow
    private float lastAutosaveIndicatorValue;

    @Inject(method = "renderSavingIndicator", at = @At("HEAD"), cancellable = true)
    private void trmt$leftOutOverOurScreens(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (SavingIndicator.hiddenOver(Minecraft.getInstance().screen)) ci.cancel();
    }

    @Override
    public void trmt$lightSavingIndicator() {
        autosaveIndicatorValue = 1F;
        lastAutosaveIndicatorValue = 1F;
    }
}
