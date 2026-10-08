package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;

import org.junit.jupiter.api.Test;

/**
 * Any version of this mod on the other side is accepted, and so is none - as the 1.7.10 edition says.
 *
 * <p>
 * Until 0.9.219 this edition declared neither, so Forge's own default decided: the exact same version
 * on both sides. A client carrying the mod could not join a server without it, and a client one build
 * behind could not join at all - while this edition's manual said its own check accepted any version.
 * Asked of the class rather than its text, without starting the mod.
 */
class RemoteVersionsAreAcceptedTest {

    private static Class<?> trmt() throws ClassNotFoundException {
        return Class.forName("com.trmtgtnh.Trmt", false, RemoteVersionsAreAcceptedTest.class.getClassLoader());
    }

    @Test
    void the_mod_accepts_any_remote_version_or_none() throws Exception {
        Mod mod = trmt().getAnnotation(Mod.class);
        assertNotNull(mod, "the mod class is no longer a @Mod");
        assertEquals("*", mod.acceptableRemoteVersions(), "Forge falls back to wanting the same version on both sides");
    }

    @Test
    void the_network_check_answers_yes() throws Exception {
        Method check = null;
        for (Method each : trmt().getDeclaredMethods()) {
            if (each.isAnnotationPresent(NetworkCheckHandler.class)) check = each;
        }
        assertNotNull(check, "the mod has no network check, so Forge's default decides who may join");
        String body = String.join("\n", SourceTree.lines("com/trmtgtnh/Trmt.java"));
        String after = body.substring(body.indexOf(check.getName() + "("));
        String method = after.substring(after.indexOf('{') + 1, after.indexOf('}'));
        assertEquals("return true;", method.trim(), "the network check refuses somebody: " + method.trim());
        assertTrue(check.getReturnType() == boolean.class, "a network check answers a boolean");
    }
}
