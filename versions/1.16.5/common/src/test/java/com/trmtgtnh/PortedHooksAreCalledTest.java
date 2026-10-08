package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Hooks the 1.7.10 edition calls and this one had carried across without calling.
 *
 * <p>
 * Found on 2026-10-07 by {@code tools/unwired.py} and an audit of everything it listed. Each was a
 * method ported whole, often with its own tests, that nothing in this edition ever reached - a class
 * that is present is not a feature that runs. Each is held here by reading the call inside the method
 * that has to make it, with the spacing taken out, and never by finding the name somewhere in the
 * file, which the method's own declaration would satisfy with every call deleted. Both loaders are
 * read, because each hands the common code its events by its own road and either can drop one.
 */
class PortedHooksAreCalledTest {

    /**
     * A player head set last on the golem's shape stands the golem up.
     *
     * <p>
     * The guide's own recipe. {@code GolemBuilder.onHeadPlaced} was ported intact and called by
     * nobody, so the shape and the head did nothing at all, and a golem came only from a loot egg or
     * the demonstrate yard. Both loaders hand over who placed the block, because the builder records
     * the golem's maker and gives them the advancement.
     */
    @Test
    void a_head_set_on_the_shape_asks_the_golem_builder() throws IOException {
        String placed = flat(body(common("com/trmtgtnh/server/ServerEvents.java"), "blockPlaced"));
        assertTrue(placed.contains("AbstractSkullBlock"), "the golem is asked about blocks that are not skulls, or none");
        assertTrue(
            placed.contains("GolemBuilder.onHeadPlaced(level,pos.getX(),pos.getY(),pos.getZ(),player)"),
            "placing a head never asks the golem builder whether it completes the shape: " + placed);

        String forge = flat(body(loader("forge", "com/trmtgtnh/forge/ForgeEvents.java"), "onBlockPlace"));
        assertTrue(
            forge.contains("ServerEvents.blockPlaced(") && forge.contains("(net.minecraft.world.entity.player.Player)event.getEntity()"),
            "Forge hands the placement over without the player who made it: " + forge);
        String fabric = flat(body(loader("fabric", "com/trmtgtnh/fabric/mixin/MixinBlockItem.java"), "trmt\\$placed"));
        assertTrue(
            fabric.contains("ServerEvents.blockPlaced(") && fabric.contains("context.getPlayer())"),
            "Fabric hands the placement over without the player who made it: " + fabric);
    }

    /**
     * A server's rules are carried out on join, not only held.
     *
     * <p>
     * Carried from the 1.12.2 edition's proxy before that edition's was corrected, this held the rules and
     * asked only the stitch question - which compared the chains with themselves, because nothing rebuilt
     * them - so a visit drew by this client's own chains and decay mode whatever the server said.
     */
    @Test
    void a_servers_rules_are_carried_out_on_join() throws IOException {
        String rules = flat(body(common("com/trmtgtnh/client/ClientRules.java"), "applyServerRules"));
        int held = rules.indexOf("ServerRules.hold(");
        assertTrue(held >= 0, "the server's rules are never held");
        for (String step : new String[] { "ErosionChain.rebuild();", "PhysicalDecay.refresh();" }) {
            assertTrue(rules.indexOf(step) > held, "the rules are held and " + step + " is not asked for after them: " + rules);
        }
        assertTrue(rules.contains(".repaintAll();") && rules.contains(".restoreAll();"), "a forced overlay is never shown, or never taken down");
        assertTrue(rules.contains("ServerRules.enabledMovedFrom(before)){resettleSurfaces();"), "a family the server switched on is never put into the table");
        assertTrue(rules.contains("ServerRules.appearancesMovedFrom(before)){WearRestitch.get().requestRestitch("), "chains reaching new materials never ask for their pictures");
        assertTrue(
            rules.contains("ServerRules.overrode()>0&&!WearRestitch.get().pending()") && rules.contains(".allChanged();"),
            "geometry that moved under built chunks is never re-meshed");
    }

    /**
     * And handed back on the way out - which until 0.9.219 never read this client's own settings at all.
     */
    @Test
    void leaving_reads_this_clients_own_settings_back_and_asks_after() throws IOException {
        String leave = flat(body(common("com/trmtgtnh/client/ClientSide.java"), "leaveWorld"));
        int released = leave.indexOf("SurfaceRegistry.releaseServerTable()");
        int read = leave.indexOf("TrmtConfig.read();");
        assertTrue(read >= 0, "leaving never reads this client's own settings back, so the last server's stay in force");
        assertTrue(released >= 0 && released < read, "the server's table is still held when this client's own is read");
        assertTrue(leave.indexOf("ServerRules.appearancesMovedFrom(underServer)") > read, "whether the chains moved is asked before this client's own are read back");
        assertTrue(leave.indexOf("ServerRules.enabledMovedFrom(underServer)") > read, "whether the switches moved is asked before this client's own are read back");
        assertTrue(
            leave.contains("if(enabledHandedBack||tableHandedBack)ClientRules.resettleSurfaces();"),
            "a table handed back, or switches that moved, never rebuild the table for the next world");
        assertTrue(leave.contains("ClientRules.get().leave();"), "the last server's table bookkeeping and pricing survive into the next");
        assertTrue(leave.contains("InspectionCache.clear();"), "the last server's inspected numbers survive into the next world");
    }

    /** The server's table: compared, installed over a cleared world, and rechecked after an edit mid-visit. */
    @Test
    void the_servers_table_is_compared_installed_and_rechecked() throws IOException {
        String rules = common("com/trmtgtnh/client/ClientRules.java");
        String consider = flat(body(rules, "considerServerTable"));
        assertTrue(consider.contains("runningServer()!=null)return;"), "a host asks its own server for the table it shares");
        assertTrue(consider.contains("SurfaceRegistry.heldFingerprint()==fingerprint)return;"), "the table already held is asked for again");
        assertTrue(
            consider.contains("fingerprint==SurfaceRegistry.ownFingerprint()){if(holding)handBackServerTable();"),
            "a server that comes round to this client's own table is never handed its table back");
        String install = flat(body(rules, "installServerTable"));
        int cleared = install.indexOf("painter.restoreAll();");
        int hold = install.indexOf("SurfaceRegistry.holdServerTable(");
        assertTrue(cleared >= 0 && cleared < hold, "a server's table is installed over ground painted under this client's own");
        assertTrue(install.indexOf("surfacesMoved();", hold) > hold, "the ground is never painted again under the server's table");
        assertTrue(
            flat(body(common("com/trmtgtnh/client/ClientSide.java"), "applyLocally")).endsWith("ClientRules.get().recheckServerTable();"),
            "an edit made mid-visit is never compared with the server's table");
    }

    /** Block ids that move rebuild the table, heard on both loaders. */
    @Test
    void moved_block_ids_rebuild_the_surface_table_on_both_loaders() throws IOException {
        String forge = flat(loader("forge", "com/trmtgtnh/forge/TrmtForge.java"));
        assertTrue(
            forge.contains("FMLModIdMappingEventmoved)->com.trmtgtnh.server.ServerEvents.idsMoved()"),
            "Forge never hears the block ids move");
        String fabric = flat(loader("fabric", "com/trmtgtnh/fabric/TrmtFabric.java"));
        assertTrue(
            fabric.contains("RegistryIdRemapCallback.event(net.minecraft.core.Registry.BLOCK).register(moved->com.trmtgtnh.server.ServerEvents.idsMoved())"),
            "Fabric never hears the block ids move");
        String server = flat(body(common("com/trmtgtnh/server/ServerEvents.java"), "idsMoved"));
        assertTrue(
            server.contains("if(com.trmtgtnh.Client.idsMoved())return;") && server.contains("SurfaceRegistry.resolve();"),
            "the moved ids are heard and nothing is rebuilt: " + server);
        String client = flat(body(common("com/trmtgtnh/client/ClientSide.java"), "idsMoved"));
        assertTrue(client.contains("ClientRules.resettleSurfaces();"), "a client's table is not rebuilt under moved ids");
        assertTrue(client.contains("WearTextures::auditAfterIdMove"), "nothing says how the atlas reads under the moved ids");
        assertTrue(client.contains("WearIcons::reset"), "the screens' wear icons survive a move of the ids they were drawn under");
    }

    /**
     * What a tick with no world still owes, and the holds a rebuild of the pictures keeps.
     *
     * <p>
     * Both loaders ran the client's queue and serviced a waiting rebuild only with a world loaded, so a
     * rebuild asked for on leaving waited for the next world and ran in the middle of joining it. And a
     * rebuild ran under the wear editor while it was open, and while a single-player world was still
     * putting its ids back.
     */
    @Test
    void a_rebuild_of_the_pictures_runs_without_a_world_and_waits_for_the_editor() throws IOException {
        for (String[] tick : new String[][] { { "forge", "com/trmtgtnh/forge/TrmtForgeClient.java" },
            { "fabric", "com/trmtgtnh/fabric/TrmtFabricClient.java" } }) {
            String source = flat(
                loader(tick[0], tick[1]).replaceAll("(?s)/\\*.*?\\*/", " ")
                    .replaceAll("//[^\\n]*", " "));
            assertTrue(
                source.contains("ClientSide.leaveWorld();}ClientSide.idleTick();}else{"),
                tick[0] + " does nothing on a tick with no world, so a rebuild asked for on leaving waits for the next one");
        }
        String idle = flat(body(common("com/trmtgtnh/client/ClientSide.java"), "idleTick"));
        assertTrue(idle.contains("MainThread.drainClient();") && idle.contains("serviceRestitch();"), "the no-world tick does not do its work: " + idle);
        String service = flat(body(common("com/trmtgtnh/client/WearRestitch.java"), "serviceRestitch"));
        assertTrue(
            service.contains("mc.screeninstanceofcom.trmtgtnh.client.gui.GuiWearEditor||mc.screeninstanceofcom.trmtgtnh.client.gui.GuiWearTable"),
            "a rebuild of the pictures lands under the wear editor while it is open");
        assertTrue(service.contains("com.trmtgtnh.Trmt.serverThreadAlive()"), "a rebuild can run while a closing world's ids are still moving");
        String events = common("com/trmtgtnh/server/ServerEvents.java");
        assertTrue(flat(body(events, "serverStarting")).contains("Trmt.serverThreadIs(Thread.currentThread());"), "the server thread is never recorded");
        assertTrue(flat(body(events, "serverStopped")).contains("Trmt.serverThreadIs(null);"), "a stopped server's thread is never forgotten");
    }

    /**
     * This client's own player is told what the client side says.
     *
     * <p>
     * {@code Notices.say} took anything that was not a server's player for a machine and dropped the line,
     * so every notice said on the client side - the server owns the geometry, the config failed to read, the
     * server forces path visuals on - reached nobody.
     */
    @Test
    void the_clients_own_player_is_told() throws IOException {
        String nobody = flat(body(common("com/trmtgtnh/server/Notices.java"), "nobodyIsThere"));
        assertTrue(
            nobody.startsWith("if(player.level!=null&&player.level.isClientSide())returnfalse;"),
            "a line said to this client's own player is dropped as though nobody were there: " + nobody);
    }

    /**
     * Snow and carpet on a rut are drawn down with it, and keep their step against each other.
     *
     * <p>
     * The footing came down on both sides and the picture never did - {@code PhysicalDecay.isSettling} was
     * read by the collision path alone, and snow hung in the air over a snowed-over road.
     */
    @Test
    void snow_and_carpet_settle_in_the_picture_too() throws IOException {
        // The two that name game methods live in each loader's module - see CommonMixinsNeedNoRefmapTest -
        // and the Sodium family's, which names none, in common.
        assertTrue(
            clientMixins("common/src/main/resources/trmtgtnh-common.mixins.json").contains("\"MixinSettledFacesSodium\""),
            "MixinSettledFacesSodium is not applied on the client");
        for (String loader : new String[] { "forge", "fabric" }) {
            String client = clientMixins(loader + "/src/main/resources/trmtgtnh-" + loader + ".mixins.json");
            for (String one : new String[] { "MixinSettleOnWornGround", "MixinSettledFaces" }) {
                assertTrue(client.contains("\"" + one + "\""), one + " is not applied on the " + loader + " client");
            }
            String offset = flat(
                body(loader(loader, "com/trmtgtnh/" + loader + "/mixin/MixinSettleOnWornGround.java"), "trmt\\$settleOnWornGround"));
            assertTrue(
                offset.contains("Settling.offset((BlockState)(Object)this,level,pos,callback.getReturnValue())")
                    && offset.contains("callback.setReturnValue(settled)"),
                "the " + loader + " offset hook never asks how far the block has settled: " + offset);
            String kept = flat(
                body(loader(loader, "com/trmtgtnh/" + loader + "/mixin/MixinSettledFaces.java"), "trmt\\$keepSettledFace"));
            assertTrue(kept.contains("Settling.keepsFace(state,level,pos,face)"), loader + " never keeps a settled step: " + kept);
        }
        String sodium = flat(body(common("com/trmtgtnh/mixin/MixinSettledFacesSodium.java"), "trmt\\$keepSettledFace"));
        assertTrue(sodium.contains("Settling.keepsFace(state,level,pos,face)"), "the Sodium family never keeps a settled step: " + sodium);
    }

    /** The client list of one mixin config, flattened. */
    private static String clientMixins(String config) throws IOException {
        String mixins = flat(
            new String(Files.readAllBytes(new File(SourceTree.repoRoot(), config).toPath()), StandardCharsets.UTF_8));
        String client = mixins.substring(mixins.indexOf("\"client\":["));
        return client.substring(0, client.indexOf(']'));
    }

    private static String common(String relative) throws IOException {
        return String.join("\n", SourceTree.lines(relative));
    }

    private static String loader(String loader, String relative) throws IOException {
        File file = new File(SourceTree.repoRoot(), loader + "/src/main/java/" + relative);
        assertTrue(file.isFile(), file.getAbsolutePath() + " is not there");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Code with every run of spacing taken out, so a call wrapped across lines still reads whole. */
    private static String flat(String code) {
        return code.replaceAll("\\s+", "");
    }

    /** One method's body by its name, comments left out. The name is a regular expression. */
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
