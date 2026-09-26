package guessmarket.server.api;

import guessmarket.dto.EventDTO;

import java.util.List;

public record UploadResponse(boolean success, int eventsAdded, List<EventDTO> events) {
    public UploadResponse {
        events = List.copyOf(events);
    }
}
