package com.trmtgtnh.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

import com.trmtgtnh.client.settings.ListLine;
import com.trmtgtnh.client.settings.NameStatus;
import com.trmtgtnh.client.settings.PictureBudget;
import com.trmtgtnh.client.settings.Slicer;
import com.trmtgtnh.client.texture.WearPatterns;
import com.trmtgtnh.command.CommandTrmt;
import com.trmtgtnh.item.ModLoot;
import com.trmtgtnh.server.ReinforceCost;
import com.trmtgtnh.surface.SurfaceFamily;
import com.trmtgtnh.util.OldNames;
import com.trmtgtnh.util.OreNames;

/**
 * This edition's answers about what the game has registered, for the settings screen's lists (spec Part 3.2, M3, M4,
 * Part 6): the shared {@link NameStatus.Lookup}, every list picture and how it is drawn, the names things go by, and
 * what each picker offers, gathered a slice a frame. The 1.7.10 edition's class of this name, asked of this game.
 *
 * <p>
 * <b>Every answer is asked of the game the way the game's own reader asks it</b>, so the screen says what the game will
 * do. A block by its registry name, a name 1.13 retired read as the block its metadata says it became, as
 * {@code SurfaceRegistry.applyList} reads one - a metadata suffix on any other name is accepted and ignored, as there,
 * variants being blocks of their own at this version; an item by its registry name, the suffix dropped, as
 * {@code MaterialCost} and {@code ReinforceCost} drop it, and a {@code #namespace:path} line as the item tag
 * {@code ReinforceCost} reads; a fluid as {@code ReinforceCost.entryExists} finds one; an {@code ore:} or a grade's ore
 * as the item tag {@code OreNames} makes of it, which {@code HealingCost} and the tamper grades match against; an
 * enchantment fragment by {@code HealingXp}'s flattening of its description id; a texture as the inner layers read
 * one, the file {@code WearPatterns.blockTextureFile} names; a dimension by the name the client's levels go by, as
 * the settings read a dimension by name ({@code TrmtConfig.dimensionNamesIn}).
 *
 * <p>
 * <b>A mod's code can throw from any of it</b>, inside a settings screen, so every question is asked in a try and a
 * throw is an answer of "not here". Pictures go through {@link PictureBudget}s kept for the game session: at most 24
 * new block or item pictures a frame and 2 new mobs (Part 6), a picture that threw never asked again.
 */
public final class SettingsPictures {

    private SettingsPictures() {}

    /** New block and item pictures a frame, and new mobs (spec Part 6). */
    static final PictureBudget ITEMS = new PictureBudget(24);

    static final PictureBudget MOBS = new PictureBudget(2);

    /** A frame's slice for gathering a picker's entries: 4 ms (Part 6, M4.5). */
    static final Slicer SLICE = new Slicer(4000000L, System::nanoTime);

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

    /** What each picker offers, gathered once a game session. */
    private static final Map<PickerList.Kind, PickerList.Gathering> GATHERED = new EnumMap<PickerList.Kind, PickerList.Gathering>(
        PickerList.Kind.class);

    /**
     * A fluid's still texture on the block atlas, as each loader names it - Forge's {@code FluidAttributes}, Fabric's
     * {@code FluidRenderHandlerRegistry} (spec 3.2) - handed in by each loader's client start; null until then, when a
     * fluid with no bucket draws nothing.
     */
    private static volatile Function<Fluid, ResourceLocation> stillTexture;

    /** Called once by each loader's client start: how that loader names a fluid's still texture. */
    public static void useStillTextures(Function<Fluid, ResourceLocation> named) {
        stillTexture = named;
    }

    /**
     * A fluid's own color, as each loader names it - Forge's {@code FluidAttributes.getColor}, Fabric API's fluid render
     * handler ({@code getFluidColor}) - handed in by each loader's client start beside the still texture; white until
     * then (0.9.228).
     */
    private static volatile java.util.function.ToIntFunction<Fluid> tintOf;

    /** Called once by each loader's client start: how that loader names a fluid's color. */
    public static void useStillTints(java.util.function.ToIntFunction<Fluid> tinted) {
        tintOf = tinted;
    }

    /**
     * The color a fluid's still texture is multiplied by, as the game's renderer multiplies it (0.9.228, 1.7.10's
     * {@code icon(still, x, y, fluid.getColor())}): red, green and blue, the alpha left to the texture. White where the
     * loader names none or its handler throws, asked as it is here with no world and no square.
     */
    static int stillTint(Fluid fluid) {
        java.util.function.ToIntFunction<Fluid> tinted = tintOf;
        if (tinted == null) return 0xFFFFFF;
        try {
            return tinted.applyAsInt(fluid) & 0xFFFFFF;
        } catch (Throwable hostile) {
            return 0xFFFFFF;
        }
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
            this.key = String.valueOf(Registry.ITEM.getKey(stack.getItem()));
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

    /** A block with no item of its own, drawn by its particle texture (survey 2.3, spec 3.2). */
    static final class Face {

        final Block block;

        Face(Block block) {
            this.block = block;
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

    /** Several pictures in turn, one a second, as a tag's members are shown (survey 2.3). */
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
    static void draw(PoseStack pose, Object ref, int x, int y) {
        try {
            if (ref instanceof Cycle) {
                List<Object> all = ((Cycle) ref).pictures;
                if (!all.isEmpty()) draw(pose, all.get((int) ((System.currentTimeMillis() / 1000L) % all.size())), x, y);
            } else if (ref instanceof Stack) {
                Stack stack = (Stack) ref;
                if (!ITEMS.mayTry(stack.key)) return;
                WearIcons.drawIcon(stack.key, stack.stack, x, y);
                if (WearIcons.broken(stack.key)) ITEMS.failed(stack.key);
                else ITEMS.fine(stack.key);
            } else if (ref instanceof Face) {
                Face face = (Face) ref;
                sprite(
                    pose,
                    Minecraft.getInstance()
                        .getBlockRenderer()
                        .getBlockModelShaper()
                        .getParticleIcon(face.block.defaultBlockState()),
                    x,
                    y);
            } else if (ref instanceof Sprite) {
                TextureAtlasSprite sprite = atlasSprite(((Sprite) ref).name);
                if (sprite != null) sprite(pose, sprite, x, y);
            } else if (ref instanceof FluidPicture) {
                Fluid fluid = fluid(((FluidPicture) ref).name);
                if (fluid == null) return;
                Item bucket = fluid.getBucket();
                if (bucket != null && bucket != Items.AIR) {
                    draw(pose, new Stack(new ItemStack(bucket)), x, y);
                    return;
                }
                Function<Fluid, ResourceLocation> named = stillTexture;
                ResourceLocation still = named == null ? null : named.apply(fluid);
                TextureAtlasSprite sprite = still == null ? null : atlas().getSprite(still);
                if (sprite != null && !MissingTextureAtlasSprite.getLocation()
                    .equals(sprite.getName())) sprite(pose, sprite, x, y, stillTint(fluid));
            } else if (ref instanceof Mob) {
                Mob mob = (Mob) ref;
                if (mob.any) {
                    MobPictures.drawGenericEgg(pose, x, y);
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
                MobPictures.draw(pose, mob.line, x, y);
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
            // A mod's texture or model lookup that throws draws nothing; the row says what it is in words.
        } finally {
            RenderSystem.color4f(1F, 1F, 1F, 1F);
        }
    }

    private static TextureAtlas atlas() {
        return Minecraft.getInstance()
            .getModelManager()
            .getAtlas(InventoryMenu.BLOCK_ATLAS);
    }

    /** A sprite of the block atlas by its name, or null where the atlas has none of that name. */
    static TextureAtlasSprite atlasSprite(String name) {
        ResourceLocation at = spriteLocation(name);
        if (at == null) return null;
        TextureAtlasSprite sprite = atlas().getSprite(at);
        return sprite == null || MissingTextureAtlasSprite.getLocation()
            .equals(sprite.getName()) ? null : sprite;
    }

    /**
     * The block atlas's name for a texture as the inner layers name one: the file {@code WearPatterns.blockTextureFile}
     * reads, without its {@code textures/} and {@code .png}.
     */
    static ResourceLocation spriteLocation(String name) {
        try {
            ResourceLocation file = WearPatterns.blockTextureFile(name.trim());
            String path = file.getPath();
            if (path.startsWith("textures/")) path = path.substring("textures/".length());
            if (path.endsWith(".png")) path = path.substring(0, path.length() - ".png".length());
            return new ResourceLocation(file.getNamespace(), path);
        } catch (RuntimeException unnamable) {
            return null;
        }
    }

    /** A sprite of the block atlas as a 16 by 16 picture, blended. */
    private static void sprite(PoseStack pose, TextureAtlasSprite sprite, int x, int y) {
        if (sprite == null) return;
        Minecraft.getInstance()
            .getTextureManager()
            .bind(InventoryMenu.BLOCK_ATLAS);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1F, 1F, 1F, 1F);
        GuiComponent.blit(pose, x, y, 0, 16, 16, sprite);
    }

    /**
     * A sprite of the block atlas multiplied by {@code tint}, as the game multiplies a fluid's still texture by the
     * fluid's own color (0.9.228): a fluid with no bucket is drawn by its still texture, and one whose texture is grey
     * takes its look from its color, so untinted it was a grey square in the fluid picker.
     */
    private static void sprite(PoseStack pose, TextureAtlasSprite sprite, int x, int y, int tint) {
        if (sprite == null) return;
        Minecraft.getInstance()
            .getTextureManager()
            .bind(InventoryMenu.BLOCK_ATLAS);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(((tint >> 16) & 0xFF) / 255F, ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, 1F);
        GuiComponent.blit(pose, x, y, 0, 16, 16, sprite);
        RenderSystem.color4f(1F, 1F, 1F, 1F);
    }

    // ------------------------------------------------------------------
    // What the game has
    // ------------------------------------------------------------------

    /** A block by its registry name as it stands, or null; air is no block a list can mean. */
    private static Block registered(String name) {
        try {
            ResourceLocation at = ResourceLocation.tryParse(name.trim()
                .toLowerCase(Locale.ROOT));
            if (at == null) return null;
            Block block = Registry.BLOCK.getOptional(at)
                .orElse(null);
            return block == null || block == Blocks.AIR ? null : block;
        } catch (Throwable hostile) {
            return null;
        }
    }

    /**
     * A block by its name as the block lists read it, at a metadata ({@link ListLine#NO_META} for none): a name 1.13
     * retired, as the block its metadata - or its first - became, as {@code OldNames.one} reads it; any other name as
     * it stands, the metadata ignored. Null where nothing answers.
     */
    static Block block(String id, int meta) {
        if (id == null) return null;
        try {
            return registered(OldNames.one(id.trim(), meta));
        } catch (Throwable hostile) {
            return null;
        }
    }

    /** Whether a name answers to any block, every variant an old name became asked. */
    static boolean anyBlock(String id) {
        if (id == null) return false;
        try {
            for (String each : OldNames.orSelf(id.trim(), -1)) {
                if (registered(each) != null) return true;
            }
        } catch (Throwable hostile) {
            return false;
        }
        return false;
    }

    /** An item by its name, a metadata suffix having been dropped, as {@code MaterialCost} reads one; or null. */
    static ItemStack item(String id) {
        if (id == null) return null;
        try {
            ResourceLocation at = ResourceLocation.tryParse(id.trim());
            if (at == null) return null;
            Item item = Registry.ITEM.getOptional(at)
                .orElse(null);
            return item == null || item == Items.AIR ? null : new ItemStack(item);
        } catch (Throwable hostile) {
            return null;
        }
    }

    /** The items an item tag holds, for a {@code #namespace:path} line, as {@code ReinforceCost} reads one. */
    static List<Item> tagged(String line) {
        try {
            ResourceLocation at = ResourceLocation.tryParse(line.trim()
                .substring(1));
            if (at == null) return Collections.emptyList();
            net.minecraft.tags.Tag<Item> tag = ItemTags.getAllTags()
                .getTag(at);
            return tag == null ? Collections.<Item>emptyList() : new ArrayList<Item>(tag.getValues());
        } catch (Throwable notThere) {
            return Collections.emptyList();
        }
    }

    /** A fluid by its registry name, or a bare name as the game's own, as {@code ReinforceCost} reads it; or null. */
    static Fluid fluid(String name) {
        if (name == null || name.trim()
            .isEmpty()) return null;
        try {
            ResourceLocation at = ResourceLocation.tryParse(name.trim());
            if (at == null) return null;
            Fluid fluid = Registry.FLUID.getOptional(at)
                .orElse(null);
            return fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY ? null : fluid;
        } catch (Throwable hostile) {
            return null;
        }
    }

    /** Lower case, with the spaces, dots and underscores {@code HealingXp} takes out of both sides. */
    static String flat(String name) {
        return name == null ? ""
            : name.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace(".", "")
                .replace(" ", "");
    }

    /** A dimension name as the list reads one: lower case, the game's own namespace where it gives none. */
    static String dimensionKey(String name) {
        String one = name == null ? ""
            : name.trim()
                .toLowerCase(Locale.ROOT);
        if (one.isEmpty()) return "";
        return one.indexOf(':') < 0 ? "minecraft:" + one : one;
    }

    /** The levels the client knows, by name, or none without a world. */
    static List<String> dimensionKeys() {
        List<String> out = new ArrayList<String>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getConnection() == null || mc.level == null) return out;
            for (ResourceKey<Level> key : mc.getConnection()
                .levels()) {
                out.add(
                    key.location()
                        .toString());
            }
            Collections.sort(out);
        } catch (Throwable hostile) {
            return out;
        }
        return out;
    }

    /**
     * A dimension's shown name: the vanilla three by their names, as the 1.7.10 edition's providers name them, any other
     * by its path in words - {@code twilightforest:twilight_forest} is "Twilight Forest".
     */
    static String dimensionShown(String key) {
        Integer vanilla = NameStatus.vanillaDimensionId(key);
        if (vanilla != null) return NameStatus.vanillaDimensionName(vanilla.intValue());
        String path = key.indexOf(':') >= 0 ? key.substring(key.indexOf(':') + 1) : key;
        StringBuilder out = new StringBuilder();
        for (String word : path.replace('/', ' ')
            .replace('_', ' ')
            .split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)))
                .append(word.substring(1));
        }
        return out.toString();
    }

    /** The names of the block textures the packs hold, as the inner layers read a texture; empty before resources. */
    static List<String> spriteNames() {
        List<String> out = new ArrayList<String>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourceManager() == null) return out;
            for (ResourceLocation file : mc.getResourceManager()
                .listResources("textures/block", path -> path.endsWith(".png"))) {
                if (com.trmtgtnh.Trmt.MODID.equals(file.getNamespace())) continue;
                String path = file.getPath()
                    .substring("textures/".length());
                out.add(file.getNamespace() + ":" + path.substring(0, path.length() - ".png".length()));
            }
            Collections.sort(out);
        } catch (Throwable hostile) {
            return out;
        }
        return out;
    }

    /** The game's answers, for {@link NameStatus}. */
    static final NameStatus.Lookup LOOKUP = new NameStatus.Lookup() {

        @Override
        public boolean block(String id) {
            return anyBlock(id);
        }

        @Override
        public boolean blockHasItem(String id, int meta) {
            Block block = SettingsPictures.block(id, meta);
            try {
                return hasItem(block);
            } catch (Throwable hostile) {
                return false;
            }
        }

        @Override
        public boolean metaExists(String id, int meta) {
            // No block has a metadata at this version: a suffix is accepted and ignored, and on a name 1.13 retired it
            // chooses which block the name became (SurfaceRegistry.applyList).
            return SettingsPictures.block(id, meta) != null;
        }

        @Override
        public int oreMembers(String name) {
            try {
                return name == null ? 0
                    : OreNames.itemsFor(name.trim())
                        .size();
            } catch (Throwable hostile) {
                return 0;
            }
        }

        @Override
        public boolean fluid(String name) {
            try {
                return name != null && ReinforceCost.entryExists(ListLine.FLUID_PREFIX + name.trim());
            } catch (Throwable hostile) {
                return false;
            }
        }

        @Override
        public boolean item(String id, int meta) {
            if (id == null) return false;
            if (id.trim()
                .startsWith("#")) {
                return !tagged(id).isEmpty();
            }
            return SettingsPictures.item(id) != null;
        }

        @Override
        public boolean mob(String name) {
            try {
                return name != null && !MobRows.unknown(name, MobPictures.registered());
            } catch (Throwable hostile) {
                return false;
            }
        }

        @Override
        public List<String> enchantMatches(String fragment) {
            List<String> out = new ArrayList<String>();
            String want = flat(fragment == null ? "" : fragment.trim());
            if (want.isEmpty()) return out;
            try {
                for (Enchantment enchantment : Registry.ENCHANTMENT) {
                    if (enchantment == null || enchantment.getDescriptionId() == null) continue;
                    if (flat(enchantment.getDescriptionId()).contains(want)) out.add(enchantmentName(enchantment));
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
                if (atlasSprite(name) != null) return true;
                // Not on the atlas by that name: the file the inner layers read it from, read as they read it.
                Minecraft mc = Minecraft.getInstance();
                return mc != null && mc.getResourceManager() != null
                    && WearPatterns.readIcon(mc.getResourceManager(), name.trim()) != null;
            } catch (Throwable notThere) {
                return false;
            }
        }

        @Override
        public String dimension(int id) {
            // Only the vanilla three have a number here, which NameStatus names itself; a modded number cannot be
            // honoured at this version (Dimensions), so it has no name.
            return null;
        }

        @Override
        public Integer dimensionId(String name) {
            return null;
        }

        @Override
        public String dimensionName(String name) {
            String key = dimensionKey(name);
            if (key.isEmpty()) return null;
            for (String known : dimensionKeys()) {
                if (known.equals(key)) return dimensionShown(known);
            }
            return null;
        }

        @Override
        public boolean oreIsTag() {
            return true;
        }

        @Override
        public boolean writesDimensionNames() {
            return true;
        }

        @Override
        public boolean dimensionsKnown() {
            try {
                Minecraft mc = Minecraft.getInstance();
                return mc != null && mc.level != null && mc.getConnection() != null;
            } catch (Throwable hostile) {
                return false;
            }
        }
    };

    /** An enchantment's own name, as a book's tooltip gives it, without a level. */
    static String enchantmentName(Enchantment enchantment) {
        try {
            return I18n.get(enchantment.getDescriptionId());
        } catch (Throwable hostile) {
            return enchantment.getDescriptionId();
        }
    }

    /**
     * Whether a block has an item of its own (0.9.228): an item, and not one that only places it under another name -
     * the carrot that plants minecraft:carrots, the seeds a crop, the string tripwire - which the 1.7.10 and 1.12.2
     * editions do not give such a block either. Such a block is drawn by its texture and said to have no item of its
     * own, as theirs is.
     */
    static boolean hasItem(Block block) {
        if (block == null) return false;
        try {
            Item item = block.asItem();
            return ownItem(item != null && item != Items.AIR, item instanceof ItemNameBlockItem);
        } catch (Throwable hostile) {
            return false;
        }
    }

    /** The rule of {@link #hasItem}, plain: an item that places the block under another name is not its own. */
    static boolean ownItem(boolean any, boolean namedOtherwise) {
        return any && !namedOtherwise;
    }

    /** A block's picture: its item where it has one, else its texture (survey 2.3). */
    /**
     * A dimension's picture (0.9.228; Xep, from "Choose a dimension" in GT New Horizons: every row an empty frame): the
     * vanilla three by their ground - grass, netherrack, end stone - and any other by the portal, so no dimension is
     * drawn as an empty frame. The portal is the default for a mod's dimension, for Xep to change.
     */
    static Object dimensionPicture(Integer id, String name) {
        int which = DimensionPictures.of(id != null ? id : NameStatus.vanillaDimensionId(name));
        if (which == DimensionPictures.OVERWORLD) return blockPicture(Blocks.GRASS_BLOCK);
        if (which == DimensionPictures.NETHER) return blockPicture(Blocks.NETHERRACK);
        if (which == DimensionPictures.END) return blockPicture(Blocks.END_STONE);
        return new Sprite(PORTAL_SPRITE);
    }

    /** The portal's sprite on the block atlas, the picture of a dimension that is not one of the vanilla three. */
    static final String PORTAL_SPRITE = "nether_portal";

    static Object blockPicture(Block block) {
        if (block == null) return null;
        try {
            if (hasItem(block)) return new Stack(new ItemStack(block.asItem()));
        } catch (Throwable hostile) {
            return new Face(block);
        }
        return new Face(block);
    }

    private static String stackName(ItemStack stack) {
        try {
            return stack.getHoverName()
                .getString();
        } catch (Throwable hostile) {
            return null;
        }
    }

    private static String blockName(Block block) {
        try {
            if (hasItem(block)) {
                String named = stackName(new ItemStack(block.asItem()));
                if (named != null) return named;
            }
            return block.getName()
                .getString();
        } catch (Throwable hostile) {
            return null;
        }
    }

    /** A mob's name as the game translates it, or its registry name. */
    static String mobName(String name) {
        try {
            for (String each : ConfigLayout.mobNames(name)) {
                EntityType<?> type = Registry.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(each))
                    .orElse(null);
                if (type != null) return type.getDescription()
                    .getString();
            }
        } catch (Throwable hostile) {
            return name;
        }
        return name;
    }

    /** A tag's members' pictures, in turn: an ore name as the tag {@code OreNames} makes of it. */
    private static Object orePicture(String ore) {
        List<Item> members;
        try {
            members = ore == null ? Collections.<Item>emptyList() : OreNames.itemsFor(ore.trim());
        } catch (Throwable hostile) {
            return null;
        }
        return cycle(members);
    }

    private static Object cycle(List<Item> members) {
        List<Object> pictures = new ArrayList<Object>();
        for (Item member : members) {
            if (member == null || member == Items.AIR) continue;
            pictures.add(new Stack(new ItemStack(member)));
            if (pictures.size() >= 16) break;
        }
        return pictures.isEmpty() ? null : new Cycle(pictures);
    }

    private static ItemStack oreFirst(String ore) {
        try {
            List<Item> members = OreNames.itemsFor(ore.trim());
            return members.isEmpty() ? null : new ItemStack(members.get(0));
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
                    case TAG:
                        return orePicture(line.ore);
                    case FLUID:
                        return new FluidPicture(line.fluid);
                    case ENCHANTMENTS:
                        return new Stack(new ItemStack(Items.ENCHANTED_BOOK));
                    case DIMENSION:
                        return dimensionPicture(line.dimension, line.id);
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
                    if (line.id != null && line.id.trim()
                        .startsWith("#")) return cycle(tagged(line.id));
                    ItemStack stack = item(line.id);
                    return stack == null ? null : new Stack(stack);
                }
                default:
                    return blockPicture(block(line.id, line.meta >= 0 ? line.meta : 0));
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
                        ItemStack first = line.ore == null ? null : oreFirst(line.ore);
                        return first == null ? line.id : line.id + " - " + stackName(first);
                    }
                    case ITEM:
                    case ITEM_OR_FLUID: {
                        if (line.fluid != null) {
                            Fluid fluid = fluid(line.fluid);
                            if (fluid == null) fluid = fluid("minecraft:" + line.fluid);
                            return fluid == null ? null : fluidName(fluid);
                        }
                        if (line.id != null && line.id.trim()
                            .startsWith("#")) {
                            List<Item> members = tagged(line.id);
                            return members.isEmpty() ? null : stackName(new ItemStack(members.get(0)));
                        }
                        ItemStack stack = item(line.id);
                        return stack == null ? null : stackName(stack);
                    }
                    default: {
                        Block block = block(line.id, line.meta >= 0 ? line.meta : 0);
                        return block == null ? null : blockName(block);
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
                return blockPicture(CommandTrmt.roadBlock(family));
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
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new WornPathPicker(mc.screen, kind, held, chosen));
        }

        @Override
        public String clipboard() {
            return Minecraft.getInstance().keyboardHandler.getClipboard();
        }

        @Override
        public void clipboard(String text) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        }

        @Override
        public long now() {
            return System.nanoTime() / 1000000L;
        }

        @Override
        public boolean ctrl() {
            return Screen.hasControlDown();
        }

        @Override
        public boolean shift() {
            return Screen.hasShiftDown();
        }

        @Override
        public boolean alt() {
            return Screen.hasAltDown();
        }
    };

    /** A fluid's own name, as its bucket or its block calls it. */
    private static String fluidName(Fluid fluid) {
        try {
            Item bucket = fluid.getBucket();
            if (bucket != null && bucket != Items.AIR) {
                String named = stackName(new ItemStack(bucket));
                if (named != null) return named;
            }
            return fluid.defaultFluidState()
                .createLegacyBlock()
                .getBlock()
                .getName()
                .getString();
        } catch (Throwable hostile) {
            return String.valueOf(Registry.FLUID.getKey(fluid));
        }
    }

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
                    for (Block block : Registry.BLOCK) sources.add(block);
                    make = source -> blockEntries((Block) source);
                    break;
                case ITEM:
                    for (Item item : Registry.ITEM) sources.add(item);
                    make = source -> itemEntries((Item) source);
                    break;
                case FLUID:
                    for (Fluid fluid : Registry.FLUID) {
                        // The still fluids, which a line names; the flowing ones are their other halves.
                        if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY
                            && fluid.isSource(fluid.defaultFluidState())) sources.add(fluid);
                    }
                    make = source -> {
                        Fluid fluid = (Fluid) source;
                        String name = String.valueOf(Registry.FLUID.getKey(fluid));
                        String line = ListLine.FLUID_PREFIX + name;
                        return Collections.singletonList(
                            new PickerList.Entry(line, null, fluidName(fluid), line, new FluidPicture(name)));
                    };
                    break;
                case ENCHANTMENT:
                    for (Enchantment one : Registry.ENCHANTMENT) {
                        if (one != null) sources.add(one);
                    }
                    make = source -> {
                        Enchantment enchantment = (Enchantment) source;
                        // The registry name's path: a fragment HealingXp's flattened match finds this enchantment by.
                        ResourceLocation id = Registry.ENCHANTMENT.getKey(enchantment);
                        String line = id == null ? enchantment.getDescriptionId() : id.getPath();
                        return Collections.singletonList(
                            new PickerList.Entry(
                                line,
                                null,
                                enchantmentName(enchantment),
                                line,
                                new Stack(new ItemStack(Items.ENCHANTED_BOOK))));
                    };
                    break;
                case MOB:
                    sources.addAll(MobPictures.registered());
                    make = source -> {
                        String name = (String) source;
                        return Collections.singletonList(
                            new PickerList.Entry(name, null, mobName(name), name, new Mob(name, false)));
                    };
                    break;
                case SPRITE:
                    sources.addAll(spriteNames());
                    make = source -> {
                        String name = (String) source;
                        return Collections
                            .singletonList(new PickerList.Entry(name, null, name, name, new Sprite(name)));
                    };
                    break;
                case DIMENSION: {
                    List<String> known = dimensionKeys();
                    if (known.isEmpty()) {
                        // No world to ask: the three the game always has.
                        known = new ArrayList<String>();
                        known.add("minecraft:overworld");
                        known.add("minecraft:the_nether");
                        known.add("minecraft:the_end");
                    }
                    sources.addAll(known);
                    make = source -> {
                        String key = (String) source;
                        return Collections
                            .singletonList(new PickerList.Entry(key, null, dimensionShown(key), key, dimensionPicture(null, key)));
                    };
                    break;
                }
                case ORE:
                    // Every item tag, written as its name, which OreNames takes as written.
                    for (ResourceLocation tag : ItemTags.getAllTags()
                        .getAvailableTags()) sources.add(tag.toString());
                    Collections.sort((List) sources);
                    make = source -> {
                        String name = (String) source;
                        ItemStack first = oreFirst(name);
                        return Collections.singletonList(
                            new PickerList.Entry(
                                name,
                                null,
                                name,
                                first == null ? name : stackName(first),
                                orePicture(name)));
                    };
                    break;
                case POOL:
                default:
                    // The chest categories by 1.7.10's names, as this edition's loot reads them (ModLoot).
                    sources.addAll(ModLoot.categories());
                    make = source -> {
                        String name = (String) source;
                        return Collections.singletonList(new PickerList.Entry(name, null, name, name, null));
                    };
            }
        } catch (Throwable hostile) {
            sources.clear();
            make = source -> Collections.<PickerList.Entry>emptyList();
        }
        return new PickerList.Gathering(sources, make);
    }

    /**
     * One block's entry: its registry name, its own name and its picture - its item where it has one, its texture
     * where it has none. A block has no variants at this version, so every block is one line, on every block list.
     */
    static List<PickerList.Entry> blockEntries(Block block) {
        if (block == null || block == Blocks.AIR) return Collections.emptyList();
        String id = String.valueOf(Registry.BLOCK.getKey(block));
        String name = blockName(block);
        List<PickerList.Variant> variants = new ArrayList<PickerList.Variant>();
        variants.add(new PickerList.Variant(0, name == null ? id : name, blockPicture(block)));
        return PickerList.forBlock(id, variants, false);
    }

    /** One item's entry: its registry name alone, which is how every item list reads an item at this version. */
    static List<PickerList.Entry> itemEntries(Item item) {
        if (item == null || item == Items.AIR) return Collections.emptyList();
        String id = String.valueOf(Registry.ITEM.getKey(item));
        ItemStack plain = new ItemStack(item);
        String name = stackName(plain);
        return Collections.singletonList(new PickerList.Entry(id, null, name == null ? id : name, id, new Stack(plain)));
    }
}
