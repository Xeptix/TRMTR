package com.trmtgtnh.client.gui;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.trmtgtnh.Trmt;
import com.trmtgtnh.client.settings.ListLine;
import com.trmtgtnh.client.settings.NameStatus;
import com.trmtgtnh.client.settings.PictureBudget;
import com.trmtgtnh.client.settings.Slicer;
import com.trmtgtnh.client.texture.WearPatterns;
import com.trmtgtnh.command.CommandTrmt;
import com.trmtgtnh.config.MobNames;
import com.trmtgtnh.surface.SurfaceFamily;

/**
 * This edition's answers about what the game has registered, for the settings screen's lists (spec Part 3.1, M3, M4,
 * Part 6): the shared {@link NameStatus.Lookup}, every list picture and how it is drawn, the names things go by, and
 * what each picker offers, gathered a slice a frame.
 *
 * <p>
 * <b>Every answer is asked of the game the way the game's own reader asks it</b>, so the screen says what the game will
 * do: a block by {@code Block.getBlockFromName} as {@code SurfaceRegistry.applyList} does, an item as
 * {@code ReinforceCost.resolve} does, a fluid by {@code FluidRegistry.getFluid} as {@code ReinforceCost} does, an
 * enchantment fragment by {@code HealingXp.matches}' flattening, an ore group by {@code OreDictionary.doesOreNameExist}
 * before {@code getOres} - which would otherwise make the group it was asked about. A texture is found as the inner
 * layers find one: on the block atlas by name, or as the file {@code WearPatterns.blockTextureFile} names.
 *
 * <p>
 * <b>A mod's code can throw from any of it</b>, inside a settings screen, so every question is asked in a try and a
 * throw is an answer of "not here". Pictures go through {@link PictureBudget}s kept for the game session: at most 24
 * new block or item pictures a frame and 2 new mobs (Part 6), a picture that threw never asked again.
 */
final class SettingsPictures {

    private SettingsPictures() {}

    /** New block and item pictures a frame, and new mobs (spec Part 6, family A). */
    static final PictureBudget ITEMS = new PictureBudget(24);

    static final PictureBudget MOBS = new PictureBudget(2);

    /** A frame's slice for gathering a picker's entries: 4 ms (Part 6, M4.5). */
    static final Slicer SLICE = new Slicer(4000000L, System::nanoTime);

    /** What each picker offers, gathered once a game session. */
    private static final Map<PickerList.Kind, PickerList.Gathering> GATHERED = new EnumMap<PickerList.Kind, PickerList.Gathering>(
        PickerList.Kind.class);

    private static final Map<Integer, String> DIMENSION_NAMES = new HashMap<Integer, String>();

    private static Field registeredSprites;

    /**
     * This frame's mob pictures, timed for the settingsperf part (0.9.228): their whole time, and the slowest by name
     * with the share of it that made the mob. Read by the harness at the frame's end; nothing here acts on them.
     */
    static long mobNanos;

    static long slowestMobNanos;

    static long slowestMobMadeNanos;

    static String slowestMob;

    /** This frame's time spent gathering a picker's entries, for the same line. */
    static long gatherNanos;

    /**
     * This frame's first draws of mobs, timed, and the room they have (0.9.228): a mob's first draw loads its model's
     * textures through its mod's renderer, which took 20 ms for one GT New Horizons mob, so once a frame's first draws
     * have taken 5 ms no other mob is drawn for the first time until the next frame. With the count's two a frame
     * (Part 6), the slow mob then has a frame to itself rather than sharing one with another slow one.
     */
    static long firstMobNanos;

    static final long FIRST_MOB_ROOM_NANOS = 5000000L;

    /**
     * Whether this frame gathered (0.9.228): a gathering that finishes in a frame's step had the mob's first draw in
     * that same frame, where the first mob made took 31 ms on dev 1.20.1 Forge and 402 ms on 1.21.1 NeoForge, so a
     * frame that gathered draws no mob for the first time either.
     */
    static boolean gathered;

    /** How long the last gathering took to start - its sources listed - for the settingsperf part (0.9.228). */
    static long startNanos;

    /**
     * Whether a picker's list is still being gathered (0.9.228): a mob is drawn for the first time only in a frame
     * with no gathering in it, since a gathering's slice and one slow mob's first draw together passed the 50 ms a
     * frame may take while gathering in GT New Horizons. Until then a mob row shows its empty frame.
     */
    static boolean gathering() {
        for (PickerList.Gathering one : GATHERED.values()) {
            if (!one.finished()) return true;
        }
        return false;
    }

    /** Call at the start of every frame of a screen that draws list pictures. */
    static void frame() {
        ITEMS.frame();
        MOBS.frame();
        SLICE.frame();
        mobNanos = 0L;
        slowestMobNanos = 0L;
        slowestMobMadeNanos = 0L;
        slowestMob = null;
        gatherNanos = 0L;
        firstMobNanos = 0L;
        gathered = false;
        WornPathArt.composeNanos = 0L;
        long started = System.nanoTime();
        for (PickerList.Gathering gathering : GATHERED.values()) {
            if (!gathering.finished() && SLICE.more()) gathered = gathering.step(SLICE);
        }
        gatherNanos += System.nanoTime() - started;
    }

    // ------------------------------------------------------------------
    // The pictures, as refs the canvas hands back to be drawn
    // ------------------------------------------------------------------

    /** An item, or a block's item, drawn as an inventory draws it. */
    static final class Stack {

        final ItemStack stack;

        final String key;

        Stack(ItemStack stack) {
            this.stack = stack;
            this.key = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())) + ":"
                + stack.getItemDamage();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Stack && ((Stack) other).key.equals(key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    /** A block with no item of its own, drawn by its top face's texture (survey 2.3). */
    static final class Face {

        final Block block;

        final int meta;

        Face(Block block, int meta) {
            this.block = block;
            this.meta = meta;
        }
    }

    /** A sprite of the block atlas, by name. */
    static final class Sprite {

        final String name;

        Sprite(String name) {
            this.name = name;
        }
    }

    /** A fluid, by its registry name. */
    static final class FluidPicture {

        final String name;

        FluidPicture(String name) {
            this.name = name;
        }
    }

    /** A mob, from a line of the mob list: itself turning, else its egg, else the generic egg (M3.4, C6). */
    static final class Mob {

        final String line;

        /** {@code *}, every mob no line names: the generic egg alone. */
        final boolean any;

        Mob(String line, boolean any) {
            this.line = line;
            this.any = any;
        }
    }

    /** Several pictures in turn, one a second, as an ore group's members are shown (survey 2.3). */
    static final class Cycle {

        final List<Object> pictures;

        Cycle(List<Object> pictures) {
            this.pictures = pictures;
        }
    }

    /** Whether a ref is one of these, which only {@link #draw} knows how to draw. */
    static boolean draws(Object ref) {
        return ref instanceof Stack || ref instanceof Face
            || ref instanceof Sprite
            || ref instanceof FluidPicture
            || ref instanceof Mob
            || ref instanceof Cycle;
    }

    /** Draws a ref of this class 16 by 16 with its top left at x, y, within the frame's budget. */
    static void draw(Object ref, int x, int y) {
        try {
            if (ref instanceof Cycle) {
                List<Object> all = ((Cycle) ref).pictures;
                if (!all.isEmpty()) draw(all.get((int) ((System.currentTimeMillis() / 1000L) % all.size())), x, y);
            } else if (ref instanceof Stack) {
                Stack stack = (Stack) ref;
                if (!ITEMS.mayTry(stack.key)) return;
                BlockIconArrayEntry.drawIcon(stack.key, stack.stack, x, y);
                if (BlockIconArrayEntry.broken(stack.key)) ITEMS.failed(stack.key);
                else ITEMS.fine(stack.key);
            } else if (ref instanceof Face) {
                Face face = (Face) ref;
                icon(face.block.getIcon(1, face.meta), x, y);
            } else if (ref instanceof Sprite) {
                TextureMap atlas = atlas();
                IIcon sprite = atlas == null ? null : atlas.getTextureExtry(((Sprite) ref).name);
                if (sprite != null) icon(sprite, x, y);
            } else if (ref instanceof FluidPicture) {
                Fluid fluid = FluidRegistry.getFluid(((FluidPicture) ref).name);
                if (fluid == null) return;
                IIcon still = fluid.getIcon();
                if (still == null && fluid.getBlock() != null) still = fluid.getBlock()
                    .getIcon(1, 0);
                icon(still, x, y, fluid.getColor());
            } else if (ref instanceof Mob) {
                Mob mob = (Mob) ref;
                if (mob.any) {
                    MobPictures.drawGenericEgg(x, y);
                    return;
                }
                String key = "mob:" + MobRows.nameOf(mob.line)
                    .trim()
                    .toLowerCase(Locale.ROOT);
                boolean first = MOBS.state(key) == PictureBudget.State.UNTRIED;
                if (first && (firstMobNanos >= FIRST_MOB_ROOM_NANOS || gathered || gathering())) return;
                if (!MOBS.mayTry(key)) return;
                long started = System.nanoTime();
                MobPictures.madeNanos = 0L;
                MobPictures.draw(mob.line, x, y);
                long took = System.nanoTime() - started;
                mobNanos += took;
                if (first) firstMobNanos += took;
                if (took > slowestMobNanos) {
                    slowestMobNanos = took;
                    slowestMobMadeNanos = MobPictures.madeNanos;
                    slowestMob = key;
                }
                MOBS.fine(key);
            }
        } catch (Throwable hostile) {
            // A mod's texture or icon lookup that throws draws nothing; the row says what it is in words.
        } finally {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    private static TextureMap atlas() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.getTextureMapBlocks();
    }

    /** An icon of the block atlas as a 16 by 16 quad, blended, the alpha test left as it was. */
    private static void icon(IIcon icon, int x, int y) {
        icon(icon, x, y, 0xFFFFFF);
    }

    /**
     * An icon of the block atlas, multiplied by {@code tint} as the game multiplies a fluid's still texture by the
     * fluid's own color (0.9.228): GT New Horizons' fluids share one grey texture and take their look from their color,
     * so every one of them was the same grey square in the fluid picker.
     */
    private static void icon(IIcon icon, int x, int y, int tint) {
        Minecraft mc = Minecraft.getMinecraft();
        if (icon == null || mc == null || mc.getTextureManager() == null) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(((tint >> 16) & 0xFF) / 255F, ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, 1F);
            mc.getTextureManager()
                .bindTexture(TextureMap.locationBlocksTexture);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + 16, 0D, icon.getMinU(), icon.getMaxV());
            tessellator.addVertexWithUV(x + 16, y + 16, 0D, icon.getMaxU(), icon.getMaxV());
            tessellator.addVertexWithUV(x + 16, y, 0D, icon.getMaxU(), icon.getMinV());
            tessellator.addVertexWithUV(x, y, 0D, icon.getMinU(), icon.getMinV());
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    // ------------------------------------------------------------------
    // What the game has
    // ------------------------------------------------------------------

    /** A block by its name as the block lists read it, or null; air is no block a list can mean. */
    static Block block(String id) {
        if (id == null) return null;
        try {
            Block block = Block.getBlockFromName(id);
            return block == null || block == Blocks.air ? null : block;
        } catch (Throwable hostile) {
            return null;
        }
    }

    /**
     * Whether a block has an item of its own (0.9.228). This game registers a plain item for nearly every vanilla
     * block, crops, fire and portals among them, which no player can hold: that item, on a vanilla block in no creative
     * tab, is the placeholder, and such a block is drawn by its texture and said to have no item - as 1.12.2 says it,
     * whose registry gives those blocks no item at all. A mod's block keeps whatever item its mod registered.
     */
    static boolean hasItem(Block block) {
        if (block == null) return false;
        try {
            Item item = Item.getItemFromBlock(block);
            if (item == null) return false;
            String id = String.valueOf(Block.blockRegistry.getNameForObject(block));
            return ownItem(
                item.getClass() == ItemBlock.class,
                id.startsWith("minecraft:"),
                block.getCreativeTabToDisplayOn() != null);
        } catch (Throwable hostile) {
            return false;
        }
    }

    /** The rule of {@link #hasItem}, plain: a placeholder item on a vanilla block in no creative tab is none. */
    static boolean ownItem(boolean plainItemBlock, boolean vanilla, boolean inCreativeTab) {
        return !(plainItemBlock && vanilla && !inCreativeTab);
    }

    /** {@code ReinforceCost.resolve}'s stack: a block's item first, then an item of that name; null for neither. */
    static ItemStack item(String id, int meta) {
        try {
            int damage = meta >= 0 ? meta : 0;
            Block block = block(id);
            if (block != null && Item.getItemFromBlock(block) != null) {
                return new ItemStack(Item.getItemFromBlock(block), 1, damage);
            }
            Object item = Item.itemRegistry.getObject(id);
            return item instanceof Item ? new ItemStack((Item) item, 1, damage) : null;
        } catch (Throwable hostile) {
            return null;
        }
    }

    /** Lower case, with the spaces, dots and underscores {@code HealingXp.matches} takes out of both sides. */
    static String flat(String name) {
        return name == null ? ""
            : name.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace(".", "")
                .replace(" ", "");
    }

    /** A name as a dimension is called by, spaces and case aside. */
    private static String plain(String name) {
        return name == null ? ""
            : name.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
    }

    /** A dimension's name, from its provider, asked once; null where it has none or its mod's provider throws. */
    static String dimensionName(int id) {
        Integer key = Integer.valueOf(id);
        if (DIMENSION_NAMES.containsKey(key)) return DIMENSION_NAMES.get(key);
        String name = null;
        try {
            if (DimensionManager.isDimensionRegistered(id)) {
                name = DimensionManager.createProviderFor(id)
                    .getDimensionName();
            }
        } catch (Throwable hostile) {
            name = null;
        }
        DIMENSION_NAMES.put(key, name);
        return name;
    }

    /** Every dimension id the game registered, or none where it cannot be asked. */
    static List<Integer> dimensionIds() {
        try {
            Integer[] ids = DimensionManager.getStaticDimensionIDs();
            List<Integer> out = new ArrayList<Integer>(Arrays.asList(ids));
            Collections.sort(out);
            return out;
        } catch (Throwable hostile) {
            return Collections.emptyList();
        }
    }

    /** The names of the block atlas's sprites, this mod's own worn pictures left out; empty before a stitch. */
    @SuppressWarnings("unchecked")
    static List<String> spriteNames() {
        List<String> out = new ArrayList<String>();
        try {
            TextureMap atlas = atlas();
            if (atlas == null) return out;
            if (registeredSprites == null) {
                Field found = null;
                for (String name : new String[] { "mapRegisteredSprites", "field_110574_e" }) {
                    try {
                        found = TextureMap.class.getDeclaredField(name);
                        break;
                    } catch (NoSuchFieldException notThatName) {
                        found = null;
                    }
                }
                if (found == null) return out;
                found.setAccessible(true);
                registeredSprites = found;
            }
            Map<String, ?> sprites = (Map<String, ?>) registeredSprites.get(atlas);
            for (String name : sprites.keySet()) {
                if (name != null && !name.startsWith(Trmt.MODID + ":")) out.add(name);
            }
        } catch (Throwable hostile) {
            return out;
        }
        return out;
    }

    /** The game's answers, for {@link NameStatus}. */
    static final NameStatus.Lookup LOOKUP = new NameStatus.Lookup() {

        @Override
        public boolean block(String id) {
            return SettingsPictures.block(id) != null;
        }

        @Override
        public boolean blockHasItem(String id, int meta) {
            Block block = SettingsPictures.block(id);
            try {
                return hasItem(block);
            } catch (Throwable hostile) {
                return false;
            }
        }

        @Override
        public boolean metaExists(String id, int meta) {
            // A block's metadata on 1.7.10 is four bits: anything past 15 is a variant no world can hold.
            return meta >= 0 && meta <= 15;
        }

        @Override
        public int oreMembers(String name) {
            try {
                if (name == null || !OreDictionary.doesOreNameExist(name)) return 0;
                return OreDictionary.getOres(name)
                    .size();
            } catch (Throwable hostile) {
                return 0;
            }
        }

        @Override
        public boolean fluid(String name) {
            try {
                return FluidRegistry.getFluid(name) != null;
            } catch (Throwable hostile) {
                return false;
            }
        }

        @Override
        public boolean item(String id, int meta) {
            return SettingsPictures.item(id, meta) != null;
        }

        @Override
        public boolean mob(String name) {
            try {
                for (String each : MobPictures.registered()) {
                    if (each != null && each.equalsIgnoreCase(name == null ? "" : name.trim())) return true;
                }
            } catch (Throwable hostile) {
                return false;
            }
            return false;
        }

        @Override
        public List<String> enchantMatches(String fragment) {
            List<String> out = new ArrayList<String>();
            String want = flat(fragment == null ? "" : fragment.trim());
            if (want.isEmpty()) return out;
            try {
                for (Enchantment enchantment : Enchantment.enchantmentsList) {
                    if (enchantment == null || enchantment.getName() == null) continue;
                    if (flat(enchantment.getName()).contains(want)) out.add(enchantmentName(enchantment));
                }
            } catch (Throwable hostile) {
                return out;
            }
            return out;
        }

        @Override
        public boolean sprite(String name) {
            if (name == null || name.trim()
                .isEmpty()) return false;
            try {
                TextureMap atlas = atlas();
                if (atlas != null && atlas.getTextureExtry(name) != null) return true;
                // Not on the atlas by that name: the file the inner layers read it from, read as they read it.
                Minecraft mc = Minecraft.getMinecraft();
                return mc != null && mc.getResourceManager() != null
                    && WearPatterns.readIcon(mc.getResourceManager(), name) != null;
            } catch (Throwable notThere) {
                return false;
            }
        }

        @Override
        public String dimension(int id) {
            return SettingsPictures.dimensionName(id);
        }

        @Override
        public Integer dimensionId(String name) {
            String wanted = plain(name);
            if (wanted.isEmpty()) return null;
            for (Integer id : dimensionIds()) {
                if (wanted.equals(plain(SettingsPictures.dimensionName(id.intValue())))) return id;
            }
            return null;
        }

        @Override
        public String dimensionName(String name) {
            return null;
        }

        @Override
        public boolean oreIsTag() {
            return false;
        }

        @Override
        public boolean writesDimensionNames() {
            return false;
        }

        @Override
        public boolean dimensionsKnown() {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                return mc != null && mc.theWorld != null;
            } catch (Throwable hostile) {
                return false;
            }
        }
    };

    /**
     * A dimension's picture (0.9.228; Xep, from "Choose a dimension" in GT New Horizons: every row an empty frame): the
     * vanilla three by their ground - grass, netherrack, end stone - and any other by the portal, so no dimension is
     * drawn as an empty frame. The portal is the default for a mod's dimension, for Xep to change.
     */
    static Object dimensionPicture(Integer id) {
        int which = DimensionPictures.of(id);
        if (which == DimensionPictures.OVERWORLD) return blockPicture(Blocks.grass, 0);
        if (which == DimensionPictures.NETHER) return blockPicture(Blocks.netherrack, 0);
        if (which == DimensionPictures.END) return blockPicture(Blocks.end_stone, 0);
        return new Sprite(PORTAL_SPRITE);
    }

    /** The portal's sprite on the block atlas, the picture of a dimension that is not one of the vanilla three. */
    static final String PORTAL_SPRITE = "portal";

    /** An enchantment's own name, as a book's tooltip gives it, without a level. */
    static String enchantmentName(Enchantment enchantment) {
        try {
            return StatCollector.translateToLocal(enchantment.getName());
        } catch (Throwable hostile) {
            return enchantment.getName();
        }
    }

    /** A block's picture at a metadata: its item where it has one, else its texture (survey 2.3). */
    static Object blockPicture(Block block, int meta) {
        try {
            if (hasItem(block)) return new Stack(new ItemStack(Item.getItemFromBlock(block), 1, Math.max(0, meta)));
        } catch (Throwable hostile) {
            return new Face(block, Math.max(0, meta));
        }
        return new Face(block, Math.max(0, meta));
    }

    private static String stackName(ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (Throwable hostile) {
            return null;
        }
    }

    /** A mob's name as the game translates it, or its registry name. */
    static String mobName(String name) {
        try {
            String key = "entity." + name + ".name";
            return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : name;
        } catch (Throwable hostile) {
            return name;
        }
    }

    /** An ore group's members' pictures, in turn. */
    private static Object orePicture(String ore) {
        List<Object> pictures = new ArrayList<Object>();
        try {
            if (ore == null || !OreDictionary.doesOreNameExist(ore)) return null;
            for (ItemStack member : OreDictionary.getOres(ore)) {
                if (member == null || member.getItem() == null) continue;
                ItemStack shown = member.copy();
                if (shown.getItemDamage() == OreDictionary.WILDCARD_VALUE) shown.setItemDamage(0);
                pictures.add(new Stack(shown));
                if (pictures.size() >= 16) break;
            }
        } catch (Throwable hostile) {
            return null;
        }
        return pictures.isEmpty() ? null : new Cycle(pictures);
    }

    private static ItemStack oreFirst(String ore) {
        try {
            if (ore == null || !OreDictionary.doesOreNameExist(ore)) return null;
            List<ItemStack> members = OreDictionary.getOres(ore);
            return members.isEmpty() ? null : members.get(0);
        } catch (Throwable hostile) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // The rows' host
    // ------------------------------------------------------------------

    /** The game's host of the list rows. */
    static final ListRows.Host HOST = new ListRows.Host() {

        @Override
        public NameStatus.Lookup lookup() {
            return LOOKUP;
        }

        @Override
        public Object picture(ListLine line, NameStatus.Status status) {
            try {
                switch (status.state) {
                    case KNOWN:
                    case KNOWN_NO_ITEM:
                    case WILDCARD:
                    case SPRITE_PAIR:
                        return known(line, status);
                    case ORE_GROUP:
                        return orePicture(line.ore);
                    case FLUID:
                        return new FluidPicture(line.fluid);
                    case ENCHANTMENTS:
                        return new Stack(new ItemStack(Items.enchanted_book));
                    case DIMENSION:
                        return dimensionPicture(line.dimension != null ? line.dimension : status.writtenAs);
                    case KEPT_OUT:
                        return new Mob(line.raw, false);
                    case ANY_MOB:
                        return new Mob(line.raw, true);
                    default:
                        return null;
                }
            } catch (Throwable hostile) {
                return null;
            }
        }

        private Object known(ListLine line, NameStatus.Status status) {
            switch (line.kind) {
                case MOB:
                    return new Mob(line.raw, false);
                case GRADE:
                    return orePicture(line.ore);
                case ITEM:
                case ITEM_OR_FLUID: {
                    ItemStack stack = item(line.id, line.meta);
                    return stack == null ? null : new Stack(stack);
                }
                default: {
                    Block block = SettingsPictures.block(line.id);
                    return block == null ? null : blockPicture(block, line.meta >= 0 ? line.meta : 0);
                }
            }
        }

        @Override
        public String name(ListLine line, NameStatus.Status status) {
            try {
                switch (line.kind) {
                    case MOB:
                        return mobName(
                            MobRows.nameOf(line.raw)
                                .trim());
                    case GRADE: {
                        ItemStack first = oreFirst(line.ore);
                        return first == null ? line.id : line.id + " - " + stackName(first);
                    }
                    case ITEM:
                    case ITEM_OR_FLUID: {
                        if (line.fluid != null) {
                            Fluid fluid = FluidRegistry.getFluid(line.fluid);
                            return fluid == null ? null : fluid.getLocalizedName();
                        }
                        ItemStack stack = item(line.id, line.meta);
                        return stack == null ? null : stackName(stack);
                    }
                    default: {
                        Block block = SettingsPictures.block(line.id);
                        if (block == null) return null;
                        Item item = Item.getItemFromBlock(block);
                        if (item != null) {
                            String named = stackName(new ItemStack(item, 1, line.meta >= 0 ? line.meta : 0));
                            if (named != null) return named;
                        }
                        return block.getLocalizedName();
                    }
                }
            } catch (Throwable hostile) {
                return null;
            }
        }

        @Override
        public Object sprite(String name) {
            return new Sprite(name);
        }

        @Override
        public Object family(String key) {
            SurfaceFamily family = SurfaceFamily.byKey(key);
            if (family == null) return null;
            try {
                return blockPicture(CommandTrmt.roadBlock(family), 0);
            } catch (Throwable hostile) {
                return null;
            }
        }

        @Override
        public List<PickerList.Entry> entries(PickerList.Kind kind) {
            long started = System.nanoTime();
            PickerList.Gathering gathering = gathering(kind);
            // The rest of this frame's slice, never a fresh one: frame() has stepped it already, and a second slice
            // here gave a frame two (0.9.228).
            if (!gathering.finished() && SLICE.more()) gathered = gathering.step(SLICE);
            gatherNanos += System.nanoTime() - started;
            return gathering.entries();
        }

        @Override
        public void pick(PickerList.Kind kind, Collection<String> held, Consumer<String> chosen) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new WornPathPicker(mc.currentScreen, kind, held, chosen));
        }

        @Override
        public String clipboard() {
            return GuiScreen.getClipboardString();
        }

        @Override
        public void clipboard(String text) {
            GuiScreen.setClipboardString(text);
        }

        @Override
        public long now() {
            return System.nanoTime() / 1000000L;
        }

        @Override
        public boolean ctrl() {
            return GuiScreen.isCtrlKeyDown();
        }

        @Override
        public boolean shift() {
            return GuiScreen.isShiftKeyDown();
        }

        @Override
        public boolean alt() {
            return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
        }
    };

    // ------------------------------------------------------------------
    // What each picker offers
    // ------------------------------------------------------------------

    /** A picker's entries, gathered once a game session, started the first time it is asked for (Part 6). */
    static PickerList.Gathering gathering(PickerList.Kind kind) {
        PickerList.Gathering gathering = GATHERED.get(kind);
        if (gathering == null) {
            long started = System.nanoTime();
            gathering = start(kind);
            startNanos = System.nanoTime() - started;
            GATHERED.put(kind, gathering);
        }
        return gathering;
    }

    private static PickerList.Gathering start(final PickerList.Kind kind) {
        List<Object> sources = new ArrayList<Object>();
        Function<Object, List<PickerList.Entry>> make;
        try {
            switch (kind) {
                case BLOCK:
                case BLOCK_NO_META:
                    for (Object block : Block.blockRegistry) sources.add(block);
                    make = new Function<Object, List<PickerList.Entry>>() {

                        @Override
                        public List<PickerList.Entry> apply(Object source) {
                            return blockEntries((Block) source, kind == PickerList.Kind.BLOCK);
                        }
                    };
                    break;
                case ITEM:
                    for (Object item : Item.itemRegistry) sources.add(item);
                    make = new Function<Object, List<PickerList.Entry>>() {

                        @Override
                        public List<PickerList.Entry> apply(Object source) {
                            return itemEntries((Item) source);
                        }
                    };
                    break;
                case FLUID:
                    sources.addAll(
                        FluidRegistry.getRegisteredFluids()
                            .keySet());
                    make = new Function<Object, List<PickerList.Entry>>() {

                        @Override
                        public List<PickerList.Entry> apply(Object source) {
                            String name = (String) source;
                            Fluid fluid = FluidRegistry.getFluid(name);
                            String line = ListLine.FLUID_PREFIX + name;
                            return Collections.singletonList(
                                new PickerList.Entry(
                                    line,
                                    null,
                                    fluid == null ? name : fluid.getLocalizedName(),
                                    line,
                                    new FluidPicture(name)));
                        }
                    };
                    break;
                case ENCHANTMENT:
                    for (Enchantment one : Enchantment.enchantmentsList) {
                        if (one != null) sources.add(one);
                    }
                    make = new Function<Object, List<PickerList.Entry>>() {

                        @Override
                        public List<PickerList.Entry> apply(Object source) {
                            Enchantment enchantment = (Enchantment) source;
                            String name = enchantment.getName();
                            String line = name.startsWith("enchantment.") ? name.substring("enchantment.".length())
                                : name;
                            return Collections.singletonList(
                                new PickerList.Entry(
                                    line,
                                    null,
                                    enchantmentName(enchantment),
                                    line,
                                    new Stack(new ItemStack(Items.enchanted_book))));
                        }
                    };
                    break;
                case MOB:
                    sources.addAll(MobNames.offered(MobPictures.registered(), ""));
                    make = new Function<Object, List<PickerList.Entry>>() {

                        @Override
                        public List<PickerList.Entry> apply(Object source) {
                            String name = (String) source;
                            return Collections.singletonList(
                                new PickerList.Entry(name, null, mobName(name), name, new Mob(name, false)));
                        }
                    };
                    break;
                case SPRITE:
                    sources.addAll(spriteNames());
                    make = new Function<Object, List<PickerList.Entry>>() {

                        @Override
                        public List<PickerList.Entry> apply(Object source) {
                            String name = (String) source;
                            return Collections
                                .singletonList(new PickerList.Entry(name, null, name, name, new Sprite(name)));
                        }
                    };
                    break;
                case DIMENSION:
                    sources.addAll(dimensionIds());
                    make = new Function<Object, List<PickerList.Entry>>() {

                        @Override
                        public List<PickerList.Entry> apply(Object source) {
                            int id = ((Integer) source).intValue();
                            String name = dimensionName(id);
                            if (name == null) name = NameStatus.vanillaDimensionName(id);
                            return Collections.singletonList(
                                new PickerList.Entry(
                                    String.valueOf(id),
                                    null,
                                    name,
                                    String.valueOf(id),
                                    dimensionPicture(Integer.valueOf(id))));
                        }
                    };
                    break;
                case ORE:
                    sources.addAll(Arrays.asList(OreDictionary.getOreNames()));
                    make = new Function<Object, List<PickerList.Entry>>() {

                        @Override
                        public List<PickerList.Entry> apply(Object source) {
                            String name = (String) source;
                            ItemStack first = oreFirst(name);
                            return Collections.singletonList(
                                new PickerList.Entry(
                                    name,
                                    null,
                                    name,
                                    first == null ? name : stackName(first),
                                    orePicture(name)));
                        }
                    };
                    break;
                case POOL:
                default:
                    sources.addAll(
                        Arrays.asList(
                            ChestGenHooks.MINESHAFT_CORRIDOR,
                            ChestGenHooks.PYRAMID_DESERT_CHEST,
                            ChestGenHooks.PYRAMID_JUNGLE_CHEST,
                            ChestGenHooks.PYRAMID_JUNGLE_DISPENSER,
                            ChestGenHooks.STRONGHOLD_CORRIDOR,
                            ChestGenHooks.STRONGHOLD_LIBRARY,
                            ChestGenHooks.STRONGHOLD_CROSSING,
                            ChestGenHooks.VILLAGE_BLACKSMITH,
                            ChestGenHooks.BONUS_CHEST,
                            ChestGenHooks.DUNGEON_CHEST));
                    make = new Function<Object, List<PickerList.Entry>>() {

                        @Override
                        public List<PickerList.Entry> apply(Object source) {
                            String name = (String) source;
                            return Collections.singletonList(new PickerList.Entry(name, null, name, name, null));
                        }
                    };
            }
        } catch (Throwable hostile) {
            sources.clear();
            make = new Function<Object, List<PickerList.Entry>>() {

                @Override
                public List<PickerList.Entry> apply(Object source) {
                    return Collections.emptyList();
                }
            };
        }
        return new PickerList.Gathering(sources, make);
    }

    /**
     * One block's entries: each variant {@code getSubBlocks} gives, by its own name and picture, in a try of its own
     * as {@code BlockIconArrayEntry} guards a block; a block with no item, once, by its texture.
     */
    static List<PickerList.Entry> blockEntries(Block block, boolean takesMeta) {
        if (block == null || block == Blocks.air) return Collections.emptyList();
        String id = String.valueOf(Block.blockRegistry.getNameForObject(block));
        List<PickerList.Variant> variants = new ArrayList<PickerList.Variant>();
        Item item = hasItem(block) ? Item.getItemFromBlock(block) : null;
        if (item != null) {
            List<ItemStack> subs = new ArrayList<ItemStack>();
            try {
                block.getSubBlocks(item, block.getCreativeTabToDisplayOn(), subs);
            } catch (Throwable hostile) {
                subs.clear();
            }
            for (ItemStack sub : subs) {
                if (sub == null || sub.getItem() == null) continue;
                int meta = sub.getItemDamage();
                if (meta == OreDictionary.WILDCARD_VALUE) meta = 0;
                variants.add(new PickerList.Variant(meta, stackName(sub), new Stack(sub)));
            }
            if (variants.isEmpty()) {
                ItemStack plain = new ItemStack(item, 1, 0);
                variants.add(new PickerList.Variant(0, stackName(plain), new Stack(plain)));
            }
        } else {
            String name;
            try {
                name = block.getLocalizedName();
            } catch (Throwable hostile) {
                name = id;
            }
            variants.add(new PickerList.Variant(0, name, new Face(block, 0)));
        }
        return PickerList.forBlock(id, variants, takesMeta);
    }

    /**
     * One item's entries: its variants by {@code getSubItems} where it has subtypes, each as {@code modid:name:meta},
     * with the item's bare name - which matches every metadata, as {@code ReinforceCost.resolve} reads it - as the
     * second button; an item without subtypes by its name alone.
     */
    static List<PickerList.Entry> itemEntries(Item item) {
        if (item == null) return Collections.emptyList();
        String id = String.valueOf(Item.itemRegistry.getNameForObject(item));
        List<ItemStack> subs = new ArrayList<ItemStack>();
        if (item.getHasSubtypes()) {
            try {
                item.getSubItems(item, item.getCreativeTab(), subs);
            } catch (Throwable hostile) {
                subs.clear();
            }
        }
        List<PickerList.Entry> out = new ArrayList<PickerList.Entry>();
        if (subs.size() <= 1) {
            ItemStack plain = subs.isEmpty() ? new ItemStack(item, 1, 0) : subs.get(0);
            out.add(new PickerList.Entry(id, null, stackName(plain), id, new Stack(plain)));
            return out;
        }
        for (ItemStack sub : subs) {
            if (sub == null || sub.getItem() == null) continue;
            int meta = sub.getItemDamage() == OreDictionary.WILDCARD_VALUE ? 0 : sub.getItemDamage();
            String line = ListLine.block(id, meta);
            out.add(new PickerList.Entry(line, id, stackName(sub), line, new Stack(sub)));
        }
        return out;
    }
}
