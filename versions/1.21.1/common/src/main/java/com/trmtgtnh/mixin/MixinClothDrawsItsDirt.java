package com.trmtgtnh.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cloth Config's settings screen, the one this mod builds, draws its dirt (1.0.0).
 *
 * <p>
 * Built solid, Cloth 15.0.140 asks for {@code renderMenuBackground} where Cloth 11 drew its dirt at 1.20.1, and then
 * lays its dirt's vertices for the header, the list and the tab row into the game's tesselator and never draws them:
 * nothing in its jar calls {@code BufferUploader.drawWithShader} (read in 15.0.140's jar, every class). So the solid
 * screen showed the world under the game's quarter-dark menu texture alone, as the transparent one did. Here, at that
 * call and for that screen alone ({@link com.trmtgtnh.client.gui.ConfigScreens#drawClothBackground}), the dirt Cloth
 * meant is drawn instead, at Cloth 11's shades, and the quarter-dark texture is not drawn over it. Every other screen
 * goes on to the game's own.
 */
@Mixin(Screen.class)
public abstract class MixinClothDrawsItsDirt {

    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void trmt$clothDrawsItsDirt(GuiGraphics graphics, CallbackInfo info) {
        if (com.trmtgtnh.client.gui.ConfigScreens.drawClothBackground((Screen) (Object) this, graphics)) info.cancel();
    }
}
