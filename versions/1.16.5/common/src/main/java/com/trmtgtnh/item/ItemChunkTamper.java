package com.trmtgtnh.item;

import java.util.List;


import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import com.trmtgtnh.Client;
import com.trmtgtnh.Trmt;
import com.trmtgtnh.block.BlockGhost;
import com.trmtgtnh.config.TrmtConfig;

/**
 * A tamper that works a cube at a time instead of a square.
 *
 * <p>
 * Everything that differs between one of these and another is carried in the stack's own NBT
 * rather than in its identity, and that is the whole design rather than a convenience. A grade
 * per material would mean a registered item per material, and GregTech alone offers some nine
 * hundred of them - which on 1.7.10 is three per cent of Forge's entire item id space claimed by
 * one mod. There are no numeric ids here, but the argument survives the change: what a save
 * records is a set of registry names, and a set that moves with the pack is a save that opens
 * with nine hundred missing entries. One name, held forever, with the grade written inside it
 * costs nobody anything.
 *
 * <p>
 * The per-stack durability that makes that possible is real and was checked rather than assumed:
 * the damage bar, the anvil and damage itself all ask the <em>stack</em> for its maximum. Only
 * the vanilla two-by-two grid repair asks the <em>item</em>, and it also builds its result
 * without copying NBT - so two of these in a crafting square would have produced one with no
 * grade at all. {@link #setNoRepair()} in the constructor closes that door outright; the anvil,
 * which honours the stack, is left as the way to mend one.
 *
 * <p>
 * This is also where every tamper's stack data is read and written, the hand tamper's included -
 * it was written here first, and the hand tamper came to it rather than the other way round.
 */
public class ItemChunkTamper extends Item implements TamperTool {

    /** Where the grade lives on the stack. */
    private static final String TAG_ROOT = "trmt";
    private static final String TAG_GRADE = "grade";
    private static final String TAG_REACH = "reach";
    private static final String TAG_STEPS = "steps";
    private static final String TAG_TARGET = "target";
    private static final String TAG_ENTITY = "entity";
    private static final String TAG_LASTKIND = "lastkind";
    private static final String TAG_REINFORCE_MODE = "reinforceMode";
    private static final String TAG_REINFORCE_LEVEL = "reinforceLevel";
    private static final String TAG_MODE = "mode";

    /** The tool does its ordinary work: no unlock mode is active. */
    public static final int MODE_NONE = 0;
    /** Clicks reinforce, given the reinforcement enchantment. */
    public static final int MODE_REINFORCE = 1;
    /** Clicks bar mob spawns, given the ward enchantment. */
    public static final int MODE_WARD = 2;
    /** Clicks light the ground, given the path-light enchantment. */
    public static final int MODE_LIGHT = 3;

    /** The highest mode there is, which is what everything clamping a mode clamps to. */
    private static final int MODE_MAX = MODE_LIGHT;

    /**
     * The damage the item itself declares.
     *
     * <p>
     * Non-zero on purpose, and not merely a default. {@code Item.isDamageable} asks the item
     * rather than the stack, and the packet writer consults it to decide whether a stack's NBT
     * is sent to the client at all - so an item declaring zero would arrive with no grade on it
     * and no way to tell why.
     */
    private static final int DECLARED_USES = 1024;

    public ItemChunkTamper() {
        this(new Item.Properties());
    }

    /**
     * Everything an item is settled when it is built, not set on it afterwards.
     *
     * <p>
     * A properties object rather than four setters, which 1.13 moved, and which is also how a
     * subclass asks for something different without a constructor of its own.
     */
    protected ItemChunkTamper(Item.Properties properties) {
        super(properties.stacksTo(1)
            .durability(DECLARED_USES)
            .tab(CreativeModeTab.TAB_TOOLS));
        // The other edition calls setFull3D here; 1.12.2 reads that off the model instead, whose
        // parent is item/handheld.
        //
        // Both older editions refuse the grid repair here - the one place a per-stack maximum is
        // ignored, and it launders the grade off the stack on its way past. There is no way to
        // refuse it in a module both loaders share: what decides it is a vanilla recipe asking
        // whether the item is damageable at all. Owed, and named in PortProgressTest.
    }

    // ------------------------------------------------------------------
    // What the stack remembers
    // ------------------------------------------------------------------

    private static CompoundTag own(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        // Three steps in one. Both older editions ask whether the stack has a tag, make one if not,
        // and then fetch it; this version has the call that does exactly that, and nothing in
        // between to get wrong.
        CompoundTag root = stack.getOrCreateTag();
        if (!root.contains(TAG_ROOT)) root.put(TAG_ROOT, new CompoundTag());
        return root.getCompound(TAG_ROOT);
    }

    /** The grade this one was made at, or iron when it predates the question. */
    public static TamperGrade gradeOf(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return TamperGrade.fallback();
        CompoundTag tag = stack.getTag()
            .getCompound(TAG_ROOT);
        return TamperGrade.byKey(tag.getString(TAG_GRADE));
    }

    public static void setGrade(ItemStack stack, TamperGrade grade) {
        CompoundTag tag = own(stack);
        if (tag != null && grade != null) tag.putString(TAG_GRADE, grade.key);
    }

    /** How far out of the clicked block the cube reaches, in blocks. */
    public static int reachOf(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return TrmtConfig.chunkTamperDefaultReach;
        CompoundTag tag = stack.getTag()
            .getCompound(TAG_ROOT);
        if (!tag.contains(TAG_REACH)) return TrmtConfig.chunkTamperDefaultReach;
        return clamp(tag.getInt(TAG_REACH), 0, TrmtConfig.chunkTamperMaxReach);
    }

    public static void setReach(ItemStack stack, int reach) {
        CompoundTag tag = own(stack);
        if (tag != null) tag.putInt(TAG_REACH, clamp(reach, 0, TrmtConfig.chunkTamperMaxReach));
    }

    /** How many gradations one gesture moves a position, rather than the usual random draw. */
    public static int stepsOf(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return 1;
        CompoundTag tag = stack.getTag()
            .getCompound(TAG_ROOT);
        if (!tag.contains(TAG_STEPS)) return 1;
        return clamp(tag.getInt(TAG_STEPS), 1, 16);
    }

    public static void setSteps(ItemStack stack, int steps) {
        CompoundTag tag = own(stack);
        if (tag != null) tag.putInt(TAG_STEPS, clamp(steps, 1, 16));
    }

    /**
     * The block this tool last worked, remembered so a screen has something to talk about.
     *
     * <p>
     * Written server-side and carried to the client on the stack itself, which is the only
     * copy either side needs. It is a name rather than a position: what the editor changes is
     * a kind of block, not a spot.
     */
    public static String targetOf(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return "";
        return stack.getTag()
            .getCompound(TAG_ROOT)
            .getString(TAG_TARGET);
    }

    public static void setTarget(ItemStack stack, String name) {
        CompoundTag tag = own(stack);
        if (tag != null && name != null) {
            tag.putString(TAG_TARGET, name);
            // A block was the last thing worked, so the editor talks about blocks. Set together
            // with the target rather than left to the caller, so the two can never disagree.
            tag.putByte(TAG_LASTKIND, (byte) 0);
        }
    }

    /**
     * The entity kind this tool last pointed at, remembered so the editor has something to offer
     * for entities the way {@link #targetOf} gives it something to offer for blocks.
     */
    public static String entityOf(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return "";
        return stack.getTag()
            .getCompound(TAG_ROOT)
            .getString(TAG_ENTITY);
    }

    public static void setEntity(ItemStack stack, String name) {
        CompoundTag tag = own(stack);
        if (tag != null && name != null) {
            tag.putString(TAG_ENTITY, name);
            tag.putByte(TAG_LASTKIND, (byte) 1);
        }
    }

    /** Whether the last thing this tool worked was an entity rather than a block. */
    public static boolean lastWorkedEntity(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return false;
        return stack.getTag()
            .getCompound(TAG_ROOT)
            .getByte(TAG_LASTKIND) == 1;
    }

    /** Whether reinforce mode is switched on for this tool. Meaningless without the enchantment. */
    public static boolean reinforceModeOf(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return false;
        return stack.getTag()
            .getCompound(TAG_ROOT)
            .getByte(TAG_REINFORCE_MODE) == 1;
    }

    public static void setReinforceMode(ItemStack stack, boolean on) {
        CompoundTag tag = own(stack);
        if (tag != null) tag.putByte(TAG_REINFORCE_MODE, (byte) (on ? 1 : 0));
    }

    /**
     * Which unlock mode this tool is set to: {@link #MODE_NONE}, {@link #MODE_REINFORCE},
     * {@link #MODE_WARD} or {@link #MODE_LIGHT}. A tool set to a mode it no longer has the
     * enchantment for still reads as set to it here; whether the mode is <em>active</em> is
     * {@link #reinforceActive} / {@link #wardActive} / {@link #lightActive}, which also check the
     * enchantment and the feature switch.
     */
    public static int modeOf(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return MODE_NONE;
        CompoundTag tag = stack.getTag()
            .getCompound(TAG_ROOT);
        if (tag.contains(TAG_MODE)) {
            int mode = tag.getByte(TAG_MODE);
            return mode < MODE_NONE ? MODE_NONE : mode > MODE_MAX ? MODE_MAX : mode;
        }
        // A tool set to reinforce mode before modes were generalised carried a boolean instead.
        return tag.getByte(TAG_REINFORCE_MODE) == 1 ? MODE_REINFORCE : MODE_NONE;
    }

    public static void setMode(ItemStack stack, int mode) {
        CompoundTag tag = own(stack);
        if (tag != null)
            tag.putByte(TAG_MODE, (byte) (mode < MODE_NONE ? MODE_NONE : mode > MODE_MAX ? MODE_MAX : mode));
    }

    /** Whether this click reinforces rather than mends: the feature on, the tool able, the mode set. */
    public static boolean reinforceActive(ItemStack stack) {
        return TrmtConfig.reinforceEnabled && EnchReinforce.has(stack) && modeOf(stack) == MODE_REINFORCE;
    }

    /** Whether this click bars spawns: the feature on, the tool able, the mode set. */
    public static boolean wardActive(ItemStack stack) {
        return TrmtConfig.wardEnabled && EnchWard.has(stack) && modeOf(stack) == MODE_WARD;
    }

    /** Whether this click lights ground: the feature on, the tool able, the mode set. */
    public static boolean lightActive(ItemStack stack) {
        return TrmtConfig.lightEnabled && EnchLight.has(stack) && modeOf(stack) == MODE_LIGHT;
    }

    /**
     * The level the Wayfarer's tamper reinforces a block straight to, 0-3.
     *
     * <p>
     * Only the Wayfarer reads it - the chunk tamper always adds one - so it is the difference
     * between the two tools in reinforce mode: the Wayfarer sets a block to exactly this, up or
     * down, for one material. Defaults to the cap so a fresh one goes straight to blast-proof.
     */
    public static int reinforceLevelOf(ItemStack stack) {
        int cap = Math.max(0, Math.min(3, TrmtConfig.reinforceMaxLevel));
        if (stack == null || !stack.hasTag()) return cap;
        CompoundTag tag = stack.getTag()
            .getCompound(TAG_ROOT);
        if (!tag.contains(TAG_REINFORCE_LEVEL)) return cap;
        int level = tag.getByte(TAG_REINFORCE_LEVEL);
        return level < 0 ? 0 : level > cap ? cap : level;
    }

    public static void setReinforceLevel(ItemStack stack, int level) {
        CompoundTag tag = own(stack);
        if (tag != null) tag.putByte(TAG_REINFORCE_LEVEL, (byte) (level < 0 ? 0 : level > 3 ? 3 : level));
    }

    private static int clamp(int value, int low, int high) {
        if (value < low) return low;
        return value > high ? high : value;
    }

    // ------------------------------------------------------------------
    // Durability, per stack
    // ------------------------------------------------------------------

    /**
     * How many uses this particular stack has, which is its grade's number and not the item's.
     *
     * <p>
     * <strong>Not an override here, and that is this version taking something away.</strong> Asking
     * an item how much damage a *stack* may take is Forge's addition; vanilla asks the item alone,
     * which would make every grade of tamper last exactly as long as every other. So the number
     * stays where it has always been and each loader is wired to come and ask for it - Forge by
     * overriding the method it has, Fabric by a mixin on the stack itself. Neither can answer
     * differently from the other, because neither answers at all.
     */
    public int maxDamageOf(ItemStack stack) {
        return gradeOf(stack).uses();
    }

    /**
     * Whether a gesture with this stack costs material. Asked rather than assumed, so a tool
     * that is past caring about material can say so without a second implementation.
     */
    public boolean isFree(ItemStack stack) {
        return false;
    }

    /** Whether a gesture with this stack wears it. Same reasoning as {@link #isFree}. */
    public boolean wearsOut(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack tool, ItemStack material) {
        return gradeOf(tool).matches(material);
    }

    /**
     * Enchantable. See {@link ItemTamper#getItemEnchantability} for why this has to be said out
     * loud rather than inherited.
     *
     * <p>
     * One number for every grade rather than one per material: the grade decides how long the
     * tool lasts and how much ground it can pay for, and how well a thing takes an enchantment
     * is not something the ore dictionary has an opinion about.
     */
    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    // ------------------------------------------------------------------
    // Gestures
    // ------------------------------------------------------------------

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Player player = context.getPlayer();
        Level world = context.getLevel();
        if (player == null || world == null) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();
        ItemStack stack = player.getItemInHand(context.getHand());
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        // Remembered whatever the gesture turns out to do, because the interesting question
        // for the editor is "what did I just touch" rather than "what did I just mend" - and
        // remembered on both sides, because the screen opens in the same tick the click lands
        // and the server's answer would not have arrived in time to fill the editor in.
        ResourceLocation name = net.minecraft.core.Registry.BLOCK.getKey(
            world.getBlockState(pos)
                .getBlock());
        if (name != null) setTarget(stack, name.toString());

        boolean settings = TamperModifiers.held(player);
        boolean warding = !settings && wardActive(stack);
        boolean reinforcing = !settings && reinforceActive(stack);
        boolean lighting = !settings && lightActive(stack);
        if (world.isClientSide()) {
            if (settings) {
                Client.openTamperScreen(stack);
                return InteractionResult.SUCCESS;
            }
            // In an unlock mode every solid block is a target, so the swing is answered without
            // asking whether there is worn ground here.
            if (reinforcing || warding || lighting) return InteractionResult.SUCCESS;
            return BlockGhost.shows(Client.ghostRecordAt(world, x, y, z)) ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
        }
        // The modifier click is the screen and nothing else. Without this the server would
        // still mend the area under the crosshair, spending material and durability on a
        // gesture the player made to open a menu.
        if (settings) return InteractionResult.SUCCESS;
        boolean acted;
        if (warding) {
            // Ward mode reads a right-click as "bar passives here" (sneak lets them back).
            acted = WardGestures.right(world, x, y, z, player, stack);
        } else if (lighting) {
            // Light mode reads a right-click as "light this, or step its color on" (sneak puts it out).
            acted = LightGestures.right(world, x, y, z, player, stack);
        } else if (reinforcing) {
            acted = ReinforceGestures.reinforce(world, x, y, z, player, stack);
        } else {
            acted = player.isShiftKeyDown() ? ChunkTamperActions.pinArea(world, x, y, z, player, stack)
                : ChunkTamperActions.mendArea(world, x, y, z, player, stack);
        }
        return acted ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Left-click wears the whole cube; sneak and left-click wears one square.
     *
     * <p>
     * The pair a right-click makes in reverse: the big gesture and the precise one, for when a
     * plaza laid out with a nine by nine needs a single square taking further.
     */
    @Override
    public boolean leftClick(Level world, int x, int y, int z, Player player, ItemStack stack, boolean sneaking) {
        // Ward mode reads a left-click as "bar hostiles here" (sneak lets them back).
        if (wardActive(stack)) return WardGestures.left(world, x, y, z, player, stack, sneaking);
        // Light mode reads a left-click as "step the color back", which saves fifteen right-clicks.
        if (lightActive(stack)) return LightGestures.left(world, x, y, z, player, stack, sneaking);
        // Reinforce mode reads a left-click as "take a reinforcement off", which needs two of them.
        if (reinforceActive(stack)) return ReinforceGestures.unreinforce(world, x, y, z, player);
        if (sneaking) return TamperActions.wearOne(world, x, y, z, player, stack);
        return ChunkTamperActions.wearArea(world, x, y, z, player, stack);
    }

    /**
     * Right-clicking nothing walks the reach up a step and round.
     *
     * <p>
     * A setting has to be reachable from somewhere, and a gesture the tool has spare beats a
     * screen it does not have yet. Clicking air is that gesture: there is nothing else it could
     * have meant, and the answer comes back in the tooltip and in chat straight away.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The screen is the client's business and opens without asking the server, because
        // everything on it is already in the stack the client is holding.
        boolean settings = TamperModifiers.held(player);
        if (world.isClientSide()) {
            if (settings) Client.openTamperScreen(stack);
            return new InteractionResultHolder<ItemStack>(InteractionResult.SUCCESS, stack);
        }
        // Both cycles are the server's, and the modifier click is neither of them.
        if (settings) return new InteractionResultHolder<ItemStack>(InteractionResult.SUCCESS, stack);
        if (player.isShiftKeyDown()) {
            int steps = stepsOf(stack) + 1;
            if (steps > TrmtConfig.chunkTamperMaxSteps) steps = 1;
            setSteps(stack, steps);
            ChunkTamperActions.say(player, "trmtgtnh.chunktamper.steps", Integer.valueOf(steps));
            return new InteractionResultHolder<ItemStack>(InteractionResult.SUCCESS, stack);
        }
        int reach = reachOf(stack) + 1;
        if (reach > TrmtConfig.chunkTamperMaxReach) reach = 0;
        setReach(stack, reach);
        ChunkTamperActions.say(player, "trmtgtnh.chunktamper.reach", Integer.valueOf(reach * 2 + 1));
        return new InteractionResultHolder<ItemStack>(InteractionResult.SUCCESS, stack);
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    /** Which family of pictures this tool is drawn from. The Wayfarer has its own. */
    public String iconBase() {
        return "chunk_tamper";
    }

    /**
     * Whether this tool is drawn, and named, in the material it was made of. The Wayfarer is not.
     *
     * <p>
     * On both sides, because the name asks it as well as the pictures, and a server asks an item its
     * name: the line /give prints, an anvil, a death message. Marked for the client alone, as it was,
     * it was taken out of the class on a dedicated server, and the first time a server named a chunk
     * tamper the server stopped.
     */
    public boolean gradedIcons() {
        return true;
    }

    @Override
    public net.minecraft.network.chat.Component getName(ItemStack stack) {
        if (!gradedIcons()) return super.getName(stack);
        // A component rather than a string, which is what every name is at this version. The text
        // inside it is the same translation, through the same helper every other line uses.
        return new net.minecraft.network.chat.TextComponent(
            com.trmtgtnh.util.Translate.get("item.trmtgtnh.chunk_tamper.graded.name", gradeOf(stack).displayName()));
    }

    /** See {@link ItemTamper#addInformation} for why this one says which side it is on. */
    @Override
    public void appendHoverText(ItemStack stack, Level world,
        List<net.minecraft.network.chat.Component> lines, TooltipFlag advanced) {
        // Named before anything is written, so every line below - and every line anybody adds later
        // - stops at the edge of a column rather than the screen. See Tooltips for why this is a
        // list of strings over the game's list of components.
        List<String> tooltip = Tooltips.lines(lines);
        int edge = reachOf(stack) * 2 + 1;
        tooltip.add(ChatFormatting.GOLD + com.trmtgtnh.util.Translate.get("trmtgtnh.chunktamper.desc"));
        tooltip.add(
            ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get(
                "trmtgtnh.chunktamper.area") + " " + ChatFormatting.WHITE + edge + "x" + edge + "x" + edge);
        tooltip.add(
            ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get("trmtgtnh.chunktamper.perUse")
                + " "
                + ChatFormatting.WHITE
                + stepsOf(stack));
        TamperTooltip.areaCost(tooltip);
        TamperTooltip.xp(tooltip, TrmtConfig.bonemealCostsABlock);
        addModeLines(stack, tooltip);
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
     * The active unlock mode and its controls, when one is on.
     *
     * <p>
     * Only the mode the tool is actually set to and enchanted for is shown, with the gestures
     * that belong to it - so the tooltip never lists reinforce controls on a warding tool. The
     * controls expand with the same shift the rest of the tooltip uses.
     */
    private void addModeLines(ItemStack stack, List<String> tooltip) {
        boolean reinforce = reinforceActive(stack);
        boolean ward = wardActive(stack);
        boolean light = lightActive(stack);
        if (!reinforce && !ward && !light) return;

        String key = reinforce ? "trmtgtnh.mode.reinforce" : ward ? "trmtgtnh.mode.ward" : "trmtgtnh.mode.light";
        tooltip.add(
            ChatFormatting.AQUA + com.trmtgtnh.util.Translate.get("trmtgtnh.mode.label")
                + " "
                + ChatFormatting.WHITE
                + com.trmtgtnh.util.Translate.get(key));
        if (!TamperTooltip.expanded()) return;

        for (String line : controlKeys(reinforce, ward)) {
            tooltip.add(ChatFormatting.GRAY + com.trmtgtnh.util.Translate.get(line));
        }
    }

    /** The gesture lines belonging to whichever mode is active. */
    private static String[] controlKeys(boolean reinforce, boolean ward) {
        if (reinforce) {
            return new String[] { "trmtgtnh.mode.reinforce.add", "trmtgtnh.mode.reinforce.remove" };
        }
        if (ward) {
            return new String[] { "trmtgtnh.mode.ward.hostile", "trmtgtnh.mode.ward.passive",
                "trmtgtnh.mode.ward.undo" };
        }
        return new String[] { "trmtgtnh.mode.light.on", "trmtgtnh.mode.light.color", "trmtgtnh.mode.light.off" };
    }

    /**
     * One of each grade the pack can actually supply.
     *
     * <p>
     * Distinct NBT stacks rather than distinct items, which is how GregTech's own meta items and
     * Tinkers' tools already appear in this pack's item list.
     */
    @Override
    public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> list) {
        if (!allowdedIn(tab)) return;
        for (TamperGrade grade : TamperGrade.available()) {
            ItemStack stack = new ItemStack(this, 1);
            setGrade(stack, grade);
            list.add(stack);
        }
    }
}
