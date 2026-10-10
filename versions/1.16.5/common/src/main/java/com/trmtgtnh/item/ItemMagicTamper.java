package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.NonNullList;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Client;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.config.TrmtConfig;

/**
 * The end of the tamper line: no material, no wear, and a reach the others cannot buy.
 *
 * <p>
 * A subclass rather than a third implementation, because every gesture it has is a gesture the
 * chunk tamper already performs and a copy of them would be a copy to keep in step. What differs
 * is two answers - it costs nothing and it never wears - and both are questions the parent asks
 * rather than assumptions the parent makes.
 *
 * <p>
 * It never takes damage rather than declaring itself unbreakable. Declaring a maximum of zero
 * would have been the obvious way and is a trap: {@code Item.isDamageable} asks the item, and the
 * packet writer consults it to decide whether a stack's own data is sent to the client at all -
 * so an item claiming no durability would arrive with its settings stripped and no clue why.
 */
public class ItemMagicTamper extends ItemChunkTamper {

    public ItemMagicTamper() {
        // The tab and the stack size are the base's, which settles both when it is built. Glints the
        // way an enchanted thing does, without an enchantment on it: the tool is the end of a ladder
        // and should read as one on the hotbar before its tooltip is opened.
        super();
    }

    /**
     * One picture, not a set of them.
     *
     * <p>
     * The grade was always a statement about material, and this tool is past caring about
     * material - so there is nothing for a per-material picture to say. It keeps the animated
     * arcane rammer, which is the same silhouette as the chunk tamper on purpose: it is the end
     * of that ladder rather than something unrelated.
     */
    @Override
    public boolean gradedIcons() {
        return false;
    }

    /** Costs nothing. Its whole point is that the material stopped being the constraint. */
    @Override
    public boolean isFree(ItemStack stack) {
        return true;
    }

    /**
     * Ctrl + right-click on an entity remembers what kind it was, and opens the screen on that
     * entity, so an operator can say from there whether that kind wears the ground.
     *
     * <p>
     * Only the modifier gesture is taken; a plain right-click is handed back untouched so the
     * Wayfarer never stops you mounting a horse or feeding a cow. Fires on both sides - the
     * client opens the screen, the server writes the authoritative stack - because the entity
     * name is the same on both and the screen needs it the instant it opens.
     *
     * <p>
     * Named the way the engine names a mob when it asks what that mob does to the ground, so the
     * name this writes is the name those settings are keyed by. That sentence is the other
     * editions' and still true, and it now means the opposite thing: they key those settings by an
     * entity's short name and go out of their way not to write its registry one, where this version
     * has no short name at all and keys them by the registry name. See
     * {@code ErosionEngine.multiplierFor}, which looks it up the same way.
     */
    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack, Player player,
        LivingEntity entity, InteractionHand hand) {
        if (entity == null || player == null) return net.minecraft.world.InteractionResult.PASS;
        if (!TamperModifiers.held(player)) return net.minecraft.world.InteractionResult.PASS;
        net.minecraft.resources.ResourceLocation named = net.minecraft.core.Registry.ENTITY_TYPE
            .getKey(entity.getType());
        String name = named == null ? null : named.toString();
        if (name == null || name.isEmpty()) {
            // Consumed the gesture, nothing to name.
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        setEntity(stack, name);
        if (player.level != null && player.level.isClientSide()) {
            Client.openTamperScreen(stack);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** Never wears. Not unbreakable-by-declaration - see the class note for why that matters. */
    @Override
    public boolean wearsOut(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** Takes to enchantment better than anything below it, which is the whole idea of it. */
    @Override
    public int getEnchantmentValue() {
        return 22;
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @SuppressWarnings("unchecked")
    @Override
    public void appendHoverText(ItemStack stack, Level world,
        List<net.minecraft.network.chat.Component> lines, TooltipFlag advanced) {
        // Substituted before anything is written, so every line below - and every line
        // anybody adds later - stops at the edge of a column rather than the screen.
        List<String> tooltip = Tooltips.lines(lines);
        int edge = reachOf(stack) * 2 + 1;
        tooltip.add(ChatFormatting.LIGHT_PURPLE + com.trmtgtnh.util.Translate.get("trmtgtnh.magictamper.blurb"));
        tooltip.add(
            ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get(
                "trmtgtnh.chunktamper.area") + " " + ChatFormatting.WHITE + edge + "x" + edge + "x" + edge);
        tooltip.add(
            ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.chunktamper.perUse")
                + " "
                + ChatFormatting.WHITE
                + stepsOf(stack));
        // The mode it is set to, said as the chunk tamper says it: this tool takes the three unlocks best of all and
        // its screen offers every mode, and until 0.9.222 nothing on it said which one was on (spec TA72).
        addModeLines(stack, tooltip);
        // The two gesture lines that stood here are added again inside the expanded block below,
        // so holding Shift printed each of them twice. Dropped rather than removed from the
        // expansion, because the chunk tamper this inherits from shows no gestures at all while
        // collapsed, and the two tools' tooltips should read the same way.
        tooltip.add(ChatFormatting.DARK_GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.chunktamper.tip.settings"));
        if (TamperTooltip.expanded()) {
            tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.chunktamper.tip.left"));
            tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.chunktamper.tip.sneakLeft"));
            tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.chunktamper.tip.right"));
            tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.chunktamper.tip.sneakRight"));
            tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.chunktamper.tip.air"));
        } else {
            TamperTooltip.shiftHint(tooltip);
        }
    }

    /**
     * One, at its full reach.
     *
     * <p>
     * No grades: the grade was always a statement about material, and this one is past caring
     * about material.
     */
    @Override
    public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> list) {
        if (!allowdedIn(tab)) return;
        ItemStack stack = new ItemStack(this, 1);
        setReach(stack, TrmtConfig.chunkTamperMaxReach);
        list.add(stack);
    }
}
