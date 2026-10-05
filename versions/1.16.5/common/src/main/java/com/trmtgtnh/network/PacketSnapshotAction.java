package com.trmtgtnh.network;

import net.minecraft.server.level.ServerPlayer;

import com.trmtgtnh.item.ItemSnapshotTool;
import com.trmtgtnh.server.SnapshotStore;
import com.trmtgtnh.util.MainThread;

import io.netty.buffer.ByteBuf;

/**
 * Client to server: something was pressed on the snapshot screen.
 *
 * <p>
 * One number, and the server decides what it means and whether the sender may. Everything that
 * could actually change a config is checked against the same permission the in-world gestures
 * are, on the server, of the server's own player object - the packet is never trusted for more
 * than which button was pressed.
 */
public class PacketSnapshotAction implements Message {

    public static final int REQUEST = 0;
    public static final int COMMIT_LEFT = 1;
    public static final int COMMIT_RIGHT = 2;
    public static final int CLEAR_LEFT = 3;
    public static final int CLEAR_RIGHT = 4;
    public static final int UNDO = 5;

    private int action;

    public PacketSnapshotAction() {}

    public PacketSnapshotAction(int action) {
        this.action = action;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readUnsignedByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action & 0xFF);
    }

    public static class Handler implements Receiver<PacketSnapshotAction> {

        @Override
        public void onMessage(final PacketSnapshotAction message, ServerPlayer from) {
            final ServerPlayer player = from;
            if (player == null) return;

            MainThread.onServer(new Runnable() {

                @Override
                public void run() {
                    if (!ItemSnapshotTool.allowed(player)) return;
                    SnapshotStore store = SnapshotStore.get();
                    java.util.UUID id = player.getUUID();

                    switch (message.action) {
                        case COMMIT_LEFT:
                        case COMMIT_RIGHT: {
                            int which = message.action == COMMIT_LEFT ? SnapshotStore.LEFT : SnapshotStore.RIGHT;
                            String snapshot = store.slot(id, which);
                            if (snapshot != null) ItemSnapshotTool.apply(player, snapshot);
                            break;
                        }
                        case CLEAR_LEFT:
                            store.clear(id, SnapshotStore.LEFT);
                            break;
                        case CLEAR_RIGHT:
                            store.clear(id, SnapshotStore.RIGHT);
                            break;
                        case UNDO: {
                            String baseline = store.baseline(id);
                            if (baseline != null) {
                                // Forgotten only once it has been put back. It is the only copy of the
                                // settings from before the change, and an undo that failed - a locked
                                // file, a reload that refused it - used to throw it away all the same and
                                // grey the button out with nothing said.
                                if (com.trmtgtnh.server.ConfigSnapshot.apply(baseline)) {
                                    store.forgetBaseline(id);
                                } else {
                                    ItemSnapshotTool.failed(player);
                                }
                            }
                            break;
                        }
                        case REQUEST:
                        default:
                            break;
                    }
                    ItemSnapshotTool.pushState(player);
                }
            });
            return;
        }
    }
}
