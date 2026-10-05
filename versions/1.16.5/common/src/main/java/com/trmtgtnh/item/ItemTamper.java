package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import com.trmtgtnh.util.OreNames;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Client;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.config.TrmtConfig;

/**
 * A hand tool for shaping worn ground: it wears a path in, mends one, and pins a square so that
 * neither happens to it.
 *
 * <p>
 * Only the two right-click gestures live here. Left-click has no item hook at all - the one place it
 * surfaces is {@code PlayerInteractEvent} - so those two are wired in {@link TamperEvents}. Both
 * halves call the same methods on {@link TamperActions}, which is where the rules actually are.
 *
 * <p>
 * Plain {@link Item} rather than {@code ItemTool} on purpose. Backhand and Battlegear both work
 * out which hand a click belongs to from the item's class - Backhand's test is literally
 * {@code ItemTool}, {@code ItemSword}, {@code ItemHoe} and TConstruct's harvest tool - so a tool
 * that is not a tool by that test is never routed anywhere this does not expect. 1.12.2 gives every
 * player a second hand of the game's own, which changes nothing here: the stack is a parameter of
 * every hook below, so whichever hand it came out of is the one that acts.
 */
public class ItemTamper extends Item implements TamperTool {

    public ItemTamper() {
        this(new Item.Properties());
    }

    /**
     * Everything an item is now settled when it is built, not set on it afterwards.
     *
     * <p>
     * Both older editions call four setters in the constructor body; 1.13 moved all of that into a
     * properties object, which is also how a subclass asks for something different without a second
     * constructor of its own. The durability is still non-zero for the reason it always was: whether
     * a stack's own data reaches the client is decided by asking the item, not the stack, and the
     * per-stack maximum a grade sets is what actually applies.
     *
     * <p>
     * Held at an angle in third person, like every other long-handled tool, comes from the model -
     * whose parent is item/handheld - exactly as it does on 1.12.2. The 1.7.10 edition says it in
     * code with setFull3D, which has been gone for two versions.
     */
    protected ItemTamper(Item.Properties properties) {
        super(properties.stacksTo(1)
            .durability(1024)
            .tab(CreativeModeTab.TAB_TOOLS));
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
    public int getEnchantmentValue() {
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
     * the duty to hand back anything that turns out not to be worn ground - which is what
     * {@link InteractionResult#PASS} is for.
     *
     * <p>
     * The client half does nothing but answer "yes, that was mine", and it can answer without
     * asking the server, because it is already painting the squares in question. Saying yes
     * swings the arm and stops the client following the click with a second use-on-air packet.
     * If the server disagrees it sends the stack back and the two are level again.
     *
     * <p>
     * The client asks the proxy for the record as the ghost itself would - through the same call
     * the block makes, so a square held flat by a plant or filled in by something built on top is
     * read here exactly as it is drawn. The other edition asks its cache directly, because there
     * the painter has already baked both of those into which ghost it wrote.
     */
    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        if (player == null || world == null) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(context.getHand());
        BlockPos pos = context.getClickedPos();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (world.isClientSide()) {
            return BlockGhost.shows(Client.ghostRecordAt(world, x, y, z)) ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
        }
        boolean acted = player.isShiftKeyDown() ? TamperActions.pin(world, x, y, z, player, stack)
            : TamperActions.mendPatch(world, x, y, z, player, stack, reachOf(stack));
        return acted ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Left-click wears a square in; sneak and left-click mends one back.
     *
     * <p>
     * The gentler of the two on the modifier, which is the way round every other tool in the
     * game has it, and the same pairing the chunk tamper uses over an area.
     */
    @Override
    public boolean leftClick(Level world, int x, int y, int z, Player player, ItemStack stack, boolean sneaking) {
        if (sneaking) return TamperActions.mendOne(world, x, y, z, player, stack);
        return TamperActions.wearOne(world, x, y, z, player, stack);
    }

    /**
     * Breaks nothing, ever.
     *
     * <p>
     * Left-click is a gesture this tool answers rather than a dig, and the answer is given
     * server side from an event that fires long before this. Refusing here is what stops the
     * client removing the block from its own copy of the world in the meantime - in creative,
     * where one click destroys immediately and locally, it is the only thing that stops it.
     */
    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    /**
     * Client-side, and marked so.
     *
     * <p>
     * The other edition leaves the annotation off and is right to: there the whole signature is made
     * of things a server has. Here it names {@code TooltipFlag}, which lives in the client package,
     * so without this the side scan would report a client-only class reached from shared code - and
     * would be telling the truth about a method a server never calls. Vanilla's own is annotated
     * exactly this way.
     */
    @Override
    public void appendHoverText(ItemStack stack, Level world,
        List<net.minecraft.network.chat.Component> lines, TooltipFlag advanced) {
        // Named before anything is written, so every line below - and every line anybody adds later
        // - stops at the edge of a column rather than the screen. A list of strings over the list of
        // components the game hands over, which is what keeps these calls the same on all three
        // editions; see Tooltips.
        List<String> tooltip = Tooltips.lines(lines);
        tooltip.add(ChatFormatting.GOLD + com.trmtgtnh.util.Translate.get("trmtgtnh.tamper.desc"));
        TamperTooltip.plainCost(tooltip);
        TamperTooltip.xp(tooltip, TrmtConfig.tamperMendCost > 0 || TrmtConfig.tamperPatchCost > 0);
        if (TamperTooltip.expanded()) {
            tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.tamper.tip.left"));
            tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.tamper.tip.sneakLeft"));
            tooltip.add(
                ChatFormatting.GRAY
                    + com.trmtgtnh.util.Translate.get("trmtgtnh.tamper.tip.right", Integer.valueOf(reachOf(stack))));
            tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.tamper.tip.sneakRight"));
        } else {
            TamperTooltip.shiftHint(tooltip);
        }
    }

    /**
     * Anvil repair, in the same material the tool was made of.
     *
     * <p>
     * By ore name, so an anvil takes whichever mod's iron the player happens to be holding - and
     * asked of the stack rather than of the name, because looking an ore id up by name registers
     * it, and a repair check has no business creating ore entries as a side effect.
     */
    /**
     * Whether an anvil will mend this with that.
     *
     * <p>
     * Vanilla's own question at this version, where both older editions override Forge's. The answer
     * is the same one: whatever the tool's repair name covers, which is a tag here and an ore
     * dictionary group there - see OreNames, which keeps the settings spelling the same on all three.
     */
    @Override
    public boolean isValidRepairItem(ItemStack tool, ItemStack material) {
        if (material == null || material.isEmpty()) return false;
        return OreNames.matches(material, repairOre(tool));
    }
}
