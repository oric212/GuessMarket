package guessmarket.javafx.client;

import guessmarket.dto.ChatMessageDTO;
import guessmarket.dto.EventDTO;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.UserDTO;

import java.util.List;
import java.util.Map;

public record SyncSnapshot(
        List<EventDTO> events,
        List<UserDTO> users,
        Map<Integer, EventStateDTO> selectedEventDetails,
        List<ChatMessageDTO> chatMessages) {
    public SyncSnapshot {
        events = List.copyOf(events);
        users = List.copyOf(users);
        selectedEventDetails = Map.copyOf(selectedEventDetails);
        chatMessages = List.copyOf(chatMessages);
    }
}
