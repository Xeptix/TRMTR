package com.trmtgtnh.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import com.trmtgtnh.client.model.GhostWindows;

/**
 * The light an entity is drawn with where the point it is lit from stands in a worn stair's cell - GhostWindows.entityLight
 * (0.9.222, spec GF8).
 *
 * <p>
 * A worn stair stops all light, as the 1.7.10 edition's does, and its cell holds none; every renderer reads a cell's light
 * through LevelRenderer.getLightColor, which MixinWindowLight answers, but an entity's renderer reads the world's light
 * straight, so an item lying on a worn step came out black. That edition's world lends the brightest neighbour's light to
 * that read as well. Taken at the return, so whatever the renderer read - its own glow, a fire - still counts, and not
 * required: a renderer that has rewritten this method loses the lending, never the game.
 *
 * <p>
 * In this module rather than common because it names a game method - see CommonMixinsNeedNoRefmapTest. The other
 * loader's module has the same class.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityLightLent {

    @Inject(
        method = "getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I",
        at = @At("RETURN"),
        cancellable = true,
        require = 0)
    private void trmt$lendLight(Entity entity, float partialTicks, CallbackInfoReturnable<Integer> callback) {
        if (entity == null || entity.level() == null) return;
        int packed = callback.getReturnValueI();
        // The client's BlockAndTintGetter is its own interface by 26.1, which the client's level implements and a
        // Level no longer does; an entity drawn is in the client's level, which is what 1.21.1 handed on.
        if (!(entity.level() instanceof net.minecraft.client.renderer.block.BlockAndTintGetter)) return;
        int lit = GhostWindows.entityLight((net.minecraft.client.renderer.block.BlockAndTintGetter) entity.level(),
            BlockPos.containing(entity.getLightProbePosition(partialTicks)), packed);
        if (lit != packed) callback.setReturnValue(Integer.valueOf(lit));
    }
}
