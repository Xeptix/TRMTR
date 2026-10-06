package com.trmtgtnh.client;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeColorHelper;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.trmtgtnh.CommonProxy;
import com.trmtgtnh.Tags;
import com.trmtgtnh.block.ModBlocks;
import com.trmtgtnh.client.model.GhostBakedModel;
import com.trmtgtnh.client.texture.WearTextures;

/**
 * Everything only a client does: textures, models, block colour, and painting ghosts.
 *
 * <p>
 * The same division the 1.7.10 edition has and for the same reason - a dedicated server must never
 * load a class that names the renderer, because it has no renderer to name and dies at class-load
 * time trying. The proxy is the one place the two sides split.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    /** Every state of the ghost resolves to this one model, which is then swapped for ours. */
    private static final ModelResourceLocation GHOST_GRASS = new ModelResourceLocation(
        Tags.MOD_ID + ":ghost_grass",
        "normal");

    @Override
    public void preInit() {
        super.preInit();
        MinecraftForge.EVENT_BUS.register(WearTextures.class);
        MinecraftForge.EVENT_BUS.register(this);
        inspectionRead = net.minecraftforge.fml.common.Loader.isModLoaded("waila");
        // The golem's renderer, handed over as a factory because the render manager that a renderer
        // needs does not exist yet. The other edition registers the object itself from here.
        net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
            com.trmtgtnh.entity.EntityGolemOfWays.class,
            new com.trmtgtnh.client.render.RenderGolemOfWays.Factory());
    }

    @Override
    public void init() {
        super.init();
        // Grass colour, asked per position. This is all that replaces MixinGrassTint, which in the
        // 1.7.10 edition had to make RenderBlocks' identity comparison against Blocks.grass succeed
        // for a block that was not Blocks.grass. 1.12.2 lets a block say what colour it is at a
        // place, and there is nothing left to trick.
        Minecraft.getMinecraft()
            .getBlockColors()
            .registerBlockColorHandler(new IBlockColor() {

                @Override
                public int colorMultiplier(IBlockState state, @Nullable IBlockAccess world, @Nullable BlockPos pos,
                    int tintIndex) {
                    if (tintIndex != GhostBakedModel.GRASS_TINT && tintIndex != GhostBakedModel.LIGHT_TINT) return -1;
                    // The path light, multiplied into both: a glow outranks every rule about when ground is
                    // tinted, because somebody chose this colour for this square.
                    int glow = world == null || pos == null ? 0
                        : com.trmtgtnh.block.GhostLight.packedAt(world, pos.getX(), pos.getY(), pos.getZ());
                    if (tintIndex == GhostBakedModel.LIGHT_TINT) {
                        return com.trmtgtnh.block.GhostLight.tinted(0xFFFFFF, glow);
                    }
                    int grass = world == null || pos == null ? ColorizerGrass.getGrassColor(0.5D, 1.0D)
                        : BiomeColorHelper.getGrassColorAtPos(world, pos);
                    return com.trmtgtnh.block.GhostLight.tinted(grass, glow);
                }
            }, ModBlocks.ghostGrass());
    }

    /**
     * Sends every state of the ghost to one model location.
     *
     * <p>
     * Without this the model loader would want a blockstate variant for every combination of listed
     * properties. The ghost has none - its wear is unlisted - so there is exactly one, but saying so
     * outright keeps it that way when properties are added later.
     */
    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomStateMapper(ModBlocks.ghostGrass(), new StateMapperBase() {

            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return GHOST_GRASS;
            }
        });
        // And the tamper's own: one model per drawn grade, and the rule that picks one per stack.
        // Here rather than in the item's registration because a dedicated server registers the item
        // and has no models at all.
        com.trmtgtnh.client.model.TamperModels.register(com.trmtgtnh.item.ModItems.gradedTamper(), "tamper");
        com.trmtgtnh.client.model.TamperModels.register(
            com.trmtgtnh.item.ModItems.chunkTamper(),
            com.trmtgtnh.item.ModItems.chunkTamper()
                .iconBase());
        // And the Wayfarer's, which has one picture rather than a set: see registerOne.
        com.trmtgtnh.client.model.TamperModels.registerOne(com.trmtgtnh.item.ModItems.magicTamper(), "magic_tamper");
        // The two bottles, each with a picture of its own and nothing to choose between.
        for (com.trmtgtnh.item.Draughts draught : com.trmtgtnh.item.Draughts.values()) {
            com.trmtgtnh.client.model.TamperModels
                .registerOne(com.trmtgtnh.item.ModItems.draught(draught), draught.itemName());
        }
        // The two tools for whoever is tuning the mod, and the golem's egg and its eleven fittings -
        // one picture each, same as the bottles.
        for (com.trmtgtnh.item.GuideBook book : com.trmtgtnh.item.GuideBook.values()) {
            com.trmtgtnh.client.model.TamperModels.registerOne(com.trmtgtnh.item.ModItems.guide(book), book.itemName());
        }
        com.trmtgtnh.client.model.TamperModels.registerOne(com.trmtgtnh.item.ModItems.devTool(), "dev_tool");
        com.trmtgtnh.client.model.TamperModels.registerOne(com.trmtgtnh.item.ModItems.snapshotTool(), "snapshot_tool");
        com.trmtgtnh.client.model.TamperModels.registerOne(com.trmtgtnh.item.ModItems.golemEgg(), "golem_egg");
        for (com.trmtgtnh.entity.GolemUpgrade upgrade : com.trmtgtnh.entity.GolemUpgrade.real()) {
            com.trmtgtnh.client.model.TamperModels
                .registerOne(com.trmtgtnh.item.ModItems.upgrade(upgrade), upgrade.itemName());
        }
    }

    /**
     * Replaces the placeholder the blockstate file names with the model that actually draws wear.
     *
     * <p>
     * The blockstate file points at an ordinary model so the loader has something real to bake and
     * nothing to complain about in the log. What it baked is then thrown away here.
     */
    @SubscribeEvent
    public void bakeModels(ModelBakeEvent event) {
        event.getModelRegistry()
            .putObject(GHOST_GRASS, new GhostBakedModel());
    }

    // ------------------------------------------------------------------
    // The channel's client end
    // ------------------------------------------------------------------

    /**
     * Whether this connection has announced itself to the server yet.
     *
     * <p>
     * One hello per connection, not one per session: the server keeps what a client asked for against
     * that player's id, and a reconnection is a new answer to the same question.
     */
    private boolean announced;

    /** The last state of the settings modifier this client told the server about. */
    private boolean modifierWasDown;

    /**
     * Whether anything on this client reads the numbers an inspection fetches.
     *
     * <p>
     * The block tooltip is the only reader there is, so a client without it never sends a byte of
     * this. Asked once at pre-init, because a mod list does not change inside a session.
     */
    private boolean inspectionRead;

    /**
     * Says hello once the client is actually in a world, and hands everything back on the way out.
     *
     * <p>
     * A tick rather than a join event because it is the plainest test of the thing that matters -
     * that there is a world and a player in it to be told about. The 1.7.10 edition does the same,
     * and this is one of the few places where the two are the same line for line.
     */
    @SubscribeEvent
    public void onClientTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        com.trmtgtnh.util.MainThread.drainClient();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            handBack();
            return;
        }
        if (!announced) {
            announced = true;
            com.trmtgtnh.network.TrmtNetwork.sendHello(com.trmtgtnh.config.TrmtConfig.showErosion);
        }

        // Only while a tamper is in hand, and only when it changes, so a player who never holds one
        // never sends a byte of this. Sent on the transition rather than with the click, which is the
        // whole reason it works: a message sent a tick before a click arrives before that click,
        // whereas a modifier sent alongside one would be racing the click it is meant to qualify.
        net.minecraft.item.ItemStack held = mc.player.getHeldItemMainhand();
        boolean relevant = held != null && held.getItem() instanceof com.trmtgtnh.item.ItemChunkTamper;
        boolean down = relevant && modifierHeld();
        if (down != modifierWasDown) {
            modifierWasDown = down;
            com.trmtgtnh.network.TrmtNetwork.sendModifier(down);
        }

        // Put in place once, from here rather than from start-up: JourneyMap builds its own tables
        // as a world loads, and a BlockMD asked for before then is one it will replace.
        com.trmtgtnh.client.journeymap.JourneyMapColors.tick();
        OverlayPainter.get()
            .tick();
        askAboutCrosshair(mc);
        serviceRestitch();
    }

    /**
     * Fetches the server's exact numbers for whatever the crosshair is on, for a tooltip to read.
     *
     * <p>
     * A client can see only what it was sent, which is enough to paint a square and not enough to
     * answer a question about one. Asked only while something is reading the answers - which out of
     * the box means a block-tooltip mod - so a client with no reader never sends a byte of this.
     *
     * <p>
     * Worn ground is asked about the moment its ghost is in view. A reinforcement or a spawn ward can
     * sit on a block that shows nothing, so while either feature is on the crosshair block is asked
     * about whatever it is; the server only answers when it has a record, so a plain block costs a
     * request and no reply. Asking only while reinforcement was on left a client with wards alone
     * never asking about an unworn floor.
     */
    private void askAboutCrosshair(Minecraft mc) {
        net.minecraft.util.math.RayTraceResult target = mc.objectMouseOver;
        if (target == null || target.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            InspectionCache.clear();
            return;
        }
        net.minecraft.util.math.BlockPos at = target.getBlockPos();
        if (at == null) {
            InspectionCache.clear();
            return;
        }
        if (com.trmtgtnh.util.InspectionReach.asks(
            inspectionRead,
            mc.world.getBlockState(at)
                .getBlock() instanceof com.trmtgtnh.block.BlockGhost,
            com.trmtgtnh.config.TrmtConfig.reinforceEnabled,
            com.trmtgtnh.config.TrmtConfig.wardEnabled)) {
            InspectionCache.poll(at.getX(), at.getY(), at.getZ());
        } else {
            InspectionCache.clear();
        }
    }

    // ------------------------------------------------------------------
    // Rebuilding the pictures
    // ------------------------------------------------------------------

    /** Why a rebuild is waiting, or null when none is. */
    private String restitchWanted;

    /** Ticks left before it runs, so a run of edits costs one rebuild rather than one apiece. */
    private int restitchIn;

    private static final int RESTITCH_DELAY = 40;

    /**
     * Asks for the wear pictures to be built again.
     *
     * <p>
     * Everything a wear texture is made of is read while the atlas is being stitched, and nothing outside a
     * stitch can change one - so a setting that decides what a picture looks like does nothing whatever until
     * the next one. The other edition answered that for a long time by asking the player to press F3+T, which
     * is a debug keybinding, reloads every pack, language, sound and atlas in the game rather than the one
     * that matters, and is not a thing anybody should have to know to make a setting they just changed take
     * effect.
     */
    public void requestRestitch(String reason) {
        if (restitchWanted == null) com.trmtgtnh.Trmt.LOG.info("Rebuilding the wear pictures: {}", reason);
        restitchWanted = reason;
        restitchIn = 0;
    }

    /**
     * Runs a pending rebuild once the countdown is out and the moment is right.
     *
     * <p>
     * Held while a world is part way through loading, which has nobody to warn yet: the rules a server sends on
     * joining are one of the ways in here, and that arrival would otherwise be a silent freeze in the middle of
     * joining, which reads as a crash. The warning is given here rather than where the rebuild was asked for,
     * so that it is given at a moment somebody can read it.
     */
    private void serviceRestitch() {
        if (restitchWanted == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != null && mc.player == null) {
            restitchIn = 0;
            return;
        }
        if (mc.world == null && (mc.currentScreen instanceof net.minecraft.client.multiplayer.GuiConnecting
            || com.trmtgtnh.Trmt.serverThreadAlive())) {
            restitchIn = 0;
            return;
        }
        if (restitchIn <= 0) {
            tell(
                net.minecraft.util.text.TextFormatting.GRAY + "[TRMT] "
                    + restitchWanted
                    + " Rebuilding them now - the game will stop responding for a moment.");
            restitchIn = RESTITCH_DELAY;
            return;
        }
        if (--restitchIn > 0) return;
        restitchWanted = null;
        restitchPictures();
    }

    /**
     * Stitches the block atlas again and bakes every model against it, and nothing else.
     *
     * <p>
     * The other edition reloads the block atlas alone, which is right there and wrong here. 1.7.10 asks a block
     * for its icon as it draws; 1.12.2 bakes a sprite's coordinates into every quad of every model when the
     * models are baked, so an atlas stitched again without baking again moves every sprite out from under every
     * model in the game. Reloading the model manager does both - and only those: not the packs, not the
     * languages, not the sounds, which is what {@code refreshResources} would cost.
     */
    private void restitchPictures() {
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.renderer.block.model.ModelManager models = com.trmtgtnh.client.texture.ModelFaces
            .modelManager();
        if (models == null) {
            com.trmtgtnh.Trmt.LOG
                .warn("The model manager could not be reached, so every resource is being reloaded instead");
            mc.refreshResources();
            return;
        }
        try {
            models.onResourceManagerReload(mc.getResourceManager());
        } catch (RuntimeException failed) {
            // Not cosmetic, unlike almost everything else this queue runs. A reload deletes the atlas's own
            // texture before it builds the replacement, so a stitch that throws half way leaves every block in
            // the game untextured with one line in the log.
            com.trmtgtnh.Trmt.LOG
                .error("Rebuilding the wear pictures failed; reloading every resource instead", failed);
            tell(
                net.minecraft.util.text.TextFormatting.RED
                    + "[TRMT] The wear pictures could not be rebuilt. Reloading resources - if the world still looks wrong, restart the game.");
            mc.refreshResources();
            return;
        }
        if (mc.renderGlobal != null) mc.renderGlobal.loadRenderers();
    }

    private static void tell(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) mc.player.sendMessage(new net.minecraft.util.text.TextComponentString(message));
    }

    @Override
    public void readConfigScreenEdits() {
        com.trmtgtnh.client.gui.ConfigAdapter.readBack();
    }

    @Override
    public void tellServerOwnsGeometry() {
        tell(
            net.minecraft.util.text.TextFormatting.GRAY
                + "[TRMT] How deep and how finely ground wears here is this server's to set, so those stay as it sent them. Your change is saved and takes effect as soon as you leave.");
    }

    /**
     * What a change of settings means for this client.
     *
     * <p>
     * A reload runs wherever it was asked for, and in single player that is the server thread. Everything here
     * belongs to the client: its world, its painter, its chat.
     */
    @Override
    public void onConfigChanged(final com.trmtgtnh.config.ConfigReload.Delta delta) {
        if (delta == null || !delta.any()) return;
        com.trmtgtnh.util.MainThread.onClient(new Runnable() {

            @Override
            public void run() {
                applyLocally(delta);
            }
        });
    }

    private void applyLocally(com.trmtgtnh.config.ConfigReload.Delta delta) {
        if (delta.textures) requestRestitch("Wear textures changed.");
        if (delta.needsRepaint()) {
            // Always off before on. Painting is skipped where a ghost already stands, so a block that has just
            // stopped being tracked would otherwise keep the one it has for ever.
            OverlayPainter painter = OverlayPainter.get();
            painter.restoreAll();
            if (com.trmtgtnh.config.TrmtConfig.enabled && com.trmtgtnh.config.TrmtConfig.overlayVisible()) {
                painter.repaintAll();
            }
        }
        // The server stops spending bandwidth on a player who cannot see the result. The player's own choice,
        // as the first hello sends, rather than what is visible: a server that remembered visibility as the
        // player's wish kept sending wear to somebody who had switched it off.
        if (delta.showErosionMoved && announced) {
            com.trmtgtnh.network.TrmtNetwork.sendHello(com.trmtgtnh.config.TrmtConfig.showErosion);
        }
    }

    /**
     * Gives this client its own settings back when it leaves a server.
     *
     * <p>
     * Smaller than the 1.7.10 edition's, which also drops the inspection cache and two minimap
     * integrations - none of which exist here yet. What it does do is the part that
     * matters and the part that is easy to get wrong: a client that keeps a server's rules, surface
     * table or wear after leaving shows the next world it opens the last one's roads.
     */
    private void handBack() {
        if (!announced) return;
        announced = false;
        // The server forgot the modifier the moment this connection went; if this did not forget it
        // too, a client that left with the key down would never send the transition again, and the
        // next server would be told the key was held only when it was finally let go.
        modifierWasDown = false;
        int[] underServer = com.trmtgtnh.config.ServerRules.chainAppearances();
        com.trmtgtnh.config.ServerRules.release();
        boolean handedBack = com.trmtgtnh.config.ServerRules.appearancesMovedFrom(underServer);
        com.trmtgtnh.surface.SurfaceRegistry.releaseServerTable();
        // And the map's coalescing, so the first square painted in the next world is told about
        // whatever the last one in this world happened to be.
        com.trmtgtnh.client.xaero.XaeroMinimap.reset();
        com.trmtgtnh.client.journeymap.JourneyMapColors.reset();
        serverNamedTable = 0L;
        tableAskedFor = 0L;
        mayEditFamilies = false;
        serverPricing = null;
        // Nothing is put back into the world first: it is the world being left, and it is thrown away
        // whole. What must not survive is the queue, which names chunks of a world that is gone.
        OverlayPainter.get()
            .clearQueue();
        ClientErosionCache.get()
            .clear();
        ClientLightCache.get()
            .clear();
        com.trmtgtnh.config.TrmtConfig.read();
        // The mirror of the join: a client that wore a server's chains needs its own pictures back.
        if (handedBack) requestRestitch("Your own settings are back.");
    }

    // -- packet sinks --

    @Override
    public void handleChunkErosion(int chunkX, int chunkZ, int[] keys, short[] states) {
        ClientErosionCache cache = ClientErosionCache.get();
        ClientErosionCache.ChunkOverlay previous = cache.overlay(chunkX, chunkZ);
        // Before the overlay is replaced, because it is the only record of what each ghost was
        // covering. See OverlayPainter.restoreVanished.
        OverlayPainter.get()
            .restoreVanished(Minecraft.getMinecraft().world, previous, keys);
        cache.put(chunkX, chunkZ, ClientErosionCache.build(chunkX, chunkZ, keys, states, previous));
        OverlayPainter.get()
            .queueChunk(chunkX, chunkZ);
        redrawColumn(chunkX, chunkZ, keys);
    }

    @Override
    public void handleDelta(int x, int y, int z, short state) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ClientErosionCache cache = ClientErosionCache.get();
        ClientErosionCache.ChunkOverlay previous = cache.overlay(chunkX, chunkZ);
        int key = com.trmtgtnh.erosion.ErosionKey.packWorld(x, y, z);
        if (state == com.trmtgtnh.erosion.ErosionState.NONE && previous != null) {
            // A cleared position has to be put back before its record is dropped, or the ghost
            // would be stranded with nothing left to say what it was covering.
            OverlayPainter.get()
                .restoreSingle(Minecraft.getMinecraft().world, chunkX, chunkZ, key, previous.originAt(key));
        }
        cache.put(chunkX, chunkZ, ClientErosionCache.withSingle(previous, chunkX, chunkZ, key, state));
        OverlayPainter.get()
            .queueChunk(chunkX, chunkZ);
        // And the mesh asked for directly, because the painter cannot always tell that anything
        // happened. A square already painted stays painted - a ghost is a ghost - and what it draws
        // is worked out from the record at every rebuild, so a record moving along its chain changes
        // the picture without the painter touching a block. Nothing else would ask for the rebuild.
        redrawAt(x, y, z);
    }

    @Override
    public void handleClearAll() {
        // Put back before the caches go, since the caches are the only record of what to put back.
        OverlayPainter.get()
            .restoreAll();
        ClientErosionCache.get()
            .clear();
        ClientLightCache.get()
            .clear();
    }

    @Override
    public void handleChunkLight(int chunkX, int chunkZ, int[] keys, byte[] values) {
        ClientLightCache.get()
            .put(chunkX, chunkZ, keys, values);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world != null && keys != null) {
            // The client keeps a block-light map of its own and works it out itself, so a glow that has
            // just arrived is propagated here before anything is redrawn - or the mesh is rebuilt against
            // the old light and the square stays dark until something else disturbs it.
            for (int key : keys) {
                mc.world.checkLightFor(
                    net.minecraft.world.EnumSkyBlock.BLOCK,
                    new net.minecraft.util.math.BlockPos(
                        (chunkX << 4) + com.trmtgtnh.erosion.ErosionKey.localX(key),
                        com.trmtgtnh.erosion.ErosionKey.y(key),
                        (chunkZ << 4) + com.trmtgtnh.erosion.ErosionKey.localZ(key)));
            }
        }
        redrawColumn(chunkX, chunkZ, keys);
    }

    @Override
    public void handleLightDelta(int x, int y, int z, int packed) {
        ClientLightCache.get()
            .set(x, y, z, packed);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;
        // Propagate first, then redraw - the other order rebuilds the mesh against the old light. And
        // as far as the glow can reach rather than one square, because a light's whole point is the
        // ground around it.
        mc.world.checkLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, new net.minecraft.util.math.BlockPos(x, y, z));
        int reach = 16;
        mc.world.markBlockRangeForRenderUpdate(
            x - reach,
            Math.max(0, y - reach),
            z - reach,
            x + reach,
            Math.min(255, y + reach),
            z + reach);
    }

    /**
     * A square's glow as this client holds it.
     *
     * <p>
     * Asked from mesher threads as well as this one, through the ghost's light value and its tint. The
     * cache is written as immutable snapshots so those reads need no lock; a read that races a chunk
     * going away is still possible, and dark is the least-wrong answer to it.
     */
    @Override
    public int clientLightLevel(int x, int y, int z) {
        try {
            return ClientLightCache.get()
                .levelAt(x, y, z);
        } catch (RuntimeException racingAnUnload) {
            return 0;
        }
    }

    @Override
    public int clientLightPacked(int x, int y, int z) {
        try {
            return ClientLightCache.get()
                .at(x, y, z);
        } catch (RuntimeException racingAnUnload) {
            return 0;
        }
    }

    /**
     * Asks for the meshes a chunk packet's squares sit in to be rebuilt, and no more.
     *
     * <p>
     * Over the heights the packet names rather than the whole column, because a chunk is sixteen
     * sections tall and a road occupies one or two of them: marking all sixteen would rebuild
     * fourteen meshes of sky and bedrock for every chunk that arrived.
     */
    private static void redrawColumn(int chunkX, int chunkZ, int[] keys) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || keys == null || keys.length == 0) return;
        int minY = 255;
        int maxY = 0;
        for (int key : keys) {
            int y = com.trmtgtnh.erosion.ErosionKey.y(key);
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        mc.world.markBlockRangeForRenderUpdate(
            chunkX << 4,
            minY,
            chunkZ << 4,
            (chunkX << 4) + 15,
            maxY,
            (chunkZ << 4) + 15);
    }

    /** Asks for the mesh around one square to be rebuilt. The game widens it by a block itself. */
    private static void redrawAt(int x, int y, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;
        mc.world.markBlockRangeForRenderUpdate(x, y, z, x, y, z);
    }

    // -- what a ghost asks about itself --

    /**
     * The record a ghost should draw and stand on: what the server sent, as the world around the
     * square now changes it.
     *
     * <p>
     * Two things change it, and both are about the block above. Something planted there holds the
     * ground at the end of the run it takes on the face it still has - as worn as this ground can
     * look while still being this ground - and what it has actually taken goes on being counted and
     * is simply not shown: break the plant and the square catches up at once. And a block built on
     * top, where the setting says so, keeps the wear drawn but fills the square's cube again: what a
     * block on top spoils is the dip, not the path.
     *
     * <p>
     * The other edition works both out in the painter and bakes the answer into which ghost it
     * writes. Here they are worked out wherever a ghost is asked, from whichever view of the world
     * the asker was handed - on a mesher thread, the snapshot the game built for that thread - so a
     * plant set down re-meshes the square beneath it through vanilla's own neighbour update and
     * the picture follows without the painter being told.
     */
    @Override
    public short ghostRecordAt(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        short record;
        int packedOrigin;
        try {
            ClientErosionCache cache = ClientErosionCache.get();
            record = cache.stateAt(x, y, z);
            packedOrigin = cache.originAt(x, y, z);
        } catch (RuntimeException racingAnUnload) {
            // A read that raced a chunk going away. Untouched is the least-wrong answer.
            return com.trmtgtnh.erosion.ErosionState.NONE;
        }
        if (!com.trmtgtnh.block.BlockGhost.shows(record) || world == null) return record;

        com.trmtgtnh.surface.SurfaceFamily appearance = com.trmtgtnh.erosion.ErosionState.familyOf(record);
        int layer = com.trmtgtnh.erosion.ErosionState.layerOf(record);
        int sink = com.trmtgtnh.erosion.ErosionState.sinkOf(record);
        boolean changed = false;

        if (packedOrigin >= 0 && com.trmtgtnh.erosion.GroundCover.holdsAt(world, x, y, z)) {
            IBlockState under = net.minecraft.block.Block.getStateById(packedOrigin);
            com.trmtgtnh.surface.SurfaceFamily base = com.trmtgtnh.surface.SurfaceRegistry.familyOf(
                under.getBlock(),
                under.getBlock()
                    .getMetaFromState(under));
            int flat = base == null ? -1 : com.trmtgtnh.erosion.ErosionChain.lastFlatIndex(base);
            if (flat >= 0) {
                int at = com.trmtgtnh.erosion.ErosionChain.indexOf(base, appearance, layer, sink);
                if (at < 0 || at > flat) {
                    com.trmtgtnh.surface.SurfaceFamily held = com.trmtgtnh.erosion.ErosionChain.familyAt(base, flat);
                    int heldLayer = com.trmtgtnh.erosion.ErosionChain.stageAt(base, flat);
                    if (held != null && heldLayer >= 0) {
                        appearance = held;
                        layer = heldLayer;
                        sink = 0;
                        changed = true;
                    }
                }
            }
        }

        if (sink > 0 && com.trmtgtnh.config.TrmtConfig.flattenWearUnderBlocks
            && OverlayPainter.covered(world, x, y, z)) {
            sink = 0;
            changed = true;
        }

        if (!changed) return record;
        return com.trmtgtnh.erosion.ErosionState.pack(
            appearance,
            layer,
            sink,
            com.trmtgtnh.erosion.ErosionState.frozenOf(record),
            com.trmtgtnh.erosion.ErosionState.reinforceOf(record));
    }

    @Override
    public int ghostOriginAt(int x, int y, int z) {
        try {
            return ClientErosionCache.get()
                .originAt(x, y, z);
        } catch (RuntimeException racingAnUnload) {
            return -1;
        }
    }

    /**
     * How far the ground under a block has dropped, as this client's ghost there stands.
     *
     * <p>
     * The footing, not the picture: in visual mode the ground is drawn sunk and walked on at full
     * height, and nothing resting on it should move either.
     */
    @Override
    public double settledDropUnder(int x, int y, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return 0.0D;
        if (!(mc.world.getBlockState(new BlockPos(x, y, z))
            .getBlock() instanceof com.trmtgtnh.block.BlockGhost)) return 0.0D;
        return com.trmtgtnh.block.BlockGhost.collisionSink(ghostRecordAt(mc.world, x, y, z)) / 16.0D;
    }

    // -- what a server tells a client about itself --

    private long serverNamedTable;

    private long tableAskedFor;

    private boolean mayEditFamilies;

    private com.trmtgtnh.erosion.WearMath.Pricing serverPricing;

    @Override
    public void applyServerRules(boolean forceOverlay, String decayMode,
        com.trmtgtnh.config.ServerRules.Geometry geometry) {
        // Taken before and compared after, because what matters is not that the rules differ but that they
        // send a chain somewhere this client has no pictures for: a surface whose run now visits gravel on a
        // client that never planned gravel would stop showing any wear at the point it reached it.
        int[] before = com.trmtgtnh.config.ServerRules.chainAppearances();
        com.trmtgtnh.config.ServerRules.hold(forceOverlay, decayMode, geometry);
        if (com.trmtgtnh.config.ServerRules.appearancesMovedFrom(before)) {
            requestRestitch("This server's ground wears through into surfaces yours does not.");
        }
    }

    /**
     * Asks for the server's surface table, once per table.
     *
     * <p>
     * A client asks only when the fingerprint it has been told about is one it has not already asked
     * for, because the rules are announced on every reload and an ask per announcement would fetch
     * the same table over and over. The server forgets what it last sent whenever it announces, so
     * an ask that crosses with a table already on its way costs nothing either way.
     */
    @Override
    public void considerServerTable(boolean sent, long fingerprint) {
        serverNamedTable = sent ? fingerprint : 0L;
        if (!sent || fingerprint == 0L || fingerprint == tableAskedFor) return;
        tableAskedFor = fingerprint;
        com.trmtgtnh.network.TrmtNetwork.requestSurfaceTable(fingerprint);
    }

    /**
     * Takes a server's surface table into use for the visit.
     *
     * <p>
     * Refused for a table this client did not ask for, and for one it cannot read or cannot hold -
     * in every refusal the client keeps its own table, which is what every version before the table
     * existed did anyway. The fingerprint is forgotten again on each refusal, so a server that comes
     * back to the same table is asked for it afresh rather than being assumed to have been answered.
     */
    @Override
    public void installServerTable(long fingerprint, boolean holdsSwitch, byte[] bytes) {
        if (com.trmtgtnh.Trmt.runningServer() != null) return;
        if (serverNamedTable == 0L || fingerprint != serverNamedTable) {
            if (tableAskedFor == fingerprint) tableAskedFor = 0L;
            return;
        }
        com.trmtgtnh.surface.SurfaceTableCodec.Table decoded;
        try {
            decoded = com.trmtgtnh.surface.SurfaceTableCodec.decode(bytes);
        } catch (IllegalArgumentException malformed) {
            tableAskedFor = fingerprint;
            com.trmtgtnh.Trmt.LOG.warn("This server's surface table could not be read; keeping your own", malformed);
            return;
        }
        if (!com.trmtgtnh.surface.SurfaceRegistry.holdServerTable(decoded, holdsSwitch, fingerprint)) {
            tableAskedFor = fingerprint;
            com.trmtgtnh.Trmt.LOG
                .warn("This server's surface table names a surface this build does not have; keeping your own");
            return;
        }
        tableAskedFor = 0L;
        com.trmtgtnh.Trmt.LOG.info(
            "Using this server's surface table for the visit: {} block states",
            Integer.valueOf(decoded.families.size()));
    }

    @Override
    public void setMayEditFamilies(boolean allowed) {
        mayEditFamilies = allowed;
    }

    @Override
    public boolean mayEditFamilies() {
        return mayEditFamilies;
    }

    /** Ctrl, which is what the other edition asks for too, and asked of the screen's own helper. */
    @Override
    public boolean modifierHeld() {
        return net.minecraft.client.gui.GuiScreen.isCtrlKeyDown();
    }

    @Override
    public void openTamperScreen(net.minecraft.item.ItemStack stack) {
        if (stack == null) return;
        Minecraft.getMinecraft()
            .displayGuiScreen(new com.trmtgtnh.client.gui.GuiTamper(stack));
    }

    @Override
    public Object golemScreen(net.minecraft.entity.player.EntityPlayer player,
        com.trmtgtnh.entity.EntityGolemOfWays golem) {
        return new com.trmtgtnh.client.gui.GuiGolem(player.inventory, golem);
    }

    @Override
    public void setServerPricing(com.trmtgtnh.erosion.WearMath.Pricing pricing) {
        serverPricing = pricing;
    }

    @Override
    public com.trmtgtnh.erosion.WearMath.Pricing serverPricing() {
        return serverPricing;
    }

    @Override
    public void setWearLook(com.trmtgtnh.surface.SurfaceFamily family, String pattern) {
        com.trmtgtnh.config.FamilySettings settings = com.trmtgtnh.config.TrmtConfig.family(family);
        if (settings == null || pattern == null || pattern.equals(settings.wearPattern)) return;
        settings.wearPattern = pattern;

        com.trmtgtnh.config.ConfigFile config = com.trmtgtnh.config.TrmtConfig.raw();
        if (config != null) {
            com.trmtgtnh.config.ConfigFile.Setting property = config
                .getCategory(
                    com.trmtgtnh.config.TrmtConfig.CATEGORY_FAMILIES + com.trmtgtnh.config.ConfigFile.CATEGORY_SPLITTER
                        + family.key())
                .get("wearPattern");
            if (property != null) {
                property.set(pattern);
                com.trmtgtnh.config.TrmtConfig.save();
            }
        }
        if (com.trmtgtnh.config.TrmtConfig.isPoisoned()) {
            // Saving refuses to write a file assembled from a bad read, so without this the look would
            // apply now and be gone at the next start with nothing said about it.
            tell(
                net.minecraft.util.text.TextFormatting.YELLOW
                    + "[TRMT] Your look changed for now, but the config file failed to read, so it will not be kept. Fix it and use /trmt reload.");
        }
        requestRestitch("Your wear look changed.");
    }

    @Override
    public void acceptInspection(int x, int y, int z, float wear, float threshold, int untouchedSeconds,
        int recoverySeconds, int chainIndex, int chainLength, int reinforce, int ward) {
        InspectionCache.accept(
            x,
            y,
            z,
            wear,
            threshold,
            untouchedSeconds,
            recoverySeconds,
            chainIndex,
            chainLength,
            reinforce,
            ward);
    }

    @Override
    public void openGuideScreen(int bookOrdinal) {
        Minecraft.getMinecraft()
            .displayGuiScreen(
                new com.trmtgtnh.client.gui.GuiGuideBook(com.trmtgtnh.item.GuideBook.byOrdinal(bookOrdinal)));
    }

    @Override
    public void openSnapshotScreen(int flags) {
        Minecraft mc = Minecraft.getMinecraft();
        // Already open: refresh it in place, so pressing a button does not blink the screen.
        if (mc.currentScreen instanceof com.trmtgtnh.client.gui.GuiSnapshot) {
            ((com.trmtgtnh.client.gui.GuiSnapshot) mc.currentScreen).update(flags);
            return;
        }
        mc.displayGuiScreen(new com.trmtgtnh.client.gui.GuiSnapshot(flags));
    }

    @Override
    public void requestSnapshotScreen() {
        com.trmtgtnh.network.TrmtNetwork.sendSnapshotAction(com.trmtgtnh.network.PacketSnapshotAction.REQUEST);
    }

    /**
     * Swaps the hard surfaces' wear patterns and reloads, so the difference can be looked at.
     *
     * <p>
     * In memory only and not written to the file: this is a comparison a developer asks for in
     * passing, and one that survived a restart would be a setting nobody remembers changing.
     */
    @Override
    public void applyDevPreview(int mode) {
        String pattern = mode == com.trmtgtnh.item.ItemDevTool.PREVIEW_OLD
            ? com.trmtgtnh.config.FamilySettings.PATTERN_SMOOTH
            : com.trmtgtnh.config.FamilySettings.PATTERN_CRACK;
        com.trmtgtnh.surface.SurfaceFamily[] hard = { com.trmtgtnh.surface.SurfaceFamily.COBBLE,
            com.trmtgtnh.surface.SurfaceFamily.STONE, com.trmtgtnh.surface.SurfaceFamily.NETHER,
            com.trmtgtnh.surface.SurfaceFamily.END };
        for (com.trmtgtnh.surface.SurfaceFamily family : hard) {
            com.trmtgtnh.config.FamilySettings settings = com.trmtgtnh.config.TrmtConfig.family(family);
            if (settings != null) settings.wearPattern = pattern;
        }
        requestRestitch("A wear preview was asked for.");
    }

    @Override
    public boolean swungAtNothing(net.minecraft.entity.player.EntityPlayer swinger) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || swinger != mc.player) return false;
        net.minecraft.util.math.RayTraceResult target = mc.objectMouseOver;
        return target != null && target.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.MISS;
    }
}
