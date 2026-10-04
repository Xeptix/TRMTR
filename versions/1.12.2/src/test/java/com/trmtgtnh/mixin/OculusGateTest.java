package com.trmtgtnh.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The gate that decides whether the Oculus seam is applied at all.
 *
 * <p>
 * This is a four-line class guarding every mixin in the mod, and the way it goes wrong is not subtle
 * in its effect but is very easy to miss in review: invert the name test and the two mixins that have
 * nothing to do with Oculus are refused on every client, which costs the librarian's books and the
 * arrival of worn ground and says nothing about why. Nothing else in the build would notice. So the
 * waving-through is pinned here by name, one assertion per mixin the config actually lists.
 *
 * <p>
 * The refusal side is pinned by running at all. There is no Oculus on the test classpath and no
 * launch class loader either, so {@code onLoad} takes its own exception path and settles on absent -
 * which is the same answer a client without Oculus gets, reached the same way. A gate that threw
 * instead of answering would fail the whole mixin config rather than this one mixin, and that is
 * exactly what the catch is there to stop.
 */
class OculusGateTest {

    private static OculusGate loaded() {
        OculusGate gate = new OculusGate();
        // No launch class loader outside a game: the probe inside this throws and is swallowed. That
        // it returns at all is half of what is being checked.
        gate.onLoad("com.trmtgtnh.mixin");
        return gate;
    }

    @Test
    void wavesThroughEveryMixinThatIsNotTheOculusOne() {
        OculusGate gate = loaded();
        assertTrue(
            gate.shouldApplyMixin(
                "net.minecraft.entity.passive.EntityVillager",
                "com.trmtgtnh.mixin.MixinLibrarianBooks"),
            "the librarian mixin has nothing to do with Oculus and must never be gated");
        assertTrue(
            gate.shouldApplyMixin(
                "net.minecraft.client.multiplayer.WorldClient",
                "com.trmtgtnh.mixin.MixinBlockArrivals"),
            "the block-arrival mixin has nothing to do with Oculus and must never be gated");
    }

    @Test
    void refusesTheOculusMixinWhenThereIsNoOculus() {
        assertFalse(
            loaded().shouldApplyMixin(OculusGate.HOLDER, "com.trmtgtnh.mixin.MixinOculusBlockContext"),
            "with no Oculus on the classpath the seam must be refused rather than reported as missing");
    }

    @Test
    void namesAClassInsideOculusRatherThanOneOfOurOwn() {
        // The whole design rests on this string being Oculus's and not something this mod ships: if it
        // ever came to name a class in this jar, the gate would always open and the mixin would always
        // be applied to something that is not the holder.
        assertTrue(OculusGate.HOLDER.startsWith("net.coderbot.iris."), OculusGate.HOLDER);
        assertFalse(OculusGate.HOLDER.startsWith("com.trmtgtnh."), OculusGate.HOLDER);
    }
}
