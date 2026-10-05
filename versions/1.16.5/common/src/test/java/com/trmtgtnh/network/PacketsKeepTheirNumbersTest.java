package com.trmtgtnh.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The packet ids are a wire format, so they are held still by a test rather than by memory.
 *
 * <p>
 * Forge puts a message's number on the wire. A client and a server that disagree about which number
 * means which message do not fail to connect - they misread each other, and the first sign of it is
 * a corrupted overlay or a handler reading somebody else's bytes. Nothing about that shows up in a
 * build.
 *
 * <p>
 * Eight of the twenty-one numbers belong to messages whose payload classes are not ported yet. They
 * are reserved rather than reused, so that each comes back to the number it had rather than being
 * renumbered around, and this is what stops a later packet quietly taking one.
 */
class PacketsKeepTheirNumbersTest {

    @Test
    void no_two_packets_share_a_number_or_a_name() {
        Set<Integer> ids = new HashSet<Integer>();
        Set<String> names = new HashSet<String>();
        for (Packets.Entry<?> each : Packets.all()) {
            assertTrue(ids.add(Integer.valueOf(each.id)), "two packets claim id " + each.id);
            assertTrue(names.add(each.name), "two packets claim the name " + each.name);
        }
    }

    @Test
    void nothing_has_taken_a_reserved_number() {
        Set<Integer> reserved = new HashSet<Integer>();
        for (int each : Packets.RESERVED) {
            reserved.add(Integer.valueOf(each));
        }

        List<String> taken = new ArrayList<String>();
        for (Packets.Entry<?> each : Packets.all()) {
            if (reserved.contains(Integer.valueOf(each.id))) {
                taken.add(each.name + " has taken " + each.id);
            }
        }

        assertTrue(
            taken.isEmpty(),
            "These ids belong to packets that are not ported yet and must stay free until they are, "
                + "so that each returns to the number it had rather than being renumbered around "
                + "every client that has already seen it: " + taken);
    }

    @Test
    void every_reserved_number_is_still_a_packet_that_is_coming() {
        // The list and the reservations have to add up to the twenty-one the other editions carry.
        // If they stop adding up, either a packet arrived and nobody took its id off the reserved
        // list, or one was dropped and nobody said so.
        assertEquals(
            21,
            Packets.all().size() + Packets.RESERVED.length,
            "The 1.12.2 edition carries twenty-one messages. What is here plus what is reserved "
                + "should still be twenty-one - unless this edition has deliberately gained or lost "
                + "one, in which case this number is the place to say so.");
    }

    @Test
    void every_packet_can_be_made_empty_and_is_what_it_says_it_is() {
        for (Packets.Entry<?> each : Packets.all()) {
            Message made = each.make.get();
            assertNotNull(made, each.name + " cannot be made");
            assertTrue(
                each.type.isInstance(made),
                each.name + " is listed as " + each.type.getSimpleName() + " but makes a "
                    + made.getClass()
                        .getSimpleName());
            assertNotNull(each.receiver, each.name + " has nothing to handle it");
            assertNotNull(each.to, each.name + " does not say which end receives it");
        }
    }

    @Test
    void a_name_is_something_a_resource_location_will_accept() {
        for (Packets.Entry<?> each : Packets.all()) {
            assertFalse(each.name.isEmpty(), "a packet has no name");
            assertEquals(
                each.name,
                each.name.toLowerCase(java.util.Locale.ROOT),
                each.name + " must be lower case, because Fabric addresses a message by this and a "
                    + "ResourceLocation refuses anything else");
            assertTrue(
                each.name.matches("[a-z0-9_]+"),
                each.name + " may only hold a-z, 0-9 and underscores, for the same reason");
        }
    }
}
