package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.trmtgtnh.server.UpdateNotice;

/**
 * The update notice is reached: asked at server start, told on every join, forgotten on every leave.
 *
 * <p>
 * Planned in {@code docs/UPDATE-NOTICE-PLAN.md}. A notice whose hooks nobody calls is a class that
 * compiles, passes its own tests and tells nobody anything - which is how five shipped features of the
 * 1.16.5 edition turned out to be dead on arrival. So each hook is read for the call inside the method
 * that has to make it, not for the name somewhere in the file, which the declaration would satisfy.
 */
class UpdateNoticeIsWiredTest {

    @Test
    void the_server_start_asks_and_every_join_and_leave_is_told() throws IOException {
        String trmt = body(source("com/trmtgtnh/Trmt.java"), "serverStarting");
        assertTrue(
            trmt.contains("UpdateNotice.serverStarting()"),
            "Trmt.serverStarting never starts the update check, so no server ever asks");

        String events = source("com/trmtgtnh/server/ServerEvents.java");
        assertTrue(
            body(events, "onPlayerLoggedIn").contains("UpdateNotice.onLogin("),
            "nothing tells a joining player about a newer release");
        assertTrue(
            body(events, "onPlayerLoggedOut").contains("UpdateNotice.onLogout("),
            "a player who leaves is never forgotten, so their next join is not told");
    }

    @Test
    void this_jar_reads_its_own_line_and_finds_the_owner_by_connection() throws IOException {
        assertEquals("1.7.10-forge", UpdateNotice.EDITION, "this jar would read another edition's line");
        String notice = body(source("com/trmtgtnh/server/UpdateNotice.java"), "canUpdate");
        // The single-player owner by the in-memory connection: vanilla makes the owner an operator only
        // with cheats on, and the owner's name does not match under an offline-mode launcher.
        assertTrue(notice.contains("isLocalChannel()"), "the single-player owner is not found by their connection");
        assertTrue(notice.contains("func_152596_g("), "a dedicated server's operators are not asked for");
    }

    @Test
    void a_server_that_will_not_ask_says_why_and_a_test_logs_the_line_as_sent() throws IOException {
        String notice = String.join("\n", SourceTree.lines("com/trmtgtnh/server/UpdateNotice.java"));
        // Squeezed, so a formatter's line breaks cannot hide the call or invent one.
        String start = body(notice, "serverStarting").replaceAll("\\s+", "");
        assertTrue(
            start.contains(
                "Stringnot=UpdateCheck.whyNot(TrmtConfig.updateNotice,development(),System.getProperties());"
                    + "if(not!=null)Trmt.LOG.info(\"Updatecheck:notasked-{}\",not);"),
            "a server that will not ask says nothing, so the notice switched off looks like a check never wired up");
        String tell = body(notice, "tell").replaceAll("\\s+", "");
        assertTrue(
            tell.contains("IChatComponentsaid=line(answer.newer,answer.running);player.addChatMessage(said);"),
            "the line logged is not the line that was sent");
        assertTrue(
            tell.contains(
                "if(System.getProperty(UpdateCheck.TEST_ADDRESS)!=null){"
                    + "Trmt.LOG.info(\"Updatenotice:thelineassent-{}\",IChatComponent.Serializer.func_150696_a(said));}"),
            "the line is not logged as sent under the test address, so no run can read where its links go");
    }

    private static String source(String relative) throws IOException {
        return String.join("\n", SourceTree.lines(relative));
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
