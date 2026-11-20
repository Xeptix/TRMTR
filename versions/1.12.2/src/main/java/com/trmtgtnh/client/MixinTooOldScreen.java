package com.trmtgtnh.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraftforge.fml.client.CustomModLoadingErrorDisplayException;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Forge's error screen, saying why this mod stopped a game whose Mixin is too old (G17, Codex's review of R5,
 * 2026-11-11): the lines {@code com.trmtgtnh.core.MixinTooOld} is given, centred, as Forge's missing-mod screen sets out
 * its own. Forge shows it in place of the title screen, and the game can only be closed from it.
 */
@SideOnly(Side.CLIENT)
public final class MixinTooOldScreen extends CustomModLoadingErrorDisplayException {

    private static final long serialVersionUID = 1L;

    private final List<String> lines;

    public MixinTooOldScreen(String message, List<String> lines) {
        super(message, null);
        this.lines = new ArrayList<String>(lines);
    }

    @Override
    public void initGui(GuiErrorScreen errorScreen, FontRenderer fontRenderer) {}

    @Override
    public void drawScreen(GuiErrorScreen errorScreen, FontRenderer fontRenderer, int mouseRelX, int mouseRelY,
        float tickTime) {
        int y = errorScreen.height / 2 - lines.size() * 6;
        for (int at = 0; at < lines.size(); at++) {
            errorScreen.drawCenteredString(fontRenderer, lines.get(at), errorScreen.width / 2, y, at == 0 ? 0xFFFFFF : 0xEEEEEE);
            y += at == 0 ? 18 : 12;
        }
    }
}
