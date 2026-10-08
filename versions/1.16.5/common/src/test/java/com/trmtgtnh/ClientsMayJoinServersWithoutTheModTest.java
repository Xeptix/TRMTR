package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * A Forge client carrying this mod may join a server without it, as in the 1.7.10 edition.
 *
 * <p>
 * The channel's two version checks were both {@code equals} until 0.9.219, so such a client was turned
 * away at the door while the manual said it was fine. A server still insists on the channel in its
 * clients - a client without the mod could not join anyway, Forge's registry handshake refuses one
 * missing the server's blocks - and that half is held as well, so loosening the wrong one is caught.
 */
class ClientsMayJoinServersWithoutTheModTest {

    @Test
    void a_forge_client_accepts_a_server_without_the_channel() throws IOException {
        File file = new File(SourceTree.repoRoot(), "forge/src/main/java/com/trmtgtnh/forge/ForgeChannel.java");
        assertTrue(file.isFile(), file.getAbsolutePath() + " is not there");
        String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        int at = source.indexOf("NetworkRegistry.newSimpleChannel(");
        assertTrue(at >= 0, "the channel is no longer opened with newSimpleChannel");
        String call = source.substring(at);
        call = call.substring(0, call.indexOf(");") + 2)
            .replaceAll("//[^\\n]*", " ")
            .replaceAll("\\s+", "");
        assertTrue(
            call.contains("NetworkRegistry.ABSENT.equals(version)"),
            "a client refuses a server without this mod's channel again: " + call);
        assertTrue(
            call.endsWith("VERSION::equals);"),
            "the server stopped asking its clients for the channel: " + call);
    }
}
