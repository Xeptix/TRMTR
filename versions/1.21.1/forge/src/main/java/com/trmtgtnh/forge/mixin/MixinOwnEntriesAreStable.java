package com.trmtgtnh.forge.mixin;

import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Lifecycle;
import com.trmtgtnh.Trmt;

/**
 * This mod's own registry entries are stable on Forge, as they are on NeoForge and Fabric (0.9.226).
 *
 * <p>
 * From 1.21 an entry read from a pack is stable only when its pack says which known pack it is
 * ({@code RegistryDataLoader}, read from its bytecode: a resource's knownPackInfo mapped to {@code Lifecycle.stable()},
 * else {@code experimental()}). NeoForge and Fabric give a mod's own pack that answer; Forge 52 gives none, so this mod's
 * three enchantments - data from 1.21 - made every world they were in "experimental", and Forge then met each opening
 * of an existing world with "Worlds using Experimental Settings are not supported" and a backup screen. Found by the
 * reload part, which sat behind that screen in the first 1.21.1 Forge games.
 *
 * <p>
 * Only entries under this mod's own namespace are given the stable lifecycle, and only where the pack left them
 * experimental; every other entry is loaded as the game decided.
 */
@Mixin(RegistryDataLoader.class)
public abstract class MixinOwnEntriesAreStable {

    @WrapOperation(
        method = "loadContentsFromManager",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/resources/RegistryDataLoader;loadElementFromResource(Lnet/minecraft/core/WritableRegistry;Lcom/mojang/serialization/Decoder;Lnet/minecraft/resources/RegistryOps;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/server/packs/resources/Resource;Lnet/minecraft/core/RegistrationInfo;)V"))
    private static <E> void trmt$ownEntriesAreStable(WritableRegistry<E> registry, Decoder<E> decoder, RegistryOps<?> ops,
        ResourceKey<E> key, Resource resource, RegistrationInfo info, Operation<Void> original) {
        RegistrationInfo given = info;
        if (key != null && Trmt.MODID.equals(key.location()
            .getNamespace()) && info != null && !Lifecycle.stable().equals(info.lifecycle())) {
            given = new RegistrationInfo(info.knownPackInfo(), Lifecycle.stable());
        }
        original.call(registry, decoder, ops, key, resource, given);
    }
}
