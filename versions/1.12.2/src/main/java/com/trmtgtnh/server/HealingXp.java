package com.trmtgtnh.server;

import java.util.Locale;
import java.util.Map;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.item.ItemChunkTamper;

/**
 * What mending ground is worth, in experience.
 *
 * <p>
 * Priced per gradation rather than per gesture or per block, so filling a deep rut is worth more
 * than brushing one scuff off, which is worth almost nothing. Every path that puts ground back
 * comes through here - bone meal, the hand tamper's two mends and the area mend - so none of them
 * can drift into being worth more than another for the same work.
 *
 * <p>
 * Only mending that cost something earns anything. Wearing ground in is free, so a free way back
 * out would make the round trip an experience farm with nothing to slow it but the tool. Creative
 * mode, a tamper price set to nought, a chunk tamper with bone meal's block cost switched off and
 * the Wayfarer's Tamper, which never spends material at all, are all cases of the same rule: they
 * mend for nothing and are paid nothing. Callers pass only the gradations that were paid for. A
 * free gradation repairs no enchanted tool either, because that repair is paid out of the same
 * experience.
 *
 * <p>
 * The fractional part is settled by a coin toss rather than by rounding. A quarter of a point
 * per gradation rounds to zero every single time, which would mean a tool that only ever paid
 * out on very large gestures; a one-in-four chance pays the same amount over a session and pays
 * it in whole orbs, which is the only unit the game has.
 */
public final class HealingXp {

    /** Vanilla's own exchange rate for repair-by-experience: two points of damage per point. */
    private static final int DURABILITY_PER_POINT = 2;

    private HealingXp() {}

    /**
     * Pays a player for mending ground, and repairs their tool first if it asks to be.
     *
     * @param tool       the stack in hand, or null for bone meal, which has nothing to repair
     * @param gradations how many gradations of wear were put back and paid for; a caller whose
     *                   mending cost nothing passes none
     */
    public static void award(EntityPlayer player, ItemStack tool, int gradations) {
        if (player == null || gradations <= 0) return;
        // A machine earns nothing. It has no use for the experience, and what it earned would
        // still repair the tool in its hand, which would make an automated tamper mend itself.
        if (player instanceof FakePlayer) return;
        if (!TrmtConfig.xpFromHealing || TrmtConfig.xpPerGradation <= 0f) return;
        if (player.world == null || player.world.isRemote) return;
        // A backstop, not the rule. Callers already pass only the gradations they took material
        // for, so the Wayfarer's are none. This stays so that a caller which forgets cannot
        // reopen the free loop: wearing ground costs nothing and neither does mending it with a
        // tool that spends no material, so paying for the round trip would be a farm.
        if (tool != null && tool.getItem() instanceof ItemChunkTamper
            && ((ItemChunkTamper) tool.getItem()).isFree(tool)) return;

        float earned = gradations * TrmtConfig.xpPerGradation;
        int level = boostLevel(tool);
        if (level > 0) earned *= 1f + TrmtConfig.xpBoostPerLevel * level;

        int points = (int) earned;
        float remainder = earned - points;
        if (remainder > 0f && player.world.rand.nextFloat() < remainder) points++;
        if (points <= 0) return;

        points = repairFirst(tool, points);
        if (points > 0) player.addExperience(points);
    }

    /**
     * Spends what the tool wants on the tool, and hands back what is left.
     *
     * <p>
     * Written for 1.7.10, which has no Mending - it arrives two versions later - and so driven by
     * whatever enchantment the pack happens to supply under a name that reads like one. That
     * indirection is the point, and it is what makes the same code right here: on 1.12.2 the list
     * simply finds the game's own Mending first, and a pack with Thaumcraft still finds Repair.
     * Vanilla Mending itself is fed by experience orbs rather than by this, so nothing is paid twice.
     */
    private static int repairFirst(ItemStack tool, int points) {
        if (tool == null || !tool.isItemStackDamageable() || tool.getItemDamage() <= 0) return points;
        if (matches(tool, TrmtConfig.mendingEnchantments) <= 0) return points;

        int wanted = Math.min(tool.getItemDamage(), points * DURABILITY_PER_POINT);
        if (wanted <= 0) return points;
        tool.setItemDamage(tool.getItemDamage() - wanted);
        // Rounded up, so a single point that repaired one durability is still a point spent.
        // Rounding it down would make one experience buy an unbounded number of repairs.
        return points - (wanted + DURABILITY_PER_POINT - 1) / DURABILITY_PER_POINT;
    }

    private static int boostLevel(ItemStack tool) {
        return tool == null ? 0 : matches(tool, TrmtConfig.xpBoostEnchantments);
    }

    /**
     * The highest level of any enchantment on this stack whose name reads like one of these.
     *
     * <p>
     * By name rather than by id, because ids are assigned by whichever mods a pack happens to
     * load and are not the same on two installs. Not cached either: the list is short, a stack
     * carries a handful of enchantments at most, and this is asked once per gesture rather than
     * once per block - so a cache would only be somewhere for a config reload to go stale.
     */
    private static int matches(ItemStack stack, String[] needles) {
        if (stack == null || needles == null || needles.length == 0) return 0;
        if (!stack.isItemEnchanted()) return 0;

        int best = 0;
        Map<Enchantment, Integer> found = EnchantmentHelper.getEnchantments(stack);
        for (Map.Entry<Enchantment, Integer> entry : found.entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (enchantment == null || entry.getValue() == null) continue;

            String name = enchantment.getName();
            if (name == null) continue;
            String flat = name.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace(".", "")
                .replace(" ", "");
            for (String needle : needles) {
                if (needle == null || needle.trim()
                    .isEmpty()) continue;
                String want = needle.trim()
                    .toLowerCase(Locale.ROOT)
                    .replace("_", "")
                    .replace(".", "")
                    .replace(" ", "");
                if (!flat.contains(want)) continue;
                int level = entry.getValue()
                    .intValue();
                if (level > best) best = level;
            }
        }
        return best;
    }
}
