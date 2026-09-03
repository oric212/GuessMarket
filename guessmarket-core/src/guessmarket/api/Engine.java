package guessmarket.api;

import guessmarket.dto.EventDTO;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.PurchaseResultDTO;
import guessmarket.dto.UserDTO;
import guessmarket.dto.OrderSubmissionResultDTO;
import guessmarket.domain.OrderSide;

import java.util.List;

public interface Engine {

    void loadMarketFromXml(String xmlFilePath);

    List<EventDTO> getEventSummaries();

    EventStateDTO getEventState(int eventId);

    List<UserDTO> getUsers();

    UserDTO getUser(String username);

    EventStateDTO startEvent(String username, int eventId);

    PurchaseResultDTO purchaseShares(String username, int eventId, int optionIndex, int quantity);

    OrderSubmissionResultDTO submitOrder(
            String username, int eventId, int optionChoice,
            OrderSide side, int quantity, double pricePerShare);

    EventStateDTO closeEvent(String username, int eventId, int winningOptionIndex);

    void saveState(String filePath);
    void loadState(String filePath);
}
