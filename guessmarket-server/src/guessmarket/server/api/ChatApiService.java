package guessmarket.server.api;

import guessmarket.dto.ChatMessageDTO;
import guessmarket.server.ServerState;

import java.util.List;

public final class ChatApiService {
    public static final int MAX_MESSAGE_LENGTH = 500;
    private final ServerState state;

    public ChatApiService(ServerState state) { this.state = state; }

    public ChatMessageDTO send(String token, ChatSendRequest request) {
        String text = request == null ? null : request.message();
        if (text == null || text.isBlank()) {
            throw new ApiException(400, "BLANK_MESSAGE", "Chat message cannot be blank");
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new ApiException(400, "MESSAGE_TOO_LONG",
                    "Chat message cannot exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
        try {
            return state.sendChatMessage(token, trimmed);
        } catch (ServerState.UnknownSessionException error) {
            throw invalidSession();
        }
    }

    public List<ChatMessageDTO> messagesAfter(String token, long after) {
        if (after < 0) throw new ApiException(400, "INVALID_SEQUENCE", "after must be non-negative");
        try {
            return state.chatMessagesAfter(token, after);
        } catch (ServerState.UnknownSessionException error) {
            throw invalidSession();
        }
    }

    private static ApiException invalidSession() {
        return new ApiException(401, "INVALID_SESSION", "The session token is missing or invalid");
    }
}
