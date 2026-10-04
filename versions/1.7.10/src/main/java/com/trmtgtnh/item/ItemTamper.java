package com.trmtgtnh.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ErosionState;

/**
 * A hand tool for shaping worn ground: it wears a path in, mends one, and pins a square so that
 * neither happens to it.
 *
 * <p>
 * Only the two right-click gestures live here. Left-click has no item hook in 1.7.10 — the one
 * place it surfaces is {@code PlayerInteractEvent}, which carries no {@link ItemStack} — so
 * those two are wired in {@link TamperEvents}. Both halves call the same methods on
 * {@link TamperActions}, which is where the rules actually are.
 *
 * <p>
 * Plain {@link Item} rather than {@code ItemTool} on purpose. Backhand and Battlegear both work
 * out which hand a click belongs to from the item's class — Backhand's test is literally
 * {@code ItemTool}, {@code ItemSword}, {@code ItemHoe} and TConstruct's harvest tool — so a tool
 * that is not a tool by that test is never routed anywhere this does not expect.
 */
public class ItemTamper extends Item implements TamperTool {

    public ItemTamper() {
        setMaxStackSize(1);
        // Non-zero, because Item.isDamageable asks the item rather than the stack and the packet
        // writer consults it to decide whether a stack's own data is sent to the client at all.
        // The per-stack maximum a grade sets is what actually applies.
        setMaxDamage(1024);
        setCreativeTab(CreativeTabs.tabTools);
        // Held at an angle in third person, the way every other long-handled tool is. Purely how
        // it looks in somebody else's hand, and it costs one line.
        setFull3D();
    }

    // ------------------------------------------------------------------
    // What differs between one tamper and another
    // ------------------------------------------------------------------

    /**
     * Enchantable, and it is worth saying why that needs stating at all.
     *
     * <p>
     * A plain {@link Item} answers zero here, which is exactly what stops an enchanting table
     * offering anything for one. With a number on it Unbreaking needs no further help: its type
     * is {@code breakable}, which asks {@code isDamageable} rather than the item's class, and a
     * tamper is damageable. Anything a mod supplies - a repair enchantment, an experience
     * booster - then applies on whatever terms that mod set, which is the right way round for a
     * mod that depends on none of them.
     */
    @Override
    public int getItemEnchantability() {
        return 14;
    }

    /** How far a right-click patch mend reaches from this stack, in blocks. */
    public int reachOf(ItemStack stack) {
        return 1;
    }

    /** The ore name an anvil mends this stack with. */
    public String repairOre(ItemStack stack) {
        return "ingotIron";
    }

    // ------------------------------------------------------------------
    // Gestures
    // ------------------------------------------------------------------

    /**
     * Both right-click gestures, and both sides.
     *
     * <p>
     * Vanilla has already decided this click is ours by the time it arrives: a block with
     * something to open consumed it at {@code onBlockActivated}, and sneaking skipped that step
     * for every held item rather than just this one. So there is no input to steal here, only
     * the duty to hand back anything that turns out not to be worn ground — which is what the
     * false returns are for.
     *
     * <p>
     * The client half does nothing but answer "yes, that was mine", and it can answer without
     * asking the server, because it is already painting the squares in question. Saying yes
     * swings the arm and stops the client following the click with a second use-on-air packet.
     * If the server disagrees it sends the stack back and the two are level again.
     */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) return Trmt.proxy.erosionStateAt(x, y, z) != ErosionState.NONE;
        if (player.isSneaking()) return TamperActions.pin(world, x, y, z, player, stack);
        return TamperActions.mendPatch(world, x, y, z, player, stack, reachOf(stack));
    }

    /**
     * Left-click wears a square in; sneak and left-click mends one back.
     *
     * <p>
     * The gentler of the two on the modifier, which is the way round every other tool in the
     * game has it, and the same pairing the chunk tamper uses over an area.
     */
    @Override
    public boolean leftClick(World world, int x, int y, int z, EntityPlayer player, ItemStack stack, boolean sneaking) {
        if (sneaking) return TamperActions.mendOne(world, x, y, z, player, stack);
        return TamperActions.wearOne(world, x, y, z, player, stack);
    }

    /**
     * Breaks nothing, ever.
     *
     * <p>
     * Left-click is a gesture this tool answers rather than a dig, and the answer is given
     * server side from an event that fires long before this. Refusing here is what stops the
     * client removing the block from its own copy of the world in the meantime — in creative,
     * where one click destroys immediately and locally, it is the only thing that stops it.
     */
    @Override
    public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player) {
        return true;
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        tooltip = Tooltips.wrapping(tooltip);
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("trmtgtnh.tamper.desc"));
        TamperTooltip.plainCost(tooltip);
        TamperTooltip.xp(tooltip, TrmtConfig.tamperMendCost > 0 || TrmtConfig.tamperPatchCost > 0);
        if (TamperTooltip.expanded()) {
            tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("trmtgtnh.tamper.tip.left"));
            tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("trmtgtnh.tamper.tip.sneakLeft"));
            tooltip.add(
                EnumChatFormatting.GRAY + StatCollector
                    .translateToLocalFormatted("trmtgtnh.tamper.tip.right", Integer.valueOf(reachOf(stack))));
            tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("trmtgtnh.tamper.tip.sneakRight"));
        } else {
            TamperTooltip.shiftHint(tooltip);
        }
    }

    /**
     * Anvil repair, in the same material the tool was made of.
     *
     * <p>
     * By ore name, so an anvil takes whichever mod's iron the player happens to be holding — and
     * asked of the stack rather than of the name, because looking an ore id up by name registers
     * it, and a repair check has no business creating ore entries as a side effect.
     */
    @Override
    public boolean getIsRepairable(ItemStack tool, ItemStack material) {
        if (material == null) return false;
        String want = repairOre(tool);
        int[] ids = OreDictionary.getOreIDs(material);
        for (int i = 0; i < ids.length; i++) {
            if (want.equals(OreDictionary.getOreName(ids[i]))) return true;
        }
        return false;
    }
}
