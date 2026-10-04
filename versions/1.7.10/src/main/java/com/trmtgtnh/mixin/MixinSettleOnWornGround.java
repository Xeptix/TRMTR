package com.trmtgtnh.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trmtgtnh.client.render.Settling;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Draws snow and carpet down with the ground that has worn away underneath them.
 *
 * <p>
 * The seam is the two lines every render type passes through: {@code renderBlockByRenderType}
 * stamps the block's bounds onto the renderer and only then decides how to draw it, so a shift
 * written between those two moments is respected by whatever draws next. Celeritas takes the same
 * road - it builds a {@code new RenderBlocks(IBlockAccess)} per meshing task and calls this very
 * method - so one injection serves both meshers.
 *
 * <p>
 * The fields written are the renderer's, never the block's. A block's bounds are one static object
 * shared by every instance of it in the world, read by the integrated server's own collision
 * thread in single player; the renderer's belong to the renderer, and both meshers build one per
 * chunk section, so a write here cannot leave the section being drawn. Nothing is put back
 * afterwards because nothing needs to be: the next block through this method stamps its own bounds
 * over these two lines before anything reads them.
 *
 * <p>
 * The one visible cost is that the four side faces lose their interpolated texture window once the
 * bounds leave the block - {@code renderFaceZNeg} and its siblings fall back to the whole sprite
 * when {@code renderMinY} goes below zero - which is why only snow and carpet are let through
 * here. Both are near enough uniform that stretching them cannot be seen. The same departure from
 * the cell costs the interpolated ambient occlusion path its meaning, which is why the block is put
 * on the whole-block path below rather than left to extrapolate.
 *
 * <p>
 * What this cannot do is move a culling decision, and that is worth writing down rather than
 * leaving to be rediscovered. Every face test in the standard block renderer asks
 * {@code shouldSideBeRendered}, which reads the <em>block's</em> bounds - stamped one line earlier
 * by {@code setBlockBoundsBasedOnState}, before this injection fires - against the
 * <em>unshifted</em> neighbour cell. So the shift moves drawn geometry out from under decisions
 * already taken about where that geometry used to be.
 *
 * <p>
 * Vanilla is unharmed by that, because vanilla never culls the face between two snow layers at all:
 * its test falls through to "is the neighbour an opaque cube", a snow layer is not one, both faces
 * are drawn, and a step in a snowfield draws its riser correctly. A client running the better face
 * culling that comes with Angelica does cull it - there the question is handed to the neighbour,
 * which compares the caller's top against its own height and finds them equal - and there, where
 * two settling blocks of the same height stand over ground worn by different amounts, the face they
 * share is culled from both sides and the band between the two heights is left open.
 *
 * <p>
 * That band is the one artefact this feature does not answer for, and it is left open knowingly.
 * It cannot be closed by shifting differently, because one pair of bounds cannot express a per-face
 * decision; and refusing to settle wherever a neighbour disagrees is not a fixed point - the column
 * that refuses then disagrees with the next one along, so the seam moves inward rather than away.
 * Closing it properly means answering {@code shouldSideBeRendered} for these blocks, which is the
 * same method that mod has already replaced, on the hottest predicate the chunk mesher has. It is
 * bounded instead: only snow and carpet settle, only equal-height neighbours cull each other at
 * all, and the band is never taller than the difference between two ruts.
 */
@SideOnly(Side.CLIENT)
@Mixin(RenderBlocks.class)
public abstract class MixinSettleOnWornGround {

    /**
     * Which faces of the block being drawn must be kept whatever the culling rule concluded, a bit
     * per side.
     *
     * <p>
     * On the renderer rather than anywhere shared, because both meshers build one of these per chunk
     * section and a worker therefore has its own. Cleared on the first line of the injection below,
     * which every block that is drawn at all passes through before it is drawn - so nothing can
     * inherit the previous block's answer, including blocks other mods supply their own renderer for.
     */
    @Unique
    private int trmt$forcedFaces;

    @Inject(
        method = "renderBlockByRenderType(Lnet/minecraft/block/Block;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;setRenderBoundsFromBlock(Lnet/minecraft/block/Block;)V",
            shift = At.Shift.AFTER),
        require = 0)
    private void trmt$settleOnWornGround(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        trmt$forcedFaces = 0;
        if (!Settling.settles(block)) return;
        // Past the guard, so it costs one static read on a path only snow and carpet reach - and it
        // is the only evidence that this injection bound at all, which nothing else can supply.
        Settling.noteShiftHookRan();

        RenderBlocks self = (RenderBlocks) (Object) this;
        double drop = Settling.dropFor(self.blockAccess, x, y, z);

        // Worked out before the shift and for every settling block, drop or no drop: the one that
        // has to draw the band is the one that stayed where it was, and that one's drop is nought.
        trmt$forcedFaces = Settling
            .forcedFaces(self.blockAccess, x, y, z, y + self.renderMinY - drop, y + self.renderMaxY - drop, drop);

        if (drop <= 0.0D) return;

        self.renderMinY -= drop;
        self.renderMaxY -= drop;
        // Ambient occlusion is interpolated across a side face from these same two numbers: the
        // four corner brightnesses are mixed with the bounds as the weights, so once renderMinY is
        // negative the weights leave nought-to-one and the mix is extrapolated past the corners it
        // was meant to sit between. The result is masked with 255 rather than clamped, so an
        // overshoot wraps - two hundred and sixty comes back as four, minus sixty as a hundred and
        // ninety-six - and one side face of a settled layer is painted black where it should be
        // bright, or bright where it should be black. That is the face that looks wrong, one at a
        // time, at the edge of a rut.
        //
        // Clearing the flag sends this block down the whole-block path instead, which reads these
        // two fields only as "at or below nought" and "at or above one" and so cannot extrapolate
        // at all. Nothing true is given up: the interpolated path interpolates within the cell, and
        // after the shift this block is no longer inside its own cell. It is also the cheaper of
        // the two - four corner weights and four blends per face that no longer have to be worked
        // out - and it is reached only for a settling block that is genuinely standing in a rut.
        self.partialRenderBounds = false;
    }

    // The eighteen places the standard block renderer asks whether a face is wanted: six sides in
    // each of the three methods it dispatches to. The ordinal is the side, and it is the same
    // ordinal in all three, so one hook per side covers all of them. All three are needed: a plain
    // client runs the third, and on a client meshing with Celeritas the two ambient occlusion
    // methods turn themselves back into the third at their own first instruction.
    //
    // Modifying the value rather than replacing the call is what makes these safe to hold beside
    // anything else - the instruction is not claimed, so another mod may modify the same answer
    // without either of us being skipped. The guard each one leaves on is the boolean just
    // returned, so a face that was going to be drawn costs one comparison and nothing else.
    //
    // Without a requirement, because a renderer detail must not take a client down - and the library
    // says nothing whatever when such an injection fails to bind, which is why the tell for that
    // lives in Settling rather than here.

    /** The face underneath. */
    @ModifyExpressionValue(
        method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z",
            ordinal = 0),
        require = 0)
    private boolean trmt$keepFaceDown(boolean wanted) {
        if (wanted) return true;
        if ((trmt$forcedFaces & 0x01) == 0) return false;
        Settling.noteCullHookRan();
        return true;
    }

    /** The face on top. */
    @ModifyExpressionValue(
        method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z",
            ordinal = 1),
        require = 0)
    private boolean trmt$keepFaceUp(boolean wanted) {
        if (wanted) return true;
        if ((trmt$forcedFaces & 0x02) == 0) return false;
        Settling.noteCullHookRan();
        return true;
    }

    /** The face toward the north. */
    @ModifyExpressionValue(
        method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z",
            ordinal = 2),
        require = 0)
    private boolean trmt$keepFaceNorth(boolean wanted) {
        if (wanted) return true;
        if ((trmt$forcedFaces & 0x04) == 0) return false;
        Settling.noteCullHookRan();
        return true;
    }

    /** The face toward the south. */
    @ModifyExpressionValue(
        method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z",
            ordinal = 3),
        require = 0)
    private boolean trmt$keepFaceSouth(boolean wanted) {
        if (wanted) return true;
        if ((trmt$forcedFaces & 0x08) == 0) return false;
        Settling.noteCullHookRan();
        return true;
    }

    /** The face toward the west. */
    @ModifyExpressionValue(
        method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z",
            ordinal = 4),
        require = 0)
    private boolean trmt$keepFaceWest(boolean wanted) {
        if (wanted) return true;
        if ((trmt$forcedFaces & 0x10) == 0) return false;
        Settling.noteCullHookRan();
        return true;
    }

    /** The face toward the east. */
    @ModifyExpressionValue(
        method = { "renderStandardBlockWithAmbientOcclusion(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithAmbientOcclusionPartial(Lnet/minecraft/block/Block;IIIFFF)Z",
            "renderStandardBlockWithColorMultiplier(Lnet/minecraft/block/Block;IIIFFF)Z" },
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;shouldSideBeRendered(Lnet/minecraft/world/IBlockAccess;IIII)Z",
            ordinal = 5),
        require = 0)
    private boolean trmt$keepFaceEast(boolean wanted) {
        if (wanted) return true;
        if ((trmt$forcedFaces & 0x20) == 0) return false;
        Settling.noteCullHookRan();
        return true;
    }
}
