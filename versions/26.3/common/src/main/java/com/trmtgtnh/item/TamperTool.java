package com.trmtgtnh.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What every tamper has in common as far as a left-click is concerned.
 *
 * <p>
 * Left-click has no item hook in 1.7.10 - the one place it surfaces is
 * {@code PlayerInteractEvent}, which carries no {@link ItemStack} - so the gesture is wired in
 * {@link TamperEvents} from the held stack instead. That handler used to test for
 * {@code ItemTamper}, which the chunk tamper is not, so the two big tools were left out of both
 * halves of the wiring: they had no left-click gesture, and nothing stopped a left-click being
 * read as a dig. One interface both hierarchies implement is what closes that, and closes it in
 * a way a third tool cannot fall out of.
 */
public interface TamperTool {

    /**
     * A left-click on a block while holding this tool.
     *
     * <p>
     * Called on the server only, after the click has been cancelled as a dig and after the
     * repeat cooldown. Returning false is fine and means "nothing here to work".
     */
    boolean leftClick(Level world, int x, int y, int z, Player player, ItemStack stack, boolean sneaking);
}
