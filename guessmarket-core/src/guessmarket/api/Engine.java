package guessmarket.api;

import guessmarket.dto.EventDTO;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.PurchaseResultDTO;
import guessmarket.dto.UserDTO;

import java.util.List;

public interface Engine {

    void loadMarketFromXml(String xmlFilePath);

    List<EventDTO> getEventSummaries();

    EventStateDTO getEventState(int eventId);

    List<UserDTO> getUsers();

    UserDTO getUser(String username);

    EventStateDTO startEvent(String username, int eventId);

    PurchaseResultDTO purchaseShares(String username, int eventId, int optionIndex, int quantity);

    EventStateDTO closeEvent(String username, int eventId, int winningOptionIndex);

    void saveState(String filePath);
    void loadState(String filePath);
}
