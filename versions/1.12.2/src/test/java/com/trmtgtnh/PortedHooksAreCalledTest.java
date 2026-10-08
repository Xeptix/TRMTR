package com.trmtgtnh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
 * file, which the method's own declaration would satisfy with every call deleted.
 */
class PortedHooksAreCalledTest {

    /**
     * A player head set last on the golem's shape stands the golem up.
     *
     * <p>
     * The guide's own recipe. {@code GolemBuilder.onHeadPlaced} was ported intact and called by
     * nobody, so the shape and the head did nothing at all, and a golem came only from a loot egg or
     * the demonstrate yard.
     */
    @Test
    void a_head_set_on_the_shape_asks_the_golem_builder() throws IOException {
        String events = source("com/trmtgtnh/server/ServerEvents.java");
        assertTrue(
            Pattern.compile("@SubscribeEvent\\s+public void onHeadPlaced\\(BlockEvent\\.PlaceEvent event\\)")
                .matcher(events)
                .find(),
            "ServerEvents.onHeadPlaced is not a handler of the place event, so nothing calls it");
        String handler = flat(body(events, "onHeadPlaced"));
        assertTrue(handler.contains("Blocks.SKULL"), "the golem is asked about blocks that are not skulls, or none");
        assertTrue(
            handler.contains(
                "GolemBuilder.onHeadPlaced(event.getWorld(),pos.getX(),pos.getY(),pos.getZ(),event.getPlayer())"),
            "placing a head never asks the golem builder whether it completes the shape: " + handler);
    }

    /**
     * A server's rules are carried out, not only held.
     *
     * <p>
     * The 1.7.10 edition rebuilds the chains and the decay mode from what it has just held, shows or
     * takes down the overlay the server forces, rebuilds the table when the server's switches differ,
     * and re-meshes when the geometry moved. This edition held the rules and did none of it, so the one
     * question it did ask - whether the chains reach new materials - compared the chains with
     * themselves and was never true.
     */
    @Test
    void a_servers_rules_are_carried_out_on_join() throws IOException {
        String rules = flat(body(source("com/trmtgtnh/client/ClientProxy.java"), "applyServerRules"));
        int held = rules.indexOf("ServerRules.hold(");
        assertTrue(held >= 0, "the server's rules are never held");
        for (String step : new String[] { "ErosionChain.rebuild();", "PhysicalDecay.refresh();" }) {
            int at = rules.indexOf(step);
            assertTrue(at > held, "the rules are held and " + step + " is not asked for after them: " + rules);
        }
        assertTrue(
            rules.contains(".repaintAll();") && rules.contains(".restoreAll();"),
            "a forced overlay is held and never shown, or never taken down again");
        assertTrue(
            rules.contains("ServerRules.enabledMovedFrom(before)){resettleSurfaces();"),
            "a family the server switched on is never put into the table");
        assertTrue(
            rules.contains("ServerRules.appearancesMovedFrom(before)){requestRestitch("),
            "chains reaching new materials never ask for their pictures");
        assertTrue(
            rules.contains("ServerRules.overrode()>0&&restitchWanted==null") && rules.contains("loadRenderers()"),
            "geometry that moved under built chunks is never re-meshed");
    }

    /**
     * And handed back on the way out, asked in the right order.
     *
     * <p>
     * Whether the chains moved is a question about the chains this client's own file builds, so it is
     * asked after that file is read back - asked before, as it was until 0.9.219, it compared the
     * server's chains with themselves. And a table or switches that moved rebuild the table, or the
     * next world opened wears by the last server's.
     */
    @Test
    void leaving_hands_everything_back_in_order() throws IOException {
        String back = flat(body(source("com/trmtgtnh/client/ClientProxy.java"), "handBack"));
        int read = back.indexOf("TrmtConfig.read();");
        int asked = back.indexOf("ServerRules.appearancesMovedFrom(underServer)");
        int enabled = back.indexOf("ServerRules.enabledMovedFrom(underServer)");
        int released = back.indexOf("SurfaceRegistry.releaseServerTable()");
        assertTrue(read >= 0, "leaving never reads this client's own settings back");
        assertTrue(asked > read, "whether the chains moved is asked before this client's own are read back: " + back);
        assertTrue(enabled > read, "whether the switches moved is asked before this client's own are read back");
        assertTrue(released >= 0 && released < read, "the server's table is still held when this client's own is read");
        assertTrue(
            back.contains("if(enabledHandedBack||tableHandedBack)resettleSurfaces();"),
            "a table handed back, or switches that moved, never rebuild the table for the next world");
        assertTrue(
            back.contains("InspectionCache.clear();"),
            "the last server's inspected numbers survive into the next world");
    }

    /**
     * The server's table: asked for only when it is not already in use, installed over a cleared
     * world, and compared again after an edit made mid-visit.
     */
    @Test
    void the_servers_table_is_compared_installed_and_rechecked() throws IOException {
        String proxy = source("com/trmtgtnh/client/ClientProxy.java");
        String consider = flat(body(proxy, "considerServerTable"));
        assertTrue(
            consider.contains("runningServer()!=null)return;"),
            "a host asks its own server for the table it shares");
        assertTrue(
            consider.contains("SurfaceRegistry.heldFingerprint()==fingerprint)return;"),
            "the table already held is asked for again");
        assertTrue(
            consider.contains(
                "fingerprint==com.trmtgtnh.surface.SurfaceRegistry.ownFingerprint()){if(holding)handBackServerTable();"),
            "a server that comes round to this client's own table is never handed its table back");
        String install = flat(body(proxy, "installServerTable"));
        int cleared = install.indexOf("painter.restoreAll();");
        int hold = install.indexOf("SurfaceRegistry.holdServerTable(");
        assertTrue(
            cleared >= 0 && cleared < hold,
            "a server's table is installed over ground painted under this client's own");
        assertTrue(
            install.indexOf("surfacesMoved();", hold) > hold,
            "the ground is never painted again under the server's table");
        assertTrue(
            flat(body(proxy, "applyLocally")).endsWith("recheckServerTable();"),
            "an edit made mid-visit is never compared with the server's table");
    }

    /**
     * Block ids that move rebuild everything keyed by them.
     *
     * <p>
     * The surface table is keyed by a block's number, which is only good for the registry it was read
     * under. With no handler for the move, a world from another mod list, or a server with its own
     * numbering, wore the wrong modded ground; {@code WearTextures.auditAfterIdMove}, ported to report on
     * exactly this, was never called either.
     */
    @Test
    void moved_block_ids_rebuild_the_surface_table() throws IOException {
        String trmt = source("com/trmtgtnh/Trmt.java");
        assertTrue(
            Pattern.compile(
                "@Mod\\.EventHandler\\s+public void idsMoved\\(net\\.minecraftforge\\.fml\\.common\\.event\\.FMLModIdMappingEvent event\\)")
                .matcher(trmt)
                .find(),
            "nothing hears the block ids move");
        assertTrue(flat(body(trmt, "idsMoved")).contains("proxy.idsMoved();"), "the ids move and nothing is rebuilt");
        String common = flat(body(source("com/trmtgtnh/CommonProxy.java"), "idsMoved"));
        assertTrue(
            common.contains("SurfaceRegistry.resolve();") && common.contains("PhysicalDecay.markSinkableBlocks();"),
            "a server's table and stamps are not rebuilt under moved ids: " + common);
        String client = flat(body(source("com/trmtgtnh/client/ClientProxy.java"), "idsMoved"));
        assertTrue(client.contains("resettleSurfaces();"), "a client's table is not rebuilt under moved ids");
        assertTrue(
            client.contains("WearTextures.auditAfterIdMove();"),
            "nothing says how the atlas reads under the moved ids");
        assertTrue(
            client.contains("WearIcons.reset();"),
            "the screens' wear icons survive a move of the ids they were drawn under");
    }

    /**
     * Snow and carpet on a rut are drawn down with it, and stood on at that height, on the client.
     *
     * <p>
     * {@code PhysicalDecay.isSettling} was read by the collision hook alone, and that hook left the
     * client's world alone entirely: the server lowered a snow layer's footing, the client neither drew
     * it down nor stood on it lower, and the config's promise to draw snow and carpet down was kept by
     * nothing.
     */
    @Test
    void snow_and_carpet_settle_on_the_client() throws IOException {
        java.io.File json = new java.io.File(
            SourceTree.mainJava()
                .getParentFile(),
            "resources/mixins.trmtgtnh.json");
        String mixins = new String(
            java.nio.file.Files.readAllBytes(json.toPath()),
            java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", "");
        int client = mixins.indexOf("\"client\":[");
        assertTrue(client >= 0, "no client mixins at all");
        String clientList = mixins.substring(client, mixins.indexOf(']', client));
        assertTrue(clientList.contains("\"MixinSettleOnWornGround\""), "snow and carpet are never drawn down");
        assertTrue(
            clientList.contains("\"MixinSettledSnowFaces\""),
            "the step between two settled snow layers is left open");

        String offset = flat(
            body(source("com/trmtgtnh/mixin/MixinSettleOnWornGround.java"), "trmt\\$settleOnWornGround"));
        assertTrue(
            offset.contains("Settling.offset(state,world,pos,callback.getReturnValue())")
                && offset.contains("callback.setReturnValue(settled)"),
            "the offset hook never asks how far the block has settled: " + offset);
        String faces = flat(body(source("com/trmtgtnh/mixin/MixinSettledSnowFaces.java"), "trmt\\$keepSettledFace"));
        assertTrue(faces.contains("Settling.keepsFace(state,world,pos,side)"), "a settled snow face is never kept");

        String hook = flat(body(source("com/trmtgtnh/erosion/PhysicalDecay.java"), "onCollisionBoxes"));
        assertTrue(
            !hook.contains("world.isRemote)return;"),
            "the collision hook still leaves the client's world alone");
        assertTrue(
            hook.contains("if(!client&&sinkable.contains(block))") && hook.contains("elseif(settling.contains(block))"),
            "on the client the hook must settle snow and carpet and leave worn ground to its ghost: " + hook);
    }

    /**
     * The moving layers behind worn Chisel stone are given their allowance every tick.
     *
     * <p>
     * The allowance starts closed and {@code InnerLayers.newTick} is the only thing that opens it; with no
     * caller, every upload was turned away and the lava and water never moved past their first frame.
     * Above the world check, as the 1.7.10 edition has it.
     */
    @Test
    void the_moving_layers_are_given_their_allowance_every_tick() throws IOException {
        String tick = flat(body(source("com/trmtgtnh/client/ClientProxy.java"), "onClientTick"));
        int opened = tick.indexOf("InnerLayers.newTick();");
        int world = tick.indexOf("if(mc.world==null");
        assertTrue(opened >= 0, "nothing opens the moving layers' allowance, so they never move");
        assertTrue(world < 0 || opened < world, "the allowance is opened only while a world is loaded");
    }

    /**
     * A rebuild of the pictures waits for the wear editor, and runs with no world loaded.
     *
     * <p>
     * The 1.7.10 edition holds a rebuild while its own screens are open - neither pauses the game - and
     * services one above its world check, so a rebuild asked for on leaving runs before the next world
     * rather than in the middle of joining it. This edition had neither until 0.9.219.
     */
    @Test
    void a_rebuild_of_the_pictures_waits_for_the_editor_and_runs_without_a_world() throws IOException {
        String proxy = source("com/trmtgtnh/client/ClientProxy.java");
        String service = flat(body(proxy, "serviceRestitch"));
        assertTrue(
            service.contains(
                "screeninstanceofcom.trmtgtnh.client.gui.GuiWearEditor||screeninstanceofcom.trmtgtnh.client.gui.GuiWearTable"),
            "a rebuild of the pictures lands under the wear editor while it is open");
        String tick = flat(body(proxy, "onClientTick"));
        int noWorld = tick.indexOf("if(mc.world==null||mc.player==null){");
        assertTrue(noWorld >= 0, "the tick no longer tells a world from none");
        int back = tick.indexOf("handBack();", noWorld);
        int serviced = tick.indexOf("serviceRestitch();", noWorld);
        int left = tick.indexOf("return;", noWorld);
        assertTrue(
            back > noWorld && serviced > back && serviced < left,
            "with no world loaded a waiting rebuild is not serviced after the hand-back: " + tick);
    }

    private static String source(String relative) throws IOException {
        return String.join("\n", SourceTree.lines(relative));
    }

    /** Code with every run of spacing taken out, so a call wrapped by the formatter still reads whole. */
    private static String flat(String code) {
        return code.replaceAll("\\s+", "");
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
