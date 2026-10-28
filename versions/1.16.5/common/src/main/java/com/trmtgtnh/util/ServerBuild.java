package com.trmtgtnh.util;

/**
 * Whether the server a client has joined runs an older build of this mod than the client does (0.9.228; Xep: "Add it,
 * every edition"), from the server tail of its rules.
 *
 * <p>
 * The server has always told an older client to update, from the protocol in the client's hello; nothing told a newer
 * client anything, because no packet from the server carried its build. From 0.9.228 the rules carry one more tail, the
 * server's protocol and its build's name, which an older client leaves unread. So a server whose rules end before that
 * tail is 0.9.227 or older; one that sends it is older when its protocol is below the client's, or its build's leading
 * number is - and the same number in another stage, a snapshot of the same version, is not older, so testers on early
 * builds are not told about each other. Asked the same way in every edition.
 *
 * <p>
 * Here, beside {@link UpdateCheck}, because the leading number is that class's own question, asked as it is - and
 * UpdateCheck is shared core, held byte for byte across the editions, so nothing is added to it.
 */
public final class ServerBuild {

    /** The last build whose rules carry no server tail: what a client says a silent server is, or older. */
    public static final String LAST_WITHOUT_TAIL = "0.9.227";

    private ServerBuild() {}

    /**
     * Whether the server is older than this client.
     *
     * @param tailSent       whether the server's rules carried the server tail at all
     * @param serverProtocol the protocol the server named in it
     * @param serverBuild    the build the server named in it
     * @param clientProtocol this client's own protocol
     * @param clientBuild    this client's own build
     */
    public static boolean older(boolean tailSent, int serverProtocol, String serverBuild, int clientProtocol,
        String clientBuild) {
        if (!tailSent) return true;
        if (serverProtocol < clientProtocol) return true;
        String theirs = UpdateCheck.leading(serverBuild);
        String ours = UpdateCheck.leading(clientBuild);
        // A build with no number - a development build named some other way - says nothing either way.
        if (theirs == null || ours == null) return false;
        return UpdateCheck.compare(theirs, ours) < 0;
    }
}
