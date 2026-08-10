package src.guessmarket.engine.domain;

import java.util.List;

public interface Engine {

    void loadMarketFromXml(String xmlFilePath);

    List<EventDTO> getEventSummaries();

    EventStateDTO getEventState(int eventId);

    PurchaseResultDTO purchaseShares(int eventId, int optionIndex, int quantity);

    CloseResultDTO closeEvent(int eventId, int winningOptionIndex);
}
