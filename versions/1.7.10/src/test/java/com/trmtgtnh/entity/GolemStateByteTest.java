package com.trmtgtnh.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.trmtgtnh.SourceTree;

/**
 * What a golem tells the clients watching it fits in one byte, and is compared as one.
 *
 * <p>
 * Everything a client needs to draw a golem doing something - the two-bit kind of work, whether it
 * is armed, stocked, ordered, fleeing, short of blocks and eating - is packed into a single watched
 * byte rather than a slot each, because a data-watcher slot costs a field on every golem and this
 * costs a bit. The flags are worked out as {@code int} constants and the byte is what goes on the
 * wire, and that seam is where two things can go wrong quietly.
 *
 * <p>
 * The first is running out of room. Seven of the eight bits are spoken for and the eighth is the
 * eating flag, so the byte is full; an eighth flag added as {@code 256} would compile, pack, narrow
 * to nothing and simply never arrive. The second is the sign. The eating flag is the top bit, so a
 * chewing golem packs a number at or above 128 while the byte holding it reads back negative, and
 * the freshly packed value compared as an {@code int} against that byte can never agree - which is
 * what it did, publishing the same state every tick a golem had anything in its mouth. Nothing broke
 * because the watcher drops a write that changes nothing, so the whole cost was a boxed byte a tick
 * and nobody would ever have found it by looking.
 *
 * <p>
 * Read from the source rather than from the classes, because both of these live in files that name
 * Minecraft and so cannot be loaded without it. What is being checked is arithmetic and a
 * comparison, both of which are legible in the text.
 */
class GolemStateByteTest {

    private static final String COMBAT = "com/trmtgtnh/entity/GolemCombat.java";

    private static final String GOLEM = "com/trmtgtnh/entity/EntityGolemOfWays.java";

    private static final Pattern FLAG = Pattern
        .compile("public static final int ([A-Z_]+_BIT|BUSY_MASK)\\s*=\\s*(\\d+)\\s*;");

    /** The comparison that decides whether the watched byte is rewritten. */
    private static final Pattern COMPARED = Pattern
        .compile("if \\(([a-zA-Z][a-zA-Z0-9]*) != dataWatcher\\.getWatchableObjectByte\\(WATCH_STATE\\)\\)");

    @Test
    void every_flag_is_a_bit_of_its_own_and_the_byte_is_full() throws IOException {
        Map<String, Integer> flags = new LinkedHashMap<String, Integer>();
        for (String line : SourceTree.lines(COMBAT)) {
            Matcher found = FLAG.matcher(line.trim());
            if (found.find()) flags.put(found.group(1), Integer.valueOf(Integer.parseInt(found.group(2))));
        }

        assertTrue(
            flags.containsKey("BUSY_MASK"),
            "BUSY_MASK is not in " + COMBAT + " any more, so this test is reading the wrong thing.");
        int mask = flags.remove("BUSY_MASK")
            .intValue();
        assertEquals(3, mask, "The kind of work is two bits. Widening it shifts every flag above it.");
        assertTrue(flags.size() >= 5, "Five flags at least were expected in " + COMBAT + "; found " + flags.size());

        int taken = mask;
        for (Map.Entry<String, Integer> flag : flags.entrySet()) {
            int bit = flag.getValue()
                .intValue();
            if (Integer.bitCount(bit) != 1) {
                fail(
                    flag.getKey() + " is " + bit + ", which is not one bit. Each flag is one bit or the packing lies.");
            }
            if ((taken & bit) != 0) {
                fail(
                    flag.getKey() + " is "
                        + bit
                        + ", which is already spoken for. Two flags on one bit means each is read as the other.");
            }
            if (bit > 128) {
                fail(
                    flag.getKey() + " is "
                        + bit
                        + ", which does not survive the narrowing to a byte: it would pack, vanish on the way to the "
                        + "wire, and read as clear on every client. The byte is full - a ninth thing to tell wants a "
                        + "watcher slot of its own, and slots 29 to 31 are free on this entity.");
            }
            taken |= bit;
        }

        assertEquals(
            0xFF,
            taken,
            "The state byte is meant to be full, so this is a note rather than a defect: adjust it "
                + "deliberately if a flag has been retired.");
    }

    @Test
    void the_packed_state_is_narrowed_before_it_is_compared() throws IOException {
        List<String> lines = SourceTree.lines(GOLEM);

        int at = -1;
        String compared = null;
        for (int i = 0; i < lines.size() && compared == null; i++) {
            Matcher found = COMPARED.matcher(
                lines.get(i)
                    .trim());
            if (found.find()) {
                compared = found.group(1);
                at = i + 1;
            }
        }

        if (compared == null) {
            fail(
                "Nothing in " + GOLEM
                    + " compares anything with the watched state byte any more. That comparison is what keeps a golem "
                    + "from republishing its state every tick, so if it has moved, move this test with it.");
        }

        Pattern declared = Pattern.compile("\\bbyte\\s+" + compared + "\\s*=");
        for (int i = 0; i < at - 1; i++) {
            if (declared.matcher(lines.get(i))
                .find()) {
                return;
            }
        }

        fail(
            GOLEM + ":"
                + at
                + " compares "
                + compared
                + " with the watched byte, and "
                + compared
                + " is not a byte. The eating flag is the top bit, so a chewing golem packs 128 or more while the byte "
                + "reads back negative and the two can never agree. Narrow what was packed before comparing it.");
    }
}
