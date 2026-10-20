package com.trmtgtnh.forge.mixin;

import java.util.function.Function;

import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
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
 *
 * <p>
 * By 26.1 that answer is given in {@code ResourceManagerRegistryLoadTask}, the task that reads a registry's entries
 * from the packs (resources/ResourceManagerRegistryLoadTask.java): each file's registration info is looked up from its
 * known pack, the same rule, inside a lambda that makes the file's pending entry. The lambda is named by its
 * descriptor, which is the method's own shape, rather than by the number the compiler gave it. Only the resource path,
 * as 1.21.1's hook was: entries a client is sent by its server are left as they come.
 */
@Mixin(net.minecraft.resources.ResourceManagerRegistryLoadTask.class)
public abstract class MixinOwnEntriesAreStable {

    @WrapOperation(
        method = "*(Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/Decoder;"
            + "Lnet/minecraft/resources/RegistryOps;Lnet/minecraft/resources/Identifier;"
            + "Lnet/minecraft/server/packs/resources/Resource;)Lnet/minecraft/resources/RegistryLoadTask$PendingRegistration;",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object trmt$ownEntriesAreStable(Function<Object, Object> lookup, Object knownPack, Operation<Object> original,
        @Local ResourceKey<?> key) {
        Object answered = original.call(lookup, knownPack);
        if (!(answered instanceof RegistrationInfo)) return answered;
        RegistrationInfo info = (RegistrationInfo) answered;
        // A key's name is identifier() by 26.1 (resources/ResourceKey.java), location() at 1.21.1.
        if (key != null && Trmt.MODID.equals(key.identifier()
            .getNamespace()) && !Lifecycle.stable()
                .equals(info.lifecycle())) {
            return new RegistrationInfo(info.knownPackInfo(), Lifecycle.stable());
        }
        return info;
    }
}
