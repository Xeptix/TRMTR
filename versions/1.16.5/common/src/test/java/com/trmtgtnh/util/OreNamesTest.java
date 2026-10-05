package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * What a pack's material names turn into at this version.
 *
 * <p>
 * Every settings list of materials in this mod is written in ore-dictionary names, and the ore
 * dictionary does not exist here - so this translation stands between a pack's tamper grades,
 * healing costs and reinforcement costs and the items they are meant to name. It is pure string
 * work, which is exactly the kind of thing that goes wrong quietly: a wrong tag is not an error, it
 * is a material nothing in the world has, and the setting simply stops doing anything.
 *
 * <p>
 * Only the naming is checked here. Whether a pack actually has anything under a tag needs a loaded
 * game and belongs in front of one.
 */
class OreNamesTest {

    private static String tag(String oreName) {
        return OreNames.tagFor(oreName) == null ? null
            : OreNames.tagFor(oreName)
                .toString();
    }

    @Test
    void the_plain_ones_are_the_folder_and_the_metal() {
        assertEquals("forge:ingots/iron", tag("ingotIron"));
        assertEquals("forge:ingots/gold", tag("ingotGold"));
        assertEquals("forge:gems/diamond", tag("gemDiamond"));
        assertEquals("forge:nuggets/gold", tag("nuggetGold"));
        assertEquals("forge:dusts/redstone", tag("dustRedstone"));
    }

    @Test
    void a_name_of_several_words_is_split_at_its_capitals() {
        assertEquals("forge:ingots/tungsten_steel", tag("ingotTungstenSteel"));
        assertEquals("forge:ingots/stainless_steel", tag("ingotStainlessSteel"));
        assertEquals("forge:ingots/wrought_iron", tag("ingotWroughtIron"));
    }

    @Test
    void an_abbreviation_keeps_its_letters_apart() {
        // HSSG is four capitals, so each one starts a word. Nothing in the convention says
        // otherwise, and a pack whose tag disagrees can write the tag itself.
        assertEquals("forge:ingots/h_s_s_g", tag("ingotHSSG"));
    }

    @Test
    void a_block_of_metal_is_not_under_blocks() {
        // The one irregular plural in the published convention, and the reason this is a table
        // rather than a rule: a storage block is not a block.
        assertEquals("forge:storage_blocks/iron", tag("blockIron"));
    }

    @Test
    void a_folder_nobody_published_is_guessed_rather_than_refused() {
        // A pack may name something neither Forge nor this mod has heard of. A close guess leaves
        // the pack a tag it can match; a refusal leaves the setting doing nothing.
        assertEquals("forge:widgets/brass", tag("widgetBrass"));
    }

    @Test
    void a_tag_written_as_a_tag_is_taken_as_written() {
        assertEquals("forge:ingots/iron", tag("forge:ingots/iron"));
        assertEquals("mymod:things/special", tag("mymod:things/special"));
    }

    @Test
    void nothing_is_nothing() {
        assertNull(tag(null));
        assertNull(tag(""));
    }
}
