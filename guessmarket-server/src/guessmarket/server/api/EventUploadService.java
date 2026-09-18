package guessmarket.server.api;

import guessmarket.dto.EventDTO;
import guessmarket.server.ServerState;
import guessmarket.service.DuplicateEventNameException;

import java.io.InputStream;
import java.util.List;

public final class EventUploadService {
    private final ServerState state;

    public EventUploadService(ServerState state) {
        this.state = state;
    }

    public UploadResponse upload(String sessionToken, InputStream xml) {
        try {
            List<EventDTO> added = state.importEvents(sessionToken, xml);
            return new UploadResponse(true, added.size(), added);
        } catch (ServerState.UnknownSessionException error) {
            throw new ApiException(401, "INVALID_SESSION", "The session token is missing or invalid");
        } catch (DuplicateEventNameException error) {
            throw new ApiException(409, "DUPLICATE_EVENT_NAME", error.getMessage());
        } catch (IllegalArgumentException error) {
            throw new ApiException(400, "INVALID_XML", error.getMessage());
        }
    }
}
