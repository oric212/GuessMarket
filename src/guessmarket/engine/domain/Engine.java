package guessmarket.engine.domain;

import guessmarket.dto.EventDTO;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.PurchaseResultDTO;
import java.util.List;

public interface Engine {

    void loadMarketFromXml(String xmlFilePath);

    List<EventDTO> getEventSummaries();

    EventStateDTO getEventState(int eventId);

    PurchaseResultDTO purchaseShares(int eventId, int optionIndex, int quantity);

    EventStateDTO closeEvent(int eventId, int winningOptionIndex);
}
