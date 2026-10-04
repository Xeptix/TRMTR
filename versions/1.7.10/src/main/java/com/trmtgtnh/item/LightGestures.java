package com.trmtgtnh.item;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.trmtgtnh.block.GhostLight;
import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.erosion.ChunkErosionData;
import com.trmtgtnh.erosion.ErosionEntry;
import com.trmtgtnh.erosion.ErosionKey;
import com.trmtgtnh.erosion.ErosionStore;
import com.trmtgtnh.network.TrmtNetwork;
import com.trmtgtnh.server.MaterialCost;

/**
 * Lighting a stretch of ground, recolouring it, and putting it out.
 *
 * <p>
 * Right-click to light a block, right-click a lit one to step its colour on, sneak and right-click
 * to put it out; left-click steps the colour back, which matters when there are sixteen of them and
 * the one you want is one behind. Lighting costs a material, the Wayfarer paying half; recolouring
 * and dousing are only a scratch on the tool, because having paid for the light once is enough.
 *
 * <p>
 * Only ground that is already worn can be lit, and that is a consequence rather than a rule: the
 * glow is emitted by the ghost block this mod paints over worn ground, so a position with no ghost
 * has nothing to emit it. Forcing a ghost onto pristine ground was the obvious alternative and is
 * wrong - the first stage of every chain is already a visibly worn one, so lighting untouched grass
 * would scuff it. Wear the path first, then light it, which is the order somebody laying a road
 * would work in anyway.
 *
 * <p>
 * Server-side only. {@link ItemChunkTamper} has already decided this is a light click; here is the
 * rule.
 */
public final class LightGestures {

    private LightGestures() {}

    /** Right-click: lights the block, steps its colour on, or puts it out when sneaking. */
    public static boolean right(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        boolean sneaking = player != null && player.isSneaking();
        if (sneaking) return douse(world, x, y, z, player, stack);
        return light(world, x, y, z, player, stack, 1);
    }

    /** Left-click: steps the colour back, or puts it out when sneaking. */
    public static boolean left(World world, int x, int y, int z, EntityPlayer player, ItemStack stack,
        boolean sneaking) {
        if (sneaking) return douse(world, x, y, z, player, stack);
        return light(world, x, y, z, player, stack, -1);
    }

    /**
     * Lights an unlit block, or moves a lit one along the colour wheel.
     *
     * @param step which way to turn the wheel when the block is already lit
     */
    private static boolean light(World world, int x, int y, int z, EntityPlayer player, ItemStack stack, int step) {
        if (world == null || world.isRemote || !TrmtConfig.lightEnabled) return false;

        Block block = world.getBlock(x, y, z);
        if (block == null || block.isAir(world, x, y, z)) return false;
        ErosionEntry entry = ghostedEntry(world, x, y, z);
        if (entry == null) {
            say(player, EnumChatFormatting.YELLOW, msg("trmtgtnh.light.notWorn"));
            return true;
        }

        int packed = entry.getLight();
        boolean alreadyLit = (packed & 0xF) != 0;

        if (alreadyLit) {
            int colours = GhostLight.colourCount();
            int next = (((packed >> 4) & 0xF) + step + colours) % colours;
            apply(world, x, y, z, TrmtConfig.lightLevel, next);
            world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.orb", 0.3f, 1.8f);
            damage(stack, player);
            say(player, EnumChatFormatting.AQUA, msg("trmtgtnh.light.colour") + " " + colourName(next));
            return true;
        }

        boolean free = player != null && player.capabilities.isCreativeMode;
        if (!free) {
            // Nought is a real answer, and the setting says what it means: free. Held at one only
            // where a real price would otherwise halve to nothing, so the Wayfarer's half is never
            // a way round paying at all.
            int count = Math.max(0, TrmtConfig.lightCostCount);
            if (count > 0 && stack != null && stack.getItem() instanceof ItemMagicTamper) {
                count = Math.max(1, count / 2);
            }
            if (!MaterialCost.pay(player, TrmtConfig.lightMaterials, count)) {
                say(player, EnumChatFormatting.RED, msg("trmtgtnh.light.needs"));
                return false;
            }
        }
        apply(world, x, y, z, TrmtConfig.lightLevel, 0);
        ModAchievements.onLit(player);
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.orb", 0.4f, 1.2f);
        world.playAuxSFX(2005, x, y, z, 0);
        damage(stack, player);
        say(player, EnumChatFormatting.AQUA, msg("trmtgtnh.light.on"));
        return true;
    }

    private static boolean douse(World world, int x, int y, int z, EntityPlayer player, ItemStack stack) {
        if (world == null || world.isRemote || !TrmtConfig.lightEnabled) return false;
        ErosionEntry lit = ghostedEntry(world, x, y, z);
        if (lit == null || (lit.getLight() & 0xF) == 0) {
            say(player, EnumChatFormatting.YELLOW, msg("trmtgtnh.light.none"));
            return true;
        }
        apply(world, x, y, z, 0, 0);
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.fizz", 0.4f, 1.4f);
        damage(stack, player);
        say(player, EnumChatFormatting.GRAY, msg("trmtgtnh.light.off"));
        return true;
    }

    // ------------------------------------------------------------------

    /**
     * The record here, but only if it is one this mod is actually painting.
     *
     * <p>
     * An invisible record - one tracking a position that has accumulated some wear but not yet
     * changed appearance - does not count, because no ghost stands there to carry a glow.
     */
    private static ErosionEntry ghostedEntry(World world, int x, int y, int z) {
        ErosionEntry entry = ErosionStore.get()
            .getEntry(world, x, y, z);
        return entry != null && entry.isVisible() ? entry : null;
    }

    /**
     * Writes the glow into the position's record and tells everyone who can see it.
     *
     * <p>
     * Three separate things have to happen and all three matter: the record changes, the world's
     * block light is recomputed so the glow actually reaches the ground around it, and the clients
     * are told so they can rebuild the chunk mesh. Miss the second and a lit block is a bright
     * texture in the dark; miss the third and only the server knows.
     */
    private static void apply(World world, int x, int y, int z, int level, int colour) {
        int dimension = world.provider.dimensionId;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int key = ErosionKey.packWorld(x, y, z);

        ChunkErosionData data = ErosionStore.get()
            .getOrCreateChunk(dimension, chunkX, chunkZ);
        ErosionEntry entry = data.get(key);
        // Never invents a record: a glow needs a ghost, and a ghost is the caller's precondition.
        if (entry == null || !entry.isVisible()) return;
        entry.setLight(level, colour);
        if (level > 0) com.trmtgtnh.block.GhostLight.noteLit();
        data.markDirty();
        ErosionStore.get()
            .markModified(world, chunkX, chunkZ);

        // The glow only reaches the ground around it once vanilla has propagated it.
        world.func_147451_t(x, y, z);
        TrmtNetwork.sendLightDelta(world, x, y, z, entry.getLight());
    }

    private static void damage(ItemStack stack, EntityPlayer player) {
        if (stack == null) return;
        if (player != null && player.capabilities.isCreativeMode) return;
        if (stack.getItem() instanceof ItemChunkTamper && !((ItemChunkTamper) stack.getItem()).wearsOut(stack)) {
            return;
        }
        stack.damageItem(1, player);
        // Vanilla takes a broken tool out of the hand only when it is a bow. Any other is left as a
        // stack of nought with its damage reset to new, and this gesture arrives by a cancelled
        // left-click that nothing tidies up after - so a tamper that broke doing it stayed in the
        // hand, whole, until the next right-click. The hand tamper's own spend does the same.
        if (stack.stackSize <= 0 && player != null && player.getCurrentEquippedItem() == stack) {
            player.destroyCurrentEquippedItem();
        }
    }

    /** The colour's own name, so the chat line says "green" rather than "colour 5". */
    private static String colourName(int index) {
        return StatCollector.translateToLocal("trmtgtnh.light.colour." + index);
    }

    private static String msg(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static void say(EntityPlayer player, EnumChatFormatting colour, String message) {
        if (player != null) player.addChatMessage(new ChatComponentText(colour + message));
    }
}
