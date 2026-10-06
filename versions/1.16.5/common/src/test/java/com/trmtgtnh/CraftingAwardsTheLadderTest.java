package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Crafting a tamper awards its step of the ladder.
 *
 * <p>
 * {@code ModAchievements.onCrafted} was carried into this edition with the advancements it awards and
 * then called by nobody, so crafting any of them awarded nothing on either loader. Found by the
 * unwired sweep rather than by anybody playing, which is the point of that sweep: a trigger with no
 * caller is a feature that compiles, ships, and does not exist.
 *
 * <p>
 * The two loaders reach it differently and that is worth holding here. Forge fires an event for
 * crafting; Fabric has none at all, so its half is a mixin into the slot a result is taken from -
 * which is where vanilla hangs its own crafting triggers, and is the argument for it being the right
 * place rather than a convenient one.
 */
class CraftingAwardsTheLadderTest {

    @Test
    void the_trigger_has_a_caller() throws IOException {
        String events = String.join("\n", SourceTree.lines("com/trmtgtnh/server/ServerEvents.java"));
        assertTrue(
            events.contains("ModAchievements.onCrafted("),
            "the shared seam has to reach the trigger, or the advancements are awarded by nobody");
        assertTrue(
            events.contains("player.level.isClientSide()"),
            "and only on a server - an advancement awarded on a client is awarded to nobody");
    }

    @Test
    void both_loaders_hear_a_crafting() throws IOException {
        String forge = read("forge/src/main/java/com/trmtgtnh/forge/ForgeEvents.java");
        assertTrue(
            forge.contains("ItemCraftedEvent") && forge.contains("ServerEvents.crafted("),
            "Forge has an event for this and has to use it");

        String mixin = read("fabric/src/main/java/com/trmtgtnh/fabric/mixin/MixinCraftingResult.java");
        assertTrue(
            mixin.contains("@Mixin(ResultSlot.class)") && mixin.contains("ServerEvents.crafted("),
            "Fabric has no crafting event, so the call goes where the game does the thing");

        String config = read("fabric/src/main/resources/trmtgtnh-fabric.mixins.json");
        assertTrue(
            config.contains("\"MixinCraftingResult\""),
            "and a mixin that is not in the config is a file that compiles and never applies");
    }

    private static String read(String relative) throws IOException {
        File at = new File(SourceTree.repoRoot(), relative);
        assertTrue(at.isFile(), at.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(at.toPath()), StandardCharsets.UTF_8);
    }
}
