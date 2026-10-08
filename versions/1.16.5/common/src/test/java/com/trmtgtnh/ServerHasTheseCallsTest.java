package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Four calls a dedicated server's copy of the game does not have, and what replaced them.
 *
 * <p>
 * Fabric marks some of the game's members client-only, and a dedicated server's jar simply does not carry them;
 * Forge does the same for a few. The compile is against the merged jar, where every one exists, and every client
 * and every integrated server has them too - so the first that anything saw of {@code Ingredient.of(ItemStack...)}
 * was a Fabric dedicated server crashing on its first tick, on 2026-10-07, when the update notice's join test kept
 * one up long enough to tick. It had shipped since 0.9.217. {@code tools/server_safe.py} then read every reference
 * against the game jar's own marks and found the other three, which no server had reached yet.
 *
 * <p>
 * This holds the four; the tool is what finds the next one.
 */
class ServerHasTheseCallsTest {

    @Test
    void recipes_build_with_what_a_server_has() throws IOException {
        String recipes = code("com/trmtgtnh/item/OreRecipes.java");
        assertFalse(
            recipes.contains("Ingredient.of((ItemStack)one)"),
            "a stack ingredient is built with Ingredient.of(ItemStack...), which a Fabric server does not have");
        assertTrue(
            recipes.contains("if(oneinstanceofItemStack)returnIngredient.of(java.util.stream.Stream.of((ItemStack)one));"),
            "a stack ingredient is no longer built through the Stream overload");
        assertFalse(recipes.contains("loose.getGroup()"), "the draught recipe reads ShapelessRecipe.getGroup, client-only on Fabric");
        assertTrue(
            recipes.contains("finalStringband=group==null?\"\":group;")
                && recipes.contains("returnnewRecipeDraught(id,band,"),
            "the draught recipe's group can be null again, and a null group fails as the recipe packet is written");
    }

    @Test
    void the_update_notice_is_styled_with_what_a_server_has() throws IOException {
        String notice = code("com/trmtgtnh/server/UpdateNotice.java");
        assertFalse(notice.contains(".withUnderlined("), "the notice's links are underlined with Style.withUnderlined, client-only on both loaders");
        assertTrue(
            notice.contains("Style.EMPTY.applyFormat(ChatFormatting.AQUA).applyFormat(ChatFormatting.UNDERLINE)"),
            "the notice's links are no longer underlined through applyFormat");
    }

    @Test
    void snapshots_are_read_and_written_with_what_a_server_has() throws IOException {
        String store = code("com/trmtgtnh/server/SnapshotStore.java");
        assertFalse(store.contains("NbtIo.read(file)"), "snapshots are read through NbtIo's File overload, client-only on Fabric");
        assertFalse(store.contains("NbtIo.write(root,file)"), "snapshots are written through NbtIo's File overload, client-only on Fabric");
        assertTrue(store.contains("CompoundTagroot=NbtIo.read(in);"), "snapshots are no longer read through the stream overload");
        assertTrue(store.contains("NbtIo.write(root,out);"), "snapshots are no longer written through the stream overload");
    }

    /** One source file, comments out and spaces squeezed, so a guard reads calls and never prose. */
    private static String code(String relative) throws IOException {
        return String.join("\n", SourceTree.lines(relative))
            .replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("//[^\\n]*", " ")
            .replaceAll("\\s+", "");
    }
}
