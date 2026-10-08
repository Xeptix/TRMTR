package com.trmtgtnh.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The update notice's portable half: what it trusts, what it compares, and when it asks at all.
 *
 * <p>
 * Planned in {@code docs/UPDATE-NOTICE-PLAN.md}. The rules held here are the ones a mistake in would
 * reach every player of every edition at once - a development build announced, a value from the file
 * shown that is not a version, a request made by a harness run or by a game whose owner switched the
 * notice off - so each is asserted rather than trusted, and the checks that read the file are run
 * against real files rather than strings.
 */
class UpdateCheckTest {

    @TempDir
    File folder;

    @Test
    @DisplayName("only a bare version, of one to four numbers and twenty characters, is ever shown")
    void trusted() {
        assertTrue(UpdateCheck.trusted("0.9.219"));
        assertTrue(UpdateCheck.trusted("1"));
        assertTrue(UpdateCheck.trusted("0.9.219.1"));
        assertFalse(UpdateCheck.trusted("0.9.219.1.2"));
        assertFalse(UpdateCheck.trusted("0.9.218-master.5+04de10a08b-dirty"));
        assertFalse(UpdateCheck.trusted("0.9.218-3-g1a2b3c4-dirty"));
        assertFalse(UpdateCheck.trusted("0.9.219 - go to example.com"));
        assertFalse(UpdateCheck.trusted("§c0.9.219"));
        assertFalse(UpdateCheck.trusted(""));
        assertFalse(UpdateCheck.trusted(null));
        // Twenty characters is the most; this is four numbers of a size each allowed, and twenty-two.
        assertTrue(UpdateCheck.trusted("123456.123456.123456"));
        assertFalse(UpdateCheck.trusted("123456.123456.123456.1"));
    }

    @Test
    @DisplayName("versions compare number by number, and a missing number is a nought")
    void compares() {
        assertTrue(UpdateCheck.compare("0.9.220", "0.9.219") > 0);
        assertTrue(UpdateCheck.compare("0.10.0", "0.9.999") > 0);
        assertTrue(UpdateCheck.compare("0.9", "0.9.1") < 0);
        assertEquals(0, UpdateCheck.compare("0.9.219", "0.9.219"));
        assertEquals(0, UpdateCheck.compare("0.9.219", "0.9.219.0"));
    }

    @Test
    @DisplayName("a development build is read back to the release it was made from, only for a test")
    void leading() {
        assertEquals("0.9.218", UpdateCheck.leading("0.9.218-master.5+04de10a08b-dirty"));
        assertEquals("0.9.218", UpdateCheck.leading("0.9.218-3-g1a2b3c4-dirty"));
        assertEquals("0.9.219", UpdateCheck.leading("0.9.219"));
        assertNull(UpdateCheck.leading("unknown"));
        assertNull(UpdateCheck.leading(null));
    }

    @Test
    @DisplayName("each edition reads its own line, comments and spacing notwithstanding")
    void ownLine() {
        String body = "# The newest release for each edition.\n1.7.10-forge=0.9.220\n1.16.5-fabric = 0.9.221 \n";
        assertEquals("0.9.220", UpdateCheck.newestFor(body, "1.7.10-forge"));
        assertEquals("0.9.221", UpdateCheck.newestFor(body, "1.16.5-fabric"));
        assertNull(UpdateCheck.newestFor(body, "1.12.2-forge"));
        assertNull(UpdateCheck.newestFor("", "1.7.10-forge"));
        assertNull(UpdateCheck.newestFor(null, "1.7.10-forge"));
    }

    @Test
    @DisplayName("a newer release is told, the same or an older one is not")
    void newerIsTold() throws IOException {
        String address = file("1.12.2-forge=0.9.220\n");
        UpdateCheck.Answer newer = UpdateCheck.ask(address, "1.12.2-forge", "0.9.219", false);
        assertEquals("0.9.220", newer.newer);
        assertEquals("0.9.219", newer.running);

        assertNull(UpdateCheck.ask(address, "1.12.2-forge", "0.9.220", false).newer);
        assertNull(UpdateCheck.ask(address, "1.12.2-forge", "0.9.221", false).newer);
        assertNull(UpdateCheck.ask(address, "1.7.10-forge", "0.9.219", false).newer);
    }

    @Test
    @DisplayName("a development build is never told - except under the test address, as its release")
    void developmentBuilds() throws IOException {
        String address = file("1.7.10-forge=0.9.220\n");
        String dev = "0.9.218-master.5+04de10a08b-dirty";
        UpdateCheck.Answer real = UpdateCheck.ask(address, "1.7.10-forge", dev, false);
        assertNull(real.newer);
        assertTrue(real.said.contains("development build"), real.said);

        UpdateCheck.Answer testing = UpdateCheck.ask(address, "1.7.10-forge", dev, true);
        assertEquals("0.9.220", testing.newer);
        assertEquals("0.9.218", testing.running);
    }

    @Test
    @DisplayName("a value in the file that is not a bare version is shown to nobody")
    void untrustedValue() throws IOException {
        String address = file("1.16.5-forge=0.9.220 - click here\n1.16.5-fabric=§c9.9.9\n");
        assertNull(UpdateCheck.ask(address, "1.16.5-forge", "0.9.219", false).newer);
        assertNull(UpdateCheck.ask(address, "1.16.5-fabric", "0.9.219", false).newer);
    }

    @Test
    @DisplayName("an address that cannot be read is an answer that says so, not a throw")
    void unreachable() {
        String missing = new File(folder, "not-there.properties").toURI()
            .toString();
        UpdateCheck.Answer answer = UpdateCheck.ask(missing, "1.12.2-forge", "0.9.219", false);
        assertNull(answer.newer);
        assertTrue(answer.said.startsWith("could not read"), answer.said);

        UpdateCheck.Answer nonsense = UpdateCheck.ask("not an address at all", "1.12.2-forge", "0.9.219", false);
        assertNull(nonsense.newer);
        assertTrue(nonsense.said.startsWith("could not read"), nonsense.said);
    }

    @Test
    @DisplayName("over the web, a 200 is read and anything else is not, whatever its body says")
    void webAnswers() throws Exception {
        final byte[] body = "1.12.2-forge=0.9.230\n".getBytes(Charset.forName("UTF-8"));
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer
            .create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/latest.properties", new com.sun.net.httpserver.HttpHandler() {

            @Override
            public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody()
                    .write(body);
                exchange.close();
            }
        });
        // A page that is not there, answering with the same words a real file would hold - which is
        // what a host's own error page could do, and must not be believed.
        server.createContext("/gone.properties", new com.sun.net.httpserver.HttpHandler() {

            @Override
            public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
                exchange.sendResponseHeaders(404, body.length);
                exchange.getResponseBody()
                    .write(body);
                exchange.close();
            }
        });
        server.start();
        try {
            String at = "http://127.0.0.1:" + server.getAddress()
                .getPort();
            assertEquals("0.9.230", UpdateCheck.ask(at + "/latest.properties", "1.12.2-forge", "0.9.219", false).newer);
            UpdateCheck.Answer gone = UpdateCheck.ask(at + "/gone.properties", "1.12.2-forge", "0.9.219", false);
            assertNull(gone.newer);
            assertTrue(gone.said.contains("HTTP 404"), gone.said);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("no more than four kilobytes of an answer is read")
    void longAnswer() throws IOException {
        StringBuilder padding = new StringBuilder();
        while (padding.length() < UpdateCheck.LONGEST_ANSWER) {
            padding.append("# padding padding padding padding\n");
        }
        String address = file(padding + "1.12.2-forge=0.9.220\n");
        assertNull(UpdateCheck.ask(address, "1.12.2-forge", "0.9.219", false).newer);
    }

    @Test
    @DisplayName("off means no request; so do the harness and a development game, unless a test asks")
    void mayAsk() {
        Map<String, String> plain = new HashMap<String, String>();
        assertTrue(UpdateCheck.mayAsk("operators", false, plain));
        assertTrue(UpdateCheck.mayAsk("everyone", false, plain));
        assertFalse(UpdateCheck.mayAsk("off", false, plain));
        assertFalse(UpdateCheck.mayAsk(" OFF ", false, plain));
        assertFalse(UpdateCheck.mayAsk("operators", true, plain));

        Map<String, String> walk = new HashMap<String, String>();
        walk.put("trmt.spike.walk", "true");
        assertFalse(UpdateCheck.mayAsk("operators", false, walk));
        Map<String, String> spike = new HashMap<String, String>();
        spike.put("trmt.spike", "true");
        assertFalse(UpdateCheck.mayAsk("operators", false, spike));
        Map<String, String> notOn = new HashMap<String, String>();
        notOn.put("trmt.spike.walk", "false");
        assertTrue(UpdateCheck.mayAsk("operators", false, notOn));

        Map<String, String> test = new HashMap<String, String>();
        test.put("trmt.spike.walk", "true");
        test.put(UpdateCheck.TEST_ADDRESS, "file:///nowhere.properties");
        assertTrue(UpdateCheck.mayAsk("operators", true, test));
        assertFalse(UpdateCheck.mayAsk("off", true, test), "off stays off, test or no test");
    }

    @Test
    @DisplayName("a game that will not ask says why, and one that will says nothing")
    void whyNot() {
        Map<String, String> plain = new HashMap<String, String>();
        assertNull(UpdateCheck.whyNot("operators", false, plain));
        assertNull(UpdateCheck.whyNot("everyone", false, plain));
        assertEquals("general.updateNotice is off, so no request is made", UpdateCheck.whyNot(" Off ", false, plain));
        assertEquals("this is a development game, which never asks", UpdateCheck.whyNot("operators", true, plain));

        Map<String, String> walk = new HashMap<String, String>();
        walk.put("trmt.spike.walk", "true");
        assertEquals("a test harness is running, which never asks", UpdateCheck.whyNot("operators", false, walk));

        Map<String, String> test = new HashMap<String, String>();
        test.put("trmt.spike.walk", "true");
        test.put(UpdateCheck.TEST_ADDRESS, "file:///nowhere.properties");
        assertNull(UpdateCheck.whyNot("operators", true, test));
        assertEquals(
            "general.updateNotice is off, so no request is made",
            UpdateCheck.whyNot("off", true, test),
            "off is said to be off, test or no test");
    }

    @Test
    @DisplayName("the sentence is cut at exactly its four places, or not at all")
    void pieces() {
        assertArrayEquals(
            new String[] { "", " is out - this world is running ", ". Get it from ", " or ", "." },
            UpdateCheck.pieces("%s is out - this world is running %s. Get it from %s or %s."));
        assertNull(UpdateCheck.pieces("%s is out"));
        assertNull(UpdateCheck.pieces("%s %s %s %s %s"));
        assertNull(UpdateCheck.pieces(null));
    }

    @Test
    @DisplayName("a check runs on its own thread and its answer is kept, with the test address honoured")
    void startsAndKeeps() throws Exception {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(UpdateCheck.TEST_ADDRESS, file("1.16.5-forge=0.9.230\n"));
        final CountDownLatch done = new CountDownLatch(1);
        UpdateCheck.start("1.16.5-forge", "0.9.219", properties, new Runnable() {

            @Override
            public void run() {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "the check never finished");
        assertNotNull(UpdateCheck.last());
        assertEquals("0.9.230", UpdateCheck.last().newer);
        assertFalse(UpdateCheck.due(System.currentTimeMillis()));
        assertTrue(UpdateCheck.due(System.currentTimeMillis() + UpdateCheck.STALE_AFTER_MS + 1));
    }

    @Test
    @DisplayName("the address and the two links are the ones every released jar was promised")
    void addressesHold() {
        // Every jar from 0.9.219 on reads this address for as long as it is played; changing it here
        // silences all of them, so it is pinned and a change has to be made on purpose in two places.
        assertEquals("https://raw.githubusercontent.com/Xeptix/TRMTR/master/latest.properties", UpdateCheck.ADDRESS);
        assertEquals("https://www.curseforge.com/minecraft/mc-mods/trmt-reimagined", UpdateCheck.CURSEFORGE);
        assertEquals("https://modrinth.com/mod/trmtr", UpdateCheck.MODRINTH);
    }

    /** A file holding this text, as an address the check can read. */
    private String file(String text) throws IOException {
        File at = File.createTempFile("latest", ".properties", folder);
        Files.write(at.toPath(), text.getBytes(Charset.forName("UTF-8")));
        return at.toURI()
            .toString();
    }
}
