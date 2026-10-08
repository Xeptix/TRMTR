package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The update notice is reached: asked at server start, told on every join, forgotten on every leave,
 * on both loaders - and each jar reads its own line in the version file.
 *
 * <p>
 * Planned in {@code docs/UPDATE-NOTICE-PLAN.md} in the 1.7.10 tree. A notice whose hooks nobody calls
 * is a class that compiles, passes its own tests and tells nobody anything - which is how five shipped
 * features of this edition turned out to be dead on arrival, each one right in {@code common} and
 * never called by a loader. So each hook is read for the call inside the method that has to make it,
 * and the chain is followed into both loader modules, by the receiver as well as the name.
 */
class UpdateNoticeIsWiredTest {

    @Test
    void the_server_start_asks_and_every_join_and_leave_is_told() throws IOException {
        String events = String.join("\n", SourceTree.lines("com/trmtgtnh/server/ServerEvents.java"));
        assertTrue(
            body(events, "serverStarting").contains("UpdateNotice.serverStarting()"),
            "ServerEvents.serverStarting never starts the update check, so no server ever asks");
        assertTrue(
            body(events, "playerJoined").contains("UpdateNotice.onLogin("),
            "nothing tells a joining player about a newer release");
        assertTrue(
            body(events, "playerLeft").contains("UpdateNotice.onLogout("),
            "a player who leaves is never forgotten, so their next join is not told");
    }

    @Test
    void both_loaders_reach_those_hooks() throws IOException {
        String forgeEvents = loader("forge/src/main/java/com/trmtgtnh/forge/ForgeEvents.java");
        assertTrue(
            squeezed(body(forgeEvents, "onPlayerLoggedIn")).contains("ServerEvents.playerJoined(event.getPlayer())"),
            "Forge never hands a joining player to ServerEvents.playerJoined");
        assertTrue(
            squeezed(body(forgeEvents, "onPlayerLoggedOut")).contains("ServerEvents.playerLeft(event.getPlayer())"),
            "Forge never hands a leaving player to ServerEvents.playerLeft");
        assertTrue(
            squeezed(loader("forge/src/main/java/com/trmtgtnh/forge/TrmtForge.java"))
                .contains("ServerEvents.serverStarting(event.getServer())"),
            "Forge never tells ServerEvents a server is starting");

        String fabricEvents = squeezed(loader("fabric/src/main/java/com/trmtgtnh/fabric/FabricEvents.java"));
        assertTrue(
            fabricEvents.contains("ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->ServerEvents.playerJoined(handler.player))"),
            "Fabric never hands a joining player to ServerEvents.playerJoined");
        assertTrue(
            fabricEvents
                .contains("ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->ServerEvents.playerLeft(handler.player))"),
            "Fabric never hands a leaving player to ServerEvents.playerLeft");
        assertTrue(
            squeezed(loader("fabric/src/main/java/com/trmtgtnh/fabric/TrmtFabric.java"))
                .contains("ServerLifecycleEvents.SERVER_STARTING.register(server->com.trmtgtnh.server.ServerEvents.serverStarting(server))"),
            "Fabric never tells ServerEvents a server is starting");
    }

    @Test
    void each_loader_hands_over_its_own_line_and_its_own_word_for_development() throws IOException {
        String forge = squeezed(body(loader("forge/src/main/java/com/trmtgtnh/forge/TrmtForge.java"), "TrmtForge"));
        assertTrue(
            forge.contains("UpdateNotice.edition(\"1.16.5-forge\",!net.minecraftforge.fml.loading.FMLEnvironment.production)"),
            "the Forge jar does not say it is 1.16.5-forge, or does not ask Forge whether this is a development game");
        String fabric = squeezed(body(loader("fabric/src/main/java/com/trmtgtnh/fabric/TrmtFabric.java"), "onInitialize"));
        assertTrue(
            fabric.contains("UpdateNotice.edition(\"1.16.5-fabric\",FabricLoader.getInstance().isDevelopmentEnvironment())"),
            "the Fabric jar does not say it is 1.16.5-fabric, or does not ask Fabric whether this is a development game");
    }

    @Test
    void the_owner_is_found_by_connection_and_a_server_asks_its_operators() throws IOException {
        String notice = body(String.join("\n", SourceTree.lines("com/trmtgtnh/server/UpdateNotice.java")), "canUpdate");
        // The single-player owner by the in-memory connection: vanilla makes the owner an operator only
        // with cheats on, and the owner's name does not match under an offline-mode launcher.
        assertTrue(notice.contains("isMemoryConnection()"), "the single-player owner is not found by their connection");
        assertTrue(notice.contains(".isOp("), "a dedicated server's operators are not asked for");
    }

    @Test
    void a_server_that_will_not_ask_says_why_and_a_test_logs_the_line_as_sent() throws IOException {
        String notice = String.join("\n", SourceTree.lines("com/trmtgtnh/server/UpdateNotice.java"));
        // Squeezed, so a formatter's line breaks cannot hide the call or invent one.
        String start = body(notice, "serverStarting").replaceAll("\\s+", "");
        assertTrue(
            start.contains("Stringnot=UpdateCheck.whyNot(TrmtConfig.updateNotice,development,System.getProperties());"
                + "if(not!=null)Trmt.LOG.info(\"Updatecheck:notasked-{}\",not);"),
            "a server that will not ask says nothing, so the notice switched off looks like a check never wired up");
        String tell = body(notice, "tell").replaceAll("\\s+", "");
        assertTrue(
            tell.contains("Componentsaid=line(answer.newer,answer.running);player.sendMessage(said,net.minecraft.Util.NIL_UUID);"),
            "the line logged is not the line that was sent");
        assertTrue(
            tell.contains("if(System.getProperty(UpdateCheck.TEST_ADDRESS)!=null){"
                + "Trmt.LOG.info(\"Updatenotice:thelineassent-{}\",Component.Serializer.toJson(said));}"),
            "the line is not logged as sent under the test address, so no run can read where its links go");
    }

    private static String loader(String relative) throws IOException {
        File file = new File(SourceTree.repoRoot(), relative);
        assertTrue(file.isFile(), relative + " is not there; this test reads it by name");
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
    }

    private static String squeezed(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("//[^\\n]*", " ")
            .replaceAll("\\s+", "");
    }

    /** One method's body by its name, comments left out. */
    private static String body(String source, String name) {
        Matcher found = Pattern.compile("\\b" + name + "\\s*\\([^)]*\\)\\s*(throws\\s+[\\w.,\\s]+)?\\{")
            .matcher(source);
        assertTrue(found.find(), "no method " + name);
        int open = source.indexOf('{', found.start());
        int depth = 0;
        for (int at = open; at < source.length(); at++) {
            char each = source.charAt(at);
            if (each == '{') depth++;
            else if (each == '}' && --depth == 0) {
                return source.substring(open + 1, at)
                    .replaceAll("(?s)/\\*.*?\\*/", " ")
                    .replaceAll("//[^\\n]*", " ");
            }
        }
        return "";
    }
}
