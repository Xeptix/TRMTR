package com.trmtgtnh.client.model;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import com.trmtgtnh.surface.SurfaceFamily;

/**
 * The picture a worn square's dust takes: the 1.7.10 edition's, side by side (0.9.223, spec GF19, GF20) - carried from
 * the 1.12.2 edition's GhostDust.
 *
 * <p>
 * There every particle a ghost throws - breaking, hitting and sprinting alike - is made with {@code
 * block.getIcon(side, meta)}, the side picked at random for each one ({@code EntityDiggingFX}'s
 * {@code rand.nextInt(6)}), and the ghost's position-free icon ({@code GhostLogic.icon}) answers by its family: a
 * grass square's bottom is dirt, its top grass's top and its four sides grass's side; every other family its own
 * stock texture on every side, and a family with none dirt. This version asks a block's model for one particle
 * picture and hands it no position, so a ghost's dust was one picture whatever it was worn as.
 *
 * <p>
 * So the square is held for the moment its dust is spawned, as the 1.7.10 edition holds its shade for its dust: on
 * Forge the ghost's hit, destroy and running hooks, on Fabric the particle engine's two dust methods and the sprint
 * read ({@code MixinGhostDust}, {@code MixinGhostSprintDust}) - each handed the square - hold its family through
 * {@code Client.holdDust} and let the game spawn the dust, and the model's particle picture, asked once for each
 * particle in the same call, answers from what is held. Let go of on the next client tick. Landing dust is spawned
 * from the server with no square to hold and keeps the model's own picture.
 */
public final class GhostDust {

    private static final Random SIDES = new Random();

    private static volatile SurfaceFamily held;

    private static volatile boolean holding;

    private GhostDust() {}

    /** The family of the square whose dust is about to be spawned. */
    public static void hold(SurfaceFamily family) {
        held = family;
        holding = true;
    }

    /** Called once a client tick, so a hold cannot outlive the dust it was for. */
    public static void release() {
        holding = false;
        held = null;
    }

    /** One particle's picture: the held square's, on a side chosen at random, or the model's own with nothing held. */
    public static TextureAtlasSprite sprite(TextureAtlasSprite unheld) {
        if (!holding) return unheld;
        TextureAtlasSprite found = Minecraft.getInstance()
            .getModelManager()
            .getAtlas(TextureAtlas.LOCATION_BLOCKS)
            .getSprite(ResourceLocation.parse(dustName(held, SIDES.nextInt(6))));
        return found != null ? found : unheld;
    }

    /** The 1.7.10 edition's {@code GhostLogic.icon(side)} for a family, as a block texture name. */
    static String dustName(SurfaceFamily family, int side) {
        if (family == SurfaceFamily.GRASS) {
            return side == 0 ? "block/dirt" : side == 1 ? "block/grass_block_top" : "block/grass_block_side";
        }
        String stock = GhostQuads.stockTopName(family);
        return stock != null ? stock : "block/dirt";
    }
}
