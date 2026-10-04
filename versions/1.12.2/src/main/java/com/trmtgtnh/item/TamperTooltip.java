package com.trmtgtnh.item;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

import com.trmtgtnh.config.TrmtConfig;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The lines every tamper's tooltip shares, in one place so the three of them cannot drift apart.
 *
 * <p>
 * Client-only, because {@code Item.addInformation} is - it is annotated {@code SideOnly(CLIENT)}
 * on the base, so a dedicated server never loads a caller of any of this and the {@link GuiScreen}
 * reference below is never touched off a client.
 */
@SideOnly(Side.CLIENT)
final class TamperTooltip {

    private TamperTooltip() {}

    static String t(String key) {
        return I18n.translateToLocal(key);
    }

    /**
     * Whether the tooltip is showing its controls, which it does while Shift is held.
     *
     * <p>
     * The routine reading - what the tool is, its area, what a use costs - is always on show; the
     * four or five action lines are the part that turns a two-line tooltip into a wall, so they
     * hide behind Shift the way a lot of tools in this pack already do.
     */
    static boolean expanded() {
        return GuiScreen.isShiftKeyDown();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static void shiftHint(List tooltip) {
        tooltip.add(TextFormatting.DARK_GRAY + t("trmtgtnh.tip.shift"));
    }

    /**
     * What mending earns, when it earns anything.
     *
     * <p>
     * Left off a tool whose mending costs nothing, because only mending that was paid for grants
     * experience - see {@link com.trmtgtnh.server.HealingXp}, which draws the same line. Read from this
     * client's own config, as every figure in these tooltips is: a server's prices are not sent, so on
     * somebody else's server this says what the holder's own file would charge. The caller says
     * whether its mending costs anything, because
     * each tamper is priced by a different setting and this cannot tell which one it is quoting.
     *
     * @param tooltip the lines being built
     * @param earns   whether this tool's mending costs material under the holder's own config
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static void xp(List tooltip, boolean earns) {
        if (earns && TrmtConfig.xpFromHealing && TrmtConfig.xpPerGradation > 0f) {
            tooltip.add(TextFormatting.DARK_GRAY + t("trmtgtnh.tamper.xp"));
        }
    }

    /**
     * What the hand tamper's two mends cost, in blocks of what the ground drops.
     *
     * <p>
     * Read from the client's own config rather than the server's, which is close enough for a
     * line that says "a couple of blocks" and would be wrong to quote to the block on a server
     * that had tuned it - so it quotes the numbers the holder can see and no more.
     *
     * <p>
     * A free patch has a line of its own, because the patch price is quoted per kind of ground and
     * "free for each kind of ground a patch mends" says nothing a reader could use.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static void plainCost(List tooltip) {
        int mend = TrmtConfig.tamperMendCost;
        int patch = TrmtConfig.tamperPatchCost;
        if (mend <= 0 && patch <= 0) {
            tooltip.add(TextFormatting.GRAY + t("trmtgtnh.tamper.cost.free"));
            return;
        }
        if (patch <= 0) {
            tooltip.add(
                TextFormatting.GRAY
                    + I18n.translateToLocalFormatted("trmtgtnh.tamper.cost.rangeFreePatch", count(mend)));
            return;
        }
        tooltip.add(
            TextFormatting.GRAY
                + I18n.translateToLocalFormatted("trmtgtnh.tamper.cost.range", count(mend), count(patch)));
    }

    /**
     * The exchange rate an area mend pays at: one block for so many gradations.
     *
     * <p>
     * The chunk tamper is paid by bone meal's rule and switched by bone meal's setting, so with
     * bonemealCostsABlock off this says free rather than quoting a rate nothing is charging.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    static void areaCost(List tooltip) {
        if (!TrmtConfig.bonemealCostsABlock) {
            tooltip.add(TextFormatting.GRAY + t("trmtgtnh.tamper.cost.free"));
            return;
        }
        int per = Math.max(1, TrmtConfig.chunkTamperGradationsPerBlock);
        tooltip.add(
            TextFormatting.GRAY
                + I18n.translateToLocalFormatted("trmtgtnh.chunktamper.cost.rate", Integer.valueOf(per)));
    }

    private static String count(int n) {
        return n <= 0 ? t("trmtgtnh.cost.free.word") : String.valueOf(n);
    }
}
