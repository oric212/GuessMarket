package guessmarket.server.api;

import guessmarket.dto.EventDTO;
import guessmarket.dto.EventStateDTO;
import guessmarket.server.ServerState;

import java.util.List;

public final class EventApiService {
    private final ServerState state;

    public EventApiService(ServerState state) {
        this.state = state;
    }

    public List<EventDTO> summaries() {
        return state.read(engine -> engine.getEventSummaries());
    }

    public EventStateDTO details(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            throw new ApiException(400, "MISSING_EVENT_NAME", "Event name cannot be blank");
        }
        try {
            return state.read(engine -> engine.getEventState(eventName));
        } catch (IllegalArgumentException error) {
            throw new ApiException(404, "EVENT_NOT_FOUND", "No event exists with name: " + eventName.trim());
        }
    }

    public EventStateDTO details(int eventId) {
        try {
            return state.read(engine -> engine.getEventState(eventId));
        } catch (IllegalArgumentException error) {
            throw new ApiException(404, "EVENT_NOT_FOUND", "No event exists with ID: " + eventId);
        }
    }
}
