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

    PurchaseResultDTO purchaseShares(int eventId, int optionIndex, int quantity);

    EventStateDTO closeEvent(int eventId, int winningOptionIndex);

    void saveState(String filePath);
    void loadState(String filePath);
}
