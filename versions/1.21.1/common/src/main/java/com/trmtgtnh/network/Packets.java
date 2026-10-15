package com.trmtgtnh.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Which messages exist, in what order, and which end receives them.
 *
 * <p>
 * This is one list because the two loaders need two different things from it and must not be allowed
 * to disagree. Forge carries a number per message on a single channel; Fabric carries a
 * {@code ResourceLocation} per message and has no channel at all. Each loader module walks this list
 * and turns it into what it needs, the same way {@code ModBlocks} hands over one list of blocks and
 * lets each loader register them its own way.
 *
 * <p>
 * <strong>The ids are a wire format.</strong> Forge puts the number on the wire, so a client and a
 * server that disagree about which number means which message do not fail to connect - they
 * misread each other. That is why each id is written out here rather than taken from the list's
 * position: a packet that arrives later in the port does not shift the ones already shipped.
 *
 * <p>
 * The ids and the order are the 1.12.2 edition's, which is deliberate. Nothing can carry a packet
 * between the two editions - they are different Minecraft versions - but keeping the numbering means
 * the two files can be read side by side, and a packet's id is one less thing that silently differs
 * between editions of the same mod.
 *
 * <p>
 * <strong>Nothing is reserved any more.</strong> Seventeen belonged to {@code PacketAirSwing}, the
 * one message this port deferred, and it has arrived - so every id from nought to twenty is the
 * message the other editions give it, which is what the numbering was kept for.
 *
 * <p>
 * The golem's was the find worth recording: its send had already landed and its entry had not, so
 * every typed coordinate would have been refused by the channel as an unknown message. Nothing
 * noticed, because nothing had opened that screen yet - which is the whole argument for the screen
 * spike that did.
 *
 */
public final class Packets {

    /** Which end a message is sent to - which is to say, which end handles it. */
    public enum To {
        SERVER, CLIENT
    }

    /** One message type, and everything either loader needs in order to carry it. */
    public static final class Entry<T extends Message> {

        /** Its number on the wire. Forge puts this on the wire; Fabric uses {@link #name}. */
        public final int id;

        /** Its name on the wire, which is what Fabric addresses a message by. */
        public final String name;

        public final Class<T> type;

        /** Makes an empty one for the decoder to fill. */
        public final Supplier<T> make;

        public final Receiver<T> receiver;

        public final To to;

        Entry(int id, String name, Class<T> type, Supplier<T> make, Receiver<T> receiver, To to) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.make = make;
            this.receiver = receiver;
            this.to = to;
        }

        /** Decodes and handles one message. Saves each loader writing the same three lines. */
        public void accept(io.netty.buffer.ByteBuf buf, net.minecraft.server.level.ServerPlayer from) {
            T message = make.get();
            message.fromBytes(buf);
            receiver.onMessage(message, from);
        }
    }

    /** The ids that belong to messages not ported yet. See the class javadoc. */
    public static final int[] RESERVED = {};

    private Packets() {}

    private static <T extends Message> Entry<T> at(int id, String name, Class<T> type, Supplier<T> make,
        Receiver<T> receiver, To to) {
        return new Entry<T>(id, name, type, make, receiver, to);
    }

    /** Every message this edition carries, in id order. */
    public static List<Entry<?>> all() {
        List<Entry<?>> list = new ArrayList<Entry<?>>();

        list.add(
            at(0, "hello", PacketHello.class, PacketHello::new, new PacketHello.Handler(), To.SERVER));
        list.add(
            at(
                1,
                "chunk_erosion",
                PacketChunkErosion.class,
                PacketChunkErosion::new,
                new PacketChunkErosion.Handler(),
                To.CLIENT));
        list.add(
            at(
                2,
                "erosion_delta",
                PacketErosionDelta.class,
                PacketErosionDelta::new,
                new PacketErosionDelta.Handler(),
                To.CLIENT));
        list.add(
            at(
                3,
                "clear_all",
                PacketClearAll.class,
                PacketClearAll::new,
                new PacketClearAll.Handler(),
                To.CLIENT));
        list.add(
            at(
                4,
                "server_rules",
                PacketServerRules.class,
                PacketServerRules::new,
                new PacketServerRules.Handler(),
                To.CLIENT));
        list.add(
            at(
                5,
                "inspect",
                PacketInspect.class,
                PacketInspect::new,
                new PacketInspect.Handler(),
                To.SERVER));
        list.add(
            at(
                6,
                "inspect_result",
                PacketInspectResult.class,
                PacketInspectResult::new,
                new PacketInspectResult.Handler(),
                To.CLIENT));
        list.add(
            at(
                7,
                "tamper_settings",
                PacketTamperSettings.class,
                PacketTamperSettings::new,
                new PacketTamperSettings.Handler(),
                To.SERVER));
        list.add(
            at(
                8,
                "push_config",
                PacketPushConfig.class,
                PacketPushConfig::new,
                new PacketPushConfig.Handler(),
                To.SERVER));
        list.add(
            at(
                9,
                "edit_family",
                PacketEditFamily.class,
                PacketEditFamily::new,
                new PacketEditFamily.Handler(),
                To.SERVER));
        list.add(
            at(
                10,
                "modifier",
                PacketModifier.class,
                PacketModifier::new,
                new PacketModifier.Handler(),
                To.SERVER));
        list.add(
            at(
                11,
                "edit_mob",
                PacketEditMob.class,
                PacketEditMob::new,
                new PacketEditMob.Handler(),
                To.SERVER));
        list.add(
            at(
                12,
                "dev_preview",
                PacketDevPreview.class,
                PacketDevPreview::new,
                new PacketDevPreview.Handler(),
                To.CLIENT));
        list.add(
            at(
                13,
                "snapshot_state",
                PacketSnapshotState.class,
                PacketSnapshotState::new,
                new PacketSnapshotState.Handler(),
                To.CLIENT));
        list.add(
            at(
                14,
                "snapshot_action",
                PacketSnapshotAction.class,
                PacketSnapshotAction::new,
                new PacketSnapshotAction.Handler(),
                To.SERVER));
        list.add(
            at(
                15,
                "chunk_light",
                PacketChunkLight.class,
                PacketChunkLight::new,
                new PacketChunkLight.Handler(),
                To.CLIENT));
        list.add(
            at(
                16,
                "light_delta",
                PacketLightDelta.class,
                PacketLightDelta::new,
                new PacketLightDelta.Handler(),
                To.CLIENT));
        list.add(
            at(
                17,
                "air_swing",
                PacketAirSwing.class,
                PacketAirSwing::new,
                new PacketAirSwing.Handler(),
                To.SERVER));
        list.add(
            at(
                18,
                "golem_home",
                PacketGolemHome.class,
                PacketGolemHome::new,
                new PacketGolemHome.Handler(),
                To.SERVER));
        list.add(
            at(
                19,
                "surface_table_request",
                PacketSurfaceTableRequest.class,
                PacketSurfaceTableRequest::new,
                new PacketSurfaceTableRequest.Handler(),
                To.SERVER));
        list.add(
            at(
                20,
                "surface_table",
                PacketSurfaceTable.class,
                PacketSurfaceTable::new,
                new PacketSurfaceTable.Handler(),
                To.CLIENT));

        return Collections.unmodifiableList(list);
    }
}
